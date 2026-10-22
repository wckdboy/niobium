import XCTest
@testable import Niobium

@MainActor
final class NiobiumTests: XCTestCase {
    func testGatewayNormalizationPreservesReverseProxyPathAndRemovesCredentialsFromQuery() {
        XCTAssertEqual(ConnectionPrefs.normalizedURL(" example.org/hermes///?token=discard#fragment "), "http://example.org/hermes")
        XCTAssertEqual(ConnectionPrefs.normalizedURL("https://[::1]:9119/"), "https://[::1]:9119")
    }
    func testGatewayRejectsEmbeddedCredentialsAndNonHTTPProtocols() {
        for address in ["", "https://name:password@example.org", "ftp://example.org", "file:///tmp/host"] {
            XCTAssertNil(ConnectionPrefs.normalizedURL(address), address)
        }
    }
    func testWebSocketTokenIsEncodedAndProxyPrefixSurvives() throws {
        let url = try XCTUnwrap(HermesRPCClient.webSocketURL(baseURL: "https://example.org/hermes", token: "a+b&c=?"))
        let parts = try XCTUnwrap(URLComponents(url: url, resolvingAgainstBaseURL: false))
        XCTAssertEqual(parts.scheme, "wss")
        XCTAssertEqual(parts.path, "/hermes/api/ws")
        XCTAssertEqual(parts.queryItems?.first?.value, "a+b&c=?")
    }
    func testRPCIdentifiersPreserveTheirWireType() {
        XCTAssertEqual(JSONValueID.parse("0042"), .string("0042"))
        XCTAssertEqual(JSONValueID.parse(42), .number(42))
        XCTAssertNil(JSONValueID.parse(nil))
    }
    func testDisconnectedRequestFailsImmediately() async {
        do { _ = try await HermesRPCClient().request("session.list"); XCTFail("A disconnected request must fail") }
        catch { XCTAssertEqual(error.localizedDescription, RPCError.notConnected.localizedDescription) }
    }
    func testCancelledRequestDoesNotCreateAWaiter() async {
        let client = HermesRPCClient()
        let task = Task { _ = try await client.request("session.list"); return true }
        task.cancel()
        do { _ = try await task.value; XCTFail("A cancelled request must fail") }
        catch { XCTAssertTrue(error is CancellationError) }
    }
    func testAllCastRecipesAreValidAdultsInTemplates() {
        for prompt in ["security", "build a release", "creative design", "memory", "camera", "research"] {
            let spec = PixelSpec.fallback(for: prompt)
            XCTAssertNil(spec.validationError)
            XCTAssertGreaterThanOrEqual(spec.character.age, 18)
            XCTAssertFalse(spec.permissions.shell)
        }
        XCTAssertEqual(CharacterRecipe.cast.count, 8)
        XCTAssertTrue(CharacterRecipe.cast.values.allSatisfy(\.isValid))
    }
    func testMalformedModelOutputCannotBecomeAValidPixel() throws {
        let spec = PixelSpec.fallback(for: "research")
        let data = try JSONEncoder().encode(spec)
        let text = try XCTUnwrap(String(data: data, encoding: .utf8))
        XCTAssertNotNil(PixelSpec.parse(text))
        XCTAssertNil(PixelSpec.parse("Here is your Pixel: " + text))
        XCTAssertNil(PixelSpec.parse(text + text))
        var object = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? JSONObject)
        var permission = try XCTUnwrap(object["permissions"] as? JSONObject)
        permission["shell"] = "true"
        object["permissions"] = permission
        XCTAssertNil(PixelSpec.parse(String(decoding: try JSONSerialization.data(withJSONObject: object), as: UTF8.self)))
    }
    func testPermissionsAndGatewayCatalogBothGateTools() {
        var spec = PixelSpec.fallback(for: "research")
        spec.toolsets = ["web", "file", "terminal", "memory", "delegation", "unknown"]
        spec.permissions = .init(approvalMode: "ask", network: false, filesystem: "none", shell: true)
        XCTAssertEqual(spec.permittedToolsets, ["memory", "delegation"])
        spec.permissions.filesystem = "workspace"
        XCTAssertEqual(spec.permittedToolsets, ["file", "terminal", "memory", "delegation"])
        let draft = PixelDraft(spec: spec, usedModel: false, availableToolsets: ["file", "memory"], availableSkills: [], notes: [])
        XCTAssertEqual(draft.enabledToolsets, ["file", "memory"])
    }
    func testInvalidNamesAgesAndDuplicateCapabilitiesAreRejected() {
        var spec = PixelSpec.fallback(for: "security")
        spec.name = "../../profile"
        XCTAssertNotNil(spec.validationError)
        spec = .fallback(for: "security")
        spec.character.age = 17
        XCTAssertNotNil(spec.validationError)
        spec = .fallback(for: "security")
        spec.toolsets = ["file", "file"]
        XCTAssertNotNil(spec.validationError)
    }
}
