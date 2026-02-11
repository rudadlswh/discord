import AVFoundation
import CallKit
import Foundation
import WebRTC

enum CallState {
    case idle
    case outgoing
    case incoming
    case connecting
    case connected
}

struct CallSession: Equatable {
    let callId: String
    let callUUID: UUID
    let channelId: String
    let friendId: String
    let friendName: String
    let isOutgoing: Bool
}

@MainActor
final class CallManager: NSObject, ObservableObject {
    static let shared = CallManager()

    @Published private(set) var state: CallState = .idle
    @Published private(set) var activeCall: CallSession? = nil
    @Published var errorMessage: String? = nil

    private var provider: CXProvider?
    private let callController = CXCallController()
    private var signalingTask: URLSessionWebSocketTask?
    private var webRtcClient: WebRtcClient?
    private var iceServers: [RTCIceServer] = []
    private var pendingRemoteCandidates: [RTCIceCandidate] = []

    private override init() {
        super.init()
    }

    func start() {
        setupCallKit()
    }

    func registerDeviceIfNeeded() {
    }

    func stop() {
        signalingTask?.cancel(with: .goingAway, reason: nil)
        signalingTask = nil
        provider?.invalidate()
        provider = nil
        endWebRtcSession()
        state = .idle
        activeCall = nil
    }

    func startOutgoingCall(thread: DirectThread) {
        guard state == .idle else { return }
        let callUUID = UUID()
        let callId = callUUID.uuidString
        activeCall = CallSession(
            callId: callId,
            callUUID: callUUID,
            channelId: thread.id,
            friendId: thread.friendId,
            friendName: thread.friendDisplayName,
            isOutgoing: true
        )
        state = .outgoing
        startCallKitOutgoing(callUUID: callUUID, handle: thread.friendDisplayName)
        connectSignaling(channelId: thread.id)
        Task { await sendCallRequest(callId: callId, targetUserId: thread.friendId) }
    }

    func endCall() {
        guard let call = activeCall else { return }
        Task { await sendSignal(type: "call_end", targetUserId: call.friendId, payload: ["callId": call.callId]) }
        endCallKit(callUUID: call.callUUID)
        cleanupCall()
    }

    func handleIncomingCall(callId: String, channelId: String, fromUserId: String, callerName: String) {
        guard state == .idle else { return }
        let callUUID = UUID()
        activeCall = CallSession(
            callId: callId,
            callUUID: callUUID,
            channelId: channelId,
            friendId: fromUserId,
            friendName: callerName,
            isOutgoing: false
        )
        state = .incoming
        reportIncomingCall(callUUID: callUUID, handle: callerName)
    }

    private func setupCallKit() {
        let config = CXProviderConfiguration(localizedName: "Discord Lite")
        config.supportsVideo = false
        config.includesCallsInRecents = false
        config.maximumCallsPerCallGroup = 1
        config.supportedHandleTypes = [.generic]

        let provider = CXProvider(configuration: config)
        provider.setDelegate(self, queue: nil)
        self.provider = provider
    }

    private func startCallKitOutgoing(callUUID: UUID, handle: String) {
        let handle = CXHandle(type: .generic, value: handle)
        let startCall = CXStartCallAction(call: callUUID, handle: handle)
        let transaction = CXTransaction(action: startCall)
        callController.request(transaction) { _ in }

        let update = CXCallUpdate()
        update.remoteHandle = handle
        update.hasVideo = false
        provider?.reportCall(with: callUUID, updated: update)
    }

    private func reportIncomingCall(callUUID: UUID, handle: String) {
        let update = CXCallUpdate()
        update.remoteHandle = CXHandle(type: .generic, value: handle)
        update.hasVideo = false
        provider?.reportNewIncomingCall(with: callUUID, update: update, completion: { _ in })
    }

    private func endCallKit(callUUID: UUID) {
        let endCall = CXEndCallAction(call: callUUID)
        let transaction = CXTransaction(action: endCall)
        callController.request(transaction) { _ in }
    }

    private func connectSignaling(channelId: String) {
        guard signalingTask == nil else { return }
        guard let token = AppPrefs.getToken(), !token.isEmpty else { return }
        let baseUrl = AppPrefs.getBaseUrl(defaultValue: AuthService.defaultBaseUrl)
        guard let url = buildWebSocketUrl(baseUrl: baseUrl, path: "/ws/signaling/\(channelId)") else {
            return
        }

        var request = URLRequest(url: url)
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        let task = URLSession.shared.webSocketTask(with: request)
        signalingTask = task
        task.resume()
        listenForSignals()
    }

    private func disconnectSignaling() {
        signalingTask?.cancel(with: .goingAway, reason: nil)
        signalingTask = nil
    }

    private func listenForSignals() {
        signalingTask?.receive { [weak self] result in
            guard let self else { return }
            switch result {
            case .success(let message):
                switch message {
                case .string(let text):
                    Task { @MainActor in self.handleSignalText(text) }
                case .data(let data):
                    if let text = String(data: data, encoding: .utf8) {
                        Task { @MainActor in self.handleSignalText(text) }
                    }
                @unknown default:
                    break
                }
            case .failure:
                Task { @MainActor in
                    self.disconnectSignaling()
                }
            }

            Task { @MainActor in
                if self.signalingTask != nil {
                    self.listenForSignals()
                }
            }
        }
    }

    private func handleSignalText(_ text: String) {
        guard let data = text.data(using: .utf8) else { return }
        guard let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return }
        guard let type = json["type"] as? String else { return }

        let payload = json["payload"] as? [String: Any]
        let callId = (payload?["callId"] as? String) ?? (json["callId"] as? String) ?? ""

        switch type {
        case "call_request":
            guard let channelId = payload?["channelId"] as? String,
                  let fromUserId = json["fromUserId"] as? String else { return }
            let callerName = payload?["callerName"] as? String ?? "상대방"
            handleIncomingCall(callId: callId, channelId: channelId, fromUserId: fromUserId, callerName: callerName)
        case "call_accept":
            if state == .outgoing {
                Task { await startOfferIfPossible(callId: callId) }
            }
        case "call_reject":
            if state == .outgoing {
                errorMessage = "통화가 거절되었습니다."
                if let call = activeCall {
                    endCallKit(callUUID: call.callUUID)
                }
                cleanupCall()
            }
        case "call_end":
            if state != .idle {
                errorMessage = "통화가 종료되었습니다."
                if let call = activeCall {
                    endCallKit(callUUID: call.callUUID)
                }
                cleanupCall()
            }
        case "sdp_offer":
            if let sdp = payload?["sdp"] as? String {
                Task { await handleRemoteOffer(sdp: sdp) }
            }
        case "sdp_answer":
            if let sdp = payload?["sdp"] as? String {
                Task { await handleRemoteAnswer(sdp: sdp) }
            }
        case "ice_candidate":
            if let candidate = payload?["candidate"] as? String,
               let sdpMid = payload?["sdpMid"] as? String,
               let sdpMLineIndex = payload?["sdpMLineIndex"] as? Int {
                let ice = RTCIceCandidate(sdp: candidate, sdpMLineIndex: Int32(sdpMLineIndex), sdpMid: sdpMid)
                addRemoteCandidate(ice)
            }
        default:
            break
        }
    }

    private func sendCallRequest(callId: String, targetUserId: String) async {
        guard let call = activeCall else { return }
        await fetchIceConfigIfNeeded()
        await sendSignal(
            type: "call_request",
            targetUserId: targetUserId,
            payload: [
                "callId": callId,
                "channelId": call.channelId,
                "callerName": AppPrefs.getDisplayName() ?? "Unknown"
            ]
        )
    }

    private func startOfferIfPossible(callId: String) async {
        guard let call = activeCall, call.callId == callId else { return }
        await fetchIceConfigIfNeeded()
        setupWebRtcIfNeeded()
        state = .connecting
        webRtcClient?.createOffer { [weak self] sdp in
            Task { @MainActor in
                await self?.sendSignal(
                    type: "sdp_offer",
                    targetUserId: call.friendId,
                    payload: ["callId": call.callId, "sdp": sdp]
                )
            }
        }
    }

    private func handleRemoteOffer(sdp: String) async {
        await fetchIceConfigIfNeeded()
        setupWebRtcIfNeeded()
        state = .connecting
        webRtcClient?.setRemoteDescription(type: .offer, sdp: sdp) { [weak self] in
            self?.drainPendingCandidates()
            self?.webRtcClient?.createAnswer { answer in
                Task { @MainActor in
                    guard let call = self?.activeCall else { return }
                    await self?.sendSignal(
                        type: "sdp_answer",
                        targetUserId: call.friendId,
                        payload: ["callId": call.callId, "sdp": answer]
                    )
                }
            }
        }
    }

    private func handleRemoteAnswer(sdp: String) async {
        webRtcClient?.setRemoteDescription(type: .answer, sdp: sdp) { [weak self] in
            self?.drainPendingCandidates()
        }
    }

    private func addRemoteCandidate(_ candidate: RTCIceCandidate) {
        if webRtcClient?.hasRemoteDescription == true {
            webRtcClient?.addRemoteCandidate(candidate)
        } else {
            pendingRemoteCandidates.append(candidate)
        }
    }

    private func drainPendingCandidates() {
        guard webRtcClient?.hasRemoteDescription == true else { return }
        pendingRemoteCandidates.forEach { webRtcClient?.addRemoteCandidate($0) }
        pendingRemoteCandidates.removeAll()
    }

    private func setupWebRtcIfNeeded() {
        if webRtcClient != nil { return }
        let client = WebRtcClient(iceServers: iceServers)
        client.onIceCandidate = { [weak self] candidate in
            Task { @MainActor in
                guard let call = self?.activeCall else { return }
                await self?.sendSignal(
                    type: "ice_candidate",
                    targetUserId: call.friendId,
                    payload: [
                        "callId": call.callId,
                        "candidate": candidate.sdp,
                        "sdpMid": candidate.sdpMid ?? "",
                        "sdpMLineIndex": Int(candidate.sdpMLineIndex)
                    ]
                )
            }
        }
        client.onConnectionStateChange = { [weak self] state in
            Task { @MainActor in
                switch state {
                case .connected:
                    self?.state = .connected
                    self?.activateAudioSession()
                case .failed, .disconnected, .closed:
                    self?.cleanupCall()
                default:
                    break
                }
            }
        }
        webRtcClient = client
    }

    private func endWebRtcSession() {
        webRtcClient?.close()
        webRtcClient = nil
        pendingRemoteCandidates.removeAll()
        deactivateAudioSession()
    }

    private func cleanupCall() {
        disconnectSignaling()
        endWebRtcSession()
        state = .idle
        activeCall = nil
    }

    private func fetchIceConfigIfNeeded() async {
        if !iceServers.isEmpty { return }
        guard let token = AppPrefs.getToken(), !token.isEmpty else { return }
        let baseUrl = AppPrefs.getBaseUrl(defaultValue: AuthService.defaultBaseUrl)
        let url = ApiClient.buildUrl(baseUrl: baseUrl, path: "/api/calls/ice")
        do {
            let result = try await ApiClient.executeRequest(method: "GET", url: url, token: token)
            guard (200...299).contains(result.code) else { return }
            guard let data = result.body.data(using: .utf8) else { return }
            let json = try JSONSerialization.jsonObject(with: data) as? [String: Any]
            let servers = (json?["iceServers"] as? [[String: Any]]) ?? []
            iceServers = servers.compactMap { server in
                guard let urls = server["urls"] as? [String] else { return nil }
                let username = server["username"] as? String
                let credential = server["credential"] as? String
                if let username, let credential {
                    return RTCIceServer(urlStrings: urls, username: username, credential: credential)
                }
                return RTCIceServer(urlStrings: urls)
            }
        } catch {
            return
        }
    }

    private func sendSignal(type: String, targetUserId: String?, payload: [String: Any]? = nil) async {
        var dict: [String: Any] = ["type": type]
        if let targetUserId {
            dict["targetUserId"] = targetUserId
        }
        if let payload {
            dict["payload"] = payload
        }

        guard let data = try? JSONSerialization.data(withJSONObject: dict),
              let text = String(data: data, encoding: .utf8),
              let signalingTask else { return }
        do {
            try await signalingTask.send(.string(text))
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func activateAudioSession() {
        let session = AVAudioSession.sharedInstance()
        try? session.setCategory(.playAndRecord, mode: .voiceChat, options: [.allowBluetooth, .defaultToSpeaker])
        try? session.setActive(true)
    }

    private func deactivateAudioSession() {
        let session = AVAudioSession.sharedInstance()
        try? session.setActive(false)
    }
}

extension CallManager: CXProviderDelegate {
    func provider(_ provider: CXProvider, perform action: CXAnswerCallAction) {
        guard let call = activeCall else {
            action.fail()
            return
        }
        state = .connecting
        connectSignaling(channelId: call.channelId)
        Task { await sendSignal(type: "call_accept", targetUserId: call.friendId, payload: ["callId": call.callId]) }
        action.fulfill()
    }

    func provider(_ provider: CXProvider, perform action: CXEndCallAction) {
        if let call = activeCall {
            Task { await sendSignal(type: "call_end", targetUserId: call.friendId, payload: ["callId": call.callId]) }
        }
        cleanupCall()
        action.fulfill()
    }

    func provider(_ provider: CXProvider, perform action: CXStartCallAction) {
        action.fulfill()
    }

    func providerDidReset(_ provider: CXProvider) {
        cleanupCall()
    }
}

final class WebRtcClient: NSObject, RTCPeerConnectionDelegate {
    private let factory: RTCPeerConnectionFactory
    private let iceServers: [RTCIceServer]
    private var peerConnection: RTCPeerConnection?
    private var audioTrack: RTCAudioTrack?

    var onIceCandidate: ((RTCIceCandidate) -> Void)?
    var onConnectionStateChange: ((RTCPeerConnectionState) -> Void)?
    var hasRemoteDescription: Bool {
        peerConnection?.remoteDescription != nil
    }

    init(iceServers: [RTCIceServer]) {
        RTCInitializeSSL()
        let encoderFactory = RTCDefaultVideoEncoderFactory()
        let decoderFactory = RTCDefaultVideoDecoderFactory()
        self.factory = RTCPeerConnectionFactory(encoderFactory: encoderFactory, decoderFactory: decoderFactory)
        self.iceServers = iceServers
        super.init()
        setupPeerConnection()
    }

    func createOffer(completion: @escaping (String) -> Void) {
        let constraints = RTCMediaConstraints(
            mandatoryConstraints: ["OfferToReceiveAudio": "true"],
            optionalConstraints: nil
        )
        peerConnection?.offer(for: constraints) { [weak self] sdp, _ in
            guard let self, let sdp else { return }
            self.peerConnection?.setLocalDescription(sdp, completionHandler: { _ in })
            completion(sdp.sdp)
        }
    }

    func createAnswer(completion: @escaping (String) -> Void) {
        let constraints = RTCMediaConstraints(
            mandatoryConstraints: ["OfferToReceiveAudio": "true"],
            optionalConstraints: nil
        )
        peerConnection?.answer(for: constraints) { [weak self] sdp, _ in
            guard let self, let sdp else { return }
            self.peerConnection?.setLocalDescription(sdp, completionHandler: { _ in })
            completion(sdp.sdp)
        }
    }

    func setRemoteDescription(type: RTCSdpType, sdp: String, completion: @escaping () -> Void) {
        let description = RTCSessionDescription(type: type, sdp: sdp)
        peerConnection?.setRemoteDescription(description, completionHandler: { _ in completion() })
    }

    func addRemoteCandidate(_ candidate: RTCIceCandidate) {
        peerConnection?.add(candidate)
    }

    func close() {
        peerConnection?.close()
        peerConnection = nil
        audioTrack = nil
    }

    private func setupPeerConnection() {
        let config = RTCConfiguration()
        config.iceServers = iceServers
        config.sdpSemantics = .unifiedPlan

        let constraints = RTCMediaConstraints(
            mandatoryConstraints: nil,
            optionalConstraints: ["DtlsSrtpKeyAgreement": "true"]
        )

        let connection = factory.peerConnection(with: config, constraints: constraints, delegate: self)
        peerConnection = connection

        let audioSource = factory.audioSource(with: RTCMediaConstraints(mandatoryConstraints: nil, optionalConstraints: nil))
        let track = factory.audioTrack(with: audioSource, trackId: "audio0")
        audioTrack = track
        _ = connection?.add(track, streamIds: ["stream0"])
    }

    func peerConnection(_ peerConnection: RTCPeerConnection, didChange stateChanged: RTCPeerConnectionState) {
        onConnectionStateChange?(stateChanged)
    }

    func peerConnection(_ peerConnection: RTCPeerConnection, didGenerate candidate: RTCIceCandidate) {
        onIceCandidate?(candidate)
    }

    func peerConnection(_ peerConnection: RTCPeerConnection, didRemove candidates: [RTCIceCandidate]) { }
    func peerConnection(_ peerConnection: RTCPeerConnection, didChange newState: RTCSignalingState) { }
    func peerConnection(_ peerConnection: RTCPeerConnection, didChange newState: RTCIceConnectionState) { }
    func peerConnection(_ peerConnection: RTCPeerConnection, didChange newState: RTCIceGatheringState) { }
    func peerConnection(_ peerConnection: RTCPeerConnection, didAdd stream: RTCMediaStream) { }
    func peerConnection(_ peerConnection: RTCPeerConnection, didRemove stream: RTCMediaStream) { }
    func peerConnectionShouldNegotiate(_ peerConnection: RTCPeerConnection) { }
    func peerConnection(_ peerConnection: RTCPeerConnection, didOpen dataChannel: RTCDataChannel) { }
}

private func buildWebSocketUrl(baseUrl: String, path: String) -> URL? {
    let trimmed = baseUrl.trimmingCharacters(in: .whitespacesAndNewlines)
    if trimmed.hasPrefix("https://") {
        let wsBase = trimmed.replacingOccurrences(of: "https://", with: "wss://")
        return URL(string: wsBase.trimmedEnd("/") + path)
    }
    if trimmed.hasPrefix("http://") {
        let wsBase = trimmed.replacingOccurrences(of: "http://", with: "ws://")
        return URL(string: wsBase.trimmedEnd("/") + path)
    }
    return nil
}

private extension String {
    func trimmedEnd(_ suffix: String) -> String {
        guard self.hasSuffix(suffix) else { return self }
        return String(self.dropLast(suffix.count))
    }
}
