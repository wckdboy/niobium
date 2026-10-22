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
    var catalogTools = ["web", "file", "delegation"]
    var failConfigure = false
    var holdClarify = false
    var createdName = "fixture-profile"
    var madeLocally = false
    var holdListFor: String?
    var failLocalAttach = false

    func request(_ method: String, params: JSONObject, timeout: TimeInterval) async throws -> Any {
        calls.append((method, params))
        switch method {
        case "session.list":
            if params.string("profile") == holdListFor, holdListFor != nil {
                return try await withCheckedThrowingContinuation { held = $0 }.value
            }
            return ["sessions": [["id": "chat-" + (params.string("profile") ?? "default")]]]
        case "local.attach":
            if failLocalAttach { throw RPCError.server(code: -32000, message: "This local chat selection changed.") }
            return ["ok": true]
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
        case "pixel.create":
            madeLocally = true
            createdName = params.dict("spec")?.string("name") ?? ""
            return ["ok": true]
        case "profiles.describe": return ["toolsets": catalogTools.map { ["name": $0] }, "skills": [["name": "audit"], ["name": "extra"]]]
        case "profiles.list": return ["profiles": [["name": createdName, "origin": madeLocally ? "dsh" : "hermes"]]]
        case "profiles.configure":
            if failConfigure { throw RPCError.server(code: 4409, message: "Fixture conflict") }
            return ["ok": true]
        case "clarify.respond":
            if holdClarify { return try await withCheckedThrowingContinuation { held = $0 }.value }
            return ["ok": true]
        case "config.set": return ["ok": true]
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
    func testLocalConnectionFailureStaysVisibleWhileLinuxIsReady() {
        let store = AppStore(api: FixtureRPC())
        store.engine = .dsh
        store.localReady = true
        store.localStatus = "Running"
        store.connectionState = .failed("Gateway returned HTTP 403.")
        XCTAssertEqual(store.localConnectionError, "Gateway returned HTTP 403.")
        store.connectionState = .connecting
        XCTAssertNil(store.localConnectionError)
        store.connectionState = .connected(version: "DSH")
        XCTAssertNil(store.localConnectionError)
        store.connectionState = .failed("Remote connection failed")
        store.engine = .hermes
        XCTAssertNil(store.localConnectionError)
    }

    func testRepeatedOpenWhileAttachingUsesOneSession() async throws {
        let rpc = FixtureRPC(), bot = try XCTUnwrap(BotProfile.parse(["name": "scout", "origin": "dsh"]))
        rpc.holdListFor = bot.name
        let store = AppStore(api: rpc)
        store.engine = .dsh
        store.openBot(bot)
        for _ in 0..<1000 { if rpc.held != nil { break }; await Task.yield() }
        let pending = try XCTUnwrap(rpc.held)
        store.openBot(bot)
        pending.resume(returning: RPCResult(value: ["sessions": [["id": "scout-session"]]] as JSONObject))
        for _ in 0..<1000 { if !store.isLoadingChat { break }; await Task.yield() }
        XCTAssertFalse(store.isLoadingChat)
        XCTAssertEqual(store.chatSessionID, "scout-session")
        XCTAssertEqual(rpc.calls.filter { $0.0 == "session.list" }.count, 1)
        XCTAssertEqual(rpc.calls.filter { $0.0 == "session.resume" }.count, 1)
        XCTAssertEqual(rpc.calls.filter { $0.0 == "local.attach" }.count, 1)
    }

    func testSupersededSessionLookupCannotChangeTheBackendSelection() async throws {
        let rpc = FixtureRPC()
        let old = try XCTUnwrap(BotProfile.parse(["name": "old", "origin": "dsh"]))
        let latest = try XCTUnwrap(BotProfile.parse(["name": "latest", "origin": "dsh"]))
        rpc.holdListFor = old.name
        let store = AppStore(api: rpc)
        store.engine = .dsh
        store.openBot(old)
        for _ in 0..<1000 { if rpc.held != nil { break }; await Task.yield() }
        let pending = try XCTUnwrap(rpc.held)
        store.openBot(latest)
        for _ in 0..<1000 { if !store.isLoadingChat { break }; await Task.yield() }
        XCTAssertEqual(store.chatSessionID, "chat-latest")
        pending.resume(returning: RPCResult(value: ["sessions": []] as JSONObject))
        for _ in 0..<50 { await Task.yield() }
        XCTAssertEqual(store.chatSessionID, "chat-latest")
        XCTAssertFalse(rpc.calls.contains { $0.0 == "session.create" })
        XCTAssertEqual(rpc.calls.filter { $0.0 == "session.resume" }.map { $0.1.string("profile") }, ["latest"])
    }

    func testFailedLocalHandoffDoesNotEnableSending() async throws {
        let rpc = FixtureRPC()
        rpc.failLocalAttach = true
        let store = AppStore(api: rpc)
        store.engine = .dsh
        store.connectionState = .connected(version: "fixture")
        store.openBot(try XCTUnwrap(BotProfile.parse(["name": "scout", "origin": "dsh"])))
        for _ in 0..<1000 { if !store.isLoadingChat { break }; await Task.yield() }
        XCTAssertNil(store.chatSessionID)
        XCTAssertFalse(store.canSubmitChat)
        XCTAssertFalse(store.isTurnActive)
    }

    func testSendingWaitsForAnAttachedConnectedChat() async throws {
        let rpc = FixtureRPC()
        let store = AppStore(api: rpc)
        store.activeBot = try XCTUnwrap(BotProfile.parse(["name": "scout"]))
        store.chatSessionID = "sid"
        store.connectionState = .connected(version: "fixture")
        store.isLoadingChat = true
        XCTAssertFalse(store.canSubmitChat)
        await store.send("Keep this draft")
        XCTAssertTrue(store.messages.isEmpty)
        XCTAssertFalse(store.isTurnActive)
        XCTAssertTrue(rpc.calls.isEmpty)
        store.isLoadingChat = false
        XCTAssertTrue(store.canSubmitChat)
        store.connectionState = .disconnected
        XCTAssertFalse(store.canSubmitChat)
        await store.send("Still keep this draft")
        XCTAssertTrue(store.messages.isEmpty)
        XCTAssertTrue(rpc.calls.isEmpty)
    }
    func testCompletedDshStepsStayDistinctWithoutEndingTheTurn() async {
        let rpc = FixtureRPC()
        let subject = AppStore(api: rpc)
        subject.chatSessionID = "sid"
        rpc.onNotification?("message.delta", "sid", ["text": "First step"])
        rpc.onNotification?("message.segment", "sid", ["text": "First step"])
        for _ in 0..<1000 { if subject.messages.last?.isStreaming == false { break }; await Task.yield() }
        XCTAssertEqual(subject.messages.map(\.text), ["First step"])
        XCTAssertTrue(subject.isTurnActive)
        rpc.onNotification?("message.delta", "sid", ["text": "Second step"])
        rpc.onNotification?("message.segment", "sid", ["text": "Second step"])
        rpc.onNotification?("message.complete", "sid", ["text": "", "summary": "Second step"])
        for _ in 0..<1000 { if !subject.isTurnActive { break }; await Task.yield() }
        XCTAssertEqual(subject.messages.map(\.text), ["First step", "Second step"])
        XCTAssertTrue(subject.messages.allSatisfy { !$0.isStreaming })
    }
    func testLocalMultiSelectResponsePreservesAllOptions() {
        let rpc = FixtureRPC()
        let store = AppStore(api: rpc)
        store.engine = .dsh
        store.chatSessionID = "sid"
        store.clarifyRequest = ClarifyRequest(rpcID: .string("q"), sessionID: "sid",
            questions: [.init(id: "a", question: "Which?", choices: ["A", "B"], multiSelect: true)])
        store.resolveClarify([:], selections: ["a": ["A", "B"]])
        XCTAssertEqual(rpc.responses.first?.1.dict("answers")?["a"] as? [String], ["A", "B"])
        XCTAssertNil(store.clarifyRequest)
    }
    func testLocalPixelCreationUsesNativeDshPresetEndpoint() async throws {
        let rpc = FixtureRPC()
        let subject = AppStore(api: rpc)
        subject.engine = .dsh
        let spec = PixelSpec.fallback(for: "Research interesting projects")
        let draft = PixelDraft(spec: spec, usedModel: false, availableToolsets: ["web", "file", "delegation"], availableSkills: [], notes: [])
        let bot = try await subject.buildPixel(draft: draft)
        XCTAssertEqual(bot.origin, "dsh")
        XCTAssertTrue(rpc.calls.contains { $0.0 == "pixel.create" })
        XCTAssertFalse(rpc.calls.contains { $0.0 == "profiles.create" || $0.0 == "config.set" })
    }

    func testPixelsWithSameNameKeepTheirProviderAndAppearanceIdentity() throws {
        let remote = try XCTUnwrap(BotProfile.parse(["name": "scout"]))
        let local = try XCTUnwrap(BotProfile.parse(["name": "scout", "origin": "dsh"]))
        XCTAssertEqual(remote.name, local.name)
        XCTAssertNotEqual(remote.id, local.id)
        XCTAssertNotEqual(remote.castKey, local.castKey)
    }
    func testOldClarifyAcknowledgementCannotClearANewerPrompt() async throws {
        let rpc = FixtureRPC()
        // Use a wired fixture to exercise the actual asynchronous response path.
        let store = AppStore(api: rpc)
        rpc.holdClarify = true
        store.chatSessionID = "sid"
        store.clarifyRequest = ClarifyRequest(rpcID: .string(""), sessionID: "sid",
            questions: [.init(id: "old", question: "Old?", choices: [], multiSelect: false)], promptGenerations: ["old": 4])
        store.resolveClarify(["old": "yes"])
        for _ in 0..<1000 { if rpc.held != nil { break }; await Task.yield() }
        let pending = try XCTUnwrap(rpc.held)
        store.clarifyRequest = ClarifyRequest(rpcID: .string(""), sessionID: "sid",
            questions: [.init(id: "new", question: "New?", choices: [], multiSelect: false)], promptGenerations: ["new": 5])
        pending.resume(returning: RPCResult(value: ["ok": true] as JSONObject))
        for _ in 0..<20 { await Task.yield() }
        XCTAssertEqual(store.clarifyRequest?.questions.map(\.id), ["new"])
        XCTAssertEqual(store.clarifyRequest?.promptGenerations, ["new": 5])
    }

    func testDisconnectClearsTransportOwnedPromptsAndStreamingState() async {
        let rpc = FixtureRPC()
        let subject = AppStore(api: rpc)
        subject.chatSessionID = "sid"
        subject.clarifyRequest = ClarifyRequest(rpcID: .string("old-rpc"), sessionID: "sid", questions: [])
        subject.isTurnActive = true
        rpc.onDisconnect?("Fixture disconnected")
        for _ in 0..<1000 { if subject.clarifyRequest == nil { break }; await Task.yield() }
        XCTAssertNil(subject.clarifyRequest)
        XCTAssertFalse(subject.isTurnActive)
        XCTAssertEqual(subject.turnStatus, "Disconnected · reconnect to check")
    }

    func testQualifiedRoomMembersDoNotCollapseIdenticalProfileNames() throws {
        let room = try XCTUnwrap(GroupRoom.parse(["room_id": "room", "name": "Cross-gateway", "members": [
            ["member_id": "a:scout", "profile": "scout", "display_name": "Scout A"],
            ["member_id": "b:scout", "profile": "scout", "display_name": "Scout B"]]]))
        XCTAssertEqual(room.member(matching: "a:scout")?.name, "Scout A")
        XCTAssertEqual(room.member(matching: "b:scout")?.name, "Scout B")
        XCTAssertNil(room.member(matching: "scout"))
    }
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
        XCTAssertEqual(rpc.calls.map { $0.0 }, ["profiles.describe", "profiles.create", "config.set", "profiles.describe", "profiles.configure", "config.set", "profiles.list"])
        let created = rpc.calls[1].1
        XCTAssertEqual(Set(created.keys), ["name", "description", "soul", "clone_from", "clone_all", "no_skills", "clone_channels"])
        XCTAssertEqual(created.string("clone_from"), "default")
        XCTAssertEqual(created.bool("clone_all"), false); XCTAssertEqual(created.bool("clone_channels"), false)
        XCTAssertEqual(created.bool("no_skills"), false)
        XCTAssertTrue(created.string("soul")?.contains(spec.soul) == true)
        XCTAssertEqual(rpc.calls[2].1.string("key"), "approvals.mode")
        XCTAssertEqual(rpc.calls[2].1.string("value"), "manual")
        let access = rpc.calls[4].1
        XCTAssertEqual(access["enabled_toolsets"] as? [String], ["file", "delegation"])
        XCTAssertEqual(access["enabled_mcp_servers"] as? [String], [])
        XCTAssertEqual(access["disabled_skills"] as? [String], ["extra"])
        let saved = try XCTUnwrap(access.dict("ui_meta")?.dict("niobium"))
        XCTAssertEqual(saved.int("schema_version"), 2)
        XCTAssertEqual(saved.dict("pixel_spec")?.dict("companion")?.string("name"), spec.companion.name)
        XCTAssertEqual(rpc.calls[5].1.string("value"), spec.reasoning)
    }

    func testCreationRechecksCatalogBeforeMakingAProfileAndNeverBroadensReview() async throws {
        let rpc = FixtureRPC(); rpc.catalogTools = ["web", "file"]
        let subject = AppStore(api: rpc)
        var spec = PixelSpec.fallback(for: "research"); spec.name = "fixture-catalog"
        spec.toolsets = ["web", "file", "delegation"]
        let draft = PixelDraft(spec: spec, usedModel: false, availableToolsets: ["file", "delegation"], availableSkills: [], notes: [])
        _ = try await subject.buildPixel(draft: draft)
        XCTAssertEqual(rpc.calls.first?.0, "profiles.describe")
        let access = try XCTUnwrap(rpc.calls.first { $0.0 == "profiles.configure" }?.1)
        XCTAssertEqual(access["enabled_toolsets"] as? [String], ["file"])
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
