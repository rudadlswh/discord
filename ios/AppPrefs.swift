import Foundation

enum AppPrefs {
    private static let keyToken = "auth_token"
    private static let keyUserId = "user_id"
    private static let keyUsername = "username"
    private static let keyDisplayName = "display_name"
    private static let keyBaseUrl = "base_url"
    private static let keySeenFriendRequests = "seen_friend_requests"
    private static let keySeenMessageIds = "seen_message_ids"

    private static let defaults = UserDefaults.standard

    static func getToken() -> String? {
        defaults.string(forKey: keyToken)
    }

    static func getUserId() -> String? {
        defaults.string(forKey: keyUserId)
    }

    static func getUsername() -> String? {
        defaults.string(forKey: keyUsername)
    }

    static func getDisplayName() -> String? {
        defaults.string(forKey: keyDisplayName)
    }

    static func saveAuth(token: String, userId: String, username: String, displayName: String) {
        defaults.set(token, forKey: keyToken)
        defaults.set(userId, forKey: keyUserId)
        defaults.set(username, forKey: keyUsername)
        defaults.set(displayName, forKey: keyDisplayName)
    }

    static func clearAuth() {
        defaults.removeObject(forKey: keyToken)
        defaults.removeObject(forKey: keyUserId)
        defaults.removeObject(forKey: keyUsername)
        defaults.removeObject(forKey: keyDisplayName)
    }

    static func getBaseUrl(defaultValue: String) -> String {
        defaults.string(forKey: keyBaseUrl) ?? defaultValue
    }

    static func setBaseUrl(_ value: String) {
        defaults.set(value, forKey: keyBaseUrl)
    }

    private static func seenFriendRequestsKey() -> String {
        if let userId = getUserId(), !userId.isEmpty {
            return "\(keySeenFriendRequests)_\(userId)"
        }
        return keySeenFriendRequests
    }

    static func getSeenFriendRequestIds() -> Set<String> {
        let key = seenFriendRequestsKey()
        let list = defaults.stringArray(forKey: key) ?? []
        return Set(list)
    }

    static func addSeenFriendRequestIds(_ ids: [String]) {
        guard !ids.isEmpty else { return }
        let key = seenFriendRequestsKey()
        var current = getSeenFriendRequestIds()
        current.formUnion(ids)
        defaults.set(Array(current), forKey: key)
    }

    private static func seenMessageIdsKey() -> String {
        if let userId = getUserId(), !userId.isEmpty {
            return "\(keySeenMessageIds)_\(userId)"
        }
        return keySeenMessageIds
    }

    static func getSeenMessageIds() -> Set<String> {
        let key = seenMessageIdsKey()
        let list = defaults.stringArray(forKey: key) ?? []
        return Set(list)
    }

    static func addSeenMessageIds(_ ids: [String]) {
        guard !ids.isEmpty else { return }
        let key = seenMessageIdsKey()
        var current = getSeenMessageIds()
        current.formUnion(ids)
        defaults.set(Array(current), forKey: key)
    }
}
