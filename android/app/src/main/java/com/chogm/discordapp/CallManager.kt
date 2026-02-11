package com.chogm.discordapp

import android.content.Context
import android.media.AudioManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.audio.JavaAudioDeviceModule
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SessionDescription
import java.util.UUID
import java.util.concurrent.TimeUnit

enum class CallState {
    IDLE,
    OUTGOING,
    INCOMING,
    CONNECTING,
    CONNECTED
}

data class CallSession(
    val callId: String,
    val channelId: String,
    val friendId: String,
    val friendName: String,
    val isOutgoing: Boolean
)

object CallManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(CallState.IDLE)
    private val _activeCall = MutableStateFlow<CallSession?>(null)
    private val _isMicMuted = MutableStateFlow(false)
    private var signalingSocket: CallSignalingSocket? = null
    private var webRtcClient: WebRtcClient? = null
    private var iceServers: List<PeerConnection.IceServer> = emptyList()
    private val pendingCandidates = mutableListOf<IceCandidate>()
    private var lastIceFetch = 0L
    private var appContext: Context? = null
    private val cleanupLock = Any()

    val state: StateFlow<CallState> = _state
    val activeCall: StateFlow<CallSession?> = _activeCall
    val isMicMuted: StateFlow<Boolean> = _isMicMuted

    fun startOutgoingCall(context: Context, channelId: String, friendId: String, friendName: String) {
        if (_state.value != CallState.IDLE) return
        appContext = context.applicationContext
        val callId = UUID.randomUUID().toString()
        _activeCall.value = CallSession(callId, channelId, friendId, friendName, true)
        _state.value = CallState.OUTGOING
        connectSignaling(context, channelId)
        scope.launch {
            ensureIceConfig(context)
            sendSignal(
                type = "call_request",
                targetUserId = friendId,
                payload = JSONObject()
                    .put("callId", callId)
                    .put("channelId", channelId)
                    .put("callerName", AppPrefs.getDisplayName(context) ?: "Unknown")
            )
        }
    }

    fun handleIncomingCall(
        context: Context,
        callId: String,
        channelId: String,
        fromUserId: String,
        callerName: String,
        notify: Boolean = true
    ) {
        if (_state.value != CallState.IDLE) return
        appContext = context.applicationContext
        _activeCall.value = CallSession(callId, channelId, fromUserId, callerName, false)
        _state.value = CallState.INCOMING
        if (notify) {
            MessageNotifications.notifyIncomingCall(
                context,
                callerName,
                "call_$callId"
            )
        }
    }

    fun acceptIncomingCall(context: Context) {
        val call = _activeCall.value ?: return
        if (_state.value != CallState.INCOMING) return
        appContext = context.applicationContext
        _state.value = CallState.CONNECTING
        connectSignaling(context, call.channelId)
        scope.launch {
            sendSignal(
                type = "call_accept",
                targetUserId = call.friendId,
                payload = JSONObject().put("callId", call.callId)
            )
        }
    }

    fun rejectIncomingCall(context: Context) {
        val call = _activeCall.value ?: return
        if (_state.value != CallState.INCOMING) return
        sendSignal(
            type = "call_reject",
            targetUserId = call.friendId,
            payload = JSONObject().put("callId", call.callId)
        )
        cleanupCall()
    }

    fun endCall(context: Context) {
        val call = _activeCall.value ?: return
        appContext = context.applicationContext
        sendSignal(
            type = "call_end",
            targetUserId = call.friendId,
            payload = JSONObject().put("callId", call.callId)
        )
        cleanupCall()
    }

    fun toggleMic() {
        setMicMuted(!_isMicMuted.value)
    }

    fun setMicMuted(muted: Boolean) {
        _isMicMuted.value = muted
        webRtcClient?.setMicrophoneMuted(muted)
    }

    fun registerDeviceIfNeeded(context: Context) {
        val token = AppPrefs.getFcmToken(context) ?: return
        val authToken = AppPrefs.getToken(context) ?: return
        val baseUrl = AppPrefs.getBaseUrl(context, context.getString(R.string.base_url_default))
        scope.launch {
            val payload = JSONObject()
                .put("platform", "android")
                .put("token", token)
            try {
                ApiClient.executeRequest(
                    method = "POST",
                    url = ApiClient.buildUrl(baseUrl, "/api/devices"),
                    jsonBody = payload,
                    token = authToken
                )
            } catch (_: Exception) {
                // ignore
            }
        }
    }

    private fun connectSignaling(context: Context, channelId: String) {
        if (signalingSocket != null) return
        val token = AppPrefs.getToken(context) ?: return
        val baseUrl = AppPrefs.getBaseUrl(context, context.getString(R.string.base_url_default))
        val socket = CallSignalingSocket(
            baseUrl = baseUrl,
            token = token,
            channelId = channelId,
            onSignal = { signal -> handleSignal(context, signal) },
            onError = { }
        )
        socket.connect()
        signalingSocket = socket
    }

    private fun handleSignal(context: Context, signal: SignalEnvelope) {
        when (signal.type) {
            "call_request" -> {
                val payload = signal.payload ?: return
                val callId = payload.optString("callId")
                val channelId = payload.optString("channelId")
                val callerName = payload.optString("callerName").ifBlank { "Caller" }
                val fromUserId = signal.fromUserId ?: return
                if (callId.isNotBlank() && channelId.isNotBlank()) {
                    handleIncomingCall(context, callId, channelId, fromUserId, callerName, notify = true)
                }
            }
            "call_accept" -> {
                if (_state.value == CallState.OUTGOING) {
                    startOffer(context)
                }
            }
            "call_reject" -> {
                if (_state.value == CallState.OUTGOING) {
                    cleanupCall()
                }
            }
            "call_end" -> {
                if (_state.value != CallState.IDLE) {
                    cleanupCall()
                }
            }
            "sdp_offer" -> {
                val payload = signal.payload ?: return
                val sdp = payload.optString("sdp")
                if (sdp.isNotBlank()) {
                    handleRemoteOffer(context, sdp)
                }
            }
            "sdp_answer" -> {
                val payload = signal.payload ?: return
                val sdp = payload.optString("sdp")
                if (sdp.isNotBlank()) {
                    handleRemoteAnswer(sdp)
                }
            }
            "ice_candidate" -> {
                val payload = signal.payload ?: return
                val candidate = payload.optString("candidate")
                val sdpMid = payload.optString("sdpMid")
                val sdpMLineIndex = payload.optInt("sdpMLineIndex", -1)
                if (candidate.isNotBlank() && sdpMid.isNotBlank() && sdpMLineIndex >= 0) {
                    addRemoteCandidate(IceCandidate(sdpMid, sdpMLineIndex, candidate))
                }
            }
        }
    }

    private fun startOffer(context: Context) {
        val call = _activeCall.value ?: return
        scope.launch {
            ensureIceConfig(context)
            setupWebRtc(context)
            _state.value = CallState.CONNECTING
            webRtcClient?.createOffer { sdp ->
                scope.launch {
                    sendSignal(
                        type = "sdp_offer",
                        targetUserId = call.friendId,
                        payload = JSONObject()
                            .put("callId", call.callId)
                            .put("sdp", sdp)
                    )
                }
            }
        }
    }

    private fun handleRemoteOffer(context: Context, sdp: String) {
        val call = _activeCall.value ?: return
        scope.launch {
            ensureIceConfig(context)
            setupWebRtc(context)
            _state.value = CallState.CONNECTING
            webRtcClient?.setRemoteDescription(SessionDescription.Type.OFFER, sdp) {
                drainPendingCandidates()
                webRtcClient?.createAnswer { answer ->
                    scope.launch {
                        sendSignal(
                            type = "sdp_answer",
                            targetUserId = call.friendId,
                            payload = JSONObject()
                                .put("callId", call.callId)
                                .put("sdp", answer)
                        )
                    }
                }
            }
        }
    }

    private fun handleRemoteAnswer(sdp: String) {
        webRtcClient?.setRemoteDescription(SessionDescription.Type.ANSWER, sdp) {
            drainPendingCandidates()
        }
    }

    private fun addRemoteCandidate(candidate: IceCandidate) {
        if (webRtcClient?.hasRemoteDescription == true) {
            webRtcClient?.addRemoteCandidate(candidate)
        } else {
            pendingCandidates.add(candidate)
        }
    }

    private fun drainPendingCandidates() {
        if (webRtcClient?.hasRemoteDescription != true) return
        pendingCandidates.forEach { webRtcClient?.addRemoteCandidate(it) }
        pendingCandidates.clear()
    }

    private fun setupWebRtc(context: Context) {
        if (webRtcClient != null) return
        val client = WebRtcClient(context, iceServers)
        client.onIceCandidate = onIceCandidate@{ candidate ->
            val call = _activeCall.value ?: return@onIceCandidate
            scope.launch {
                sendSignal(
                    type = "ice_candidate",
                    targetUserId = call.friendId,
                    payload = JSONObject()
                        .put("callId", call.callId)
                        .put("candidate", candidate.sdp)
                        .put("sdpMid", candidate.sdpMid)
                        .put("sdpMLineIndex", candidate.sdpMLineIndex)
                )
            }
        }
        client.onConnectionStateChange = { state ->
            when (state) {
                PeerConnection.PeerConnectionState.CONNECTED -> {
                    _state.value = CallState.CONNECTED
                    activateAudio(context)
                }
                PeerConnection.PeerConnectionState.FAILED,
                PeerConnection.PeerConnectionState.DISCONNECTED,
                PeerConnection.PeerConnectionState.CLOSED -> cleanupCall()
                else -> Unit
            }
        }
        webRtcClient = client
        client.setMicrophoneMuted(_isMicMuted.value)
    }

    private suspend fun ensureIceConfig(context: Context) {
        val now = System.currentTimeMillis()
        if (iceServers.isNotEmpty() && now - lastIceFetch < 5 * 60 * 1000) {
            return
        }
        val token = AppPrefs.getToken(context) ?: return
        val baseUrl = AppPrefs.getBaseUrl(context, context.getString(R.string.base_url_default))
        try {
            val response = ApiClient.executeRequest(
                method = "GET",
                url = ApiClient.buildUrl(baseUrl, "/api/calls/ice"),
                token = token
            )
            if (response.code !in 200..299) return
            val json = JSONObject(response.body)
            val servers = json.optJSONArray("iceServers") ?: JSONArray()
            val parsed = mutableListOf<PeerConnection.IceServer>()
            for (index in 0 until servers.length()) {
                val server = servers.optJSONObject(index) ?: continue
                val urls = server.optJSONArray("urls") ?: JSONArray()
                val urlList = mutableListOf<String>()
                for (u in 0 until urls.length()) {
                    val value = urls.optString(u).trim()
                    if (value.isNotEmpty()) {
                        urlList.add(value)
                    }
                }
                if (urlList.isEmpty()) continue
                val username = server.optString("username").ifBlank { null }
                val credential = server.optString("credential").ifBlank { null }
                val iceServer = if (username != null && credential != null) {
                    PeerConnection.IceServer.builder(urlList)
                        .setUsername(username)
                        .setPassword(credential)
                        .createIceServer()
                } else {
                    PeerConnection.IceServer.builder(urlList).createIceServer()
                }
                parsed.add(iceServer)
            }
            if (parsed.isNotEmpty()) {
                iceServers = parsed
                lastIceFetch = now
            }
        } catch (_: Exception) {
            return
        }
    }

    private fun cleanupCall() {
        val socket: CallSignalingSocket?
        val client: WebRtcClient?
        val context: Context?
        synchronized(cleanupLock) {
            if (signalingSocket == null && webRtcClient == null && _activeCall.value == null) {
                return
            }
            socket = signalingSocket
            client = webRtcClient
            context = appContext
            signalingSocket = null
            webRtcClient = null
            appContext = null
            pendingCandidates.clear()
            _isMicMuted.value = false
            _state.value = CallState.IDLE
            _activeCall.value = null
        }
        socket?.close()
        client?.close()
        context?.let { deactivateAudio(it) }
    }

    private fun sendSignal(type: String, targetUserId: String?, payload: JSONObject?) {
        signalingSocket?.send(type, targetUserId, payload)
    }

    private fun activateAudio(context: Context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.isSpeakerphoneOn = true
    }

    private fun deactivateAudio(context: Context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.mode = AudioManager.MODE_NORMAL
        audioManager.isSpeakerphoneOn = false
    }
}

private data class SignalEnvelope(
    val type: String,
    val fromUserId: String?,
    val payload: JSONObject?
)

private class CallSignalingSocket(
    private val baseUrl: String,
    private val token: String,
    private val channelId: String,
    private val onSignal: (SignalEnvelope) -> Unit,
    private val onError: (String) -> Unit
) {
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
    private var webSocket: WebSocket? = null

    fun connect() {
        val wsUrl = ApiClient.buildWebSocketUrl(baseUrl, "/ws/signaling/$channelId")
        if (wsUrl.isNullOrBlank()) {
            onError("웹소켓 URL이 올바르지 않습니다.")
            return
        }
        val request = Request.Builder()
            .url(wsUrl)
            .addHeader("Authorization", "Bearer $token")
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                onError("통화 웹소켓 오류: ${t.message ?: "Unknown error"}")
            }
        })
    }

    fun send(type: String, targetUserId: String?, payload: JSONObject?): Boolean {
        val ws = webSocket ?: return false
        val body = JSONObject().put("type", type)
        if (!targetUserId.isNullOrBlank()) {
            body.put("targetUserId", targetUserId)
        }
        if (payload != null) {
            body.put("payload", payload)
        }
        return ws.send(body.toString())
    }

    fun close() {
        webSocket?.close(1000, "bye")
        webSocket = null
        client.dispatcher.executorService.shutdown()
    }

    private fun handleMessage(text: String) {
        try {
            val json = JSONObject(text)
            val error = json.optString("error").trim()
            if (error.isNotEmpty()) {
                onError(error)
                return
            }
            val type = json.optString("type").trim()
            if (type.isEmpty()) return
            val fromUserId = json.optString("fromUserId").ifBlank { null }
            val payload = json.optJSONObject("payload")
            onSignal(SignalEnvelope(type, fromUserId, payload))
        } catch (ex: Exception) {
            onError("통화 메시지 파싱 실패: ${ex.message ?: "Unknown error"}")
        }
    }
}

private class WebRtcClient(
    private val context: Context,
    private val iceServers: List<PeerConnection.IceServer>
) : PeerConnection.Observer {
    private val factory: PeerConnectionFactory
    private val audioDeviceModule: JavaAudioDeviceModule
    private var peerConnection: PeerConnection? = null
    private var audioTrack: AudioTrack? = null
    private var micMuted = false
    @Volatile
    private var closed = false
    private val eglBase = EglBase.create()

    var onIceCandidate: ((IceCandidate) -> Unit)? = null
    var onConnectionStateChange: ((PeerConnection.PeerConnectionState) -> Unit)? = null
    val hasRemoteDescription: Boolean
        get() = peerConnection?.remoteDescription != null

    init {
        initializePeerConnectionFactory()
        audioDeviceModule = JavaAudioDeviceModule.builder(context).createAudioDeviceModule()
        val encoderFactory = DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true)
        val decoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)
        factory = PeerConnectionFactory.builder()
            .setAudioDeviceModule(audioDeviceModule)
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()
        setupPeerConnection()
    }

    fun createOffer(onOffer: (String) -> Unit) {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        }
        peerConnection?.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(desc: SessionDescription?) {
                if (desc == null) return
                peerConnection?.setLocalDescription(SimpleSdpObserver(), desc)
                onOffer(desc.description)
            }
        }, constraints)
    }

    fun createAnswer(onAnswer: (String) -> Unit) {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        }
        peerConnection?.createAnswer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(desc: SessionDescription?) {
                if (desc == null) return
                peerConnection?.setLocalDescription(SimpleSdpObserver(), desc)
                onAnswer(desc.description)
            }
        }, constraints)
    }

    fun setRemoteDescription(type: SessionDescription.Type, sdp: String, onSet: () -> Unit) {
        val description = SessionDescription(type, sdp)
        peerConnection?.setRemoteDescription(object : SimpleSdpObserver() {
            override fun onSetSuccess() {
                onSet()
            }
        }, description)
    }

    fun addRemoteCandidate(candidate: IceCandidate) {
        peerConnection?.addIceCandidate(candidate)
    }

    fun close() {
        if (closed) {
            return
        }
        closed = true
        peerConnection?.close()
        peerConnection = null
        audioTrack = null
        micMuted = false
        audioDeviceModule.release()
        eglBase.release()
    }

    fun setMicrophoneMuted(muted: Boolean) {
        micMuted = muted
        audioTrack?.setEnabled(!muted)
    }

    private fun setupPeerConnection() {
        val config = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }
        peerConnection = factory.createPeerConnection(config, this)

        val audioSource: AudioSource = factory.createAudioSource(MediaConstraints())
        audioTrack = factory.createAudioTrack("audio0", audioSource)
        audioTrack?.setEnabled(!micMuted)
        peerConnection?.addTrack(audioTrack)
    }

    override fun onIceCandidate(candidate: IceCandidate?) {
        if (candidate != null) {
            onIceCandidate?.invoke(candidate)
        }
    }

    override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
        if (newState != null) {
            onConnectionStateChange?.invoke(newState)
        }
    }

    override fun onSignalingChange(newState: PeerConnection.SignalingState?) {}
    override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {}
    override fun onIceConnectionReceivingChange(receiving: Boolean) {}
    override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState?) {}
    override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
    override fun onAddStream(stream: org.webrtc.MediaStream?) {}
    override fun onRemoveStream(stream: org.webrtc.MediaStream?) {}
    override fun onDataChannel(dataChannel: org.webrtc.DataChannel?) {}
    override fun onRenegotiationNeeded() {}
    override fun onAddTrack(receiver: org.webrtc.RtpReceiver?, streams: Array<out org.webrtc.MediaStream>?) {}

    private fun initializePeerConnectionFactory() {
        if (PeerConnectionFactoryInitialized.initialized) return
        val options = PeerConnectionFactory.InitializationOptions.builder(context).createInitializationOptions()
        PeerConnectionFactory.initialize(options)
        PeerConnectionFactoryInitialized.initialized = true
    }
}

private object PeerConnectionFactoryInitialized {
    var initialized = false
}

private open class SimpleSdpObserver : org.webrtc.SdpObserver {
    override fun onCreateSuccess(desc: SessionDescription?) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(error: String?) {}
    override fun onSetFailure(error: String?) {}
}
