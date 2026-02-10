import SwiftUI
import UIKit
import UserNotifications

final class NotificationDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        return true
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([.banner, .sound, .badge])
    }
}

@main
struct DiscordLiteApp: App {
    @UIApplicationDelegateAdaptor(NotificationDelegate.self) var notificationDelegate
    @State private var isAuthenticated = AppPrefs.getToken() != nil

    var body: some Scene {
        WindowGroup {
            if isAuthenticated {
                HomeTabsView(onLogout: {
                    AppPrefs.clearAuth()
                    isAuthenticated = false
                })
            } else {
                AuthFlowView(onAuthSuccess: { isAuthenticated = true })
            }
        }
    }
}
