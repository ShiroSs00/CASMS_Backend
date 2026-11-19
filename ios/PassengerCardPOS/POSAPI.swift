import Foundation

struct POSCredentials {
    let baseURL: URL
    let employeeToken: String
    let sessionToken: String
}

struct PassengerCard: Decodable {
    let bookingPassengerId: UUID
    let passengerName: String
    let confirmedAt: String?
}

struct POSAPI {
    private struct Envelope<Value: Decodable>: Decodable {
        let data: Value?
        let message: String?
    }

    private struct Login: Encodable {
        let username: String
        let password: String
    }

    private struct LoginResult: Decodable {
        let accessToken: String
    }

    private struct OpenSession: Encodable {
        let cruiseId: UUID
        let deviceSecret: String?
        let deviceAccessToken: String?
    }

    private struct SessionResult: Decodable {
        let sessionToken: String
    }

    private struct Claim: Encodable {
        let code: String
    }

    private struct Confirmation: Encodable {
        let code: String
        let cardIdentifier: String
    }

    private struct CardLookup: Encodable {
        let cardIdentifier: String
    }

    struct APIError: LocalizedError {
        let errorDescription: String?

        init(_ message: String) {
            errorDescription = message
        }
    }

    static func signIn(baseURL: URL, username: String, password: String, terminalID: UUID,
                       cruiseID: UUID, deviceCredential: String, usesDeviceToken: Bool) async throws -> POSCredentials {
        let accessToken = try await signInAccount(baseURL: baseURL, username: username, password: password)
        let session: SessionResult = try await post(
            at: baseURL, path: "pos-terminals/\(terminalID.uuidString)/sessions",
            body: OpenSession(cruiseId: cruiseID, deviceSecret: usesDeviceToken ? nil : deviceCredential,
                              deviceAccessToken: usesDeviceToken ? deviceCredential : nil),
            employeeToken: accessToken)
        return POSCredentials(baseURL: baseURL, employeeToken: accessToken, sessionToken: session.sessionToken)
    }

    static func signInAccount(baseURL: URL, username: String, password: String) async throws -> String {
        let result: LoginResult = try await post(
            at: baseURL, path: "auth/login", body: Login(username: username, password: password))
        return result.accessToken
    }

    struct CheckinResult: Decodable {
        let checkinStatus: String
    }

    struct CardClaim: Decodable {
        let code: String
        let expiresAt: String
    }

    static func startCustomerCheckin(baseURL: URL, token: String, passengerID: UUID,
                                     contactName: String, contactPhone: String) async throws -> CheckinResult {
        var fields = URLComponents()
        fields.queryItems = [
            URLQueryItem(name: "emergencyContactName", value: contactName),
            URLQueryItem(name: "emergencyContactPhone", value: contactPhone)
        ]
        let encoded = fields.percentEncodedQuery?.replacingOccurrences(of: "+", with: "%2B") ?? ""
        return try await send(at: baseURL, path: "checkin/passenger/\(passengerID)/start",
                              payload: Data(encoded.utf8), contentType: "application/x-www-form-urlencoded",
                              employeeToken: token, sessionToken: nil)
    }

    static func completeCustomerCheckin(baseURL: URL, token: String, passengerID: UUID) async throws -> CheckinResult {
        try await post(at: baseURL, path: "checkin/passenger/\(passengerID)/complete",
                       body: [String: String](), employeeToken: token)
    }

    static func customerClaim(baseURL: URL, token: String, passengerID: UUID) async throws -> CardClaim {
        try await post(at: baseURL, path: "checkin/passenger/\(passengerID)/card-claim",
                       body: [String: String](), employeeToken: token)
    }

    static func secureBaseURL(_ address: String) -> URL? {
        guard let url = URL(string: address.trimmingCharacters(in: .whitespacesAndNewlines)),
              url.scheme?.lowercased() == "https", url.host != nil, url.user == nil,
              url.password == nil, url.query == nil, url.fragment == nil else { return nil }
        return url
    }

    let credentials: POSCredentials

    func signOut() async throws {
        let url = credentials.baseURL.appendingPathComponent("pos/session")
        var request = URLRequest(url: url)
        request.httpMethod = "DELETE"
        request.setValue("Bearer \(credentials.employeeToken)", forHTTPHeaderField: "Authorization")
        request.setValue(credentials.sessionToken, forHTTPHeaderField: "X-POS-Session")
        let (_, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse,
              [204, 401, 403].contains(http.statusCode) else {
            throw APIError("Không thể đóng phiên POS. Thử lại.")
        }
    }

    func preview(code: String) async throws -> PassengerCard {
        try await send("preview", body: Claim(code: code))
    }

    func confirm(code: String, cardIdentifier: String) async throws -> PassengerCard {
        try await send("confirm", body: Confirmation(code: code, cardIdentifier: cardIdentifier))
    }

    func lookup(cardIdentifier: String) async throws -> PassengerCard {
        try await send("lookup", body: CardLookup(cardIdentifier: cardIdentifier))
    }

    private func send<Body: Encodable>(_ action: String, body: Body) async throws -> PassengerCard {
        try await Self.post(at: credentials.baseURL, path: "pos/passenger-cards/\(action)",
                            body: body, employeeToken: credentials.employeeToken,
                            sessionToken: credentials.sessionToken)
    }

    private static func post<Body: Encodable, Value: Decodable>(
        at base: URL, path: String, body: Body, employeeToken: String? = nil,
        sessionToken: String? = nil
    ) async throws -> Value {
        try await send(at: base, path: path, payload: JSONEncoder().encode(body),
                       contentType: "application/json", employeeToken: employeeToken, sessionToken: sessionToken)
    }

    private static func send<Value: Decodable>(
        at base: URL, path: String, payload: Data, contentType: String,
        employeeToken: String?, sessionToken: String?
    ) async throws -> Value {
        let url = path.split(separator: "/").reduce(base) { $0.appendingPathComponent(String($1)) }
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.httpBody = payload
        request.setValue(contentType, forHTTPHeaderField: "Content-Type")
        if let employeeToken {
            request.setValue("Bearer \(employeeToken)", forHTTPHeaderField: "Authorization")
        }
        if let sessionToken {
            request.setValue(sessionToken, forHTTPHeaderField: "X-POS-Session")
        }
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse else {
            throw APIError("Máy chủ trả về phản hồi không hợp lệ.")
        }
        guard (200..<300).contains(http.statusCode) else {
            let error = try? JSONDecoder().decode(Envelope<String>.self, from: data)
            throw APIError(error?.message ?? "Yêu cầu bị từ chối (HTTP \(http.statusCode)).")
        }
        let envelope = try JSONDecoder().decode(Envelope<Value>.self, from: data)
        guard let value = envelope.data else {
            throw APIError(envelope.message ?? "Phản hồi bị thiếu dữ liệu.")
        }
        return value
    }
}
