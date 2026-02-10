import Foundation

struct AuthPayload {
    let token: String
    let userId: String
    let username: String
    let displayName: String
}

enum AuthServiceError: LocalizedError {
    case message(String)
    case invalidResponse

    var errorDescription: String? {
        switch self {
        case .message(let message):
            return message
        case .invalidResponse:
            return "Unexpected server response."
        }
    }
}

enum AuthService {
    static let defaultBaseUrl = "http://192.168.45.158:8080"

    static func login(email: String, password: String, baseUrl: String? = nil) async throws -> AuthPayload {
        let url = ApiClient.buildUrl(baseUrl: baseUrl ?? defaultBaseUrl, path: "/api/auth/login")
        let result = try await ApiClient.executeRequest(
            method: "POST",
            url: url,
            jsonBody: [
                "email": email,
                "password": password
            ]
        )
        return try parseAuthResponse(result)
    }

    static func register(
        email: String,
        username: String,
        displayName: String,
        password: String,
        baseUrl: String? = nil
    ) async throws -> AuthPayload {
        let url = ApiClient.buildUrl(baseUrl: baseUrl ?? defaultBaseUrl, path: "/api/auth/register")
        let result = try await ApiClient.executeRequest(
            method: "POST",
            url: url,
            jsonBody: [
                "email": email,
                "username": username,
                "displayName": displayName,
                "password": password
            ]
        )
        return try parseAuthResponse(result)
    }

    private static func parseAuthResponse(_ result: ApiClient.HttpResult) throws -> AuthPayload {
        guard (200...299).contains(result.code) else {
            let message = ApiClient.extractErrorMessage(result.body)
            throw AuthServiceError.message("ERROR \(result.code): \(message)")
        }

        guard let data = result.body.data(using: .utf8) else {
            throw AuthServiceError.invalidResponse
        }

        let json = try JSONSerialization.jsonObject(with: data, options: [])
        guard
            let dict = json as? [String: Any],
            let token = dict["token"] as? String,
            let user = dict["user"] as? [String: Any],
            let userId = stringValue(user["id"]),
            let username = user["username"] as? String,
            let displayName = user["displayName"] as? String
        else {
            throw AuthServiceError.invalidResponse
        }

        return AuthPayload(
            token: token,
            userId: userId,
            username: username,
            displayName: displayName
        )
    }

    private static func stringValue(_ value: Any?) -> String? {
        if let value = value as? String {
            return value
        }
        if let number = value as? NSNumber {
            return number.stringValue
        }
        return nil
    }
}
