import XCTest
import CryptoKit
@testable import Niobium

@MainActor
final class NiobiumTests: XCTestCase {
    func testPortableSeedsMatchAllRecipeFieldsAndRenderedPixels() throws {
        let url = try XCTUnwrap(Bundle(for: Self.self).url(forResource: "character-recipes", withExtension: "json"))
        struct Fixture: Decodable {
            let archetype: String
            let seed: String
            let recipe: CharacterRecipe
            let rgba_sha256: String
            let blink_rgba_sha256: String
        }
        let fixtures = try JSONDecoder().decode([Fixture].self, from: Data(contentsOf: url))
        for fixture in fixtures {
            let recipe = CharacterRecipe.generated(archetype: fixture.archetype, seed: fixture.seed)
            XCTAssertEqual(recipe, fixture.recipe, fixture.seed)
            let cg = try XCTUnwrap(CharacterEngine.shared.image(for: recipe)?.cgImage)
            XCTAssertEqual(cg.width, 48); XCTAssertEqual(cg.height, 48)
            let bytes = try XCTUnwrap(cg.dataProvider?.data) as Data
            XCTAssertEqual(SHA256.hash(data: bytes).map { String(format: "%02x", $0) }.joined(), fixture.rgba_sha256)
            let closed = try XCTUnwrap(CharacterEngine.shared.image(for: recipe, blink: true))
            let closedBytes = try XCTUnwrap(closed.cgImage?.dataProvider?.data) as Data
            XCTAssertEqual(SHA256.hash(data: closedBytes).map { String(format: "%02x", $0) }.joined(), fixture.blink_rgba_sha256)
            XCTAssertNotEqual(bytes, closedBytes)
            XCTAssertTrue(closed === CharacterEngine.shared.image(for: recipe, blink: true))
            XCTAssertEqual(bytes, CharacterEngine.shared.image(for: recipe)?.cgImage?.dataProvider?.data as Data?)
        }
    }
    func testAuthoredLooksMatchPortablePixelsAndKeepIdentityDuringRemix() throws {
        struct Fixture: Decodable { let look: PixelLook; let rgba_sha256: String }
        let url = try XCTUnwrap(Bundle(for: Self.self).url(forResource: "portrait-looks", withExtension: "json"))
        let fixtures = try JSONDecoder().decode([Fixture].self, from: Data(contentsOf: url))
        for fixture in fixtures {
            let image = try XCTUnwrap(CharacterEngine.shared.image(for: fixture.look))
            let cg = try XCTUnwrap(image.cgImage)
            XCTAssertEqual(cg.width, 48); XCTAssertEqual(cg.height, 48)
            let raw = try XCTUnwrap(cg.dataProvider?.data as Data?)
            XCTAssertEqual(SHA256.hash(data: raw).map { String(format: "%02x", $0) }.joined(), fixture.rgba_sha256)
            XCTAssertTrue(image === CharacterEngine.shared.image(for: fixture.look))
            let changed = fixture.look.remixed(seed: "different")
            XCTAssertEqual(changed.portrait, fixture.look.portrait)
            XCTAssertNotEqual(changed.accent, fixture.look.accent)
        }
    }
    func testPortableCustomArtRoundTripsAndRejectsMalformedPixels() throws {
        struct Fixture: Decodable { let art: PixelArt; let rgba_sha256: String }
        let url = try XCTUnwrap(Bundle(for: Self.self).url(forResource: "custom-art", withExtension: "json"))
        let fixtures = try JSONDecoder().decode([Fixture].self, from: Data(contentsOf: url))
        for f in fixtures {
            XCTAssertTrue(f.art.isValid)
            let image = try XCTUnwrap(CharacterEngine.shared.image(for: f.art))
            let raw = try XCTUnwrap(image.cgImage?.dataProvider?.data as Data?)
            XCTAssertEqual(SHA256.hash(data: raw).map { String(format: "%02x", $0) }.joined(), f.rgba_sha256)
            XCTAssertEqual(try JSONDecoder().decode(PixelArt.self, from: JSONEncoder().encode(f.art)), f.art)
            XCTAssertFalse(PixelArt(size: f.art.size, palette: [0x1000000], pixels: f.art.pixels).isValid)
            XCTAssertFalse(PixelArt(size: f.art.size, palette: f.art.palette, pixels: "short").isValid)
            XCTAssertFalse(PixelArt(size: f.art.size, palette: f.art.palette, pixels: Data(repeating: 0, count: f.art.size*f.art.size).base64EncodedString()).isValid)
            if f.art.size == 48 {
                let look = PixelLook(portrait: "lilith", customArt: f.art)
                XCTAssertTrue(look.isValid)
                XCTAssertTrue(CharacterEngine.shared.image(for: look) === image)
            }
        }
    }
    func testWardrobeVarietyFollowsBriefAndDoesNotExpandColorIDs() {
        XCTAssertEqual(PixelLook.portraits.count, 32)
        XCTAssertEqual(PixelLook.accents.count, 8)
        XCTAssertEqual(PixelLook.masculine.count, 12)
        XCTAssertFalse(PixelLook(portrait: "lilith", accent: "lilith").isValid)
        XCTAssertEqual(PixelLook.generated(archetype: "nyx", body: "woman", seed: "same", prompt: "vampire crimson corset").portrait, "lilith")
        XCTAssertEqual(PixelLook.generated(archetype: "forge", body: "man", seed: "same", prompt: "cyborg chrome harness").portrait, "zero")
        let selected = Set((0..<100).map { PixelLook.generated(archetype: "orion", body: "man", seed: String($0)).portrait })
        XCTAssertGreaterThan(selected.count, 2)
        XCTAssertTrue(selected.allSatisfy { PixelLook.masculine.contains($0) })
    }
    func testNewLooksRoundTripAndRejectUnknownValuesAndInvalidTypes() throws {
        let original = PixelLook(portrait: "nyx", accent: "astra")
        XCTAssertEqual(try JSONDecoder().decode(PixelLook.self, from: JSONEncoder().encode(original)), original)
        XCTAssertFalse(PixelLook(portrait: "https://example.org/image.png").isValid)
        XCTAssertFalse(PixelLook(portrait: "nyx", accent: "unknown").isValid)
        XCTAssertThrowsError(try JSONDecoder().decode(PixelLook.self, from: Data(#"{"portrait":"nyx","accent":null}"#.utf8)))
        XCTAssertThrowsError(try JSONDecoder().decode(PixelLook.self, from: Data(#"{"portrait":"nyx","accent":false}"#.utf8)))
        var spec = PixelBuilder.shared.preview(for: "security witch").spec
        XCTAssertEqual(spec.character.look?.body, "woman")
        XCTAssertFalse(spec.permissions.shell)
        spec.character.look = original
        XCTAssertEqual(PixelSpec.parse(String(decoding: try JSONEncoder().encode(spec), as: UTF8.self))?.character.look, original)
        spec.character.look = nil
        XCTAssertNotNil(PixelSpec.parse(String(decoding: try JSONEncoder().encode(spec), as: UTF8.self)))
    }
    func testLookPersistenceKeepsCompanionsAndExplicitLegacyChoiceWins() {
        let profile = "dsh:test-look-" + UUID().uuidString
        var member = CastMember.all[0]
        member.look = PixelLook(portrait: "nyx", accent: "astra")
        CastStore.shared.select(member, for: profile)
        CastStore.shared.setCompanion("mimic", name: "Bite", for: profile)
        XCTAssertEqual(CastStore.shared.member(for: profile).look, member.look)
        CastStore.shared.restore(profile: profile, metadata: ["appearance": ["archetype": "orion", "look": NSNull()]])
        XCTAssertNil(CastStore.shared.member(for: profile).look)
    }
    func testLegacyRecipesDecodeAndRejectInvalidOptionalTypes() throws {
        let legacy = #"{"body":"woman","palette":"nyx","hair":"cat_black","face":"fierce","outfit":"rogue","accessory":"cat_ears"}"#
        let recipe = try JSONDecoder().decode(CharacterRecipe.self, from: Data(legacy.utf8))
        XCTAssertEqual(recipe, CharacterRecipe.cast["nyx"])
        for bad in [#"{"skin":true}"#, #"{"hair_tone":null}"#, #"{"detail":7}"#] {
            var object = try XCTUnwrap(JSONSerialization.jsonObject(with: Data(legacy.utf8)) as? [String: Any])
            object.merge(try XCTUnwrap(JSONSerialization.jsonObject(with: Data(bad.utf8)) as? [String: Any])) { _, new in new }
            XCTAssertThrowsError(try JSONDecoder().decode(CharacterRecipe.self, from: JSONSerialization.data(withJSONObject: object)))
        }
        var invalid = recipe; invalid.skin = "anything"
        XCTAssertFalse(invalid.isValid)
    }
    func testPinsPreserveTraitsAndInheritedColors() {
        let original = CharacterRecipe.cast["nyx"]!
        let all = Set(CharacterRecipe.fieldNames)
        XCTAssertEqual(original.remixed(archetype: "nyx", seed: "new", locked: all), original)
        for field in CharacterRecipe.fieldNames {
            for seed in ["first", "second", "æøå-星"] {
                let next = original.remixed(archetype: "nyx", seed: seed, locked: [field])
                XCTAssertEqual(next.value(for: field), original.value(for: field))
                XCTAssertTrue(next.isValid)
                if ["skin", "hair_tone"].contains(field) { XCTAssertEqual(next.palette, original.palette) }
            }
        }
    }
    func testCompanionChoiceDoesNotReplaceAuthoredPortrait() throws {
        var member = CastMember.all[0]
        let original = try XCTUnwrap(CharacterEngine.shared.authoredImage(member))
        member = CastMember(id: member.id, name: member.name, portrait: member.portrait, companion: "mimic",
                            companionName: "Bite", accentHex: member.accentHex, recipe: member.recipe)
        XCTAssertTrue(CharacterEngine.shared.usesAuthored(member))
        XCTAssertTrue(original === CharacterEngine.shared.authoredImage(member))
        member.recipe?.detail = "scar"
        XCTAssertFalse(CharacterEngine.shared.usesAuthored(member))
    }
    func testDistinctRoleTemplatesAndExplicitCharacterIntent() {
        let cases = [("research biology", "orion"), ("creative art", "nova"), ("release engineer who watches a build", "forge"),
                     ("organize memory notes", "sage"), ("audit security logs", "nyx"), ("camera vision", "astra"),
                     ("write a newsletter", "vesper"), ("plan a travel route", "rook")]
        var voices = Set<String>()
        for (prompt, expected) in cases {
            let spec = PixelSpec.fallback(for: prompt, seed: "atelier")
            XCTAssertEqual(spec.character.archetype, expected, prompt)
            XCTAssertNil(spec.validationError)
            XCTAssertTrue((28...40).contains(spec.character.age))
            XCTAssertFalse(spec.permissions.shell)
            XCTAssertTrue(spec.connectors.isEmpty)
            voices.insert(spec.soul)
        }
        XCTAssertEqual(voices.count, 8)
        XCTAssertEqual(PixelSpec.fallback(for: "security witch", seed: "same").character.recipe.body, "woman")
        XCTAssertEqual(PixelSpec.fallback(for: "male creative director", seed: "same").character.recipe.body, "man")
        XCTAssertEqual(PixelSpec.fallback(for: "research with an axolotl", seed: "same").companion.kind, "axolotl")
        let first = PixelSpec.fallback(for: "research", seed: "one")
        let second = PixelSpec.fallback(for: "research", seed: "two")
        XCTAssertNotEqual(first.name, second.name)
        XCTAssertNotEqual(first.character.recipe, second.character.recipe)
    }

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
        spec.toolsets = ["web", "file", "terminal", "memory", "delegation", "messaging", "unknown"]
        spec.permissions = .init(approvalMode: "ask", network: false, filesystem: "none", shell: true)
        XCTAssertEqual(spec.permittedToolsets, ["memory", "delegation", "messaging"])
        spec.permissions.filesystem = "workspace"
        XCTAssertEqual(spec.permittedToolsets, ["file", "terminal", "memory", "delegation", "messaging"])
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
