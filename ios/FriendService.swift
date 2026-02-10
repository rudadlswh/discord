import Foundation

struct UserLookup {
    let id: String
    let username: String
    let displayName: String
}

struct PendingFriendRequest: Identifiable {
    let id: String
    let requesterId: String
    let displayName: String
    let username: String
}

struct FriendSummary: Identifiable {
    let id: String
    let username: String
    let displayName: String
}

struct DirectMessagePreview: Identifiable {
    let id: String
    let senderId: String
    let content: String
    let createdAt: String
}

struct DirectThread: Identifiable {
    let id: String
    let friendId: String
    let friendUsername: String
    let friendDisplayName: String
    let lastMessage: DirectMessagePreview?
}

struct ChatMessage: Identifiable {
    let id: String
    let senderId: String
    let content: String
    let createdAt: String
}

enum FriendServiceError: LocalizedError {
    case message(String)
    case invalidResponse
    case invalidQuery

    var errorDescription: String? {
        switch self {
        case .message(let message):
            return message
        case .invalidResponse:
            return "Unexpected server response."
        case .invalidQuery:
            return "Please enter a username or email."
        }
    }
}

enum FriendService {
    static func lookupUser(query: String, baseUrl: String, token: String) async throws -> UserLookup {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty {
            throw FriendServiceError.invalidQuery
        }

        let base = ApiClient.buildUrl(baseUrl: baseUrl, path: "/api/users/lookup")
        guard var components = URLComponents(string: base) else {
            throw FriendServiceError.invalidResponse
        }
        components.queryItems = [URLQueryItem(name: "query", value: trimmed)]
        guard let url = components.url?.absoluteString else {
            throw FriendServiceError.invalidResponse
        }

        let result = try await ApiClient.executeRequest(
            method: "GET",
            url: url,
            token: token
        )

        guard (200...299).contains(result.code) else {
            let message = ApiClient.extractErrorMessage(result.body)
            throw FriendServiceError.message("ERROR \(result.code): \(message)")
        }

        guard let data = result.body.data(using: .utf8) else {
            throw FriendServiceError.invalidResponse
        }

        let json = try JSONSerialization.jsonObject(with: data, options: [])
        guard
            let dict = json as? [String: Any],
            let id = dict["id"] as? String,
            let username = dict["username"] as? String,
            let displayName = dict["displayName"] as? String
        else {
            throw FriendServiceError.invalidResponse
        }

        return UserLookup(id: id, username: username, displayName: displayName)
    }

    static func sendFriendRequest(addresseeId: String, baseUrl: String, token: String) async throws {
        let url = ApiClient.buildUrl(baseUrl: baseUrl, path: "/api/friends/requests")
        let result = try await ApiClient.executeRequest(
            method: "POST",
            url: url,
            jsonBody: [
                "addresseeId": addresseeId
            ],
            token: token
        )

        guard (200...299).contains(result.code) else {
            let message = ApiClient.extractErrorMessage(result.body)
            throw FriendServiceError.message("ERROR \(result.code): \(message)")
        }
    }

    static func fetchPendingRequests(baseUrl: String, token: String) async throws -> [PendingFriendRequest] {
        let url = ApiClient.buildUrl(baseUrl: baseUrl, path: "/api/friends/requests")
        let result = try await ApiClient.executeRequest(
            method: "GET",
            url: url,
            token: token
        )

        guard (200...299).contains(result.code) else {
            let message = ApiClient.extractErrorMessage(result.body)
            throw FriendServiceError.message("ERROR \(result.code): \(message)")
        }

        let trimmedBody = result.body.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmedBody.isEmpty {
            return []
        }

        guard let data = trimmedBody.data(using: .utf8) else {
            return []
        }

        let json = try JSONSerialization.jsonObject(with: data, options: [])
        guard let array = json as? [[String: Any]] else {
            return []
        }

        var cache: [String: UserLookup] = [:]
        var pending: [PendingFriendRequest] = []

        for item in array {
            guard let id = item["id"] as? String,
                  let requesterId = item["requesterId"] as? String else {
                continue
            }

            var lookup = cache[requesterId]
            if lookup == nil {
                lookup = try? await lookupUser(
                    query: requesterId,
                    baseUrl: baseUrl,
                    token: token
                )
                if let lookup {
                    cache[requesterId] = lookup
                }
            }

            pending.append(
                PendingFriendRequest(
                    id: id,
                    requesterId: requesterId,
                    displayName: lookup?.displayName ?? requesterId,
                    username: lookup?.username ?? requesterId
                )
            )
        }

        return pending
    }

    static func fetchFriends(baseUrl: String, token: String) async throws -> [FriendSummary] {
        let url = ApiClient.buildUrl(baseUrl: baseUrl, path: "/api/friends")
        let result = try await ApiClient.executeRequest(
            method: "GET",
            url: url,
            token: token
        )

        guard (200...299).contains(result.code) else {
            let message = ApiClient.extractErrorMessage(result.body)
            throw FriendServiceError.message("ERROR \(result.code): \(message)")
        }

        let trimmedBody = result.body.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmedBody.isEmpty {
            return []
        }

        guard let data = trimmedBody.data(using: .utf8) else {
            return []
        }

        let json = try JSONSerialization.jsonObject(with: data, options: [])
        guard let array = json as? [[String: Any]] else {
            return []
        }

        return array.compactMap { item in
            guard
                let id = item["id"] as? String,
                let username = item["username"] as? String,
                let displayName = item["displayName"] as? String
            else {
                return nil
            }
            return FriendSummary(id: id, username: username, displayName: displayName)
        }
    }

    static func fetchDirectThreads(baseUrl: String, token: String) async throws -> [DirectThread] {
        let url = ApiClient.buildUrl(baseUrl: baseUrl, path: "/api/dm/threads")
        let result = try await ApiClient.executeRequest(
            method: "GET",
            url: url,
            token: token
        )

        guard (200...299).contains(result.code) else {
            let message = ApiClient.extractErrorMessage(result.body)
            throw FriendServiceError.message("ERROR \(result.code): \(message)")
        }

        let trimmedBody = result.body.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmedBody.isEmpty {
            return []
        }

        guard let data = trimmedBody.data(using: .utf8) else {
            return []
        }

        let json = try JSONSerialization.jsonObject(with: data, options: [])
        guard let array = json as? [[String: Any]] else {
            return []
        }

        return array.compactMap { item in
            guard
                let channelId = item["channelId"] as? String,
                let friendId = item["friendId"] as? String,
                let friendUsername = item["friendUsername"] as? String,
                let friendDisplayName = item["friendDisplayName"] as? String
            else {
                return nil
            }

            var lastMessage: DirectMessagePreview? = nil
            if let last = item["lastMessage"] as? [String: Any],
               let messageId = last["id"] as? String,
               let senderId = last["senderId"] as? String,
               let content = last["content"] as? String,
               let createdAt = last["createdAt"] as? String {
                lastMessage = DirectMessagePreview(
                    id: messageId,
                    senderId: senderId,
                    content: content,
                    createdAt: createdAt
                )
            }

            return DirectThread(
                id: channelId,
                friendId: friendId,
                friendUsername: friendUsername,
                friendDisplayName: friendDisplayName,
                lastMessage: lastMessage
            )
        }
    }

    static func sendDirectMessage(
        toUserId: String,
        content: String,
        baseUrl: String,
        token: String
    ) async throws {
        let url = ApiClient.buildUrl(baseUrl: baseUrl, path: "/api/dm/send")
        let result = try await ApiClient.executeRequest(
            method: "POST",
            url: url,
            jsonBody: [
                "toUserId": toUserId,
                "content": content
            ],
            token: token
        )

        guard (200...299).contains(result.code) else {
            let message = ApiClient.extractErrorMessage(result.body)
            throw FriendServiceError.message("ERROR \(result.code): \(message)")
        }
    }

    static func fetchChannelMessages(
        channelId: String,
        baseUrl: String,
        token: String
    ) async throws -> [ChatMessage] {
        let url = ApiClient.buildUrl(baseUrl: baseUrl, path: "/api/channels/\(channelId)/messages")
        let result = try await ApiClient.executeRequest(
            method: "GET",
            url: url,
            token: token
        )

        guard (200...299).contains(result.code) else {
            let message = ApiClient.extractErrorMessage(result.body)
            throw FriendServiceError.message("ERROR \(result.code): \(message)")
        }

        let trimmedBody = result.body.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmedBody.isEmpty {
            return []
        }

        guard let data = trimmedBody.data(using: .utf8) else {
            return []
        }

        let json = try JSONSerialization.jsonObject(with: data, options: [])
        guard let array = json as? [[String: Any]] else {
            return []
        }

        return array.compactMap { item in
            guard
                let id = item["id"] as? String,
                let senderId = item["senderId"] as? String,
                let content = item["content"] as? String,
                let createdAt = item["createdAt"] as? String
            else {
                return nil
            }
            return ChatMessage(
                id: id,
                senderId: senderId,
                content: content,
                createdAt: createdAt
            )
        }
    }

    static func sendChannelMessage(
        channelId: String,
        content: String,
        baseUrl: String,
        token: String
    ) async throws -> ChatMessage {
        let url = ApiClient.buildUrl(baseUrl: baseUrl, path: "/api/channels/\(channelId)/messages")
        let result = try await ApiClient.executeRequest(
            method: "POST",
            url: url,
            jsonBody: [
                "content": content
            ],
            token: token
        )

        guard (200...299).contains(result.code) else {
            let message = ApiClient.extractErrorMessage(result.body)
            throw FriendServiceError.message("ERROR \(result.code): \(message)")
        }

        guard let data = result.body.data(using: .utf8) else {
            throw FriendServiceError.invalidResponse
        }

        let json = try JSONSerialization.jsonObject(with: data, options: [])
        guard
            let dict = json as? [String: Any],
            let id = dict["id"] as? String,
            let senderId = dict["senderId"] as? String,
            let content = dict["content"] as? String,
            let createdAt = dict["createdAt"] as? String
        else {
            throw FriendServiceError.invalidResponse
        }

        return ChatMessage(
            id: id,
            senderId: senderId,
            content: content,
            createdAt: createdAt
        )
    }

    static func acceptFriendRequest(requestId: String, baseUrl: String, token: String) async throws {
        let url = ApiClient.buildUrl(
            baseUrl: baseUrl,
            path: "/api/friends/requests/\(requestId)/accept"
        )
        let result = try await ApiClient.executeRequest(
            method: "POST",
            url: url,
            token: token
        )

        guard (200...299).contains(result.code) else {
            let message = ApiClient.extractErrorMessage(result.body)
            throw FriendServiceError.message("ERROR \(result.code): \(message)")
        }
    }

    static func rejectFriendRequest(requestId: String, baseUrl: String, token: String) async throws {
        let url = ApiClient.buildUrl(
            baseUrl: baseUrl,
            path: "/api/friends/requests/\(requestId)/reject"
        )
        let result = try await ApiClient.executeRequest(
            method: "POST",
            url: url,
            token: token
        )

        guard (200...299).contains(result.code) else {
            let message = ApiClient.extractErrorMessage(result.body)
            throw FriendServiceError.message("ERROR \(result.code): \(message)")
        }
    }
}
