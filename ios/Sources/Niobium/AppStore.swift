import Foundation
import SwiftUI
import UIKit

enum AgentEngine: String { case hermes, dsh }

@MainActor
final class AppStore: ObservableObject {
    @Published var engine: AgentEngine = .hermes
    @Published var localStatus = "Stopped"
    @Published var localReady = false
    @Published var connection: GatewayConnection?
    @Published var connectionState: GatewayState = .disconnected
    @Published var isDemo = false
    @Published var needsConnection = true
    @Published var isConnecting = false
    @Published var connectError: String?

    @Published var bots: [BotProfile] = []
    @Published var groups: [GroupRoom] = []
    @Published var groupsHaveDriver = false
    @Published var toolsets: [Toolset] = []
    @Published var routines: [Routine] = []
    @Published var companionTasks: [CompanionTask] = []
    @Published var companionError: String?
    @Published var companionsLoading = false
    @Published var gatewayLatencyMs: Int?
    @Published var runtimeInfo: JSONObject?
    @Published var gatewayCapabilities: JSONObject?
    @Published var modelChoices: [ModelChoice] = []
    @Published var sessions: [SessionRow] = []
    @Published var usage: UsageStats?
    @Published var skills: [String: [String]] = [:]
    @Published var mcpServers: [MCPServerRow] = []
    @Published var chatRevision = 0
    @Published var chatGeneration: Int?
    @Published var chatReasoning: String?
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
    @Published var credentialRequests: [CredentialRequest] = []
    @Published var toast: String?
    @Published var errorBanner: String?
    @Published var isRefreshing = false
    @Published var isLoadingChat = false
    @Published var historyHasMore = false
    @Published var historyBeforeSeq: Int?
    @Published var isLoadingHistory = false
    @Published var lastSyncedAt: Date?

    private var api: (any HermesRPC)?
    private var streamingMessageID: String?
    private var groupPollTask: Task<Void, Never>?
    private var toastTask: Task<Void, Never>?
    private var demoTurnTask: Task<Void, Never>?
    private var pendingDemoToolID: String?
    private var chatEpoch = 0
    private var groupEpoch = 0
    private var connectionEpoch = 0
    private var localBootTask: Task<Void, Never>?
    private var localProviderToken = UUID().uuidString + UUID().uuidString
#if NIOBIUM_LOCAL_DSH
    private var localObserver: NSObjectProtocol?
#endif

    init(api: (any HermesRPC)? = nil) {
#if NIOBIUM_LOCAL_DSH
        localObserver = NotificationCenter.default.addObserver(forName: .init("DSHHarnessStateDidChangeNotification"), object: nil, queue: .main) { [weak self] _ in
            Task { @MainActor in
                guard let self else { return }
                let harness = DSHHarness.shared()
                self.localReady = harness.state == .ready
                switch harness.state {
                case .ready: self.localStatus = "Running"
                case .failed: self.localStatus = harness.lastError ?? "Local engine stopped."
                case .starting, .restarting: self.localStatus = "Starting your Pixels…"
                default: self.localStatus = "Stopped"
                }
                if harness.state == .ready && self.engine == .dsh && !self.isConnecting && !self.connectionState.isConnected { self.useLocal() }
            }
        }
#endif
        if let api {
            self.api = api
            wire(api)
            needsConnection = false
            return
        }
        let env = ProcessInfo.processInfo.environment
        if env["NIOBIUM_DEMO"] == "1" || ProcessInfo.processInfo.arguments.contains("--demo") {
            enterDemo()
            presentLaunchBotIfRequested()
            return
        }
        if ProcessInfo.processInfo.arguments.contains("--dsh") || UserDefaults.standard.string(forKey: "niobium.agent-provider") == "dsh" {
            useLocal()
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

    func connect(url rawURL: String, token: String, name: String, local: Bool = false) async {
        guard !isConnecting else { return }
        guard let normalized = ConnectionPrefs.normalizedURL(rawURL) else {
            connectError = "Enter a full gateway address, like http://192.168.1.20:9119"
            return
        }
        guard !token.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            connectError = "A gateway access token is required. Use the session token from `hermes serve`."
            return
        }
        connectionEpoch += 1
        let epoch = connectionEpoch
        api?.disconnect(notify: false)
        api = nil
        activeBot = nil; activeGroup = nil; messages = []; bots = []; groups = []; toolsets = []; activity = []; routines = []
        chatEpoch += 1; groupEpoch += 1; groupPollTask?.cancel(); demoTurnTask?.cancel()
        engine = local ? .dsh : .hermes
        needsConnection = false
        credentialRequests = []
        approvals = []
        clarifyRequest = nil
        isConnecting = true
        connectError = nil
        connectionState = .connecting
        do {
            let status = try await HermesRPCClient.probe(baseURL: normalized, token: token)
            guard epoch == connectionEpoch else { return }
            let version = status.string("release_date") ?? status.string("version") ?? "Hermes"
            let client = HermesRPCClient()
            wire(client)
            try await client.connect(baseURL: normalized, token: token)
            guard epoch == connectionEpoch else { client.disconnect(notify: false); return }
            api = client
            let saved = GatewayConnection(id: UUID(), name: name.isEmpty ? "My Hermes" : name,
                                          baseURL: normalized, savedAt: Date(), isDemo: false)
            if !local {
                guard ConnectionPrefs.save(saved, token: token) else { client.disconnect(notify: false); throw RPCError.transport("Could not store the gateway token securely.") }
            }
            UserDefaults.standard.set(local ? "dsh" : "hermes", forKey: "niobium.agent-provider")
            connection = saved
            isDemo = false
            needsConnection = false
            connectionState = .connected(version: version)
            await loadRuntimeInfo()
            await refreshAll()
            toastMessage("Connected to \(saved.name)")
            presentLaunchBotIfRequested()
        } catch {
            guard epoch == connectionEpoch else { return }
            api?.disconnect(notify: false)
            api = nil
            connectionState = .failed(error.localizedDescription)
            connectError = error.localizedDescription
        }
        isConnecting = false
    }

    func reconnect() async {
        if engine == .dsh { useLocal(); return }
        guard let connection, !connection.isDemo else { return }
        let token = ConnectionPrefs.token(for: connection)
        await connect(url: connection.baseURL, token: token, name: connection.name)
    }

    func resumeConnection() async {
        guard !isDemo, !needsConnection, !isConnecting, !connectionState.isConnected else { return }
#if NIOBIUM_LOCAL_DSH
        // Foregrounding must not undo an explicit Stop from Engines.
        if engine == .dsh && DSHHarness.shared().state == .stopped { return }
#endif
        await reconnect()
    }

    func disconnect(forget: Bool = false) {
        connectionEpoch += 1; chatEpoch += 1; groupEpoch += 1
        localBootTask?.cancel(); localBootTask = nil
        groupPollTask?.cancel(); demoTurnTask?.cancel()
        isConnecting = false
        api?.disconnect(notify: false)
        api = nil
        connectionState = .disconnected
        activeBot = nil
        activeGroup = nil
        messages = []
        bots = []
        groups = []
        toolsets = []
        chatSessionID = nil; modelChoices = []; approvals = []; clarifyRequest = nil; credentialRequests = []
        isTurnActive = false; companionTasks = []; sessions = []; usage = nil; skills = [:]; mcpServers = []
        routines = []
        activity = []
        if forget {
            if engine == .hermes { ConnectionPrefs.forget() }
            UserDefaults.standard.removeObject(forKey: "niobium.agent-provider")
            connection = nil
            needsConnection = true
        }
    }

    func useLocal() {
#if NIOBIUM_LOCAL_DSH
        if engine == .dsh && (isConnecting || connectionState.isConnected) { return }
        disconnect()
        engine = .dsh; isDemo = false; needsConnection = false
        isConnecting = true; connectionState = .connecting
        UserDefaults.standard.set("dsh", forKey: "niobium.agent-provider")
        let epoch = connectionEpoch
        let token = localProviderToken
        localBootTask = Task { @MainActor [weak self] in
            guard let self else { return }
            do {
                let url = try await LocalDSHProvider.start(token: token) { [weak self] message, ready in
                    self?.localStatus = message; self?.localReady = ready
                }
                try Task.checkCancellation()
                guard self.connectionEpoch == epoch, self.engine == .dsh else { return }
                self.isConnecting = false
                await self.connect(url: url.appendingPathComponent("niobium").absoluteString, token: token, name: "On device · DSH", local: true)
            } catch is CancellationError { }
            catch {
                guard self.connectionEpoch == epoch else { return }
                self.isConnecting = false; self.localReady = false
                self.localStatus = error.localizedDescription
                self.connectionState = .failed(error.localizedDescription)
                self.showError(error.localizedDescription)
            }
        }
#else
        showError("The on-device engine is included in the full iOS build.")
#endif
    }

    func useHermes() async {
        let saved = ConnectionPrefs.load().flatMap { $0.isDemo ? nil : $0 }
        disconnect()
        engine = .hermes; isDemo = false
        UserDefaults.standard.set("hermes", forKey: "niobium.agent-provider")
        connection = saved
        needsConnection = saved == nil
        if let saved { await connect(url: saved.baseURL, token: ConnectionPrefs.token(for: saved), name: saved.name) }
    }

    func stopLocal() {
#if NIOBIUM_LOCAL_DSH
        if engine == .dsh { disconnect() }
        DSHHarness.shared().stop()
        localReady = false; localStatus = "Stopped"
#endif
    }

    func saveLocalCredential(ref: String, value: String) async throws {
        guard engine == .dsh, let api else { throw RPCError.notConnected }
        _ = try await api.request("local.credential", params: ["ref": ref, "value": value], timeout: 20)
        toastMessage("Provider key saved on this device")
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
        connectionEpoch += 1; localBootTask?.cancel(); engine = .hermes
        UserDefaults.standard.set("hermes", forKey: "niobium.agent-provider")
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

    private func wire(_ client: any HermesRPC) {
        client.onNotification = { [weak self, weak client] type, sessionID, payload in
            Task { @MainActor in
                guard let self, let client, self.api === client else { return }
                self.handleEvent(type: type, sessionID: sessionID, payload: payload)
            }
        }
        client.onServerRequest = { [weak self, weak client] id, method, params in
            Task { @MainActor in
                guard let self, let client, self.api === client else { return }
                self.handleServerRequest(id: id, method: method, params: params)
            }
        }
        client.onDisconnect = { [weak self, weak client] message in
            Task { @MainActor in
                guard let self, let client, self.api === client, !self.isDemo else { return }
                self.credentialRequests = []
                self.approvals = []
                self.clarifyRequest = nil
                self.isTurnActive = false
                self.turnStatus = "Disconnected · reconnect to check"
                self.streamingMessageID = nil
                for index in self.messages.indices { self.messages[index].isStreaming = false }
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
        let started = Date()
        if let caps = try? await api.request("gateway.capabilities", params: [:], timeout: 6) as? JSONObject {
            gatewayCapabilities = caps
            gatewayLatencyMs = max(1, Int(Date().timeIntervalSince(started) * 1000))
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
            guard self.api === api else { return }
            for row in result?.objects("profiles") ?? [] {
                guard let name = row.string("name"), let meta = row.dict("ui_meta")?.dict("niobium") else { continue }
                CastStore.shared.restore(profile: engine == .dsh ? "dsh:" + name : name, metadata: meta)
            }
            let parsed = result?.objects("profiles").compactMap(BotProfile.parse) ?? []
            bots = parsed.filter { !$0.hidden }.sorted { a, b in
                if a.isLive != b.isLive { return a.isLive }
                return (a.activityAt ?? .distantPast) > (b.activityAt ?? .distantPast)
            }
            lastSyncedAt = Date()
        } catch {
            guard self.api === api else { return }
            showError("Could not load bots. \(error.localizedDescription)")
        }
    }

    func loadGroups() async {
        guard !isDemo, let api else { return }
        do {
            let capabilities = try await api.request("groups.capabilities", params: [:]) as? JSONObject
            guard self.api === api else { return }
            groupsHaveDriver = capabilities?.bool("driver") ?? false
            guard capabilities?.array("methods")?.contains(where: { ($0 as? String) == "groups.list" }) == true else {
                groups = []
                return
            }
            let result = try await api.request("groups.list", params: ["limit": 100, "offset": 0]) as? JSONObject
            guard self.api === api else { return }
            groups = (result?.objects("rooms").compactMap(GroupRoom.parse) ?? []).filter { !$0.disbanded }
        } catch {
            // Group chats are an optional capability; a gateway may not host rooms.
            guard self.api === api else { return }
            groups = []
        }
    }

    func loadToolsets() async {
        guard !isDemo, let api else { return }
        let session = chatSessionID
        do {
            var params: JSONObject = [:]
            if let session { params["session_id"] = session }
            let result = try await api.request("tools.list", params: params) as? JSONObject
            guard self.api === api, chatSessionID == session else { return }
            toolsets = result?.objects("toolsets").compactMap(Toolset.parse) ?? []
        } catch {
            // Non-fatal; leave the previous catalog visible.
        }
    }

    // MARK: - Direct bot chat

    func openBot(_ bot: BotProfile) {
        historyHasMore = false; historyBeforeSeq = nil; isLoadingHistory = false
        chatEpoch += 1
        let epoch = chatEpoch
        demoTurnTask?.cancel()
        streamingMessageID = nil
        pendingDemoToolID = nil
        for request in credentialRequests { api?.respond(id: request.rpcID, result: ["value": ""]) }
        credentialRequests = []
        activeBot = bot
        messages = []
        approvals = []
        clarifyRequest = nil
        chatSessionID = nil
        chatRevision = 0
        chatGeneration = nil
        chatReasoning = nil
        modelChoices = []
        usage = nil
        companionTasks = []
        companionError = nil
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
        Task { await attachBot(bot, epoch: epoch) }
    }

    func closeBot() {
        chatEpoch += 1
        demoTurnTask?.cancel()
        streamingMessageID = nil
        pendingDemoToolID = nil
        for request in credentialRequests { api?.respond(id: request.rpcID, result: ["value": ""]) }
        credentialRequests = []
        activeBot = nil
        chatSessionID = nil
        messages = []
        companionTasks = []
        companionError = nil
        approvals = []
        clarifyRequest = nil
        isTurnActive = false
        isLoadingChat = false
    }

    private func attachBot(_ bot: BotProfile, epoch: Int) async {
        guard let api else { return }
        defer { if chatEpoch == epoch { isLoadingChat = false } }
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
            guard chatEpoch == epoch, self.api === api else { return }
            restoreChat(result)
            recoverPrompts(result)
            if engine == .dsh { _ = try await api.request("local.attach", params: ["session_id": chatSessionID ?? ""]) }
            guard chatEpoch == epoch, self.api === api else { return }
            await loadModels()
            guard chatEpoch == epoch, self.api === api else { return }
            await loadToolsets()
        } catch {
            guard chatEpoch == epoch, self.api === api else { return }
            showError("Could not open \(bot.title). \(error.localizedDescription)")
        }
    }

    private func restoreChat(_ result: JSONObject, fallbackSessionID: String? = nil) {
        chatSessionID = result.string("session_id") ?? fallbackSessionID
        chatRevision = result.int("revision") ?? 0
        chatGeneration = result.int("execution_generation")
        chatReasoning = result.dict("info")?.string("reasoning_effort")
        let inflight = result.dict("inflight")
        let streaming = inflight?.bool("streaming") ?? false
        isTurnActive = result.bool("running") ?? streaming
        turnStatus = ""
        streamingReasoning = ""
        streamingMessageID = nil
        messages = parseTranscript(result.objects("messages"))
        historyHasMore = result.bool("history_has_more") ?? false
        historyBeforeSeq = result.int("history_before_seq")
        isLoadingHistory = false
        let partial = inflight?.string("assistant") ?? ""
        if streaming || !partial.isEmpty {
            messages.append(ChatMessage(id: "inflight", role: .assistant, text: partial,
                                        timestamp: Date(), toolStatus: .completed, isStreaming: streaming))
            if streaming { streamingMessageID = "inflight" }
        }
    }

    func loadOlderMessages() async {
        guard let api, let session = chatSessionID, let before = historyBeforeSeq, historyHasMore, !isLoadingHistory else { return }
        isLoadingHistory = true
        do {
            let result = try await api.request("session.history", params: ["session_id": session, "before_seq": before]) as? JSONObject
            guard self.api === api, chatSessionID == session else { return }
            var seen = Set<String>()
            messages = (parseTranscript(result?.objects("messages") ?? []) + messages).filter { seen.insert($0.id).inserted }
            historyHasMore = result?.bool("has_more") ?? false
            historyBeforeSeq = result?.int("before_seq")
            isLoadingHistory = false
        } catch {
            guard self.api === api, chatSessionID == session else { return }
            isLoadingHistory = false; showError(error.localizedDescription)
        }
    }

    func send(_ rawText: String, attachment: UIImage? = nil) async {
        guard !isLoadingChat else { return }
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
            let receipt = try await api.request("prompt.submit", params: [
                "session_id": sessionID,
                "profile": bot.name,
                "text": displayed, "submission_id": UUID().uuidString, "clarify_batch": true
            ], timeout: 30) as? JSONObject
            if chatSessionID == sessionID, let generation = receipt?.int("execution_generation") { chatGeneration = generation }
        } catch {
            guard chatSessionID == sessionID, self.api === api else { return }
            isTurnActive = false
            showError("Message failed. \(error.localizedDescription)")
        }
    }

    func loadMCP() async {
        guard !isDemo, let api else { mcpServers = []; return }
        let profile = activeBot?.name ?? "default"
        guard let listed = try? await api.request("mcp.servers.list", params: ["profile": profile], timeout: 20) as? JSONObject else { return }
        let statuses = (try? await api.request("mcp.servers.status", params: ["profile": profile], timeout: 20) as? JSONObject)?.objects("servers") ?? []
        let byName = Dictionary(uniqueKeysWithValues: statuses.compactMap { row -> (String, JSONObject)? in
            guard let name = row.string("name") else { return nil }
            return (name, row)
        })
        mcpServers = listed.objects("servers").compactMap { MCPServerRow.parse($0, status: byName[$0.string("name") ?? ""]) }
    }

    func loadSkills() async {
        guard !isDemo, let api else { skills = [:]; return }
        let profile = activeBot?.name ?? "default"
        guard let result = try? await api.request("skills.manage", params: ["action": "list", "profile": profile], timeout: 20) as? JSONObject else { return }
        guard let raw = result.dict("skills") else { skills = [:]; return }
        var parsed: [String: [String]] = [:]
        for (key, value) in raw {
            if let names = value as? [String] { parsed[key] = names }
        }
        skills = parsed
    }

    func reloadSkills() async {
        guard !isDemo, let api else { return }
        let profile = activeBot?.name ?? "default"
        _ = try? await api.request("skills.reload", params: ["profile": profile], timeout: 30)
        await loadSkills()
        toastMessage("Skills reloaded")
    }

    func loadUsage() async {
        guard !isDemo, let api, let sessionID = chatSessionID else { usage = nil; return }
        guard let result = try? await api.request("session.stats", params: ["session_id": sessionID], timeout: 15) as? JSONObject else { return }
        guard chatSessionID == sessionID, self.api === api else { return }
        usage = UsageStats.parse(result)
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
        chatEpoch += 1
        let epoch = chatEpoch
        isLoadingChat = true
        defer { if chatEpoch == epoch { isLoadingChat = false } }
        do {
            let result = try await api.request("session.resume", params: [
                "session_id": row.sessionID,
                "profile": bot.name,
                "source": chatSource
            ], timeout: 90) as? JSONObject
            guard let result else { throw RPCError.malformed }
            guard chatEpoch == epoch, activeBot?.name == bot.name, self.api === api else { return }
            restoreChat(result, fallbackSessionID: row.sessionID)
            recoverPrompts(result)
            if engine == .dsh { _ = try await api.request("local.attach", params: ["session_id": chatSessionID ?? ""]) }
            await loadModels()
            guard chatEpoch == epoch, self.api === api else { return }
            toastMessage("Opened \(row.title)")
        } catch {
            guard chatEpoch == epoch, self.api === api else { return }
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
        guard chatSessionID == sessionID, self.api === api else { return }
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
            let snapshot = try await controlSnapshot()
            guard chatSessionID == sessionID else { return }
            var params: JSONObject = ["session_id": sessionID, "request_id": UUID().uuidString,
                                      "expected_revision": snapshot.int("revision") ?? chatRevision,
                                      "operation": "model", "payload": ["model": choice.model, "provider": choice.provider]]
            if let generation = snapshot.int("execution_generation") { params["expected_generation"] = generation }
            let result = try await api.request("session.mutate", params: params, timeout: 30) as? JSONObject
            guard chatSessionID == sessionID else { return }
            chatGeneration = result?.int("execution_generation") ?? chatGeneration
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

    func setReasoning(_ effort: String?) async {
        guard effort == nil || ["minimal", "low", "medium", "high", "xhigh", "max", "ultra"].contains(effort!),
              !isDemo, let api, let session = chatSessionID else { return }
        do {
            let snapshot = try await controlSnapshot()
            guard self.api === api, chatSessionID == session, let generation = snapshot.int("execution_generation") else { return }
            let result = try await api.request("session.mutate", params: ["session_id": session, "request_id": UUID().uuidString,
                "expected_revision": snapshot.int("revision") ?? chatRevision, "expected_generation": generation,
                "operation": "reasoning", "payload": ["effort": effort as Any? ?? NSNull()]]) as? JSONObject
            guard self.api === api, chatSessionID == session else { return }
            chatRevision = result?.int("revision") ?? chatRevision
            chatReasoning = effort
            toastMessage("Reasoning: \(effort ?? "profile default")")
        } catch { showError("Could not change reasoning. \(error.localizedDescription)") }
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
            do {
                let snapshot = try await controlSnapshot()
                var params: JSONObject = ["session_id": sessionID, "profile": bot.name]
                if let generation = snapshot.int("execution_generation") { params["execution_generation"] = generation }
                _ = try await api.request("session.interrupt", params: params)
                if chatSessionID == sessionID { isTurnActive = false }
            } catch { showError("Could not stop the turn. \(error.localizedDescription)") }
        }
    }

    // MARK: - Events

    private func handleEvent(type: String, sessionID: String, payload: JSONObject) {
        if !sessionID.isEmpty, sessionID != chatSessionID { return }
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
        case "message.segment":
            ensureStreamingMessage()
            if let id = streamingMessageID, let index = messages.firstIndex(where: { $0.id == id }) {
                if messages[index].text.isEmpty { messages[index].text = payload.string("text") ?? "" }
                messages[index].isStreaming = false
            }
            streamingMessageID = nil
            streamingReasoning = ""
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
        case "request.cancel":
            let id = JSONValueID.parse(payload["id"] ?? payload["request_id"])?.description ?? ""
            approvals.removeAll { $0.rpcID.description == id || $0.requestID == id }
            if clarifyRequest?.rpcID.description == id { clarifyRequest = nil }
            credentialRequests.removeAll { $0.rpcID.description == id }
        case "approval.cancelled":
            approvals.removeAll { $0.sessionID == sessionID }
        case "background.complete", "btw.complete":
            let text = payload.string("text") ?? "Background task finished"
            addActivity(.message, title: "Background result", detail: String(text.prefix(140)), bot: activeBot?.name ?? "")
            toastMessage("Background task finished")
        case "session.usage":
            if let live = payload.dict("usage"), let parsed = UsageStats.parse(live) { usage = parsed }
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
            addActivity(.message, title: "Reply from \(bot.title)", detail: String((payload.string("summary") ?? finalText).prefix(120)), bot: bot.name)
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
        if !sessionID.isEmpty, chatSessionID != sessionID {
            if api?.declinesNotShown == true { api?.respondError(id: id, code: 4404, message: "This window does not show that session.") }
            return
        }
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
            let title: String
            switch method {
            case "sudo": title = "Host password requested"
            case "secret": title = params.string("env_var") ?? "Secret requested"
            case "vault.unlock_prompt": title = "Unlock " + (params.string("display_name") ?? "credential vault")
            case "vault.save_login": title = "Save a login on your host"
            default: title = "Verification code"
            }
            let detail = params.string("prompt") ?? params.string("hint") ?? params.string("command") ?? params.string("site") ?? "Your Hermes host is waiting for this value."
            guard credentialRequests.count < 16 else { api?.respond(id: id, result: ["value": ""]); return }
            credentialRequests.append(.init(rpcID: id, sessionID: sessionID, method: method, title: title, detail: detail, login: method == "vault.save_login", connectionID: connection?.id))
        default:
            api?.respondError(id: id, message: "Niobium does not handle \(method) yet.")
        }
    }

    func saveAppearance(_ member: CastMember, profile: String) async -> Bool {
        if isDemo { CastStore.shared.select(member, for: profile); return true }
        guard let api else { showError("Connect a gateway before saving this character."); return false }
        do {
            let inventory = try await api.request("profiles.list", params: ["include_sessions": false]) as? JSONObject
            guard let row = inventory?.objects("profiles").first(where: { $0.string("name") == profile }), self.api === api else { throw RPCError.transport("The profile or connection changed. Refresh and try again.") }
            var metadata = row.dict("ui_meta")?.dict("niobium") ?? [:]
            var appearance: JSONObject = ["archetype": member.id, "companion": member.companion, "companion_name": member.companionName]
            if let recipe = member.recipe {
                appearance["recipe"] = try JSONSerialization.jsonObject(with: JSONEncoder().encode(recipe))
            }
            metadata["appearance"] = appearance
            let revision = row.dict("ui_meta_revisions")?.int("niobium") ?? 0
            let result = try await api.request("profiles.configure", params: ["name": profile,
                "ui_meta": ["niobium": metadata], "ui_meta_expected_revisions": ["niobium": revision]]) as? JSONObject
            guard self.api === api, result?.bool("ok") == true else { throw RPCError.transport("This Pixel changed elsewhere. Refresh before saving again.") }
            CastStore.shared.select(member, for: engine == .dsh ? "dsh:" + profile : profile)
            toastMessage("Character saved")
            return true
        } catch { showError("Could not save the character. \(error.localizedDescription)"); return false }
    }

    func resolveCredential(_ request: CredentialRequest, value: String) {
        guard request.connectionID == connection?.id, credentialRequests.contains(where: { $0.id == request.id }) else { return }
        api?.respond(id: request.rpcID, result: ["value": value])
        credentialRequests.removeAll { $0.id == request.id }
    }

    private func controlSnapshot() async throws -> JSONObject {
        guard let api, let session = chatSessionID, let profile = activeBot?.name else { throw RPCError.notConnected }
        guard let snapshot = try await api.request("session.resume", params: ["session_id": session, "profile": profile,
                                                                            "source": chatSource, "omit_messages": true]) as? JSONObject else { throw RPCError.malformed }
        guard chatSessionID == session, activeBot?.name == profile else { throw RPCError.transport("This chat has changed. Try again in the current chat.") }
        chatRevision = snapshot.int("revision") ?? chatRevision
        chatGeneration = snapshot.int("execution_generation")
        chatReasoning = snapshot.dict("info")?.string("reasoning_effort")
        return snapshot
    }
    private func recoverPrompts(_ snapshot: JSONObject) {
        let session = snapshot.string("session_id") ?? chatSessionID ?? ""
        approvals = snapshot.objects("prompts").filter { $0.string("kind") == "approval" }.map {
            var row = parseApproval($0, sessionID: session, rpcID: .string(""))
            row.promptID = $0.string("prompt_id")
            row.executionGeneration = $0.int("execution_generation")
            return row
        }
        if let pending = snapshot.dict("pending_approval"), approvals.isEmpty {
            var row = parseApproval(pending, sessionID: session, rpcID: .string(""))
            row.recovered = true
            approvals = [row]
        }
        let clarify = snapshot.objects("prompts").filter { $0.string("kind") == "clarify" }
        let questions = clarify.compactMap { row -> ClarifyQuestion? in
            guard let id = row.string("prompt_id"), row.int("execution_generation") != nil else { return nil }
            return ClarifyQuestion(id: id, question: row.string("question") ?? "More information requested",
                                   choices: row.array("choices") as? [String] ?? [], multiSelect: row.bool("multi_select") ?? false)
        }
        let generations = Dictionary(clarify.compactMap { row -> (String, Int)? in
            guard let id = row.string("prompt_id"), let generation = row.int("execution_generation") else { return nil }
            return (id, generation)
        }, uniquingKeysWith: { _, new in new })
        clarifyRequest = questions.isEmpty ? nil : ClarifyRequest(rpcID: .string(""), sessionID: session,
                                                                 questions: questions, promptGenerations: generations)
        if engine == .dsh {
            for request in snapshot.objects("local_requests") {
                guard let payload = request.dict("payload"), let id = request.string("rpcId") else { continue }
                if payload.string("type") == "approval/requested" {
                    handleServerRequest(id: .string(id), method: "approval", params: ["session_id": session,
                        "request_id": id, "command": payload.string("toolName") ?? "Tool",
                        "description": payload.string("reason") ?? "DSH requests permission to run this tool.",
                        "tool_name": payload.string("toolName") ?? "tool", "choices": ["once", "deny"]])
                } else if payload.string("type") == "question/requested" {
                    let questions: [JSONObject] = payload.objects("questions").map { q in ["qid": q.string("id") ?? "",
                        "question": q.string("question") ?? "", "choices": q.objects("options").compactMap { $0.string("label") },
                        "multi_select": q.bool("multiSelect") ?? false] }
                    handleServerRequest(id: .string(id), method: "clarify", params: ["session_id": session, "questions": questions])
                }
            }
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
        guard approval.choices.contains(choice) else { return }
        if isDemo {
            approvals.removeAll { $0.id == approval.id }
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
        guard let api, chatSessionID == approval.sessionID else { return }
        if let promptID = approval.promptID, let generation = approval.executionGeneration {
            Task {
                do {
                    _ = try await api.request("approval.respond", params: ["session_id": approval.sessionID, "execution_generation": generation, "prompt_id": promptID, "choice": choice])
                    guard self.api === api, chatSessionID == approval.sessionID else { return }
                    approvals.removeAll { $0.id == approval.id }
                    toastMessage("Approval sent: \(choice)")
                } catch { showError("Approval was not accepted. \(error.localizedDescription)") }
            }
        } else if approval.recovered {
            Task {
                do {
                    _ = try await api.request("approval.respond", params: ["session_id": approval.sessionID, "request_id": approval.requestID, "choice": choice])
                    guard self.api === api, chatSessionID == approval.sessionID else { return }
                    approvals.removeAll { $0.id == approval.id }
                } catch { showError("Approval was not accepted. \(error.localizedDescription)") }
            }
        } else {
            api.respond(id: approval.rpcID, result: ["choice": choice])
            approvals.removeAll { $0.id == approval.id }
            toastMessage("Approval sent: \(choice)")
        }
    }

    func resolveClarify(_ answers: [String: String], selections: [String: [String]] = [:]) {
        guard let request = clarifyRequest else { return }
        guard let api, chatSessionID == request.sessionID else { return }
        let flattened = answers.merging(selections.mapValues { $0.joined(separator: ", ") }, uniquingKeysWith: { _, new in new })
        guard request.questions.allSatisfy({ !(flattened[$0.id] ?? "").trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }) else {
            showError("Answer every question before sending. You can dismiss it and leave the task waiting.")
            return
        }
        if request.promptGenerations.isEmpty {
            var wire: JSONObject = flattened
            if engine == .dsh { for (id, values) in selections { wire[id] = values } }
            api.respond(id: request.rpcID, result: ["answers": wire, "outcome": "submitted"])
            clarifyRequest = nil
            return
        }
        Task {
            do {
                for question in request.questions {
                    guard self.api === api, chatSessionID == request.sessionID,
                          let generation = request.promptGenerations[question.id],
                          clarifyRequest?.promptGenerations[question.id] == generation else { return }
                    _ = try await api.request("clarify.respond", params: ["session_id": request.sessionID,
                        "execution_generation": generation, "prompt_id": question.id, "answer": flattened[question.id]!])
                    guard self.api === api, chatSessionID == request.sessionID,
                          clarifyRequest?.promptGenerations[question.id] == generation else { return }
                    clarifyRequest?.questions.removeAll { $0.id == question.id }
                    clarifyRequest?.promptGenerations.removeValue(forKey: question.id)
                }
                if clarifyRequest?.questions.isEmpty == true { clarifyRequest = nil }
            } catch { showError("The answer was not accepted. \(error.localizedDescription)") }
        }
    }

    func cancelClarify() {
        guard let request = clarifyRequest, let api, chatSessionID == request.sessionID else { return }
        // Recovered prompts have no cancellable RPC request. Keep them pending on the host.
        guard request.promptGenerations.isEmpty else { return }
        api.respond(id: request.rpcID, result: ["answers": JSONObject(), "outcome": "cancelled"])
        clarifyRequest = nil
    }

    // MARK: - Groups

    func openGroup(_ room: GroupRoom) {
        groupEpoch += 1
        groupPollTask?.cancel()
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
        groupEpoch += 1
        groupPollTask?.cancel(); groupPollTask = nil
        activeGroup = nil
        roomEvents = []
    }

    func refreshGroup() async {
        guard !isDemo, let api, let room = activeGroup else { return }
        let epoch = groupEpoch
        do {
            let result = try await api.request("groups.log", params: [
                "room_id": room.roomID, "since_seq": 0, "limit": 500
            ]) as? JSONObject
            guard groupEpoch == epoch, self.api === api else { return }
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
        let epoch = groupEpoch
        let replies = room.members.prefix(2).enumerated().map { index, member in
            let text = index == 0
                ? "On it. I’ll take the first pass and report back with a recommendation."
                : "I’ll check the sources and flag anything that looks off."
            return (member, text)
        }
        Task { @MainActor in
            for (offset, pair) in replies.enumerated() {
                try? await Task.sleep(for: .milliseconds(900 + offset * 500))
                guard !Task.isCancelled, groupEpoch == epoch else { return }
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


    func draftPixel(prompt: String) async throws -> PixelDraft {
        try await PixelBuilder.shared.draft(for: prompt, api: isDemo ? nil : api)
    }

    func buildPixel(draft: PixelDraft) async throws -> BotProfile {
        let spec = draft.spec
        if let error = spec.validationError { throw RPCError.transport(error) }
        let slug = spec.name
        guard !bots.contains(where: { $0.name == slug }) else { throw RPCError.transport("That Pixel name already exists. Choose another name.") }
        guard let base = CastMember.all.first(where: { $0.id == spec.character.archetype }) else { throw RPCError.malformed }
        let member = CastMember(id: base.id, name: base.name, portrait: base.portrait,
                                companion: spec.companion.kind, companionName: spec.companion.name,
                                accentHex: base.accentHex, recipe: spec.character.recipe)
        if isDemo {
            let bot = BotProfile(name: slug, title: spec.title, role: spec.role, provider: "", model: "Demo",
                                 skillCount: spec.skills.count, canonicalSessionID: "demo-\(slug)",
                                 canonicalResolvedID: nil, lastSessionID: nil,
                                 preview: "\(spec.companion.name) and I are ready. What trouble are we solving?", activityAt: Date(),
                                 workerActive: false, isDefault: false, hidden: false,
                                 accentHex: member.accentHex, hasAvatar: false)
            bots.insert(bot, at: 0)
            CastStore.shared.select(member, for: slug)
            return bot
        }
        guard let api else { throw RPCError.notConnected }
        if engine == .dsh {
            let data = try JSONEncoder().encode(spec)
            let object = try JSONSerialization.jsonObject(with: data) as? JSONObject ?? [:]
            let result = try await api.request("pixel.create", params: ["spec": object,
                "enabled_toolsets": draft.enabledToolsets, "briefing": spec.soul + spec.policyBriefing,
                "accent": member.accentHex, "builder": draft.usedModel ? "model" : "template"], timeout: 120) as? JSONObject
            guard result?.bool("ok") == true else { throw RPCError.transport("The local engine did not confirm creation.") }
            CastStore.shared.select(member, for: "dsh:" + slug)
            await refreshRoster()
            guard let bot = bots.first(where: { $0.name == slug }) else { throw RPCError.transport("Created the Pixel. Refresh the roster before trying again.") }
            return bot
        }
        // Clone installed skill files, then pin the reviewed tools, skills and MCP access.
        let created = try await api.request("profiles.create", params: [
            "name": slug, "description": spec.role, "soul": spec.soul + spec.policyBriefing,
            "clone_from": "default", "clone_all": false, "no_skills": false, "clone_channels": false
        ], timeout: 120) as? JSONObject
        guard created?.bool("ok") == true else { throw RPCError.transport("The gateway did not confirm creation.") }
        do {
            _ = try await api.request("config.set", params: ["profile": slug, "key": "approvals.mode", "value": "manual"])
            let inventory = try await api.request("profiles.describe", params: ["name": slug]) as? JSONObject
            let installed = Set(inventory?.objects("skills").compactMap { $0.string("name") } ?? [])
            guard Set(spec.skills).isSubset(of: installed) else { throw RPCError.transport("A selected skill was not copied by the gateway.") }
            let data = try JSONEncoder().encode(spec)
            let meta = try JSONSerialization.jsonObject(with: data) as? JSONObject ?? [:]
            let result = try await api.request("profiles.configure", params: [
                "name": slug, "description": spec.role, "soul": spec.soul + spec.policyBriefing,
                "enabled_toolsets": draft.enabledToolsets,
                "enabled_mcp_servers": [],
                "disabled_skills": Array(installed.subtracting(spec.skills)).sorted(),
                "ui_meta": ["hermes-bots": ["title": spec.title, "description": spec.role, "color": member.accentHex, "hidden": false],
                            "niobium": ["schema_version": 2, "pixel_spec": meta, "builder": draft.usedModel ? "model" : "template"]]
            ], timeout: 120) as? JSONObject
            guard result?.bool("ok") == true else { throw RPCError.transport("The gateway could not apply all profile sections.") }
            _ = try await api.request("config.set", params: ["profile": slug, "key": "reasoning", "value": spec.reasoning, "scope": "global"])
        } catch {
            await refreshRoster()
            throw RPCError.transport("Created \(slug), but setup is incomplete: \(error.localizedDescription) Open the profile to repair it; creation was not retried.")
        }
        CastStore.shared.select(member, for: slug)
        await refreshRoster()
        guard let bot = bots.first(where: { $0.name == slug }) else { throw RPCError.transport("Created \(slug), but the roster has not refreshed. Refresh before trying again.") }
        toastMessage(draft.usedModel ? "Built \(bot.title) with AI" : "Built \(bot.title) from a template")
        return bot
    }

    func loadCompanions() async {
        guard !isDemo else { companionTasks = []; return }
        guard let api, let session = chatSessionID, let profile = activeBot?.name else { return }
        companionsLoading = true
        defer { companionsLoading = false }
        do {
            let result = try await api.request("subagent.list", params: ["session_id": session, "profile": profile]) as? JSONObject
            guard chatSessionID == session, activeBot?.name == profile else { return }
            var children = result?.objects("subagents").compactMap(CompanionTask.parse) ?? []
            for failed in result?.objects("delegations") ?? [] {
                guard let id = failed.string("delegation_id") else { continue }
                children.append(.init(id: "\(id)-\(failed.int("task_index") ?? 0)", goal: failed.string("goal") ?? "Delegated task",
                                      status: failed.string("status") ?? "failed", lastTool: failed.string("error") ?? "Task failed.",
                                      toolCount: 0, canSteer: false, failed: true))
            }
            companionTasks = children; companionError = nil
        } catch {
            guard chatSessionID == session, activeBot?.name == profile else { return }
            companionError = "Companion work is unavailable on this gateway. \(error.localizedDescription)"
        }
    }
    func companionTranscript(_ id: String) async throws -> String {
        guard let api, let session = chatSessionID, let profile = activeBot?.name else { throw RPCError.notConnected }
        let result = try await api.request("subagent.tail", params: ["session_id": session, "profile": profile, "subagent_id": id]) as? JSONObject
        guard chatSessionID == session, activeBot?.name == profile else { throw RPCError.transport("This chat has changed. Reopen companion tasks.") }
        return result?.string("text") ?? ""
    }
    func controlCompanion(_ id: String, action: String, text: String) async throws {
        guard ["steer", "interrupt"].contains(action), let api, let session = chatSessionID, let profile = activeBot?.name else { throw RPCError.notConnected }
        var params: JSONObject = ["session_id": session, "profile": profile, "subagent_id": id]
        if action == "steer" { params["text"] = text }
        let result = try await api.request("subagent." + action, params: params) as? JSONObject
        guard chatSessionID == session, activeBot?.name == profile else { return }
        if action == "steer" {
            guard result?.string("status") == "queued" else { throw RPCError.transport("The helper could not accept this correction.") }
            toastMessage("Correction queued for the helper")
        } else {
            toastMessage(result?.bool("found") == true ? "Stop requested" : "The helper has already finished")
        }
        await loadCompanions()
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
            guard !Task.isCancelled else { return }
            let toolID = "demo-tool-\(UUID().uuidString)"
            pendingDemoToolID = toolID
            messages.append(ChatMessage(id: toolID, role: .tool, text: "", timestamp: Date(),
                                        toolName: "terminal", toolArgs: "{\n  \"command\": \"rm -rf ./build\"\n}",
                                        toolResult: nil, toolStatus: .running,
                                        toolPreview: "rm -rf ./build"))
            try? await Task.sleep(for: .milliseconds(500))
            guard !Task.isCancelled else { return }
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
            guard !Task.isCancelled else { return }
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
