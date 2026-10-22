import XCTest
import Network
@testable import Niobium

private final class RefusedWebSocketServer: @unchecked Sendable {
    private let listener: NWListener
    private let queue = DispatchQueue(label: "niobium.handshake.fixture")
    private let lock = NSLock()
    private var completed = false

    init() throws { listener = try NWListener(using: .tcp, on: .any) }
    func start() async throws -> UInt16 {
        try await withCheckedThrowingContinuation { continuation in
            listener.stateUpdateHandler = { [self] state in
                switch state {
                case .ready:
                    finish { continuation.resume(returning: listener.port!.rawValue) }
                case .failed(let error):
                    finish { continuation.resume(throwing: error) }
                default: break
                }
            }
            listener.newConnectionHandler = { [queue] connection in
                connection.start(queue: queue)
                connection.receive(minimumIncompleteLength: 1, maximumLength: 16_384) { _, _, _, _ in
                    let response = "HTTP/1.1 403 Forbidden\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                    connection.send(content: Data(response.utf8), completion: .contentProcessed { _ in connection.cancel() })
                }
            }
            listener.start(queue: queue)
        }
    }
    private func finish(_ operation: () -> Void) {
        lock.lock(); defer { lock.unlock() }
        guard !completed else { return }; completed = true; operation()
    }
    func stop() { listener.stateUpdateHandler = nil; listener.cancel() }
}

@MainActor
final class ConnectionHandshakeTests: XCTestCase {
    func testEarlySocketFailureReachesTheCallerAndReleasesTheHandshake() async throws {
        let server = try RefusedWebSocketServer()
        defer { server.stop() }
        let port = try await server.start()
        let client = HermesRPCClient()
        let start = Date()
        do {
            try await client.connect(baseURL: "http://127.0.0.1:\(port)", token: "fixture")
            XCTFail("A refused WebSocket must not connect")
        } catch {
            XCTAssertNotEqual(error.localizedDescription, RPCError.notConnected.localizedDescription)
            XCTAssertLessThan(Date().timeIntervalSince(start), 5, "A failed socket must not wait for the ready timeout")
            XCTAssertFalse(client.connected)
        }
    }
}
