import SwiftUI
import UserNotifications

struct DirectMessage: Identifiable {
    let id: String
    let initials: String
    let name: String
    let preview: String
    let time: String
    let highlight: Bool
}

struct DirectMessageListItem: Identifiable {
    let id: String
    let thread: DirectThread
    let message: DirectMessage
}

struct MessagesView: View {
    @State private var animateIn = false
    @State private var showAddFriendSheet = false
    @State private var showComposeSheet = false
    @State private var messageItems: [DirectMessageListItem] = []
    @State private var threads: [DirectThread] = []
    @State private var isLoadingThreads = false
    @State private var threadsError: String? = nil
    @State private var pollingTask: Task<Void, Never>? = nil
    @State private var selectedThread: DirectThread? = nil

    var body: some View {
        ZStack(alignment: .bottomTrailing) {
            ScrollView {
                HStack(alignment: .top, spacing: 0) {
                    ServerRailView()

                    VStack(alignment: .leading, spacing: 16) {
                        Text("Messages")
                            .font(AppFont.title(20))
                            .foregroundColor(AppTheme.textPrimaryDark)

                        SearchBarView(placeholder: "Search")

                        PillButton(title: "Add Friend", icon: "plus") {
                            showAddFriendSheet = true
                        }

                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 10) {
                                AvatarCircle(text: "N", size: 46, fill: AppTheme.accentBlue)
                                AvatarCircle(text: "T", size: 46)
                                AvatarCircle(text: "D", size: 46)
                                AvatarCircle(text: "K", size: 46)
                            }
                        }

                        Text("Messages")
                            .font(AppFont.subtitle(12))
                            .foregroundColor(AppTheme.textSecondaryDark)

                        if isLoadingThreads {
                            Text("메시지 불러오는 중...")
                                .font(AppFont.body(12))
                                .foregroundColor(AppTheme.textMutedDark)
                        }

                        if let threadsError {
                            Text(threadsError)
                                .font(AppFont.body(12))
                                .foregroundColor(.red)
                        }

                        if messageItems.isEmpty && !isLoadingThreads {
                            Text("아직 대화가 없어요.")
                                .font(AppFont.body(12))
                                .foregroundColor(AppTheme.textSecondaryDark)
                        } else {
                            VStack(spacing: 14) {
                                ForEach(Array(messageItems.enumerated()), id: \.element.id) { index, item in
                                    Button {
                                        selectedThread = item.thread
                                    } label: {
                                        MessageRowView(message: item.message)
                                    }
                                    .buttonStyle(.plain)
                                        .opacity(animateIn ? 1 : 0)
                                        .offset(y: animateIn ? 0 : 10)
                                        .animation(.easeOut(duration: 0.45).delay(Double(index) * 0.05), value: animateIn)
                                }
                            }
                        }
                    }
                    .padding(16)
                }
            }

            Button(action: { showComposeSheet = true }) {
                Image(systemName: "square.and.pencil")
                    .font(.system(size: 20, weight: .bold))
                    .foregroundColor(AppTheme.textPrimaryDark)
                    .frame(width: 56, height: 56)
                    .background(
                        Circle().fill(AppTheme.accentBlue)
                    )
                    .shadow(color: AppTheme.accentBlue.opacity(0.4), radius: 10, x: 0, y: 6)
            }
            .padding(20)
        }
        .background(AppTheme.darkBackground)
        .sheet(isPresented: $showAddFriendSheet) {
            AddFriendSheetView()
                .presentationDetents([.height(380), .medium])
        }
        .sheet(isPresented: $showComposeSheet) {
            ComposeMessageSheetView(onSent: {
                Task { await refreshThreads(showLoading: true) }
            })
            .presentationDetents([.height(420), .medium])
        }
        .onAppear {
            animateIn = true
            MessageNotifications.requestAuthorizationIfNeeded()
            pollingTask?.cancel()
            pollingTask = Task {
                await refreshThreads(showLoading: true)
                while !Task.isCancelled {
                    try? await Task.sleep(nanoseconds: 8_000_000_000)
                    await refreshThreads(showLoading: false)
                }
            }
        }
        .onDisappear {
            pollingTask?.cancel()
            pollingTask = nil
        }
        .fullScreenCover(item: $selectedThread) { thread in
            DirectChatView(
                thread: thread,
                onDismiss: { selectedThread = nil },
                onMessageSent: {
                    Task { await refreshThreads(showLoading: false) }
                }
            )
        }
    }

    @MainActor
    private func refreshThreads(showLoading: Bool) async {
        if showLoading {
            isLoadingThreads = true
        }
        defer {
            if showLoading {
                isLoadingThreads = false
            }
        }

        guard let token = AppPrefs.getToken(), !token.isEmpty else {
            threadsError = "로그인이 필요합니다."
            return
        }

        let baseUrl = AppPrefs.getBaseUrl(defaultValue: AuthService.defaultBaseUrl)
        threadsError = nil

        do {
            threads = try await FriendService.fetchDirectThreads(
                baseUrl: baseUrl,
                token: token
            )
            messageItems = buildMessageItems(from: threads, currentUserId: AppPrefs.getUserId())
            notifyNewMessages(threads, currentUserId: AppPrefs.getUserId())
        } catch {
            threadsError = error.localizedDescription
        }
    }

    private func buildMessageItems(from threads: [DirectThread], currentUserId: String?) -> [DirectMessageListItem] {
        threads.map { thread in
            let last = thread.lastMessage
            let preview: String
            if let last {
                if let currentUserId, !currentUserId.isEmpty, last.senderId == currentUserId {
                    preview = "나: \(last.content)"
                } else {
                    preview = "\(thread.friendDisplayName): \(last.content)"
                }
            } else {
                preview = "대화가 없습니다."
            }
            let time: String
            if let createdAt = last?.createdAt, let formatted = formatRelativeTime(createdAt) {
                time = formatted
            } else {
                time = ""
            }
            let initial = thread.friendDisplayName
                .trimmingCharacters(in: .whitespacesAndNewlines)
                .prefix(1)
                .uppercased()

            let message = DirectMessage(
                id: thread.id,
                initials: String(initial),
                name: thread.friendDisplayName,
                preview: preview,
                time: time,
                highlight: last != nil && last?.senderId != currentUserId
            )
            return DirectMessageListItem(
                id: thread.id,
                thread: thread,
                message: message
            )
        }
    }

    private func notifyNewMessages(_ threads: [DirectThread], currentUserId: String?) {
        let seen = AppPrefs.getSeenMessageIds()
        let newMessages = threads.compactMap { thread -> (DirectThread, DirectMessagePreview)? in
            guard let last = thread.lastMessage else { return nil }
            if let currentUserId, !currentUserId.isEmpty, last.senderId == currentUserId {
                return nil
            }
            if seen.contains(last.id) {
                return nil
            }
            return (thread, last)
        }

        guard !newMessages.isEmpty else { return }
        AppPrefs.addSeenMessageIds(newMessages.map { $0.1.id })
        newMessages.forEach { thread, message in
            MessageNotifications.sendNewMessage(
                displayName: thread.friendDisplayName,
                preview: String(message.content.prefix(80))
            )
        }
    }
}

struct ServerRailView: View {
    var body: some View {
        VStack(spacing: 12) {
            AvatarCircle(text: "D", size: 44, fill: AppTheme.accentBlue)
            Spacer().frame(height: 6)
            AvatarCircle(text: "k", size: 44, fill: AppTheme.darkSurfaceAlt)
            AvatarCircle(text: "z", size: 44, fill: AppTheme.darkSurfaceAlt)

            Button(action: {}) {
                Image(systemName: "plus")
                    .font(.system(size: 18, weight: .bold))
                    .foregroundColor(AppTheme.accentBlue)
                    .frame(width: 44, height: 44)
                    .background(Circle().fill(AppTheme.darkSurfaceAlt))
            }
        }
        .frame(width: 64)
        .padding(.vertical, 16)
        .background(AppTheme.darkRail)
    }
}

struct SearchBarView: View {
    let placeholder: String

    var body: some View {
        HStack(spacing: 8) {
            Image(systemName: "magnifyingglass")
                .foregroundColor(AppTheme.textMutedDark)
            Text(placeholder)
                .font(AppFont.body(13))
                .foregroundColor(AppTheme.textMutedDark)
            Spacer()
        }
        .padding(.horizontal, 12)
        .frame(height: 44)
        .background(
            RoundedRectangle(cornerRadius: 14).fill(AppTheme.darkSurface)
        )
    }
}

struct MessageRowView: View {
    let message: DirectMessage

    var body: some View {
        HStack(spacing: 12) {
            AvatarCircle(
                text: message.initials,
                size: 44,
                fill: message.highlight ? AppTheme.accentBlue : AppTheme.darkSurfaceAlt
            )

            VStack(alignment: .leading, spacing: 2) {
                Text(message.name)
                    .font(AppFont.subtitle(14))
                    .foregroundColor(AppTheme.textPrimaryDark)

                Text(message.preview)
                    .font(AppFont.body(12))
                    .foregroundColor(AppTheme.textSecondaryDark)
            }

            Spacer()

            Text(message.time)
                .font(AppFont.body(11))
                .foregroundColor(AppTheme.textMutedDark)
        }
    }
}

struct DirectChatView: View {
    let thread: DirectThread
    let onDismiss: () -> Void
    let onMessageSent: () -> Void

    @State private var messages: [ChatMessage] = []
    @State private var isLoading = false
    @State private var errorMessage: String? = nil
    @State private var input = ""
    @State private var isSending = false
    @State private var socketTask: URLSessionWebSocketTask? = nil

    var body: some View {
        GeometryReader { proxy in
            VStack(spacing: 0) {
                HStack(spacing: 12) {
                    Button(action: onDismiss) {
                        Image(systemName: "chevron.left")
                            .font(.system(size: 16, weight: .semibold))
                            .foregroundColor(AppTheme.textPrimaryDark)
                            .frame(width: 36, height: 36)
                            .background(Circle().fill(AppTheme.darkSurfaceAlt))
                    }

                    Text(thread.friendDisplayName)
                        .font(AppFont.title(18))
                        .foregroundColor(AppTheme.textPrimaryDark)

                    Spacer()
                }
                .padding(16)
                .frame(maxWidth: .infinity, alignment: .leading)

                Rectangle()
                    .fill(AppTheme.darkBorder)
                    .frame(height: 1)

                ScrollView {
                    VStack(spacing: 12) {
                        if isLoading {
                            Text("메시지 불러오는 중...")
                                .font(AppFont.body(12))
                                .foregroundColor(AppTheme.textMutedDark)
                                .frame(maxWidth: .infinity, alignment: .leading)
                        }

                        if let errorMessage {
                            Text(errorMessage)
                                .font(AppFont.body(12))
                                .foregroundColor(.red)
                                .frame(maxWidth: .infinity, alignment: .leading)
                        }

                        if messages.isEmpty && !isLoading && errorMessage == nil {
                            Text("아직 메시지가 없어요.")
                                .font(AppFont.body(12))
                                .foregroundColor(AppTheme.textSecondaryDark)
                                .frame(maxWidth: .infinity, alignment: .leading)
                        }

                        ForEach(messages) { message in
                            let isSelf = message.senderId == (AppPrefs.getUserId() ?? "")
                            HStack {
                                ChatBubble(text: message.content, isSelf: isSelf)
                            }
                            .frame(
                                maxWidth: .infinity,
                                alignment: isSelf ? .trailing : .leading
                            )
                        }
                    }
                    .padding(16)
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
                .frame(width: proxy.size.width)

                HStack(spacing: 10) {
                    TextField("메시지를 입력하세요", text: $input)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .foregroundColor(AppTheme.textPrimaryDark)
                        .padding(.horizontal, 16)
                        .frame(maxWidth: .infinity, minHeight: 46)
                        .background(
                            RoundedRectangle(cornerRadius: 14).fill(AppTheme.darkSurface)
                        )

                    Button(action: sendMessage) {
                        Text(isSending ? "보내는 중..." : "보내기")
                            .font(AppFont.subtitle(12))
                            .foregroundColor(AppTheme.textPrimaryDark)
                            .padding(.horizontal, 12)
                            .frame(height: 46)
                            .background(
                                RoundedRectangle(cornerRadius: 14).fill(AppTheme.accentBlue)
                            )
                    }
                    .disabled(isSending)
                }
                .padding(16)
                .frame(maxWidth: .infinity)
            }
            .frame(width: proxy.size.width, height: proxy.size.height, alignment: .top)
            .background(AppTheme.darkBackground.ignoresSafeArea())
        }
        .onAppear {
            Task { await loadMessages(showLoading: true) }
            connectWebSocket()
        }
        .onDisappear {
            disconnectWebSocket()
        }
    }

    @MainActor
    private func loadMessages(showLoading: Bool) async {
        if showLoading {
            isLoading = true
        }
        defer {
            if showLoading {
                isLoading = false
            }
        }

        guard let token = AppPrefs.getToken(), !token.isEmpty else {
            errorMessage = "로그인이 필요합니다."
            return
        }

        let baseUrl = AppPrefs.getBaseUrl(defaultValue: AuthService.defaultBaseUrl)
        errorMessage = nil

        do {
            messages = try await FriendService.fetchChannelMessages(
                channelId: thread.id,
                baseUrl: baseUrl,
                token: token
            )
            AppPrefs.addSeenMessageIds(messages.map { $0.id })
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func sendMessage() {
        let trimmed = input.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty {
            errorMessage = "메시지를 입력하세요."
            return
        }

        guard let token = AppPrefs.getToken(), !token.isEmpty else {
            errorMessage = "로그인이 필요합니다."
            return
        }

        Task { @MainActor in
            isSending = true
            errorMessage = nil
            do {
                if socketTask != nil {
                    try await sendWebSocketMessage(trimmed)
                    input = ""
                } else {
                    let baseUrl = AppPrefs.getBaseUrl(defaultValue: AuthService.defaultBaseUrl)
                    let message = try await FriendService.sendChannelMessage(
                        channelId: thread.id,
                        content: trimmed,
                        baseUrl: baseUrl,
                        token: token
                    )
                    messages.append(message)
                    AppPrefs.addSeenMessageIds([message.id])
                    input = ""
                    onMessageSent()
                }
            } catch {
                errorMessage = error.localizedDescription
            }
            isSending = false
        }
    }

    private func connectWebSocket() {
        guard socketTask == nil else { return }
        guard let token = AppPrefs.getToken(), !token.isEmpty else {
            errorMessage = "로그인이 필요합니다."
            return
        }
        let baseUrl = AppPrefs.getBaseUrl(defaultValue: AuthService.defaultBaseUrl)
        guard let url = buildWebSocketUrl(baseUrl: baseUrl, path: "/ws/chat/\(thread.id)") else {
            errorMessage = "웹소켓 URL이 올바르지 않습니다."
            return
        }

        var request = URLRequest(url: url)
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")

        let task = URLSession.shared.webSocketTask(with: request)
        socketTask = task
        task.resume()
        listenForWebSocketMessages()
    }

    private func disconnectWebSocket() {
        socketTask?.cancel(with: .goingAway, reason: nil)
        socketTask = nil
    }

    private func listenForWebSocketMessages() {
        socketTask?.receive { result in
            switch result {
            case .success(let message):
                switch message {
                case .string(let text):
                    handleWebSocketText(text)
                case .data(let data):
                    if let text = String(data: data, encoding: .utf8) {
                        handleWebSocketText(text)
                    }
                @unknown default:
                    break
                }
            case .failure(let error):
                Task { @MainActor in
                    errorMessage = "웹소켓 오류: \(error.localizedDescription)"
                }
            }

            if socketTask != nil {
                listenForWebSocketMessages()
            }
        }
    }

    private func handleWebSocketText(_ text: String) {
        guard let data = text.data(using: .utf8) else { return }
        guard let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return }

        if let error = json["error"] as? String, !error.isEmpty {
            Task { @MainActor in
                errorMessage = error
            }
            return
        }

        guard
            let id = json["id"] as? String,
            let senderId = json["senderId"] as? String,
            let content = json["content"] as? String,
            let createdAt = json["createdAt"] as? String
        else {
            return
        }

        let message = ChatMessage(
            id: id,
            senderId: senderId,
            content: content,
            createdAt: createdAt
        )

        Task { @MainActor in
            if !messages.contains(where: { $0.id == id }) {
                messages.append(message)
            }
            AppPrefs.addSeenMessageIds([id])
            onMessageSent()
        }
    }

    private func sendWebSocketMessage(_ content: String) async throws {
        let payload = ["content": content]
        let data = try JSONSerialization.data(withJSONObject: payload)
        guard let text = String(data: data, encoding: .utf8) else {
            throw FriendServiceError.invalidResponse
        }
        guard let socketTask else {
            throw FriendServiceError.invalidResponse
        }
        try await socketTask.send(.string(text))
    }
}

struct ChatBubble: View {
    let text: String
    let isSelf: Bool

    var body: some View {
        Text(text)
            .font(AppFont.body(12))
            .foregroundColor(AppTheme.textPrimaryDark)
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .background(
                RoundedRectangle(cornerRadius: 16)
                    .fill(isSelf ? AppTheme.accentBlue : AppTheme.darkSurfaceAlt)
            )
            .frame(maxWidth: 240, alignment: .leading)
    }
}

private func formatRelativeTime(_ isoTime: String) -> String? {
    let formatter = ISO8601DateFormatter()
    guard let date = formatter.date(from: isoTime) else { return nil }
    let seconds = Date().timeIntervalSince(date)
    if seconds < 60 {
        return "방금"
    }
    let minutes = Int(seconds / 60)
    if minutes < 60 {
        return "\(minutes)분"
    }
    let hours = Int(seconds / 3600)
    if hours < 24 {
        return "\(hours)시간"
    }
    let days = Int(seconds / 86400)
    return "\(days)일"
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

enum MessageNotifications {
    static func requestAuthorizationIfNeeded() {
        let center = UNUserNotificationCenter.current()
        center.getNotificationSettings { settings in
            if settings.authorizationStatus != .notDetermined {
                return
            }
            center.requestAuthorization(options: [.alert, .sound, .badge]) { _, _ in }
        }
    }

    static func sendNewMessage(displayName: String, preview: String) {
        let content = UNMutableNotificationContent()
        content.title = "\(displayName)의 새 메시지"
        content.body = preview
        content.sound = .default

        let trigger = UNTimeIntervalNotificationTrigger(timeInterval: 1, repeats: false)
        let request = UNNotificationRequest(
            identifier: "dm_message_\(UUID().uuidString)",
            content: content,
            trigger: trigger
        )
        UNUserNotificationCenter.current().add(request, withCompletionHandler: nil)
    }
}

struct AddFriendSheetView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var query = ""
    @State private var foundUser: UserLookup? = nil
    @State private var isLoading = false
    @State private var statusMessage: String? = nil
    @State private var errorMessage: String? = nil
    @State private var didSend = false

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack {
                Text("Add Friend")
                    .font(AppFont.title(18))
                    .foregroundColor(AppTheme.textPrimaryDark)
                Spacer()
                Button("Close") {
                    dismiss()
                }
                .font(AppFont.subtitle(12))
                .foregroundColor(AppTheme.textSecondaryDark)
            }

            Text("Enter a username or email to send a friend request.")
                .font(AppFont.body(12))
                .foregroundColor(AppTheme.textSecondaryDark)

            VStack(alignment: .leading, spacing: 8) {
                Text("Username or Email")
                    .font(AppFont.subtitle(12))
                    .foregroundColor(AppTheme.textSecondaryDark)

                TextField("username@example.com", text: $query)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .keyboardType(.emailAddress)
                    .foregroundColor(AppTheme.textPrimaryDark)
                    .padding(.horizontal, 16)
                    .frame(height: 52)
                    .background(
                        RoundedRectangle(cornerRadius: 14).fill(AppTheme.darkSurface)
                    )
            }

            SecondaryButton(title: isLoading ? "Searching..." : "Find User") {
                lookupUser()
            }
            .disabled(isLoading)

            if let foundUser {
                AppCard(background: AppTheme.darkCard) {
                    HStack(spacing: 12) {
                        let initial = foundUser.displayName
                            .trimmingCharacters(in: .whitespacesAndNewlines)
                            .prefix(1)
                            .uppercased()
                        AvatarCircle(text: String(initial), size: 40, fill: AppTheme.accentBlue)

                        VStack(alignment: .leading, spacing: 2) {
                            Text(foundUser.displayName)
                                .font(AppFont.subtitle(14))
                                .foregroundColor(AppTheme.textPrimaryDark)
                            Text("@\(foundUser.username)")
                                .font(AppFont.body(12))
                                .foregroundColor(AppTheme.textSecondaryDark)
                        }

                        Spacer()
                    }
                }

                if !didSend {
                    PrimaryButton(title: isLoading ? "Sending..." : "Send Request", isEnabled: !isLoading) {
                        sendRequest()
                    }
                }
            }

            if let statusMessage {
                Text(statusMessage)
                    .font(AppFont.body(12))
                    .foregroundColor(AppTheme.accentBlue)
            }

            if let errorMessage {
                Text(errorMessage)
                    .font(AppFont.body(12))
                    .foregroundColor(.red)
            }

            Spacer()
        }
        .padding(20)
        .background(AppTheme.sheetBackground.ignoresSafeArea())
    }

    private func lookupUser() {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty {
            errorMessage = "사용자 이름 또는 이메일을 입력하세요."
            return
        }

        guard let token = AppPrefs.getToken(), !token.isEmpty else {
            errorMessage = "로그인이 필요합니다."
            return
        }

        let baseUrl = AppPrefs.getBaseUrl(defaultValue: AuthService.defaultBaseUrl)

        Task { @MainActor in
            isLoading = true
            errorMessage = nil
            statusMessage = nil
            didSend = false
            foundUser = nil
            do {
                foundUser = try await FriendService.lookupUser(
                    query: trimmed,
                    baseUrl: baseUrl,
                    token: token
                )
            } catch {
                errorMessage = error.localizedDescription
            }
            isLoading = false
        }
    }

    private func sendRequest() {
        guard let foundUser else {
            errorMessage = "먼저 사용자를 찾아주세요."
            return
        }
        guard let token = AppPrefs.getToken(), !token.isEmpty else {
            errorMessage = "로그인이 필요합니다."
            return
        }

        let baseUrl = AppPrefs.getBaseUrl(defaultValue: AuthService.defaultBaseUrl)

        Task { @MainActor in
            isLoading = true
            errorMessage = nil
            statusMessage = nil
            do {
                try await FriendService.sendFriendRequest(
                    addresseeId: foundUser.id,
                    baseUrl: baseUrl,
                    token: token
                )
                statusMessage = "\(foundUser.displayName)에게 친구 요청을 보냈어요."
                didSend = true
            } catch {
                errorMessage = error.localizedDescription
            }
            isLoading = false
        }
    }
}

struct ComposeMessageSheetView: View {
    @Environment(\.dismiss) private var dismiss
    let onSent: () -> Void

    @State private var friends: [FriendSummary] = []
    @State private var isLoading = false
    @State private var isSending = false
    @State private var errorMessage: String? = nil
    @State private var selectedFriend: FriendSummary? = nil
    @State private var message = ""

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                HStack {
                    Text("새 메시지 작성")
                        .font(AppFont.title(18))
                        .foregroundColor(AppTheme.textPrimaryDark)
                    Spacer()
                    Button("닫기") {
                        dismiss()
                    }
                    .font(AppFont.subtitle(12))
                    .foregroundColor(AppTheme.textSecondaryDark)
                }

                Text("보낼 친구 선택")
                    .font(AppFont.subtitle(12))
                    .foregroundColor(AppTheme.textSecondaryDark)

                if isLoading {
                    Text("친구 목록 불러오는 중...")
                        .font(AppFont.body(12))
                        .foregroundColor(AppTheme.textMutedDark)
                }

                if let errorMessage {
                    Text(errorMessage)
                        .font(AppFont.body(12))
                        .foregroundColor(.red)
                }

                if friends.isEmpty && !isLoading {
                    AppCard {
                        Text("친구 목록이 비어있어요.")
                            .font(AppFont.body(12))
                            .foregroundColor(AppTheme.textSecondaryDark)
                    }
                } else {
                    VStack(spacing: 10) {
                        ForEach(friends) { friend in
                            let isSelected = selectedFriend?.id == friend.id
                            Button {
                                selectedFriend = friend
                            } label: {
                                AppCard(background: isSelected ? AppTheme.darkSurfaceAlt : AppTheme.darkCard) {
                                    FriendPickerRow(friend: friend)
                                }
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }

                Text("메시지")
                    .font(AppFont.subtitle(12))
                    .foregroundColor(AppTheme.textSecondaryDark)

                TextField("메시지 내용을 입력하세요", text: $message)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .foregroundColor(AppTheme.textPrimaryDark)
                    .padding(.horizontal, 16)
                    .frame(height: 52)
                    .background(
                        RoundedRectangle(cornerRadius: 14).fill(AppTheme.darkSurface)
                    )

                PrimaryButton(title: isSending ? "보내는 중..." : "보내기", isEnabled: !isLoading && !isSending) {
                    sendMessage()
                }
            }
            .padding(20)
        }
        .background(AppTheme.sheetBackground.ignoresSafeArea())
        .onAppear {
            loadFriends()
        }
    }

    private func loadFriends() {
        guard let token = AppPrefs.getToken(), !token.isEmpty else {
            errorMessage = "로그인이 필요합니다."
            return
        }

        let baseUrl = AppPrefs.getBaseUrl(defaultValue: AuthService.defaultBaseUrl)

        Task { @MainActor in
            isLoading = true
            errorMessage = nil
            do {
                friends = try await FriendService.fetchFriends(
                    baseUrl: baseUrl,
                    token: token
                )
            } catch {
                errorMessage = error.localizedDescription
            }
            isLoading = false
        }
    }

    private func sendMessage() {
        guard let selectedFriend else {
            errorMessage = "친구를 선택하세요."
            return
        }
        let trimmed = message.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty {
            errorMessage = "메시지를 입력하세요."
            return
        }
        guard let token = AppPrefs.getToken(), !token.isEmpty else {
            errorMessage = "로그인이 필요합니다."
            return
        }

        let baseUrl = AppPrefs.getBaseUrl(defaultValue: AuthService.defaultBaseUrl)
        Task { @MainActor in
            isSending = true
            errorMessage = nil
            do {
                try await FriendService.sendDirectMessage(
                    toUserId: selectedFriend.id,
                    content: trimmed,
                    baseUrl: baseUrl,
                    token: token
                )
                onSent()
                dismiss()
            } catch {
                errorMessage = error.localizedDescription
            }
            isSending = false
        }
    }
}

struct FriendPickerRow: View {
    let friend: FriendSummary

    var body: some View {
        HStack(spacing: 10) {
            let initial = friend.displayName
                .trimmingCharacters(in: .whitespacesAndNewlines)
                .prefix(1)
                .uppercased()
            AvatarCircle(
                text: String(initial),
                size: 38,
                fill: AppTheme.accentBlue
            )

            VStack(alignment: .leading, spacing: 2) {
                Text(friend.displayName)
                    .font(AppFont.subtitle(13))
                    .foregroundColor(AppTheme.textPrimaryDark)
                Text("@\(friend.username)")
                    .font(AppFont.body(11))
                    .foregroundColor(AppTheme.textSecondaryDark)
            }

            Spacer()
        }
    }
}
