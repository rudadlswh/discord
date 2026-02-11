import Foundation

enum ApiClient {
    struct HttpResult {
        let code: Int
        let body: String
    }

    enum ApiError: Error {
        case invalidUrl
    }

    private static let session: URLSession = {
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 3
        config.timeoutIntervalForResource = 3
        return URLSession(configuration: config)
    }()

    static func buildUrl(baseUrl: String, path: String) -> String {
        let trimmed = baseUrl.hasSuffix("/") ? String(baseUrl.dropLast()) : baseUrl
        return trimmed + path
    }

    static func executeRequest(
        method: String,
        url: String,
        jsonBody: [String: Any]? = nil,
        token: String? = nil
    ) async throws -> HttpResult {
        guard let endpoint = URL(string: url) else {
            throw ApiError.invalidUrl
        }

        var request = URLRequest(url: endpoint)
        request.httpMethod = method
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        if let token, !token.isEmpty {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        if let jsonBody {
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            request.httpBody = try JSONSerialization.data(withJSONObject: jsonBody, options: [])
        }

        let (data, response) = try await session.data(for: request)
        let code = (response as? HTTPURLResponse)?.statusCode ?? -1
        let body = String(data: data, encoding: .utf8) ?? ""
        return HttpResult(code: code, body: body)
    }

    static func extractErrorMessage(_ body: String) -> String {
        let trimmed = body.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty {
            return "No response body"
        }

        guard let data = body.data(using: .utf8) else {
            return body
        }

        do {
            if let json = try JSONSerialization.jsonObject(with: data) as? [String: Any] {
                let error = (json["error"] as? String ?? "")
                    .trimmingCharacters(in: .whitespacesAndNewlines)
                return error.isEmpty ? body : error
            }
        } catch {
            return body
        }

        return body
    }
}
