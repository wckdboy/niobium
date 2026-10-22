import XCTest
@testable import Niobium

@MainActor
private final class FixtureRPC: HermesRPC {
    var declinesNotShown = false
    var onNotification: ((String, String, JSONObject) -> Void)?
    var onServerRequest: ((JSONValueID, String, JSONObject) -> Void)?
    var onDisconnect: ((String) -> Void)?
    var calls: [(String, JSONObject)] = []
    var responses: [(JSONValueID, JSONObject)] = []
    var held: CheckedContinuation<RPCResult, Error>?
    var failConfigure = false
    var createdName = "fixture-profile"

    func request(_ method: String, params: JSONObject, timeout: TimeInterval) async throws -> Any {
        calls.append((method, params))
        switch method {
        case "session.resume":
            if params.string("session_id") == "older" {
                return try await withCheckedThrowingContinuation { held = $0 }.value
            }
            return ["session_id": params.string("session_id") ?? "", "revision": 8,
                    "execution_generation": 4, "running": true,
                    "inflight": ["streaming": true, "assistant": "Already generated"],
                    "messages": []] as JSONObject
        case "profiles.create":
            createdName = params.string("name") ?? ""
            return ["ok": true]
        case "profiles.describe": return ["skills": [["name": "audit"], ["name": "extra"]]]
        case "profiles.list": return ["profiles": [["name": createdName]]]
        case "profiles.configure":
            if failConfigure { throw RPCError.server(code: 4409, message: "Fixture conflict") }
            return ["ok": true]
        case "config.set", "clarify.respond": return ["ok": true]
        case "model.options": return ["providers": []]
        case "tools.list": return ["toolsets": []]
        default: throw RPCError.server(code: -32601, message: "Unsupported fixture method: " + method)
        }
    }
    func respond(id: JSONValueID, result: JSONObject) { responses.append((id, result)) }
    func respondError(id: JSONValueID, code: Int, message: String) {}
    func disconnect(notify: Bool) {}
}

@MainActor
final class GatewayStateTests: XCTestCase {
    func testLatestChatSelectionWinsWhenOlderReplyArrivesLast() async throws {
        let rpc = FixtureRPC()
        // The tested store owns the same transport throughout both overlapping requests.
        let subject = AppStore(api: rpc)
        let bot = DemoData.bots[0]
        subject.activeBot = bot
        let old = try XCTUnwrap(SessionRow.parse(["session_id": "older", "title": "Older"]))
        let new = try XCTUnwrap(SessionRow.parse(["session_id": "newer", "title": "Newer"]))
        let pending = Task { await subject.resumeSession(old, for: bot) }
        for _ in 0..<1000 { if rpc.held != nil { break }; await Task.yield() }
        let reply = try XCTUnwrap(rpc.held)
        await subject.resumeSession(new, for: bot)
        XCTAssertEqual(subject.chatSessionID, "newer")
        reply.resume(returning: RPCResult(value: ["session_id": "older", "messages": []] as JSONObject))
        await pending.value
        XCTAssertEqual(subject.chatSessionID, "newer")
        XCTAssertEqual(subject.chatRevision, 8)
        XCTAssertFalse(subject.isLoadingChat)
    }

    func testRestoredPartialAnswerReceivesLaterDeltasInTheSameMessage() async throws {
        let rpc = FixtureRPC()
        let store = AppStore(api: rpc), bot = DemoData.bots[0]
        store.activeBot = bot
        let row = try XCTUnwrap(SessionRow.parse(["session_id": "running"]))
        await store.resumeSession(row, for: bot)
        XCTAssertEqual(store.messages.map(\.text), ["Already generated"])
        rpc.onNotification?("message.delta", "running", ["text": " and the rest"])
        for _ in 0..<1000 { if store.messages.last?.text == "Already generated and the rest" { break }; await Task.yield() }
        XCTAssertEqual(store.messages.map(\.text), ["Already generated and the rest"])
        XCTAssertTrue(store.messages[0].isStreaming)
    }

    func testRecoveredClarificationCancelAndMissingAnswersSendNothing() async {
        let rpc = FixtureRPC()
        let subject = AppStore(api: rpc)
        subject.chatSessionID = "sid"
        let question = ClarifyQuestion(id: "prompt-7", question: "Which environment?", choices: [], multiSelect: false)
        subject.clarifyRequest = ClarifyRequest(rpcID: .string(""), sessionID: "sid",
            questions: [question], promptGenerations: ["prompt-7": 4])
        subject.cancelClarify()
        subject.resolveClarify([:])
        await Task.yield()
        XCTAssertEqual(subject.clarifyRequest?.questions, [question])
        XCTAssertTrue(rpc.calls.isEmpty)
        XCTAssertTrue(rpc.responses.isEmpty)
        subject.resolveClarify(["prompt-7": "staging"])
        for _ in 0..<1000 { if subject.clarifyRequest == nil { break }; await Task.yield() }
        XCTAssertNil(subject.clarifyRequest)
        let sent = rpc.calls.first
        XCTAssertEqual(sent?.0, "clarify.respond")
        XCTAssertEqual(sent?.1.string("answer"), "staging")
        XCTAssertEqual(sent?.1.string("prompt_id"), "prompt-7")
        XCTAssertEqual(sent?.1.int("execution_generation"), 4)
    }

    func testLiveClarificationCancelKeepsItsRPCIdentifierAndCancellationOutcome() {
        let rpc = FixtureRPC()
        let subject = AppStore(api: rpc)
        subject.chatSessionID = "sid"
        subject.clarifyRequest = ClarifyRequest(rpcID: .string("request-007"), sessionID: "sid", questions: [])
        subject.cancelClarify()
        XCTAssertNil(subject.clarifyRequest)
        XCTAssertEqual(rpc.responses.first?.0, .string("request-007"))
        XCTAssertEqual(rpc.responses.first?.1.string("outcome"), "cancelled")
        XCTAssertTrue(rpc.calls.isEmpty)
    }

    func testProfileProvisioningAppliesTheReviewedAccessAndPersona() async throws {
        let rpc = FixtureRPC()
        let subject = AppStore(api: rpc)
        var spec = PixelSpec.fallback(for: "security")
        spec.name = "fixture-security"; spec.skills = ["audit"]
        let draft = PixelDraft(spec: spec, usedModel: false, availableToolsets: ["file", "delegation"],
            availableSkills: ["audit", "extra"], notes: [])
        let bot = try await subject.buildPixel(draft: draft)
        XCTAssertEqual(bot.name, spec.name)
        XCTAssertEqual(rpc.calls.map { $0.0 }, ["profiles.create", "config.set", "profiles.describe", "profiles.configure", "config.set", "profiles.list"])
        let created = rpc.calls[0].1
        XCTAssertEqual(Set(created.keys), ["name", "description", "soul", "clone_from", "clone_all", "no_skills", "clone_channels"])
        XCTAssertEqual(created.string("clone_from"), "default")
        XCTAssertEqual(created.bool("clone_all"), false); XCTAssertEqual(created.bool("clone_channels"), false)
        XCTAssertEqual(created.bool("no_skills"), false)
        XCTAssertTrue(created.string("soul")?.contains(spec.soul) == true)
        XCTAssertEqual(rpc.calls[1].1.string("key"), "approvals.mode")
        XCTAssertEqual(rpc.calls[1].1.string("value"), "manual")
        let access = rpc.calls[3].1
        XCTAssertEqual(access["enabled_toolsets"] as? [String], ["file", "delegation"])
        XCTAssertEqual(access["enabled_mcp_servers"] as? [String], [])
        XCTAssertEqual(access["disabled_skills"] as? [String], ["extra"])
        let saved = try XCTUnwrap(access.dict("ui_meta")?.dict("niobium"))
        XCTAssertEqual(saved.int("schema_version"), 2)
        XCTAssertEqual(saved.dict("pixel_spec")?.dict("companion")?.string("name"), spec.companion.name)
        XCTAssertEqual(rpc.calls[4].1.string("value"), spec.reasoning)
    }

    func testFailureAfterProfileCreationIsReportedWithoutCreatingAgain() async {
        let rpc = FixtureRPC()
        let subject = AppStore(api: rpc)
        rpc.failConfigure = true
        var spec = PixelSpec.fallback(for: "security"); spec.name = "fixture-incomplete"
        let draft = PixelDraft(spec: spec, usedModel: false, availableToolsets: ["file"], availableSkills: [], notes: [])
        do { _ = try await subject.buildPixel(draft: draft); XCTFail("Expected setup failure") }
        catch { XCTAssertTrue(error.localizedDescription.contains("setup is incomplete")) }
        XCTAssertEqual(rpc.calls.filter { $0.0 == "profiles.create" }.count, 1)
        XCTAssertEqual(rpc.calls.filter { $0.0 == "config.set" }.count, 1)
        XCTAssertEqual(rpc.calls.last?.0, "profiles.list")
    }

    func testFailedAppearanceSaveDoesNotChangeTheLocalCharacter() async {
        let rpc = FixtureRPC()
        let subject = AppStore(api: rpc), profile = "fixture-appearance"
        rpc.createdName = profile; rpc.failConfigure = true
        let old = CastMember.all[0], preview = CastMember.all[1]
        CastStore.shared.select(old, for: profile)
        let success = await subject.saveAppearance(preview, profile: profile)
        XCTAssertFalse(success)
        XCTAssertEqual(CastStore.shared.member(for: profile).id, old.id)
    }
}
