import SwiftUI

struct ProfileView: View {
    var onLogout: () -> Void = {}
    private var displayName: String {
        AppPrefs.getDisplayName() ?? "Jordan"
    }

    private var username: String {
        AppPrefs.getUsername() ?? "join2988"
    }

    private var avatarInitial: String {
        displayName.trimmingCharacters(in: .whitespacesAndNewlines).prefix(1).uppercased()
    }

    @State private var animateIn = false
    @State private var showFriendsSheet = false
    @State private var friends: [FriendSummary] = []
    @State private var friendsLoading = false
    @State private var friendsError: String? = nil

    var body: some View {
        ScrollView {
            VStack(spacing: 0) {
                ZStack(alignment: .topTrailing) {
                    LinearGradient(
                        colors: [AppTheme.profileHeaderStart, AppTheme.profileHeaderEnd],
                        startPoint: .leading,
                        endPoint: .trailing
                    )
                    .frame(height: 150)

                    HStack(spacing: 8) {
                        IconBubble(symbol: "checkmark.seal.fill")
                        IconBubble(symbol: "trophy.fill")
                        Text("Nitro")
                            .font(AppFont.subtitle(11))
                            .foregroundColor(AppTheme.textPrimaryDark)
                            .padding(.horizontal, 10)
                            .frame(height: 32)
                            .background(Capsule().fill(AppTheme.darkSurfaceAlt))
                        IconBubble(symbol: "gearshape.fill")
                    }
                    .padding(12)
                }

                VStack(alignment: .leading, spacing: 16) {
                    HStack(spacing: 12) {
                        AvatarCircle(text: avatarInitial, size: 72, fill: AppTheme.darkSurfaceAlt)

                        VStack(alignment: .leading, spacing: 4) {
                            Text(displayName)
                                .font(AppFont.title(20))
                                .foregroundColor(AppTheme.textPrimaryDark)
                            Text("@\(username)")
                                .font(AppFont.body(13))
                                .foregroundColor(AppTheme.textSecondaryDark)
                        }
                    }
                    .padding(.top, -32)

                    AppCard {
                        Text("Today I learned...")
                            .font(AppFont.body(12))
                            .foregroundColor(AppTheme.textSecondaryDark)
                    }

                    Button(action: {}) {
                        HStack(spacing: 8) {
                            Image(systemName: "square.and.pencil")
                                .font(.system(size: 14, weight: .semibold))
                            Text("Edit profile")
                                .font(AppFont.subtitle(14))
                        }
                        .foregroundColor(AppTheme.textPrimaryDark)
                        .frame(maxWidth: .infinity, minHeight: 48)
                        .background(RoundedRectangle(cornerRadius: 14).fill(AppTheme.darkSurfaceAlt))
                    }

                    AppCard {
                        HStack(spacing: 10) {
                            Image(systemName: "calendar")
                                .font(.system(size: 16, weight: .semibold))
                                .foregroundColor(AppTheme.textSecondaryDark)
                            VStack(alignment: .leading, spacing: 2) {
                                Text("Member since")
                                    .font(AppFont.body(11))
                                    .foregroundColor(AppTheme.textSecondaryDark)
                                Text("Sep 11, 2017")
                                    .font(AppFont.subtitle(13))
                                    .foregroundColor(AppTheme.textPrimaryDark)
                            }
                        }
                    }

                    SectionHeader(title: "Connections")
                    AppCard {
                        VStack(spacing: 10) {
                        }
                    }

                    SectionHeader(title: "Friends")
                    Button(action: { showFriendsSheet = true }) {
                        AppCard {
                            HStack(spacing: 6) {
                                Spacer()
                                Image(systemName: "chevron.right")
                                    .font(.system(size: 12, weight: .semibold))
                                    .foregroundColor(AppTheme.textSecondaryDark)
                            }
                        }
                    }
                    .buttonStyle(.plain)

                    SectionHeader(title: "Notes")
                    AppCard {
                        HStack(spacing: 10) {
                            Image(systemName: "square.and.pencil")
                                .font(.system(size: 16, weight: .semibold))
                                .foregroundColor(AppTheme.textSecondaryDark)
                            Text("Add a note for yourself.")
                                .font(AppFont.body(12))
                                .foregroundColor(AppTheme.textSecondaryDark)
                            Spacer()
                        }
                    }

                    Button(action: onLogout) {
                        HStack(spacing: 8) {
                            Image(systemName: "arrow.right.square")
                                .font(.system(size: 14, weight: .semibold))
                            Text("Log out")
                                .font(AppFont.subtitle(14))
                        }
                        .foregroundColor(.red)
                        .frame(maxWidth: .infinity, minHeight: 48)
                        .background(
                            RoundedRectangle(cornerRadius: 14).fill(AppTheme.darkSurfaceAlt)
                        )
                    }
                }
                .padding(16)
            }
            .opacity(animateIn ? 1 : 0)
            .offset(y: animateIn ? 0 : 12)
            .animation(.easeOut(duration: 0.5), value: animateIn)
        }
        .background(AppTheme.darkBackground)
        .onAppear { animateIn = true }
        .sheet(isPresented: $showFriendsSheet) {
            FriendsSheetView(
                friends: friends,
                isLoading: friendsLoading,
                errorMessage: friendsError,
                onRefresh: {
                    await refreshFriends(showLoading: true)
                }
            )
            .presentationDetents([.height(420), .medium])
            .onAppear {
                Task { await refreshFriends(showLoading: true) }
            }
        }
    }

    @MainActor
    private func refreshFriends(showLoading: Bool) async {
        if showLoading {
            friendsLoading = true
        }
        defer {
            if showLoading {
                friendsLoading = false
            }
        }

        guard let token = AppPrefs.getToken(), !token.isEmpty else {
            friendsError = "로그인이 필요합니다."
            return
        }

        let baseUrl = AppPrefs.getBaseUrl(defaultValue: AuthService.defaultBaseUrl)
        friendsError = nil

        do {
            friends = try await FriendService.fetchFriends(
                baseUrl: baseUrl,
                token: token
            )
        } catch {
            friendsError = error.localizedDescription
        }
    }
}

struct IconBubble: View {
    let symbol: String

    var body: some View {
        Image(systemName: symbol)
            .font(.system(size: 14, weight: .semibold))
            .foregroundColor(AppTheme.textPrimaryDark)
            .frame(width: 32, height: 32)
            .background(Circle().fill(AppTheme.darkSurfaceAlt))
    }
}

struct SectionHeader: View {
    let title: String

    var body: some View {
        Text(title)
            .font(AppFont.subtitle(12))
            .foregroundColor(AppTheme.textSecondaryDark)
            .frame(maxWidth: .infinity, alignment: .leading)
    }
}

struct ConnectionRow: View {
    let title: String

    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: "link")
                .font(.system(size: 14, weight: .semibold))
                .foregroundColor(AppTheme.textSecondaryDark)
            Text(title)
                .font(AppFont.body(13))
                .foregroundColor(AppTheme.textPrimaryDark)
            Spacer()
            Image(systemName: "chevron.right")
                .font(.system(size: 12, weight: .semibold))
                .foregroundColor(AppTheme.textSecondaryDark)
        }
    }
}

struct FriendsSheetView: View {
    let friends: [FriendSummary]
    let isLoading: Bool
    let errorMessage: String?
    let onRefresh: () async -> Void
    @State private var selectedFriend: FriendSummary? = nil

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Text("친구 목록")
                    .font(AppFont.subtitle(16))
                    .foregroundColor(AppTheme.textPrimaryDark)

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
                        Text("아직 친구가 없어요.")
                            .font(AppFont.body(12))
                            .foregroundColor(AppTheme.textSecondaryDark)
                    }
                } else {
                    VStack(spacing: 10) {
                        ForEach(friends) { friend in
                            Button {
                                selectedFriend = friend
                            } label: {
                                AppCard {
                                    FriendRow(friend: friend)
                                }
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
            }
            .padding(16)
        }
        .background(AppTheme.sheetBackground.ignoresSafeArea())
        .refreshable {
            await onRefresh()
        }
        .sheet(item: $selectedFriend) { friend in
            FriendProfileSheetView(friend: friend)
                .presentationDetents([.height(420), .medium])
        }
    }
}

struct FriendRow: View {
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
            Image(systemName: "chevron.right")
                .font(.system(size: 12, weight: .semibold))
                .foregroundColor(AppTheme.textSecondaryDark)
        }
    }
}

struct FriendProfileSheetView: View {
    @Environment(\.dismiss) private var dismiss
    let friend: FriendSummary

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                HStack {
                    Text("친구 프로필")
                        .font(AppFont.subtitle(16))
                        .foregroundColor(AppTheme.textPrimaryDark)
                    Spacer()
                    Button("닫기") {
                        dismiss()
                    }
                    .font(AppFont.subtitle(12))
                    .foregroundColor(AppTheme.textSecondaryDark)
                }

                let initial = friend.displayName
                    .trimmingCharacters(in: .whitespacesAndNewlines)
                    .prefix(1)
                    .uppercased()
                HStack(spacing: 12) {
                    AvatarCircle(
                        text: String(initial),
                        size: 64,
                        fill: AppTheme.accentBlue
                    )

                    VStack(alignment: .leading, spacing: 4) {
                        Text(friend.displayName)
                            .font(AppFont.title(18))
                            .foregroundColor(AppTheme.textPrimaryDark)
                        Text("@\(friend.username)")
                            .font(AppFont.body(12))
                            .foregroundColor(AppTheme.textSecondaryDark)
                    }
                }

                AppCard {
                    Text("상태 메시지를 설정하지 않았어요.")
                        .font(AppFont.body(12))
                        .foregroundColor(AppTheme.textSecondaryDark)
                }

                SectionHeader(title: "Notes")
                AppCard {
                    Text("메모를 추가하세요.")
                        .font(AppFont.body(12))
                        .foregroundColor(AppTheme.textSecondaryDark)
                }
            }
            .padding(16)
        }
        .background(AppTheme.sheetBackground.ignoresSafeArea())
    }
}
