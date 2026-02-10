import SwiftUI

enum HomeTab: String, CaseIterable, Identifiable {
    case home = "Home"
    case notifications = "Notifications"
    case profile = "Profile"

    var id: String { rawValue }

    var icon: String {
        switch self {
        case .home:
            return "message.fill"
        case .notifications:
            return "bell.fill"
        case .profile:
            return "person.fill"
        }
    }
}

struct HomeTabsView: View {
    var onLogout: () -> Void = {}
    @State private var selectedTab: HomeTab = .home

    var body: some View {
        VStack(spacing: 0) {
            ZStack {
                switch selectedTab {
                case .home:
                    MessagesView()
                case .notifications:
                    NotificationsView()
                case .profile:
                    ProfileView(onLogout: onLogout)
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)

            Rectangle()
                .fill(AppTheme.darkBorder)
                .frame(height: 1)

            HomeTabBar(selectedTab: $selectedTab)
        }
        .background(AppTheme.darkBackground.ignoresSafeArea())
    }
}

struct HomeTabBar: View {
    @Binding var selectedTab: HomeTab

    var body: some View {
        HStack(spacing: 0) {
            ForEach(HomeTab.allCases) { tab in
                Button {
                    selectedTab = tab
                } label: {
                    VStack(spacing: 4) {
                        Image(systemName: tab.icon)
                            .font(.system(size: 18, weight: .semibold))
                            .foregroundColor(selectedTab == tab ? AppTheme.navActive : AppTheme.navInactive)

                        Circle()
                            .fill(selectedTab == tab ? AppTheme.navActive : Color.clear)
                            .frame(width: 6, height: 6)

                        Text(tab.rawValue)
                            .font(AppFont.body(10))
                            .foregroundColor(selectedTab == tab ? AppTheme.navActive : AppTheme.navInactive)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 8)
                }
            }
        }
        .frame(height: 64)
        .background(AppTheme.darkBackground)
    }
}

#Preview {
    HomeTabsView()
}
