import Foundation
import SwiftUI
import UIKit

@MainActor
final class AppStore: ObservableObject {
    @Published var connection: GatewayConnection?
    @Published var connectionState: GatewayState = .disconnected
    @Published var isDemo = false
    @Published var needsConnection = true
    @Published var isConnecting = false
    @Published var connectError: String?

    @Published var bots: [BotProfile] = []
    @Published var groups: [GroupRoom] = []
    @Published var toolsets: [Toolset] = []
    @Published var routines: [Routine] = []
    @Published var runtimeInfo: JSONObject?
    @Published var gatewayCapabilities: JSONObject?
    @Published var modelChoices: [ModelChoice] = []
    @Published var sessions: [SessionRow] = []
    @Published var chatRevision = 0
    var chatSource = "gui"
    @Published var activity: [ActivityItem] = []

    @Published var activeBot: BotProfile?
    @Published var activeGroup: GroupRoom?
    @Published var messages: [ChatMessage] = []
    @Published var roomEvents: [RoomEvent] = []
    @Published var chatSessionID: String?
    @Published var isTurnActive = false
    @Published var turnStatus = ""
    @Published var streamingReasoning = ""
    @Published var approvals: [ApprovalRequest] = []
    @Published var clarifyRequest: ClarifyRequest?
    @Published var toast: String?
    @Published var errorBanner: String?
    @Published var isRefreshing = false
    @Published var isLoadingChat = false
    @Published var lastSyncedAt: Date?

    private var api: HermesRPCClient?
    private var streamingMessageID: String?
    private var groupPollTask: Task<Void, Never>?
    private var toastTask: Task<Void, Never>?
    private var demoTurnTask: Task<Void, Never>?
    private var pendingDemoToolID: String?

    init() {
        let env = ProcessInfo.processInfo.environment
        if env["NIOBIUM_DEMO"] == "1" || ProcessInfo.processInfo.arguments.contains("--demo") {
            enterDemo()
            presentLaunchBotIfRequested()
            return
        }
        if let saved = ConnectionPrefs.load() {
            connection = saved
            needsConnection = false
            if saved.isDemo {
                enterDemo()
            } else {
                Task { await reconnect() }
            }
        } else if let gateway = env["NIOBIUM_GATEWAY"], let token = env["NIOBIUM_TOKEN"] {
            needsConnection = false
            Task { await connect(url: gateway, token: token, name: "Live test") }
        }
    }

    // MARK: - Connection

    func connect(url rawURL: String, token: String, name: String) async {
        guard !isConnecting else { return }
        guard let normalized = ConnectionPrefs.normalizedURL(rawURL) else {
            connectError = "Enter a full gateway address, like http://192.168.1.20:9119"
            return
        }
        guard !token.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            connectError = "A gateway access token is required. Use the session token from `hermes serve`."
            return
        }
        isConnecting = true
        connectError = nil
        connectionState = .connecting
        do {
            let status = try await HermesRPCClient.probe(baseURL: normalized, token: token)
            let version = status.string("release_date") ?? status.string("version") ?? "Hermes"
            let client = HermesRPCClient()
            wire(client)
            try await client.connect(baseURL: normalized, token: token)
            api = client
            let saved = GatewayConnection(id: UUID(), name: name.isEmpty ? "My Hermes" : name,
                                          baseURL: normalized, savedAt: Date(), isDemo: false)
            connection = saved
            ConnectionPrefs.save(saved, token: token)
            isDemo = false
            needsConnection = false
            connectionState = .connected(version: version)
            await loadRuntimeInfo()
            await refreshAll()
            toastMessage("Connected to \(saved.name)")
            presentLaunchBotIfRequested()
        } catch {
            api?.disconnect(notify: false)
            api = nil
            connectionState = .failed(error.localizedDescription)
            connectError = error.localizedDescription
        }
        isConnecting = false
    }

    func reconnect() async {
        guard let connection, !connection.isDemo else { return }
        let token = ConnectionPrefs.token(for: connection)
        await connect(url: connection.baseURL, token: token, name: connection.name)
    }

    func disconnect(forget: Bool = false) {
        api?.disconnect(notify: false)
        api = nil
        connectionState = .disconnected
        activeBot = nil
        activeGroup = nil
        messages = []
        bots = []
        groups = []
        toolsets = []
        routines = []
        activity = []
        if forget {
            ConnectionPrefs.forget()
            connection = nil
            needsConnection = true
        }
    }

    private func presentLaunchBotIfRequested() {
        let args = ProcessInfo.processInfo.arguments
        if let index = args.firstIndex(of: "--bot"), index + 1 < args.count {
            let name = args[index + 1]
            if let bot = bots.first(where: { $0.name == name }) {
                Task { @MainActor in
                    try? await Task.sleep(for: .milliseconds(260))
                    openBot(bot)
                    if args.contains("--demo-tool") {
                        try? await Task.sleep(for: .milliseconds(600))
                        await send("Run a clean build and report blockers")
                    }
                }
                return
            }
        }
        if let index = args.firstIndex(of: "--room"), index + 1 < args.count {
            let name = args[index + 1]
            if let room = groups.first(where: { $0.roomID == name || $0.name == name }) {
                Task { @MainActor in
                    try? await Task.sleep(for: .milliseconds(260))
                    openGroup(room)
                }
            }
        }
    }

    func enterDemo() {
        api?.disconnect(notify: false)
        api = nil
        isDemo = true
        needsConnection = false
        connectionState = .connected(version: "Demo workspace")
        let saved = GatewayConnection(id: UUID(), name: "Nb Studio", baseURL: "demo://niobium",
                                      savedAt: Date(), isDemo: true)
        connection = saved
        bots = DemoData.bots
        groups = DemoData.groups
        toolsets = DemoData.toolsets
        activity = DemoData.activity
        messages = []
        routines = DemoData.routines
        lastSyncedAt = Date()
    }

    private func wire(_ client: HermesRPCClient) {
        client.onNotification = { [weak self] type, sessionID, payload in
            Task { @MainActor in self?.handleEvent(type: type, sessionID: sessionID, payload: payload) }
        }
        client.onServerRequest = { [weak self] id, method, params in
            Task { @MainActor in self?.handleServerRequest(id: id, method: method, params: params) }
        }
        client.onDisconnect = { [weak self] message in
            Task { @MainActor in
                guard let self, !self.isDemo else { return }
                self.connectionState = .failed(message)
                self.showError("Connection lost. \(message)")
            }
        }
    }

    private func loadRuntimeInfo() async {
        guard let api, !isDemo else { return }
        if let runtime = try? await api.request("runtime.describe", params: [:], timeout: 10) as? JSONObject {
            runtimeInfo = runtime
            let sources = runtime.dict("session_create")?.array("sources")?.compactMap { $0 as? String } ?? []
            chatSource = sources.contains("gui") ? "gui" : (sources.contains("tui") ? "tui" : "gui")
        }
        if let caps = try? await api.request("gateway.capabilities", params: [:], timeout: 6) as? JSONObject {
            gatewayCapabilities = caps
        }
    }

    // MARK: - Roster

    func refreshAll() async {
        guard !isDemo, connectionState.isConnected else { return }
        isRefreshing = true
        await refreshRoster()
        await loadGroups()
        await loadToolsets()
        isRefreshing = false
    }

    func refreshRoster() async {
        guard !isDemo, let api else { return }
        do {
            let result = try await api.request("profiles.list", params: ["include_sessions": true]) as? JSONObject
            let parsed = result?.objects("profiles").compactMap(BotProfile.parse) ?? []
            bots = parsed.filter { !$0.hidden }.sorted { a, b in
                if a.isLive != b.isLive { return a.isLive }
                return (a.activityAt ?? .distantPast) > (b.activityAt ?? .distantPast)
            }
            lastSyncedAt = Date()
        } catch {
            showError("Could not load bots. \(error.localizedDescription)")
        }
    }

    func loadGroups() async {
        guard !isDemo, let api else { return }
        do {
            let capabilities = try await api.request("groups.capabilities", params: [:]) as? JSONObject
            guard capabilities?.array("methods")?.contains(where: { ($0 as? String) == "groups.list" }) == true else {
                groups = []
                return
            }
            let result = try await api.request("groups.list", params: ["limit": 100, "offset": 0]) as? JSONObject
            groups = (result?.objects("rooms").compactMap(GroupRoom.parse) ?? []).filter { !$0.disbanded }
        } catch {
            // Group chats are an optional capability; a gateway may not host rooms.
            groups = []
        }
    }

    func loadToolsets() async {
        guard !isDemo, let api else { return }
        do {
            var params: JSONObject = [:]
            if let chatSessionID, let activeBot { params = ["session_id": chatSessionID, "profile": activeBot.name] }
            let result = try await api.request("tools.list", params: params) as? JSONObject
            toolsets = result?.objects("toolsets").compactMap(Toolset.parse) ?? []
        } catch {
            // Non-fatal; leave the previous catalog visible.
        }
    }

    // MARK: - Direct bot chat

    func openBot(_ bot: BotProfile) {
        activeBot = bot
        messages = []
        approvals = []
        clarifyRequest = nil
        chatSessionID = nil
        isTurnActive = false
        turnStatus = ""
        streamingReasoning = ""
        isLoadingChat = true
        if isDemo {
            messages = DemoData.messages(for: bot)
            chatSessionID = "demo-\(bot.name)"
            isLoadingChat = false
            return
        }
        Task { await attachBot(bot) }
    }

    func closeBot() {
        activeBot = nil
        chatSessionID = nil
        messages = []
        approvals = []
        isTurnActive = false
    }

    private func attachBot(_ bot: BotProfile) async {
        guard let api else { return }
        defer { isLoadingChat = false }
        do {
            var storedID = bot.canonicalSessionID
            if storedID == nil {
                let listed = try await api.request("session.list", params: ["title": "Bot Chat", "profile": bot.name]) as? JSONObject
                storedID = listed?.objects("sessions").first?.string("id")
            }
            let result: JSONObject?
            if let storedID {
                result = try await api.request("session.resume", params: [
                    "session_id": storedID,
                    "profile": bot.name,
                    "source": chatSource
                ], timeout: 90) as? JSONObject
            } else {
                result = try await api.request("session.create", params: [
                    "profile": bot.name,
                    "title": "Bot Chat",
                    "source": chatSource
                ], timeout: 90) as? JSONObject
            }
            guard let result else { throw RPCError.malformed }
            chatSessionID = result.string("session_id")
            chatRevision = result.int("revision") ?? chatRevision
            messages = parseTranscript(result.objects("messages"))
            await loadModels()
            if let inflight = result.dict("inflight") {
                isTurnActive = inflight.bool("streaming") ?? false
                let text = inflight.string("assistant") ?? ""
                if !text.isEmpty {
                    let msg = ChatMessage(id: "inflight", role: .assistant, text: text,
                                          timestamp: Date(), toolStatus: .completed)
                    messages.append(msg)
                }
            }
            if let approval = result.dict("pending_approval") {
                approvals = [parseApproval(approval, sessionID: chatSessionID ?? "", rpcID: .string("recovered-\(UUID().uuidString)"))]
            }
            await loadToolsets()
        } catch {
            showError("Could not open \(bot.title). \(error.localizedDescription)")
        }
    }

    func send(_ rawText: String, attachment: UIImage? = nil) async {
        let text = rawText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty || attachment != nil else { return }
        guard isDemo || chatSessionID != nil else {
            showError("This chat isn’t attached to a session yet.")
            return
        }
        let displayed = text.isEmpty ? "Look at this image." : text
        messages.append(ChatMessage(id: UUID().uuidString, role: .user, text: displayed,
                                    timestamp: Date(), toolStatus: .completed))
        turnStatus = ""
        isTurnActive = true
        if isDemo {
            demoTurnTask?.cancel()
            demoTurnTask = Task { await demoReply(to: displayed) }
            return
        }
        guard let api, let sessionID = chatSessionID, let bot = activeBot else { return }
        do {
            if let attachment, let data = attachment.jpegData(compressionQuality: 0.86) {
                let base64 = data.base64EncodedString()
                _ = try await api.request("image.attach_bytes", params: [
                    "session_id": sessionID,
                    "profile": bot.name,
                    "data": base64,
                    "filename": "niobium-photo.jpg",
                    "mime": "image/jpeg"
                ])
            }
            _ = try await api.request("prompt.submit", params: [
                "session_id": sessionID,
                "profile": bot.name,
                "text": text
            ], timeout: 30)
        } catch {
            isTurnActive = false
            showError("Message failed. \(error.localizedDescription)")
        }
    }

    func loadSessions(for bot: BotProfile) async {
        guard !isDemo, let api else {
            sessions = []
            return
        }
        guard let result = try? await api.request("session.list", params: [
            "profile": bot.name, "scope": "stored", "limit": 60
        ], timeout: 20) as? JSONObject else {
            sessions = []
            return
        }
        sessions = result.objects("sessions").compactMap(SessionRow.parse)
    }

    func resumeSession(_ row: SessionRow, for bot: BotProfile) async {
        guard !isDemo, let api else { return }
        do {
            let result = try await api.request("session.resume", params: [
                "session_id": row.sessionID,
                "profile": bot.name,
                "source": chatSource
            ], timeout: 90) as? JSONObject
            guard let result else { throw RPCError.malformed }
            chatSessionID = result.string("session_id") ?? row.sessionID
            chatRevision = result.int("revision") ?? chatRevision
            messages = parseTranscript(result.objects("messages"))
            if let inflight = result.dict("inflight") {
                isTurnActive = inflight.bool("streaming") ?? false
                if let text = inflight.string("assistant"), !text.isEmpty {
                    messages.append(ChatMessage(id: "inflight", role: .assistant, text: text,
                                                timestamp: Date(), toolStatus: .completed))
                }
            } else {
                isTurnActive = false
            }
            await loadModels()
            toastMessage("Opened \(row.title)")
        } catch {
            showError("Could not open chat. \(error.localizedDescription)")
        }
    }

    func loadModels() async {
        guard !isDemo, let api, let sessionID = chatSessionID else {
            modelChoices = []
            return
        }
        guard let result = try? await api.request("model.options", params: [
            "profile": activeBot?.name ?? "default",
            "session_id": sessionID
        ], timeout: 20) as? JSONObject else { return }
        var rows: [ModelChoice] = []
        for provider in result.objects("providers") {
            let slug = provider.string("slug") ?? provider.string("id") ?? ""
            let name = provider.string("name") ?? slug.capitalized
            let currentProvider = provider.bool("is_current") ?? false
            let models = provider.array("models")?.compactMap { $0 as? String } ?? []
            for model in models {
                rows.append(ModelChoice(provider: slug, providerName: name, model: model,
                                        current: currentProvider && (result.string("model") == model),
                                        reasoning: true))
            }
        }
        modelChoices = rows
    }

    func switchModel(_ choice: ModelChoice) async {
        guard !isDemo, let api, let sessionID = chatSessionID else { return }
        do {
            let result = try await api.request("session.mutate", params: [
                "session_id": sessionID,
                "request_id": UUID().uuidString,
                "expected_revision": chatRevision,
                "operation": "model",
                "payload": ["model": choice.model, "provider": choice.provider]
            ], timeout: 30) as? JSONObject
            if let revision = result?.int("revision") { chatRevision = revision }
            if let index = bots.firstIndex(where: { $0.name == activeBot?.name }) {
                bots[index].model = choice.model
                bots[index].provider = choice.provider
                activeBot = bots[index]
            }
            modelChoices = modelChoices.map { item in
                var copy = item; copy.current = item.id == choice.id; return copy
            }
            toastMessage("Model switched to \(choice.label)")
        } catch {
            showError("Could not switch model. \(error.localizedDescription)")
        }
    }

    func stopTurn() {
        if isDemo {
            demoTurnTask?.cancel()
            isTurnActive = false
            if let id = streamingMessageID,
               let index = messages.firstIndex(where: { $0.id == id }) {
                messages[index].isStreaming = false
            }
            streamingMessageID = nil
            return
        }
        guard let api, let sessionID = chatSessionID, let bot = activeBot else { return }
        Task {
            _ = try? await api.request("session.interrupt", params: ["session_id": sessionID, "profile": bot.name])
            isTurnActive = false
        }
    }

    // MARK: - Events

    private func handleEvent(type: String, sessionID: String, payload: JSONObject) {
        if !sessionID.isEmpty, let active = chatSessionID, sessionID != active { return }
        switch type {
        case "message.start":
            isTurnActive = true
            ensureStreamingMessage()
        case "message.delta":
            let text = payload.string("text") ?? ""
            guard !text.isEmpty else { return }
            isTurnActive = true
            appendStreaming(text)
        case "message.interim":
            let text = payload.string("text") ?? ""
            if !text.isEmpty { appendStreaming("\n\n" + text) }
        case "reasoning.delta", "thinking.delta":
            let text = payload.string("text") ?? ""
            streamingReasoning += text
            updateStreamingReasoning(streamingReasoning)
        case "reasoning.available":
            streamingReasoning = payload.string("text") ?? streamingReasoning
            updateStreamingReasoning(streamingReasoning)
        case "message.complete":
            completeTurn(payload)
        case "tool.start":
            startTool(payload)
        case "tool.complete":
            completeTool(payload)
        case "tool.progress":
            updateToolProgress(payload)
        case "status.update":
            turnStatus = payload.string("text") ?? ""
        case "session.title":
            if let title = payload.string("title"), let bot = activeBot {
                updateBotTitle(bot.name, title: title)
            }
        case "error":
            isTurnActive = false
            showError(payload.string("message") ?? "The agent hit an error.")
        case "notice":
            toastMessage(payload.string("message") ?? "")
        case "approval.cancelled":
            approvals.removeAll { $0.sessionID == sessionID }
        case "background.complete", "btw.complete":
            let text = payload.string("text") ?? "Background task finished"
            addActivity(.message, title: "Background result", detail: String(text.prefix(140)), bot: activeBot?.name ?? "")
            toastMessage("Background task finished")
        case "session.usage":
            if let usage = payload.dict("usage") {
                _ = usage
            }
        default:
            break
        }
    }

    private func ensureStreamingMessage() {
        if let id = streamingMessageID, messages.contains(where: { $0.id == id }) { return }
        let id = UUID().uuidString
        streamingMessageID = id
        streamingReasoning = ""
        messages.append(ChatMessage(id: id, role: .assistant, text: "", timestamp: Date(),
                                    toolStatus: .completed, isStreaming: true))
    }

    private func appendStreaming(_ text: String) {
        ensureStreamingMessage()
        guard let id = streamingMessageID, let index = messages.firstIndex(where: { $0.id == id }) else { return }
        messages[index].text += text
        messages[index].isStreaming = true
    }

    private func updateStreamingReasoning(_ text: String) {
        guard let id = streamingMessageID, let index = messages.firstIndex(where: { $0.id == id }) else { return }
        messages[index].reasoning = text
    }

    private func completeTurn(_ payload: JSONObject) {
        isTurnActive = false
        turnStatus = ""
        let finalText = payload.string("text") ?? ""
        if let id = streamingMessageID, let index = messages.firstIndex(where: { $0.id == id }) {
            if messages[index].text.isEmpty, !finalText.isEmpty { messages[index].text = finalText }
            messages[index].isStreaming = false
        } else if !finalText.isEmpty {
            messages.append(ChatMessage(id: UUID().uuidString, role: .assistant, text: finalText,
                                        timestamp: Date(), toolStatus: .completed))
        }
        if payload.string("status") == "error" || payload.string("error") != nil {
            showError(payload.string("error") ?? "The turn ended with an error.")
        }
        streamingMessageID = nil
        streamingReasoning = ""
        if let bot = activeBot {
            addActivity(.message, title: "Reply from \(bot.title)", detail: String(finalText.prefix(120)), bot: bot.name)
        }
    }

    private func startTool(_ payload: JSONObject) {
        let id = payload.string("tool_id") ?? UUID().uuidString
        if let index = messages.firstIndex(where: { $0.id == id }) {
            messages[index].toolStatus = .running
            return
        }
        let name = payload.string("name") ?? "tool"
        let args = jsonString(payload["args"])
        let preview = payload.string("preview") ?? ""
        messages.append(ChatMessage(id: id, role: .tool, text: "", timestamp: Date(),
                                    toolName: name, toolArgs: args, toolResult: nil,
                                    toolStatus: .running, toolPreview: preview))
        if let bot = activeBot {
            addActivity(.tool, title: name, detail: preview.isEmpty ? "Running" : preview, bot: bot.name)
        }
    }

    private func completeTool(_ payload: JSONObject) {
        let id = payload.string("tool_id") ?? ""
        let result = jsonString(payload["result"])
        let summary = payload.string("summary") ?? payload.string("result_text") ?? ""
        let duration = payload.double("duration_s")
        let resultObject = payload["result"] as? JSONObject
        let failed = (payload.bool("is_error") ?? false) || (resultObject?.bool("is_error") ?? false)
            || (resultObject?.bool("success") == false)
        if let index = messages.firstIndex(where: { $0.id == id }) {
            messages[index].toolStatus = failed ? .failed : .completed
            messages[index].toolResult = summary.isEmpty ? result : summary
            messages[index].toolDuration = duration
            if let preview = payload.string("summary"), !preview.isEmpty { messages[index].toolPreview = preview }
        } else {
            let name = payload.string("name") ?? "tool"
            messages.append(ChatMessage(id: id.isEmpty ? UUID().uuidString : id, role: .tool, text: "",
                                        timestamp: Date(), toolName: name,
                                        toolArgs: jsonString(payload["args"]),
                                        toolResult: summary.isEmpty ? result : summary,
                                        toolStatus: .completed, toolPreview: summary,
                                        toolDuration: duration))
        }
        if let bot = activeBot {
            let toolName = payload.string("name") ?? "Tool"
            addActivity(.tool, title: "\(toolName) completed",
                        detail: summary.isEmpty ? "Finished" : String(summary.prefix(120)), bot: bot.name)
        }
    }

    private func updateToolProgress(_ payload: JSONObject) {
        guard let id = payload.string("tool_id"),
              let index = messages.firstIndex(where: { $0.id == id }) else { return }
        if let message = payload.string("message") { messages[index].toolPreview = message }
    }

    private func handleServerRequest(id: JSONValueID, method: String, params: JSONObject) {
        let sessionID = params.string("session_id") ?? ""
        if !sessionID.isEmpty, let active = chatSessionID, active != sessionID { return }
        switch method {
        case "approval":
            let approval = parseApproval(params, sessionID: sessionID, rpcID: id)
            approvals.append(approval)
            addActivity(.approval, title: "Approval needed", detail: approval.command, bot: activeBot?.name ?? "")
            Haptics.warning()
        case "clarify":
            let questions = params.objects("questions").compactMap { raw -> ClarifyQuestion? in
                guard let qid = raw.string("qid") else { return nil }
                return ClarifyQuestion(id: qid, question: raw.string("question") ?? "",
                                       choices: (raw.array("choices") as? [String]) ?? [],
                                       multiSelect: raw.bool("multi_select") ?? false)
            }
            clarifyRequest = ClarifyRequest(rpcID: id, sessionID: sessionID, questions: questions)
        case "sudo", "secret", "vault.code", "vault.unlock_prompt", "vault.save_login":
            // The mobile client declines interactive host secrets; the agent fails fast and
            // can ask for a different route.
            api?.respond(id: id, result: ["value": ""])
        default:
            api?.respondError(id: id, message: "Niobium does not handle \(method) yet.")
        }
    }

    private func parseApproval(_ params: JSONObject, sessionID: String, rpcID: JSONValueID) -> ApprovalRequest {
        let choices = (params.array("choices") as? [String]) ?? ["once", "deny"]
        return ApprovalRequest(rpcID: rpcID,
                               requestID: params.string("request_id") ?? "",
                               sessionID: sessionID,
                               command: params.string("command") ?? "",
                               description: params.string("description") ?? "",
                               toolName: params.string("tool_name") ?? "terminal",
                               choices: choices)
    }

    func resolveApproval(_ approval: ApprovalRequest, choice: String) {
        approvals.removeAll { $0.id == approval.id }
        if isDemo {
            if choice == "deny" {
                toastMessage("Command denied")
                appendAssistant("Understood — I’ve left the build directory untouched.")
            } else {
                appendAssistant("Approved. The tool is running with \(choice) scope.")
                if let toolID = pendingDemoToolID,
                   let idx = messages.firstIndex(where: { $0.id == toolID }) {
                    messages[idx].toolStatus = .completed
                    messages[idx].toolResult = "Removed 48 stale build artifacts."
                    messages[idx].toolDuration = 0.8
                }
                pendingDemoToolID = nil
            }
            isTurnActive = false
            return
        }
        api?.respond(id: approval.rpcID, result: ["choice": choice])
        toastMessage("Approval sent: \(choice)")
    }

    func resolveClarify(_ answers: [String: String]) {
        guard let request = clarifyRequest else { return }
        clarifyRequest = nil
        api?.respond(id: request.rpcID, result: ["answers": answers, "outcome": "submitted"])
    }

    // MARK: - Groups

    func openGroup(_ room: GroupRoom) {
        activeGroup = room
        roomEvents = []
        if isDemo {
            roomEvents = DemoData.events(for: room)
            return
        }
        Task { await refreshGroup() }
        groupPollTask?.cancel()
        groupPollTask = Task { @MainActor in
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(3))
                await refreshGroup()
            }
        }
    }

    func closeGroup() {
        groupPollTask?.cancel(); groupPollTask = nil
        activeGroup = nil
        roomEvents = []
    }

    func refreshGroup() async {
        guard !isDemo, let api, let room = activeGroup else { return }
        do {
            let result = try await api.request("groups.log", params: [
                "room_id": room.roomID, "since_seq": 0, "limit": 500
            ]) as? JSONObject
            roomEvents = result?.objects("events").compactMap(RoomEvent.parse) ?? []
        } catch {
            // Background polling should stay quiet.
        }
    }

    func sendGroup(_ text: String) async {
        guard let room = activeGroup else { return }
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        if isDemo {
            let event = RoomEvent(seq: (roomEvents.last?.seq ?? 0) + 1, roomID: room.roomID,
                                  eventID: UUID().uuidString, kind: "message.user",
                                  actorKind: "user", actorID: "user", actorName: "You",
                                  text: trimmed, timestamp: Date(), status: nil, reason: nil)
            roomEvents.append(event)
            demoGroupReply(room: room, prompt: trimmed)
            return
        }
        guard let api else { return }
        let eventID = UUID().uuidString
        do {
            let result = try await api.request("groups.send", params: [
                "room_id": room.roomID,
                "profile": activeBot?.name ?? bots.first?.name ?? "default",
                "event_id": eventID,
                "payload": ["text": trimmed, "thread_id": eventID]
            ]) as? JSONObject
            if let eventDict = result?.dict("event"), let event = RoomEvent.parse(eventDict) {
                roomEvents.append(event)
            }
            await refreshGroup()
        } catch {
            showError("Group send failed. \(error.localizedDescription)")
        }
    }

    private func demoGroupReply(room: GroupRoom, prompt: String) {
        let replies = room.members.prefix(2).enumerated().map { index, member in
            let text = index == 0
                ? "On it. I’ll take the first pass and report back with a recommendation."
                : "I’ll check the sources and flag anything that looks off."
            return (member, text)
        }
        Task { @MainActor in
            for (offset, pair) in replies.enumerated() {
                try? await Task.sleep(for: .milliseconds(900 + offset * 500))
                let (member, text) = pair
                roomEvents.append(RoomEvent(seq: (roomEvents.last?.seq ?? 0) + 1, roomID: room.roomID,
                                            eventID: UUID().uuidString, kind: "message.member",
                                            actorKind: "member", actorID: member.memberID,
                                            actorName: member.name, text: text, timestamp: Date(),
                                            status: nil, reason: nil))
            }
        }
    }

    func createGroup(name: String, members: [BotProfile]) async throws {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty, members.count >= 2 else { throw RPCError.transport("Name a group and pick at least two pixels.") }
        if isDemo {
            let room = GroupRoom(roomID: UUID().uuidString, name: trimmed,
                                 members: members.map { GroupMember(memberID: $0.name, profile: $0.name,
                                                                    handle: $0.name, displayName: $0.title) },
                                 updatedAt: Date(), latestSeq: 0, disbanded: false,
                                 preview: "Room created")
            groups.insert(room, at: 0)
            return
        }
        guard let api else { throw RPCError.notConnected }
        let roster: [JSONObject] = members.map { bot in
            ["member_id": bot.name, "profile": bot.name, "handle": bot.name,
             "display_name": bot.title, "target": ["kind": "local", "profile": bot.name]]
        }
        let result = try await api.request("groups.create", params: [
            "room_id": UUID().uuidString.lowercased(),
            "name": trimmed,
            "members": roster
        ]) as? JSONObject
        guard let dict = result?.dict("room"), let room = GroupRoom.parse(dict) else { throw RPCError.malformed }
        groups.insert(room, at: 0)
    }

    // MARK: - AI Pixel builder

    @Published var isBuildingPixel = false

    func buildPixel(prompt: String) async throws -> BotProfile {
        let clean = prompt.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !clean.isEmpty else { throw RPCError.transport("Describe what the Pixel should do.") }
        isBuildingPixel = true
        defer { isBuildingPixel = false }
        let (spec, usedModel) = await PixelBuilder.shared.spec(for: clean, api: isDemo ? nil : api)
        let slug = spec.name.lowercased().replacingOccurrences(of: " ", with: "-")
        let member = CastMember.all.first(where: { $0.id == spec.character.archetype }) ?? CastMember.seeded(slug, title: spec.title)
        if isDemo {
            let bot = BotProfile(name: slug, title: spec.title, role: spec.role, provider: "", model: "Hermes",
                                 skillCount: spec.skills.count, canonicalSessionID: "demo-\(slug)",
                                 canonicalResolvedID: nil, lastSessionID: nil,
                                 preview: "Built by the Pixel builder.", activityAt: Date(),
                                 workerActive: false, isDefault: false, hidden: false,
                                 accentHex: member.accentHex, hasAvatar: false)
            bots.insert(bot, at: 0)
            CastStore.shared.select(member, for: slug)
            return bot
        }
        guard let api else { throw RPCError.notConnected }
        _ = try await api.request("profiles.create", params: [
            "name": slug,
            "description": spec.role,
            "no_skills": false
        ], timeout: 120)
        var uiMeta: JSONObject = [
            "title": spec.title,
            "description": spec.role,
            "color": member.accentHex,
            "hidden": false,
            "companion": spec.companion.kind,
            "builder": usedModel ? "model" : "template"
        ]
        var configure: JSONObject = [
            "name": slug,
            "description": spec.role,
            "soul": spec.soul,
            "enabled_toolsets": spec.toolsets,
            "ui_meta": ["hermes-bots": uiMeta]
        ]
        _ = try await api.request("profiles.configure", params: configure, timeout: 120)
        CastStore.shared.select(member, for: slug)
        await refreshRoster()
        guard let created = bots.first(where: { $0.name == slug }) else { throw RPCError.malformed }
        toastMessage(usedModel ? "Built \(created.title) with AI" : "Built \(created.title) from a template")
        return created
    }

    // MARK: - Create bot

    func createBot(name: String, title: String, role: String, colorHex: String) async throws {
        let slug = name.trimmingCharacters(in: .whitespacesAndNewlines)
            .lowercased()
            .replacingOccurrences(of: " ", with: "-")
        guard !slug.isEmpty else { throw RPCError.transport("Give your bot a name.") }
        if isDemo {
            let bot = BotProfile(name: slug, title: title.isEmpty ? slug.capitalized : title,
                                 role: role, provider: "", model: "Hermes",
                                 skillCount: 12, canonicalSessionID: "demo-\(slug)",
                                 canonicalResolvedID: nil, lastSessionID: nil,
                                 preview: "Say hello to \(title.isEmpty ? slug.capitalized : title).",
                                 activityAt: Date(), workerActive: false, isDefault: false,
                                 hidden: false, accentHex: colorHex, hasAvatar: false)
            bots.insert(bot, at: 0)
            return
        }
        guard let api else { throw RPCError.notConnected }
        _ = try await api.request("profiles.create", params: [
            "name": slug,
            "description": role,
            "no_skills": false
        ], timeout: 120)
        _ = try await api.request("profiles.configure", params: [
            "name": slug,
            "description": role,
            "ui_meta": ["hermes-bots": ["title": title.isEmpty ? slug.capitalized : title,
                                        "description": role,
                                        "color": colorHex,
                                        "hidden": false]]
        ])
        await refreshRoster()
        if let created = bots.first(where: { $0.name == slug }) {
            openBot(created)
        }
    }

    // MARK: - Tools

    func toggleToolset(_ toolset: Toolset) async {
        if isDemo {
            if let index = toolsets.firstIndex(where: { $0.id == toolset.id }) {
                toolsets[index].enabled.toggle()
            }
            return
        }
        guard let api, let sessionID = chatSessionID, let bot = activeBot else {
            showError("Open a pixel chat first — toolsets are toggled per live session.")
            return
        }
        do {
            _ = try await api.request("tools.configure", params: [
                "session_id": sessionID,
                "profile": bot.name,
                "action": toolset.enabled ? "disable" : "enable",
                "names": [toolset.name]
            ])
            await loadToolsets()
            toastMessage("\(toolset.name) \(toolset.enabled ? "disabled" : "enabled") for \(bot.title)")
        } catch {
            showError("Could not update tools. \(error.localizedDescription)")
        }
    }

    // MARK: - Routines

    func loadRoutines(for bot: BotProfile) async {
        if isDemo {
            routines = DemoData.routines.filter { $0.bot == bot.name || $0.bot.isEmpty }
            return
        }
        guard let api else { return }
        do {
            let result = try await api.request("cron.manage", params: [
                "action": "list", "profile": bot.name, "include_disabled": true
            ]) as? JSONObject
            var parsed = result?.objects("jobs").compactMap { Routine.parse($0, bot: bot.name) } ?? []
            if parsed.isEmpty { parsed = (result?.array("jobs") as? [JSONObject])?.compactMap { Routine.parse($0, bot: bot.name) } ?? [] }
            routines = parsed
        } catch {
            routines = []
        }
    }

    func createRoutine(name: String, schedule: String, prompt: String, bot: BotProfile) async throws {
        if isDemo {
            routines.append(Routine(id: UUID().uuidString, name: name, schedule: schedule,
                                    prompt: prompt, enabled: true, nextRun: "Tomorrow, 08:00", bot: bot.name))
            return
        }
        guard let api else { throw RPCError.notConnected }
        _ = try await api.request("cron.manage", params: [
            "action": "add", "name": name, "schedule": schedule,
            "prompt": prompt, "profile": bot.name
        ])
        await loadRoutines(for: bot)
    }

    func deleteRoutine(_ routine: Routine, bot: BotProfile) async {
        if isDemo {
            routines.removeAll { $0.id == routine.id }
            return
        }
        guard let api else { return }
        _ = try? await api.request("cron.manage", params: [
            "action": "remove", "name": routine.id, "profile": bot.name
        ])
        await loadRoutines(for: bot)
    }

    // MARK: - Helpers

    private func parseTranscript(_ rows: [JSONObject]) -> [ChatMessage] {
        var output: [ChatMessage] = []
        for (index, row) in rows.enumerated() {
            let displayKind = row.string("display_kind")
            if displayKind == "hidden" { continue }
            let roleString = row.string("role") ?? "assistant"
            let timestamp = epochDate(row.double("timestamp")) ?? Date()
            let rowID = row.int("row_id")
            let text = row.string("text") ?? (row["content"] as? String) ?? ""
            if roleString == "tool" {
                let result = jsonString(row["content"])
                output.append(ChatMessage(id: row.string("tool_call_id") ?? "row-\(rowID ?? index)",
                                          role: .tool, text: "", timestamp: timestamp,
                                          toolName: row.string("name") ?? "tool",
                                          toolArgs: jsonString(row["args"]),
                                          toolResult: result,
                                          toolStatus: .completed,
                                          toolPreview: row.string("context"),
                                          rowID: rowID))
            } else if roleString == "assistant" {
                if !text.isEmpty {
                    output.append(ChatMessage(id: "row-\(rowID ?? index)", role: .assistant,
                                              text: text, timestamp: timestamp,
                                              reasoning: row.string("reasoning"),
                                              toolStatus: .completed, rowID: rowID))
                }
            } else if roleString == "user" {
                output.append(ChatMessage(id: "row-\(rowID ?? index)", role: .user,
                                          text: text, timestamp: timestamp,
                                          toolStatus: .completed, rowID: rowID))
            } else if roleString == "system", !text.isEmpty {
                output.append(ChatMessage(id: "row-\(rowID ?? index)", role: .system,
                                          text: text, timestamp: timestamp,
                                          toolStatus: .completed, rowID: rowID))
            }
        }
        return output
    }

    private func updateBotTitle(_ name: String, title: String) {
        guard title != "Bot Chat", !title.isEmpty else { return }
        if let index = bots.firstIndex(where: { $0.name == name }) {
            bots[index].preview = bots[index].preview
        }
    }

    func addActivity(_ kind: ActivityItem.Kind, title: String, detail: String, bot: String, success: Bool = true) {
        let item = ActivityItem(id: UUID().uuidString, kind: kind, title: title, detail: detail,
                                date: Date(), botName: bot, success: success)
        activity.insert(item, at: 0)
        if activity.count > 80 { activity.removeLast(activity.count - 80) }
    }

    func toastMessage(_ text: String) {
        guard !text.isEmpty else { return }
        toast = text
        toastTask?.cancel()
        toastTask = Task { @MainActor in
            try? await Task.sleep(for: .seconds(2.8))
            if self.toast == text { self.toast = nil }
        }
    }

    func showError(_ text: String) {
        errorBanner = text
        toastTask?.cancel()
        toastTask = Task { @MainActor in
            try? await Task.sleep(for: .seconds(7))
            if self.errorBanner == text { self.errorBanner = nil }
        }
    }

    // MARK: - Demo turn

    private func demoReply(to prompt: String) async {
        try? await Task.sleep(for: .milliseconds(450))
        guard !Task.isCancelled else { return }
        let id = UUID().uuidString
        streamingMessageID = id
        messages.append(ChatMessage(id: id, role: .assistant, text: "", timestamp: Date(),
                                    toolStatus: .completed, isStreaming: true))
        streamingReasoning = "Reading the request against the current workspace and the team roster."
        if let index = messages.firstIndex(where: { $0.id == id }) {
            messages[index].reasoning = streamingReasoning
        }
        let wantsAction = prompt.lowercased().contains("build") || prompt.lowercased().contains("clean")
            || prompt.lowercased().contains("delete") || prompt.lowercased().contains("deploy")
        if wantsAction {
            try? await Task.sleep(for: .milliseconds(400))
            let toolID = "demo-tool-\(UUID().uuidString)"
            pendingDemoToolID = toolID
            messages.append(ChatMessage(id: toolID, role: .tool, text: "", timestamp: Date(),
                                        toolName: "terminal", toolArgs: "{\n  \"command\": \"rm -rf ./build\"\n}",
                                        toolResult: nil, toolStatus: .running,
                                        toolPreview: "rm -rf ./build"))
            try? await Task.sleep(for: .milliseconds(500))
            approvals.append(ApprovalRequest(rpcID: .string("demo-\(UUID().uuidString)"),
                                             requestID: "demo-approval",
                                             sessionID: chatSessionID ?? "demo",
                                             command: "rm -rf ./build",
                                             description: "Remove the generated build directory before a clean rebuild.",
                                             toolName: "terminal",
                                             choices: ["once", "session", "always", "deny"]))
            isTurnActive = true
            return
        }
        let reply = demoText(for: prompt)
        for chunk in reply.chunked(into: 4) {
            if Task.isCancelled { return }
            try? await Task.sleep(for: .milliseconds(28))
            appendStreaming(chunk)
        }
        if let index = messages.firstIndex(where: { $0.id == id }) {
            messages[index].isStreaming = false
        }
        isTurnActive = false
        streamingMessageID = nil
        streamingReasoning = ""
        addActivity(.message, title: "Reply from \(activeBot?.title ?? "Bot")",
                    detail: String(reply.prefix(120)), bot: activeBot?.name ?? "")
    }

    private func demoText(for prompt: String) -> String {
        let lower = prompt.lowercased()
        if lower.contains("status") || lower.contains("morning") || lower.contains("update") {
            return """
            Three things need you today.

            **1 · Build is green.** Forge finished the clean build and the release notes are ready for review. The only open thread is the signing profile for the TestFlight build.

            **2 · Scout found a signal.** Two competitors shipped agent memory this week. I saved the teardown in *Research › Memory landscape* — the useful difference is their retrieval UI, not the storage layer.

            **3 · One decision.** Athena recommends we ship Pixels before custom routines. I agree: the roster is the habit, routines are the power feature.

            Say the word and I’ll open the release checklist.
            """
        }
        if lower.contains("research") || lower.contains("find") || lower.contains("search") {
            return """
            I ran a pass across the web and your saved sessions.

            The strongest angle is **quiet capability**: people don’t want another chat box, they want a team that keeps working while they’re away. Lead with the roster, then show one agent doing real work end to end.

            I’ve put the strongest three sources and a draft outline in the workspace. Want me to turn it into a one-page brief?
            """
        }
        return """
        I’m on it. I’ll keep the work scoped to what you asked and surface anything that needs a decision rather than guessing.

        I’ve noted the context in this bot’s memory, so the next turn starts where we left off. If you want, I can also pull in \(bots.filter { $0.name != activeBot?.name }.prefix(2).map(\.title).joined(separator: " and ")) for a second pass.
        """
    }

    private func appendAssistant(_ text: String) {
        messages.append(ChatMessage(id: UUID().uuidString, role: .assistant, text: text,
                                    timestamp: Date(), toolStatus: .completed))
    }
}

private extension String {
    func chunked(into size: Int) -> [String] {
        var result: [String] = []
        var current = ""
        for character in self {
            current.append(character)
            if current.count >= size {
                result.append(current)
                current = ""
            }
        }
        if !current.isEmpty { result.append(current) }
        return result
    }
}
