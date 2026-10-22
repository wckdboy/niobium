import Foundation
import Security

enum ConnectionPrefs {
    private static let connectionKey = "niobium.connection.v1"
    private static let tokenService = "io.sovware.niobium.gateway-token"

    static func load() -> GatewayConnection? {
        guard let data = UserDefaults.standard.data(forKey: connectionKey) else { return nil }
        return try? JSONDecoder().decode(GatewayConnection.self, from: data)
    }

    @discardableResult
    static func save(_ connection: GatewayConnection, token: String) -> Bool {
        let previous = load()
        guard Keychain.set(token, service: tokenService, account: connection.id.uuidString) else { return false }
        if let data = try? JSONEncoder().encode(connection) {
            UserDefaults.standard.set(data, forKey: connectionKey)
        }
        if let previous, previous.id != connection.id { Keychain.delete(service: tokenService, account: previous.id.uuidString) }
        return true
    }

    static func token(for connection: GatewayConnection) -> String {
        Keychain.get(service: tokenService, account: connection.id.uuidString) ?? ""
    }

    static func forget() {
        if let connection = load() {
            Keychain.delete(service: tokenService, account: connection.id.uuidString)
        }
        UserDefaults.standard.removeObject(forKey: connectionKey)
    }

    static func normalizedURL(_ raw: String) -> String? {
        var text = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return nil }
        if !text.contains("://") { text = "http://" + text }
        guard var components = URLComponents(string: text),
              let scheme = components.scheme?.lowercased(),
              scheme == "http" || scheme == "https",
              components.host != nil, components.user == nil, components.password == nil else { return nil }
        components.path = components.path.replacingOccurrences(of: "/+$", with: "", options: .regularExpression)
        components.query = nil
        components.fragment = nil
        return components.string
    }
}

enum Keychain {
    @discardableResult
    static func set(_ value: String, service: String, account: String) -> Bool {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account
        ]
        guard let data = value.data(using: .utf8) else { return false }
        let status = SecItemUpdate(query as CFDictionary, [kSecValueData as String: data] as CFDictionary)
        if status == errSecSuccess { return true }
        guard status == errSecItemNotFound else { return false }
        var insert = query
        insert[kSecValueData as String] = data
        insert[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        return SecItemAdd(insert as CFDictionary, nil) == errSecSuccess
    }

    static func get(service: String, account: String) -> String? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]
        var result: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &result) == errSecSuccess,
              let data = result as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }

    static func delete(service: String, account: String) {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account
        ]
        SecItemDelete(query as CFDictionary)
    }
}

struct RPCResult: @unchecked Sendable {
    let value: Any
}

enum RPCError: LocalizedError {
    case invalidURL
    case notConnected
    case timeout
    case transport(String)
    case server(code: Int, message: String)
    case malformed

    var errorDescription: String? {
        switch self {
        case .invalidURL: return "That gateway address isn’t valid."
        case .notConnected: return "Not connected to a Hermes gateway."
        case .timeout: return "The gateway didn’t answer in time."
        case .transport(let message): return message
        case .server(_, let message): return message
        case .malformed: return "The gateway sent an unexpected response."
        }
    }
}

@MainActor
protocol HermesRPC: AnyObject {
    var declinesNotShown: Bool { get }
    var onNotification: ((String, String, JSONObject) -> Void)? { get set }
    var onServerRequest: ((JSONValueID, String, JSONObject) -> Void)? { get set }
    var onDisconnect: ((String) -> Void)? { get set }
    func request(_ method: String, params: JSONObject, timeout: TimeInterval) async throws -> Any
    func respond(id: JSONValueID, result: JSONObject)
    func respondError(id: JSONValueID, code: Int, message: String)
    func disconnect(notify: Bool)
}

extension HermesRPC {
    func request(_ method: String, params: JSONObject = [:]) async throws -> Any {
        try await request(method, params: params, timeout: 60)
    }
    func respondError(id: JSONValueID, message: String) {
        respondError(id: id, code: -32601, message: message)
    }
}

@MainActor
final class HermesRPCClient: NSObject, HermesRPC {
    private var socket: URLSessionWebSocketTask?
    private var session: URLSession!
    private var nextID = 1
    private var pending: [Int: CheckedContinuation<RPCResult, Error>] = [:]
    private var pendingTimeouts: [Int: Task<Void, Never>] = [:]
    private var heartbeat: Task<Void, Never>?
    private var receiveTask: Task<Void, Never>?
    private var readyTimeoutTask: Task<Void, Never>?
    private var readyContinuation: CheckedContinuation<Void, Error>?
    private var isClosing = false

    var onNotification: ((String, String, JSONObject) -> Void)?
    var onServerRequest: ((JSONValueID, String, JSONObject) -> Void)?
    var onDisconnect: ((String) -> Void)?
    var onReady: ((JSONObject) -> Void)?
    private(set) var declinesNotShown = false

    override init() {
        super.init()
        let configuration = URLSessionConfiguration.default
        configuration.timeoutIntervalForRequest = 30
        configuration.waitsForConnectivity = true
        session = URLSession(configuration: configuration, delegate: self, delegateQueue: .main)
    }

    var connected: Bool { socket != nil }

    func connect(baseURL: String, token: String) async throws {
        disconnect(notify: false)
        guard let url = Self.webSocketURL(baseURL: baseURL, token: token) else { throw RPCError.invalidURL }
        isClosing = false
        var urlRequest = URLRequest(url: url)
        urlRequest.timeoutInterval = 20
        let task = session.webSocketTask(with: urlRequest)
        socket = task
        task.resume()
        receiveTask = Task { @MainActor [weak self] in
            await self?.receiveLoop(owning: task)
        }

        try await waitForReady(timeout: 16)
        heartbeat = Task { @MainActor [weak self] in
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(15))
                guard let self, self.socket != nil else { return }
                self.socket?.sendPing { _ in }
            }
        }
        do {
            let capabilities = try await request("client.capabilities", params: ["server_requests": true])
            if let dict = capabilities as? JSONObject {
                declinesNotShown = dict.bool("declines_not_shown") ?? false
                onReady?(dict)
            }
        } catch {
            // Capability advertisement is additive; older gateways can still chat.
        }
    }

    private func waitForReady(timeout: TimeInterval) async throws {
        try await withCheckedThrowingContinuation { continuation in
            readyContinuation = continuation
            let expectedSocket = socket
            readyTimeoutTask?.cancel()
            readyTimeoutTask = Task { @MainActor [weak self] in
                do { try await Task.sleep(for: .seconds(timeout)) } catch { return }
                guard let self, self.socket === expectedSocket else { return }
                if let waiting = self.readyContinuation {
                    self.readyContinuation = nil
                    waiting.resume(throwing: RPCError.timeout)
                    self.disconnect(notify: true)
                }
            }
        }
    }

    func disconnect(notify: Bool) {
        isClosing = true
        heartbeat?.cancel(); heartbeat = nil
        readyTimeoutTask?.cancel(); readyTimeoutTask = nil
        receiveTask?.cancel(); receiveTask = nil
        socket?.cancel(with: .normalClosure, reason: nil)
        socket = nil
        let waiters = pending
        pending.removeAll()
        pendingTimeouts.values.forEach { $0.cancel() }
        pendingTimeouts.removeAll()
        for (_, waiter) in waiters { waiter.resume(throwing: RPCError.notConnected) }
        if let ready = readyContinuation {
            readyContinuation = nil
            ready.resume(throwing: RPCError.notConnected)
        }
        if notify { onDisconnect?("Disconnected") }
    }

    func request(_ method: String, params: JSONObject = [:], timeout: TimeInterval = 60) async throws -> Any {
        try Task.checkCancellation()
        guard let socket else { throw RPCError.notConnected }
        let id = nextID; nextID += 1
        let payload: JSONObject = ["jsonrpc": "2.0", "id": id, "method": method, "params": params]
        let data = try JSONSerialization.data(withJSONObject: payload)

        let result: RPCResult = try await withTaskCancellationHandler {
            try await withCheckedThrowingContinuation { continuation in
                pending[id] = continuation
                pendingTimeouts[id] = Task { @MainActor in
                    try? await Task.sleep(for: .seconds(timeout))
                    self.failPending(id, error: RPCError.timeout)
                }
                socket.send(.string(String(data: data, encoding: .utf8) ?? "{}")) { [weak self] error in
                    guard let error else { return }
                    Task { @MainActor in self?.failPending(id, error: RPCError.transport(error.localizedDescription)) }
                }
            }
        } onCancel: {
            Task { @MainActor [weak self] in self?.failPending(id, error: CancellationError()) }
        }
        return result.value
    }

    func respond(id: JSONValueID, result: JSONObject) {
        guard let socket else { return }
        let payload: JSONObject = ["jsonrpc": "2.0", "id": id.wire, "result": result]
        guard let data = try? JSONSerialization.data(withJSONObject: payload) else { return }
        socket.send(.string(String(data: data, encoding: .utf8) ?? "{}")) { _ in }
    }

    func respondError(id: JSONValueID, code: Int = -32601, message: String) {
        guard let socket else { return }
        let payload: JSONObject = ["jsonrpc": "2.0", "id": id.wire,
                                   "error": ["code": code, "message": message]]
        guard let data = try? JSONSerialization.data(withJSONObject: payload) else { return }
        socket.send(.string(String(data: data, encoding: .utf8) ?? "{}")) { _ in }
    }

    private func failPending(_ id: Int, error: Error) {
        guard let continuation = pending.removeValue(forKey: id) else { return }
        pendingTimeouts.removeValue(forKey: id)?.cancel()
        continuation.resume(throwing: error)
    }

    private func receiveLoop(owning task: URLSessionWebSocketTask) async {
        while !Task.isCancelled, self.socket === task {
            do {
                let message = try await task.receive()
                let data: Data
                switch message {
                case .data(let value): data = value
                case .string(let value): data = Data(value.utf8)
                @unknown default: continue
                }
                guard self.socket === task, let object = parseJSON(data) else { continue }
                handle(object)
            } catch {
                guard !isClosing, self.socket === task else { return }
                // Close every waiter and timer, including a handshake still waiting
                // for gateway.ready. A dead socket must not linger until timeout.
                disconnect(notify: false)
                onDisconnect?(Self.friendly(error))
                return
            }
        }
    }

    private func handle(_ frame: JSONObject) {
        if let idValue = frame["id"], let method = frame.string("method") {
            if method == "event" { return }
            if let id = JSONValueID.parse(idValue) {
                onServerRequest?(id, method, frame.dict("params") ?? [:])
            }
            return
        }
        if let rawID = frame["id"], let id = rawID as? Int {
            pendingTimeouts.removeValue(forKey: id)?.cancel()
            guard let continuation = pending.removeValue(forKey: id) else { return }
            if let error = frame.dict("error") {
                continuation.resume(throwing: RPCError.server(code: error.int("code") ?? -1,
                                                              message: error.string("message") ?? "Hermes error"))
                return
            }
            continuation.resume(returning: RPCResult(value: frame["result"] ?? NSNull()))
            return
        }
        guard frame.string("method") == "event" else { return }
        let params = frame.dict("params") ?? [:]
        let type = params.string("type") ?? ""
        let sessionID = params.string("session_id") ?? ""
        let payload = params.dict("payload") ?? [:]
        if type == "gateway.ready" {
            readyTimeoutTask?.cancel(); readyTimeoutTask = nil
            onNotification?(type, sessionID, payload)
            if let ready = readyContinuation {
                readyContinuation = nil
                ready.resume()
            }
            return
        }
        onNotification?(type, sessionID, payload)
    }

    nonisolated private static func friendly(_ error: Error) -> String {
        let text = error.localizedDescription
        if text.localizedCaseInsensitiveContains("cancelled") { return "Connection closed" }
        return text
    }

    nonisolated static func webSocketURL(baseURL: String, token: String) -> URL? {
        guard var components = URLComponents(string: baseURL) else { return nil }
        components.scheme = components.scheme?.lowercased() == "https" ? "wss" : "ws"
        let prefix = components.path.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        components.path = "/" + (prefix.isEmpty ? "" : prefix + "/") + "api/ws"
        if !token.isEmpty {
            components.queryItems = [URLQueryItem(name: "token", value: token)]
        }
        return components.url
    }

    static func probe(baseURL: String, token: String) async throws -> JSONObject {
        guard let base = ConnectionPrefs.normalizedURL(baseURL),
              var components = URLComponents(string: base) else { throw RPCError.invalidURL }
        components.path = components.path + "/api/status"
        guard let url = components.url else { throw RPCError.invalidURL }
        var request = URLRequest(url: url)
        request.timeoutInterval = 12
        if !token.isEmpty {
            request.setValue(token, forHTTPHeaderField: "X-Hermes-Session-Token")
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse else { throw RPCError.transport("No response from gateway.") }
        if http.statusCode == 401 { throw RPCError.transport("That access token was rejected.") }
        guard (200..<300).contains(http.statusCode) else {
            throw RPCError.transport("Gateway returned HTTP \(http.statusCode).")
        }
        guard let object = parseJSON(data) else { throw RPCError.malformed }
        return object
    }
}

extension HermesRPCClient: URLSessionWebSocketDelegate {
    nonisolated func urlSession(_ session: URLSession, webSocketTask: URLSessionWebSocketTask,
                                didCloseWith closeCode: URLSessionWebSocketTask.CloseCode, reason: Data?) {
        Task { @MainActor in
            guard !self.isClosing, self.socket === webSocketTask else { return }
            let message = reason.flatMap { String(data: $0, encoding: .utf8) } ?? "Gateway closed the connection"
            self.disconnect(notify: false)
            self.onDisconnect?(message)
        }
    }
}
