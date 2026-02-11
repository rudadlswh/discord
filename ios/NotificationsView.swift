import SwiftUI
import UserNotifications

enum LocalNotifications {
    static func requestAuthorizationIfNeeded() {
        let center = UNUserNotificationCenter.current()
        center.getNotificationSettings { settings in
            if settings.authorizationStatus != .notDetermined {
                return
            }
            center.requestAuthorization(options: [.alert, .sound, .badge]) { _, _ in }
        }
    }

    static func sendFriendRequestNotification(displayName: String) {
        let content = UNMutableNotificationContent()
        content.title = "새 친구 요청"
        content.body = "\(displayName)님이 친구 요청을 보냈어요."
        content.sound = .default

        let trigger = UNTimeIntervalNotificationTrigger(timeInterval: 1, repeats: false)
        let request = UNNotificationRequest(
            identifier: "friend_request_\(UUID().uuidString)",
            content: content,
            trigger: trigger
        )
        UNUserNotificationCenter.current().add(request, withCompletionHandler: nil)
    }
}

struct NotificationsView: View {
    @State private var showSheet = false
    @State private var animateIn = false
    @State private var pendingRequests: [PendingFriendRequest] = []
    @State private var isLoadingRequests = false
    @State private var requestError: String? = nil
    @State private var requestStatus: String? = nil
    @State private var pollingTask: Task<Void, Never>? = nil

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                HStack {
                    Text("Notifications")
                        .font(AppFont.title(20))
                        .foregroundColor(AppTheme.textPrimaryDark)

                    Spacer()

                    Button(action: { showSheet = true }) {
                        Image(systemName: "ellipsis")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundColor(AppTheme.textSecondaryDark)
                            .frame(width: 36, height: 36)
                            .background(Circle().fill(AppTheme.darkSurfaceAlt))
                    }
                }

                Text("친구 요청")
                    .font(AppFont.subtitle(12))
                    .foregroundColor(AppTheme.textSecondaryDark)
                    .frame(maxWidth: .infinity, alignment: .leading)

                if isLoadingRequests {
                    Text("친구 요청 불러오는 중...")
                        .font(AppFont.body(12))
                        .foregroundColor(AppTheme.textMutedDark)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }

                if let requestStatus {
                    Text(requestStatus)
                        .font(AppFont.body(12))
                        .foregroundColor(AppTheme.accentBlue)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }

                if let requestError {
                    Text(requestError)
                        .font(AppFont.body(12))
                        .foregroundColor(.red)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }

                if pendingRequests.isEmpty && !isLoadingRequests {
                    AppCard {
                        Text("대기 중인 친구 요청이 없어요.")
                            .font(AppFont.body(12))
                            .foregroundColor(AppTheme.textSecondaryDark)
                    }
                } else {
                    VStack(spacing: 10) {
                        ForEach(pendingRequests) { request in
                            AppCard {
                                FriendRequestRow(
                                    request: request,
                                    onAccept: { acceptRequest(request) },
                                    onReject: { rejectRequest(request) }
                                )
                            }
                        }
                    }
                }

                AppCard {
                    HStack(spacing: 12) {
                        Image(systemName: "message.fill")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundColor(AppTheme.textPrimaryDark)
                            .frame(width: 36, height: 36)
                            .background(Circle().fill(AppTheme.accentBlue))

                        VStack(alignment: .leading, spacing: 2) {
                            Text("Game Room")
                                .font(AppFont.subtitle(14))
                                .foregroundColor(AppTheme.textPrimaryDark)
                            Text("You have unread messages.")
                                .font(AppFont.body(12))
                                .foregroundColor(AppTheme.textSecondaryDark)
                        }

                        Spacer()

                        Text("2mo")
                            .font(AppFont.body(11))
                            .foregroundColor(AppTheme.textMutedDark)
                    }
                }

                Text("Recommended Friends")
                    .font(AppFont.subtitle(12))
                    .foregroundColor(AppTheme.textSecondaryDark)
                    .frame(maxWidth: .infinity, alignment: .leading)

                AppCard {
                    VStack(spacing: 10) {

                    }
                }

                HStack {
                    Text("Show All")
                        .font(AppFont.subtitle(12))
                        .foregroundColor(AppTheme.accentBlue)
                    Spacer()
                    Image(systemName: "chevron.right")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundColor(AppTheme.textSecondaryDark)
                }
            }
            .padding(16)
            .opacity(animateIn ? 1 : 0)
            .offset(y: animateIn ? 0 : 12)
            .animation(.easeOut(duration: 0.5), value: animateIn)
        }
        .background(AppTheme.darkBackground)
        .refreshable {
            await refreshRequests(showLoading: true)
        }
        .sheet(isPresented: $showSheet) {
            NotificationsSheetView()
                .presentationDetents([.height(340), .medium])
        }
        .onAppear {
            animateIn = true
            LocalNotifications.requestAuthorizationIfNeeded()
            pollingTask?.cancel()
            pollingTask = Task {
                await refreshRequests(showLoading: true)
                while !Task.isCancelled {
                    try? await Task.sleep(nanoseconds: 15_000_000_000)
                    await refreshRequests(showLoading: false)
                }
            }
        }
        .onDisappear {
            pollingTask?.cancel()
            pollingTask = nil
        }
    }

    @MainActor
    private func refreshRequests(showLoading: Bool) async {
        if showLoading {
            isLoadingRequests = true
        }
        defer {
            if showLoading {
                isLoadingRequests = false
            }
        }

        guard let token = AppPrefs.getToken(), !token.isEmpty else {
            requestError = "로그인이 필요합니다."
            return
        }

        let baseUrl = AppPrefs.getBaseUrl(defaultValue: AuthService.defaultBaseUrl)
        requestError = nil
        requestStatus = nil

        do {
            let requests = try await FriendService.fetchPendingRequests(
                baseUrl: baseUrl,
                token: token
            )
            pendingRequests = requests
            notifyNewRequestsIfNeeded(requests)
        } catch {
            requestError = error.localizedDescription
        }
    }

    private func acceptRequest(_ request: PendingFriendRequest) {
        guard let token = AppPrefs.getToken(), !token.isEmpty else {
            requestError = "로그인이 필요합니다."
            return
        }
        let baseUrl = AppPrefs.getBaseUrl(defaultValue: AuthService.defaultBaseUrl)
        Task { @MainActor in
            requestError = nil
            requestStatus = nil
            do {
                try await FriendService.acceptFriendRequest(
                    requestId: request.id,
                    baseUrl: baseUrl,
                    token: token
                )
                pendingRequests.removeAll { $0.id == request.id }
                requestStatus = "\(request.displayName)님을 친구로 추가했어요."
            } catch {
                requestError = error.localizedDescription
            }
        }
    }

    private func rejectRequest(_ request: PendingFriendRequest) {
        guard let token = AppPrefs.getToken(), !token.isEmpty else {
            requestError = "로그인이 필요합니다."
            return
        }
        let baseUrl = AppPrefs.getBaseUrl(defaultValue: AuthService.defaultBaseUrl)
        Task { @MainActor in
            requestError = nil
            requestStatus = nil
            do {
                try await FriendService.rejectFriendRequest(
                    requestId: request.id,
                    baseUrl: baseUrl,
                    token: token
                )
                pendingRequests.removeAll { $0.id == request.id }
                requestStatus = "\(request.displayName)님의 요청을 거절했어요."
            } catch {
                requestError = error.localizedDescription
            }
        }
    }

    private func notifyNewRequestsIfNeeded(_ requests: [PendingFriendRequest]) {
        let seen = AppPrefs.getSeenFriendRequestIds()
        let newRequests = requests.filter { !seen.contains($0.id) }
        guard !newRequests.isEmpty else { return }
        AppPrefs.addSeenFriendRequestIds(newRequests.map { $0.id })
        newRequests.forEach { LocalNotifications.sendFriendRequestNotification(displayName: $0.displayName) }
    }
}

struct RecommendedFriendRow: View {
    let initials: String
    let name: String
    let tag: String
    let highlight: Bool

    var body: some View {
        HStack(spacing: 10) {
            AvatarCircle(
                text: initials,
                size: 38,
                fill: highlight ? AppTheme.accentBlue : AppTheme.darkSurfaceAlt
            )

            VStack(alignment: .leading, spacing: 2) {
                Text(name)
                    .font(AppFont.subtitle(13))
                    .foregroundColor(AppTheme.textPrimaryDark)
                Text(tag)
                    .font(AppFont.body(11))
                    .foregroundColor(AppTheme.textSecondaryDark)
            }

            Spacer()

            Text("Add")
                .font(AppFont.subtitle(12))
                .foregroundColor(AppTheme.textPrimaryDark)
                .padding(.horizontal, 12)
                .frame(height: 32)
                .background(
                    Capsule().fill(AppTheme.darkSurfaceAlt)
                )
        }
    }
}

struct FriendRequestRow: View {
    let request: PendingFriendRequest
    let onAccept: () -> Void
    let onReject: () -> Void

    var body: some View {
        HStack(spacing: 10) {
            let initial = request.displayName
                .trimmingCharacters(in: .whitespacesAndNewlines)
                .prefix(1)
                .uppercased()
            AvatarCircle(
                text: String(initial),
                size: 38,
                fill: AppTheme.accentBlue
            )

            VStack(alignment: .leading, spacing: 2) {
                Text(request.displayName)
                    .font(AppFont.subtitle(13))
                    .foregroundColor(AppTheme.textPrimaryDark)
                Text("@\(request.username)")
                    .font(AppFont.body(11))
                    .foregroundColor(AppTheme.textSecondaryDark)
            }

            Spacer()

            HStack(spacing: 8) {
                ActionPill(title: "수락", background: AppTheme.accentBlue, textColor: AppTheme.textPrimaryDark, action: onAccept)
                ActionPill(title: "거절", background: AppTheme.darkSurfaceAlt, textColor: AppTheme.textPrimaryDark, action: onReject)
            }
        }
    }
}

struct ActionPill: View {
    let title: String
    let background: Color
    let textColor: Color
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(AppFont.subtitle(11))
                .foregroundColor(textColor)
                .padding(.horizontal, 10)
                .frame(height: 28)
                .background(Capsule().fill(background))
        }
    }
}

struct NotificationsSheetView: View {
    @State private var roleMentions = true
    @State private var indirectMentions = true

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Capsule()
                .fill(AppTheme.darkBorder)
                .frame(width: 36, height: 4)
                .frame(maxWidth: .infinity, alignment: .center)

            Text("Notifications")
                .font(AppFont.subtitle(16))
                .foregroundColor(AppTheme.textPrimaryDark)

            AppCard(background: AppTheme.darkCard) {
                HStack(spacing: 10) {
                    AvatarCircle(text: "@", size: 32, fill: AppTheme.darkSurfaceAlt)
                    Text("Role mentions")
                        .font(AppFont.body(13))
                        .foregroundColor(AppTheme.textPrimaryDark)
                    Spacer()
                    Toggle("", isOn: $roleMentions)
                        .labelsHidden()
                        .tint(AppTheme.accentBlue)
                }
            }

            AppCard(background: AppTheme.darkCard) {
                HStack(spacing: 10) {
                    AvatarCircle(text: "@", size: 32, fill: AppTheme.darkSurfaceAlt)
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Indirect mentions")
                            .font(AppFont.body(13))
                            .foregroundColor(AppTheme.textPrimaryDark)
                        Text("@here, @everyone")
                            .font(AppFont.body(11))
                            .foregroundColor(AppTheme.textSecondaryDark)
                    }
                    Spacer()
                    Toggle("", isOn: $indirectMentions)
                        .labelsHidden()
                        .tint(AppTheme.accentBlue)
                }
            }

            AppCard(background: AppTheme.darkCard) {
                HStack(spacing: 10) {
                    Image(systemName: "gearshape.fill")
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundColor(AppTheme.textSecondaryDark)
                        .frame(width: 20)
                    Text("Notification settings")
                        .font(AppFont.body(13))
                        .foregroundColor(AppTheme.textPrimaryDark)
                    Spacer()
                    Image(systemName: "chevron.right")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundColor(AppTheme.textSecondaryDark)
                }
            }
        }
        .padding(16)
        .background(AppTheme.sheetBackground.ignoresSafeArea())
    }
}
