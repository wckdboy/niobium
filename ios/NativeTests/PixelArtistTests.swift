import XCTest
import UIKit
@testable import Niobium

private final class ArtistFixture: @unchecked Sendable {
    private let lock = NSLock()
    private var seen: [URLRequest] = []
    var respond: @Sendable (URLRequest) throws -> (Int, JSONObject) = { _ in (200, ["id": "resp_fixture", "status": "queued"]) }
    func receive(_ request: URLRequest) throws -> (Int, JSONObject) {
        lock.lock(); seen.append(request); lock.unlock()
        return try respond(request)
    }
    var requests: [URLRequest] { lock.lock(); defer { lock.unlock() }; return seen }
}
private final class ArtistURLProtocol: URLProtocol, @unchecked Sendable {
    nonisolated(unsafe) static var fixture: ArtistFixture?
    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }
    override func startLoading() {
        do {
            let (code, body) = try Self.fixture!.receive(request)
            let response = HTTPURLResponse(url: request.url!, statusCode: code, httpVersion: "HTTP/1.1", headerFields: ["Content-Type": "application/json"])!
            client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
            client?.urlProtocol(self, didLoad: try JSONSerialization.data(withJSONObject: body))
            client?.urlProtocolDidFinishLoading(self)
        } catch { client?.urlProtocol(self, didFailWithError: error) }
    }
    override func stopLoading() {}
}

@MainActor
private final class MemoryArtistKeys: ArtistKeyVault {
    private var value: String?
    func read() -> String? { value }
    func save(_ value: String) -> Bool { self.value = value; return true }
    func remove() { value = nil }
}

@MainActor
final class PixelArtistTests: XCTestCase {
    private let keys = MemoryArtistKeys()
    private func make(defaults: UserDefaults, service: String, fixture: ArtistFixture) -> PixelArtist {
        let config = URLSessionConfiguration.ephemeral; config.protocolClasses = [ArtistURLProtocol.self]
        ArtistURLProtocol.fixture = fixture
        return PixelArtist(session: URLSession(configuration: config), defaults: defaults, keys: keys, pollInterval: .milliseconds(10))
    }
    private func wait(_ condition: () -> Bool) async throws {
        for _ in 0..<300 { if condition() { return }; try await Task.sleep(for: .milliseconds(10)) }
        XCTFail("The fixture job did not settle")
    }
    func testBackgroundRequestUsesTransparentArtAndAnExplicitStoredReceipt() throws {
        let body = PixelArtist.body(prompt: "new cloak", target: "pixel", referencePNG: Data([1,2,3]))
        XCTAssertEqual(body["background"] as? Bool, true); XCTAssertEqual(body["store"] as? Bool, true)
        let tool = try XCTUnwrap(body.objects("tools").first)
        XCTAssertEqual(tool.string("background"), "transparent"); XCTAssertEqual(tool.string("output_format"), "png")
        XCTAssertTrue(PixelArtist.validID("resp_valid-123")); XCTAssertFalse(PixelArtist.validID("resp_x/../../secrets"))
    }
    func testAmbiguousSubmissionIsNotRetriedAfterRestart() async throws {
        let name = "artist-tests-" + UUID().uuidString, service = "artist-tests-" + UUID().uuidString
        let defaults = UserDefaults(suiteName: name)!, fixture = ArtistFixture()
        defer { defaults.removePersistentDomain(forName: name); Keychain.delete(service: service, account: "openai") }
        fixture.respond = { _ in throw URLError(.timedOut) }
        let artist = make(defaults: defaults, service: service, fixture: fixture)
        XCTAssertTrue(artist.saveKey("fixture-key-never-a-real-provider-key"))
        artist.start(prompt: "A cloak", target: "pixel", referencePNG: nil)
        try await wait { artist.job?.phase == "unknown" }
        let restored = make(defaults: defaults, service: service, fixture: fixture)
        restored.resume(); try await Task.sleep(for: .milliseconds(50))
        XCTAssertEqual(fixture.requests.count, 1)
        XCTAssertEqual(restored.job?.phase, "unknown")
    }
    func testServerErrorWithoutAReceiptIsAmbiguousAndIsNotReposted() async throws {
        let name = "artist-tests-" + UUID().uuidString, service = "artist-tests-" + UUID().uuidString
        let defaults = UserDefaults(suiteName: name)!, fixture = ArtistFixture()
        defer { defaults.removePersistentDomain(forName: name) }
        fixture.respond = { _ in (500, [:]) }
        let artist = make(defaults: defaults, service: service, fixture: fixture)
        XCTAssertTrue(artist.saveKey("fixture-key-never-a-real-provider-key"))
        artist.start(prompt: "A cloak", target: "pixel", referencePNG: nil)
        try await wait { artist.job?.phase == "unknown" }
        artist.resume(); try await Task.sleep(for: .milliseconds(30))
        XCTAssertEqual(fixture.requests.count, 1)
    }
    func testSavedReceiptResumesWithGetAndPersistsAcceptedPixels() async throws {
        let name = "artist-tests-" + UUID().uuidString, service = "artist-tests-" + UUID().uuidString
        let defaults = UserDefaults(suiteName: name)!, fixture = ArtistFixture()
        defer { defaults.removePersistentDomain(forName: name); Keychain.delete(service: service, account: "openai") }
        let png = try XCTUnwrap(UIImage(named: "Comp_cat")?.pngData()).base64EncodedString()
        defaults.set(try JSONEncoder().encode(PixelArtJob(target: "companion", phase: "queued", responseID: "resp_saved")), forKey: "niobium.artist.job")
        fixture.respond = { _ in (200, ["id": "resp_saved", "status": "completed", "output": [["type": "image_generation_call", "result": png]]]) }
        let artist = make(defaults: defaults, service: service, fixture: fixture)
        XCTAssertTrue(artist.saveKey("fixture-key-never-a-real-provider-key"))
        artist.resume(); try await wait { artist.job?.phase == "completed" }
        XCTAssertTrue(try XCTUnwrap(artist.job?.candidate).isValid)
        XCTAssertEqual(artist.job?.candidate?.size, 16)
        XCTAssertEqual(fixture.requests.map(\.httpMethod), ["GET"])
        let restored = make(defaults: defaults, service: service, fixture: fixture)
        XCTAssertEqual(restored.job?.candidate, artist.job?.candidate)
    }
    func testOpaqueProviderBackgroundIsRejected() throws {
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: 48, height: 48))
        let image = renderer.image { context in UIColor.white.setFill(); context.fill(CGRect(x: 0,y: 0,width: 48,height: 48)) }
        XCTAssertThrowsError(try PixelArtBaker.bake(XCTUnwrap(image.pngData()), size: 16))
    }
}
