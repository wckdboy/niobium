package io.sovware.niobium

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class NbState(
    val needsConnection: Boolean = true,
    val connection: Connection? = null,
    val connecting: Boolean = false,
    val connected: Boolean = false,
    val demo: Boolean = false,
    val gatewayVersion: String = "",
    val bots: List<BotProfile> = emptyList(),
    val groups: List<GroupRoom> = emptyList(),
    val toolsets: List<Toolset> = emptyList(),
    val activities: List<ActivityItem> = emptyList(),
    val activeBot: BotProfile? = null,
    val activeGroup: GroupRoom? = null,
    val messages: List<ChatMessage> = emptyList(),
    val roomEvents: List<RoomEvent> = emptyList(),
    val sessionId: String? = null,
    val turnActive: Boolean = false,
    val turnStatus: String = "",
    val approvals: List<Approval> = emptyList(),
    val clarify: ClarifyRequest? = null,
    val loadingChat: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
    val toast: String? = null,
    val lastSync: Long? = null
)

class NiobiumViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = SecurePrefs(app)
    private var gateway: GatewayClient? = null
    private var readySignal: CompletableDeferred<Unit>? = null
    private var streamingId: String? = null
    private var groupPoll: Job? = null
    private var toastJob: Job? = null
    private var demoTurn: Job? = null
    private var launchBotName: String? = null
    private var bootstrapped = false

    var state by mutableStateOf(NbState())
        private set

    fun bootstrap(demoRequested: Boolean = false, botName: String? = null) {
        if (bootstrapped) return
        bootstrapped = true
        launchBotName = botName
        val saved = prefs.loadConnection()
        when {
            demoRequested -> enterDemo(botName)
            saved != null && saved.isDemo -> enterDemo(botName)
            saved != null -> {
                state = state.copy(needsConnection = false, connection = saved)
                connect(saved.url, saved.token, saved.name, remember = false)
            }
            else -> state = state.copy(needsConnection = true)
        }
    }

    fun connect(rawUrl: String, token: String, name: String, remember: Boolean = true) {
        if (state.connecting) return
        viewModelScope.launch {
            val normalized = normalizeUrl(rawUrl)
            if (normalized == null) {
                state = state.copy(error = "Enter a full gateway address, like http://192.168.1.20:9119")
                return@launch
            }
            if (token.isBlank()) {
                state = state.copy(error = "A gateway access token is required. Use the session token from hermes serve.")
                return@launch
            }
            state = state.copy(connecting = true, error = null)
            try {
                val status = withContext(Dispatchers.IO) { GatewayClient.probe(normalized, token) }
                val ready = CompletableDeferred<Unit>()
                readySignal = ready
                val client = GatewayClient(object : GatewayClient.Listener {
                    override fun onReady(payload: JSONObject) {
                        ready.complete(Unit)
                    }

                    override fun onNotification(type: String, sessionId: String, payload: JSONObject) {
                        viewModelScope.launch(Dispatchers.Main) { handleEvent(type, sessionId, payload) }
                    }

                    override fun onServerRequest(id: Any, method: String, params: JSONObject) {
                        viewModelScope.launch(Dispatchers.Main) { handleServerRequest(id, method, params) }
                    }

                    override fun onClosed(reason: String) {
                        viewModelScope.launch(Dispatchers.Main) {
                            if (state.demo) return@launch
                            state = state.copy(connected = false, error = reason)
                        }
                    }
                })
                gateway = client
                client.connect(normalized, token)
                withTimeout(16_000) { ready.await() }
                runCatching {
                    client.request("client.capabilities", JSONObject().put("server_requests", true), 5_000)
                }
                val saved = Connection(name.ifBlank { "My Hermes" }, normalized, token)
                if (remember) prefs.saveConnection(saved)
                state = state.copy(
                    needsConnection = false, connection = saved, connecting = false, connected = true,
                    demo = false, gatewayVersion = status.s("release_date") ?: status.s("version") ?: "Hermes",
                    error = null
                )
                refreshAll()
                launchBotName?.let { nameToOpen ->
                    state.bots.firstOrNull { it.name == nameToOpen }?.let { openBot(it) }
                }
                launchBotName = null
                showToast("Connected to ${saved.name}")
            } catch (e: Exception) {
                gateway?.disconnect(silent = true)
                gateway = null
                state = state.copy(connecting = false, connected = false,
                    error = e.message ?: "Could not connect to the gateway.")
            }
        }
    }

    fun reconnect() {
        state.connection?.let { connect(it.url, it.token, it.name, remember = false) }
    }

    fun disconnect(forget: Boolean = false) {
        gateway?.disconnect(silent = true)
        gateway = null
        state = state.copy(connected = false, activeBot = null, activeGroup = null,
            bots = emptyList(), groups = emptyList(), toolsets = emptyList(), messages = emptyList())
        if (forget) {
            prefs.clear()
            state = NbState(needsConnection = true)
        }
    }

    fun enterDemo(botName: String? = null) {
        gateway?.disconnect(silent = true)
        gateway = null
        state = NbState(
            needsConnection = false,
            connection = Connection("Nb Studio", "demo://niobium", "", isDemo = true),
            connected = true, demo = true, gatewayVersion = "Demo workspace",
            bots = DemoData.bots, groups = DemoData.groups, toolsets = DemoData.toolsets,
            activities = DemoData.activity, lastSync = System.currentTimeMillis()
        )
        prefs.saveConnection(Connection("Nb Studio", "demo://niobium", "", isDemo = true))
        botName?.let { n -> state.bots.firstOrNull { it.name == n }?.let { openBot(it) } }
    }

    fun runDemoAction() {
        viewModelScope.launch {
            delay(600)
            send("Run a clean build and report blockers")
        }
    }

    fun leaveDemoToConnect() {
        gateway?.disconnect(silent = true)
        gateway = null
        prefs.clear()
        state = NbState(needsConnection = true)
    }

    suspend fun refreshAll() {
        if (state.demo || !state.connected) return
        state = state.copy(refreshing = true)
        refreshRoster()
        loadGroups()
        loadToolsets()
        state = state.copy(refreshing = false, lastSync = System.currentTimeMillis())
    }

    fun refreshAllAsync() {
        viewModelScope.launch { refreshAll() }
    }

    suspend fun refreshRoster() {
        val client = gateway ?: return
        try {
            val result = client.request("profiles.list", JSONObject().put("include_sessions", true))
            val parsed = result.objects("profiles").mapNotNull(BotProfile::parse).filter { !it.hidden }
                .sortedWith(compareByDescending<BotProfile> { it.isLive }
                    .thenByDescending { it.activityAt ?: 0L })
            state = state.copy(bots = parsed, lastSync = System.currentTimeMillis())
        } catch (e: Exception) {
            state = state.copy(error = "Could not load bots. ${e.message}")
        }
    }

    suspend fun loadGroups() {
        val client = gateway ?: return
        try {
            val caps = client.request("groups.capabilities")
            val canList = caps.a("methods")?.let { arr ->
                (0 until arr.length()).any { arr.optString(it) == "groups.list" }
            } ?: false
            if (!canList) {
                state = state.copy(groups = emptyList())
                return
            }
            val result = client.request("groups.list", JSONObject().put("limit", 100).put("offset", 0))
            state = state.copy(groups = result.objects("rooms").mapNotNull(GroupRoom::parse).filter { !it.disbanded })
        } catch (_: Exception) {
            state = state.copy(groups = emptyList())
        }
    }

    suspend fun loadToolsets() {
        val client = gateway ?: return
        try {
            val params = JSONObject()
            state.sessionId?.let { params.put("session_id", it) }
            state.activeBot?.let { params.put("profile", it.name) }
            val result = client.request("tools.list", params)
            state = state.copy(toolsets = result.objects("toolsets").mapNotNull { j ->
                if (j.s("name").isNullOrBlank()) null else Toolset.parse(j)
            })
        } catch (_: Exception) {
        }
    }

    // ── Direct chats ─────────────────────────────────────────────────────────

    fun openBot(bot: BotProfile) {
        state = state.copy(activeBot = bot, messages = emptyList(), sessionId = null,
            loadingChat = true, approvals = emptyList(), clarify = null, turnActive = false, turnStatus = "")
        viewModelScope.launch {
            if (state.demo) {
                delay(260)
                state = state.copy(messages = DemoData.messages(bot), sessionId = "demo-${bot.name}", loadingChat = false)
                return@launch
            }
            attachBot(bot)
        }
    }

    private suspend fun attachBot(bot: BotProfile) {
        val client = gateway ?: return
        try {
            var stored = bot.canonicalSessionId
            if (stored == null) {
                val listed = client.request("session.list", JSONObject().put("title", "Bot Chat").put("profile", bot.name))
                stored = listed.objects("sessions").firstOrNull()?.s("id")
            }
            val result = if (stored != null) {
                client.request("session.resume", JSONObject()
                    .put("session_id", stored).put("profile", bot.name).put("inline_images", false), 90_000)
            } else {
                client.request("session.create", JSONObject()
                    .put("profile", bot.name).put("title", "Bot Chat").put("source", "desktop"), 90_000)
            }
            val sessionId = result.s("session_id")
            val messages = parseTranscript(result.objects("messages"))
            val inflight = result.o("inflight")
            val withInflight = if (inflight != null) {
                messages + ChatMessage("inflight", "assistant", inflight.s("assistant") ?: "",
                    timestamp = System.currentTimeMillis())
            } else messages
            state = state.copy(sessionId = sessionId, messages = withInflight,
                loadingChat = false, turnActive = inflight?.b("streaming") ?: false)
            loadToolsets()
        } catch (e: Exception) {
            state = state.copy(loadingChat = false, error = "Could not open ${bot.title}. ${e.message}")
        }
    }

    fun closeBot() {
        state = state.copy(activeBot = null, sessionId = null, messages = emptyList(), approvals = emptyList(), turnActive = false)
    }

    fun send(text: String) {
        val value = text.trim()
        if (value.isEmpty()) return
        if (!state.demo && state.sessionId == null) {
            showError("This chat isn’t attached to a session yet.")
            return
        }
        state = state.copy(messages = state.messages + ChatMessage(UUID.randomUUID().toString(), "user", value),
            turnActive = true, turnStatus = "")
        if (state.demo) {
            demoTurn?.cancel()
            demoTurn = viewModelScope.launch { demoReply(value) }
            return
        }
        val client = gateway ?: return
        val sessionId = state.sessionId ?: return
        val bot = state.activeBot ?: return
        viewModelScope.launch {
            try {
                client.request("prompt.submit", JSONObject()
                    .put("session_id", sessionId).put("profile", bot.name).put("text", value), 30_000)
            } catch (e: Exception) {
                state = state.copy(turnActive = false, error = "Message failed. ${e.message}")
            }
        }
    }

    fun stopTurn() {
        if (state.demo) {
            demoTurn?.cancel()
            streamingId?.let { id ->
                state = state.copy(messages = state.messages.map { if (it.id == id) it.copy(streaming = false) else it })
            }
            streamingId = null
            state = state.copy(turnActive = false)
            return
        }
        val client = gateway ?: return
        val sessionId = state.sessionId ?: return
        val bot = state.activeBot ?: return
        viewModelScope.launch {
            runCatching { client.request("session.interrupt", JSONObject()
                .put("session_id", sessionId).put("profile", bot.name)) }
            state = state.copy(turnActive = false)
        }
    }

    // ── Events ───────────────────────────────────────────────────────────────

    private fun handleEvent(type: String, sessionId: String, payload: JSONObject) {
        if (sessionId.isNotBlank() && state.sessionId != null && sessionId != state.sessionId) return
        when (type) {
            "message.start" -> {
                ensureStreaming()
                state = state.copy(turnActive = true)
            }
            "message.delta" -> {
                val text = payload.s("text") ?: return
                if (text.isNotEmpty()) {
                    ensureStreaming()
                    appendStreaming(text)
                    state = state.copy(turnActive = true)
                }
            }
            "message.interim" -> payload.s("text")?.takeIf { it.isNotBlank() }?.let { appendStreaming("\n\n$it") }
            "reasoning.delta", "thinking.delta" -> {
                val id = streamingId ?: return
                state = state.copy(messages = state.messages.map {
                    if (it.id == id) it.copy(reasoning = (it.reasoning ?: "") + (payload.s("text") ?: "")) else it
                })
            }
            "reasoning.available" -> {
                val id = streamingId ?: return
                state = state.copy(messages = state.messages.map {
                    if (it.id == id) it.copy(reasoning = payload.s("text") ?: it.reasoning) else it
                })
            }
            "message.complete" -> completeTurn(payload)
            "tool.start" -> startTool(payload)
            "tool.complete" -> completeTool(payload)
            "status.update" -> state = state.copy(turnStatus = payload.s("text") ?: "")
            "error" -> state = state.copy(turnActive = false,
                error = payload.s("message") ?: "The agent hit an error.")
            "approval.cancelled" -> state = state.copy(approvals = emptyList())
            "background.complete", "btw.complete" -> {
                addActivity(ActivityItem(UUID.randomUUID().toString(), "message", "Background result",
                    (payload.s("text") ?: "Finished").take(140), botName = state.activeBot?.name ?: ""))
                showToast("Background task finished")
            }
        }
    }

    private fun ensureStreaming() {
        if (streamingId != null && state.messages.any { it.id == streamingId }) return
        val id = UUID.randomUUID().toString()
        streamingId = id
        state = state.copy(messages = state.messages + ChatMessage(id, "assistant", "", streaming = true))
    }

    private fun appendStreaming(text: String) {
        ensureStreaming()
        val id = streamingId ?: return
        state = state.copy(messages = state.messages.map {
            if (it.id == id) it.copy(text = it.text + text, streaming = true) else it
        })
    }

    private fun completeTurn(payload: JSONObject) {
        val text = payload.s("text") ?: ""
        val id = streamingId
        state = state.copy(turnActive = false, turnStatus = "", messages = state.messages.map {
            if (it.id == id) it.copy(
                text = it.text.ifEmpty { text },
                streaming = false
            ) else it
        } + if (id == null && text.isNotBlank()) listOf(
            ChatMessage(UUID.randomUUID().toString(), "assistant", text)) else emptyList())
        streamingId = null
        if (payload.s("status") == "error" || payload.s("error") != null) {
            state = state.copy(error = payload.s("error") ?: "The turn ended with an error.")
        }
        state.activeBot?.let { bot ->
            addActivity(ActivityItem(UUID.randomUUID().toString(), "message", "Reply from ${bot.title}",
                text.take(120), botName = bot.name))
        }
    }

    private fun startTool(payload: JSONObject) {
        val id = payload.s("tool_id") ?: UUID.randomUUID().toString()
        if (state.messages.any { it.id == id }) return
        val name = payload.s("name") ?: "tool"
        val args = jstr(payload.opt("args"))
        val preview = payload.s("preview") ?: ""
        state = state.copy(messages = state.messages + ChatMessage(id, "tool", timestamp = System.currentTimeMillis(),
            toolName = name, toolArgs = args, toolStatus = ToolStatus.RUNNING, toolPreview = preview))
        state.activeBot?.let {
            addActivity(ActivityItem(UUID.randomUUID().toString(), "tool", name, preview.ifBlank { "Running" }, botName = it.name))
        }
    }

    private fun completeTool(payload: JSONObject) {
        val id = payload.s("tool_id") ?: ""
        val summary = payload.s("summary") ?: payload.s("result_text") ?: ""
        val result = jstr(payload.opt("result"))
        val duration = payload.d("duration_s")
        val resultObject = payload.o("result")
        val failed = payload.b("is_error") == true || resultObject?.b("is_error") == true || resultObject?.b("success") == false
        val existing = state.messages.any { it.id == id }
        state = state.copy(messages = if (existing) state.messages.map {
            if (it.id == id) it.copy(toolStatus = if (failed) ToolStatus.FAILED else ToolStatus.DONE,
                toolResult = summary.ifBlank { result }, toolDuration = duration,
                toolPreview = summary.ifBlank { it.toolPreview }) else it
        } else state.messages + ChatMessage(id.ifBlank { UUID.randomUUID().toString() }, "tool",
            timestamp = System.currentTimeMillis(), toolName = payload.s("name") ?: "tool",
            toolArgs = jstr(payload.opt("args")), toolResult = summary.ifBlank { result },
            toolStatus = if (failed) ToolStatus.FAILED else ToolStatus.DONE, toolPreview = summary, toolDuration = duration))
        state.activeBot?.let {
            addActivity(ActivityItem(UUID.randomUUID().toString(), "tool", "${payload.s("name") ?: "Tool"} completed",
                summary.ifBlank { "Finished" }.take(120), botName = it.name))
        }
    }

    private fun handleServerRequest(id: Any, method: String, params: JSONObject) {
        val sessionId = params.s("session_id") ?: ""
        if (sessionId.isNotBlank() && state.sessionId != null && sessionId != state.sessionId) return
        when (method) {
            "approval" -> {
                val choices = params.a("choices")?.let { arr -> (0 until arr.length()).map { arr.optString(it) } }
                    ?: listOf("once", "deny")
                state = state.copy(approvals = state.approvals + Approval(id,
                    params.s("request_id") ?: "", sessionId, params.s("command") ?: "",
                    params.s("description") ?: "", params.s("tool_name") ?: "terminal", choices))
                state.activeBot?.let {
                    addActivity(ActivityItem(UUID.randomUUID().toString(), "approval", "Approval needed",
                        params.s("command") ?: "", botName = it.name))
                }
            }
            "clarify" -> {
                val questions = params.objects("questions").mapNotNull { q ->
                    val qid = q.s("qid") ?: return@mapNotNull null
                    ClarifyQuestion(qid, q.s("question") ?: "",
                        q.a("choices")?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList(),
                        q.b("multi_select") ?: false)
                }
                state = state.copy(clarify = ClarifyRequest(id, sessionId, questions))
            }
            "sudo", "secret", "vault.code", "vault.unlock_prompt", "vault.save_login" ->
                gateway?.respond(id, JSONObject().put("value", ""))
            else -> gateway?.respondError(id, "Niobium does not handle $method yet.")
        }
    }

    fun resolveApproval(approval: Approval, choice: String) {
        state = state.copy(approvals = state.approvals.filterNot { it.id == approval.id })
        if (state.demo) {
            if (choice == "deny") {
                showToast("Command denied")
                state = state.copy(messages = state.messages + ChatMessage(UUID.randomUUID().toString(),
                    "assistant", "Understood — I’ve left the build directory untouched."))
            } else {
                showToast("Approved: $choice")
                state = state.copy(messages = state.messages.map {
                    if (it.toolStatus == ToolStatus.RUNNING) it.copy(toolStatus = ToolStatus.DONE,
                        toolResult = "Removed 48 stale build artifacts.", toolDuration = 0.8) else it
                } + ChatMessage(UUID.randomUUID().toString(), "assistant",
                    "Approved. The tool ran with **$choice** scope."))
            }
            state = state.copy(turnActive = false)
            return
        }
        gateway?.respond(approval.rpcId, JSONObject().put("choice", choice))
        showToast("Approval sent: $choice")
    }

    fun resolveClarify(answers: Map<String, String>) {
        val req = state.clarify ?: return
        state = state.copy(clarify = null)
        val obj = JSONObject()
        answers.forEach { (k, v) -> obj.put(k, v) }
        gateway?.respond(req.rpcId, JSONObject().put("answers", obj).put("outcome", "submitted"))
    }

    // ── Groups ───────────────────────────────────────────────────────────────

    fun openGroup(room: GroupRoom) {
        state = state.copy(activeGroup = room, roomEvents = emptyList())
        if (state.demo) {
            state = state.copy(roomEvents = DemoData.events(room))
            return
        }
        refreshGroup()
        groupPoll?.cancel()
        groupPoll = viewModelScope.launch {
            while (true) {
                delay(3000)
                refreshGroup()
            }
        }
    }

    fun closeGroup() {
        groupPoll?.cancel(); groupPoll = null
        state = state.copy(activeGroup = null, roomEvents = emptyList())
    }

    fun refreshGroup() {
        val client = gateway ?: return
        val room = state.activeGroup ?: return
        if (state.demo) return
        viewModelScope.launch {
            runCatching {
                client.request("groups.log", JSONObject().put("room_id", room.roomId)
                    .put("since_seq", 0).put("limit", 500))
            }.onSuccess { result ->
                state = state.copy(roomEvents = result.objects("events").mapNotNull(RoomEvent::parse))
            }
        }
    }

    fun sendGroup(text: String) {
        val value = text.trim()
        if (value.isEmpty()) return
        val room = state.activeGroup ?: return
        if (state.demo) {
            val next = (state.roomEvents.lastOrNull()?.seq ?: 0) + 1
            state = state.copy(roomEvents = state.roomEvents + RoomEvent(next, room.roomId,
                UUID.randomUUID().toString(), "message.user", "user", "user", "You", value,
                System.currentTimeMillis() / 1000, null, null))
            viewModelScope.launch {
                room.members.take(2).forEachIndexed { index, member ->
                    delay(900L + index * 500)
                    val seq = (state.roomEvents.lastOrNull()?.seq ?: 0) + 1
                    state = state.copy(roomEvents = state.roomEvents + RoomEvent(seq, room.roomId,
                        UUID.randomUUID().toString(), "message.member", "member", member.id, member.name,
                        if (index == 0) "On it. I’ll take the first pass and report back with a recommendation."
                        else "I’ll check the sources and flag anything that looks off.",
                        System.currentTimeMillis() / 1000, null, null))
                }
            }
            return
        }
        val client = gateway ?: return
        viewModelScope.launch {
            val eventId = UUID.randomUUID().toString()
            try {
                client.request("groups.send", JSONObject()
                    .put("room_id", room.roomId)
                    .put("profile", state.activeBot?.name ?: state.bots.firstOrNull()?.name ?: "default")
                    .put("event_id", eventId)
                    .put("payload", JSONObject().put("text", value).put("thread_id", eventId)))
                refreshGroup()
            } catch (e: Exception) {
                state = state.copy(error = "Group send failed. ${e.message}")
            }
        }
    }

    fun createGroup(name: String, members: List<BotProfile>, onCreated: () -> Unit) {
        if (name.isBlank() || members.size < 2) return
        if (state.demo) {
            val room = GroupRoom(UUID.randomUUID().toString(), name.trim(), members.map {
                GroupMember(it.name, it.name, it.name, it.title)
            }, System.currentTimeMillis() / 1000, 0, "Room created")
            state = state.copy(groups = listOf(room) + state.groups)
            onCreated()
            return
        }
        val client = gateway ?: return
        viewModelScope.launch {
            try {
                val roster = JSONArray()
                members.forEach { bot ->
                    roster.put(JSONObject().put("member_id", bot.name).put("profile", bot.name)
                        .put("handle", bot.name).put("display_name", bot.title)
                        .put("target", JSONObject().put("kind", "local").put("profile", bot.name)))
                }
                val result = client.request("groups.create", JSONObject()
                    .put("room_id", UUID.randomUUID().toString()).put("name", name.trim()).put("members", roster))
                result.o("room")?.let(GroupRoom::parse)?.let { room ->
                    state = state.copy(groups = listOf(room) + state.groups)
                    onCreated()
                }
            } catch (e: Exception) {
                state = state.copy(error = "Could not create room. ${e.message}")
            }
        }
    }

    // ── Bots and tools ───────────────────────────────────────────────────────

    fun createBot(name: String, title: String, role: String, color: String, onCreated: () -> Unit) {
        val slug = name.trim().lowercase().replace(" ", "-")
        if (slug.isBlank()) return
        if (state.demo) {
            val bot = BotProfile(slug, title.ifBlank { slug.replaceFirstChar { it.uppercase() } }, role,
                "", "Hermes", 12, "demo-$slug", "Say hello.", System.currentTimeMillis() / 1000,
                false, false, false, color, false)
            state = state.copy(bots = listOf(bot) + state.bots)
            onCreated()
            return
        }
        val client = gateway ?: return
        viewModelScope.launch {
            try {
                client.request("profiles.create", JSONObject().put("name", slug)
                    .put("description", role).put("no_skills", false), 120_000)
                client.request("profiles.configure", JSONObject().put("name", slug)
                    .put("description", role)
                    .put("ui_meta", JSONObject().put("hermes-bots", JSONObject()
                        .put("title", title.ifBlank { slug.replaceFirstChar { it.uppercase() } })
                        .put("description", role).put("color", color).put("hidden", false))))
                refreshRoster()
                onCreated()
            } catch (e: Exception) {
                state = state.copy(error = "Could not create bot. ${e.message}")
            }
        }
    }

    fun toggleToolset(toolset: Toolset) {
        if (state.demo) {
            state = state.copy(toolsets = state.toolsets.map {
                if (it.name == toolset.name) it.copy(enabled = !it.enabled) else it
            })
            return
        }
        val client = gateway ?: return
        val sessionId = state.sessionId
        if (sessionId == null) {
            showError("Open a pixel chat first — toolsets are toggled per live session.")
            return
        }
        val bot = state.activeBot ?: return
        viewModelScope.launch {
            try {
                client.request("tools.configure", JSONObject()
                    .put("session_id", sessionId).put("profile", bot.name)
                    .put("action", if (toolset.enabled) "disable" else "enable")
                    .put("names", JSONArray().put(toolset.name)))
                loadToolsets()
                showToast("${toolset.name} ${if (toolset.enabled) "disabled" else "enabled"} for ${bot.title}")
            } catch (e: Exception) {
                state = state.copy(error = "Could not update tools. ${e.message}")
            }
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun parseTranscript(rows: List<JSONObject>): List<ChatMessage> {
        val output = mutableListOf<ChatMessage>()
        rows.forEachIndexed { index, row ->
            if (row.s("display_kind") == "hidden") return@forEachIndexed
            val role = row.s("role") ?: "assistant"
            val timestamp = epochDate(row.d("timestamp"))?.times(1000) ?: System.currentTimeMillis()
            val rowId = row.i("row_id")
            val text = row.s("text") ?: row.optString("content").takeIf { it.isNotBlank() && it != "null" } ?: ""
            when (role) {
                "tool" -> output.add(ChatMessage(row.s("tool_call_id") ?: "row-${rowId ?: index}",
                    "tool", timestamp = timestamp, toolName = row.s("name") ?: "tool",
                    toolArgs = jstr(row.opt("args")), toolResult = jstr(row.opt("content")),
                    toolStatus = ToolStatus.DONE, toolPreview = row.s("context"), rowId = rowId))
                "assistant" -> if (text.isNotBlank()) output.add(ChatMessage("row-${rowId ?: index}",
                    "assistant", text, timestamp, reasoning = row.s("reasoning"), rowId = rowId))
                "user" -> output.add(ChatMessage("row-${rowId ?: index}", "user", text, timestamp, rowId = rowId))
                "system" -> if (text.isNotBlank()) output.add(ChatMessage("row-${rowId ?: index}", "system", text, timestamp, rowId = rowId))
            }
        }
        return output
    }

    private fun addActivity(item: ActivityItem) {
        state = state.copy(activities = (listOf(item) + state.activities).take(80))
    }

    fun showToast(message: String) {
        if (message.isBlank()) return
        state = state.copy(toast = message)
        toastJob?.cancel()
        toastJob = viewModelScope.launch {
            delay(2800)
            if (state.toast == message) state = state.copy(toast = null)
        }
    }

    fun showError(message: String) {
        state = state.copy(error = message)
        toastJob?.cancel()
        toastJob = viewModelScope.launch {
            delay(7000)
            if (state.error == message) state = state.copy(error = null)
        }
    }

    fun clearError() { state = state.copy(error = null) }
    fun clearToast() { state = state.copy(toast = null) }

    private fun normalizeUrl(raw: String): String? {
        var text = raw.trim()
        if (text.isEmpty()) return null
        if (!text.contains("://")) text = "http://$text"
        val url = text.trimEnd('/').toHttpUrlOrNull() ?: return null
        if (url.scheme != "http" && url.scheme != "https") return null
        return text.trimEnd('/')
    }

    // ── Demo ─────────────────────────────────────────────────────────────────

    private suspend fun demoReply(prompt: String) {
        delay(450)
        val id = UUID.randomUUID().toString()
        streamingId = id
        state = state.copy(messages = state.messages + ChatMessage(id, "assistant", "",
            reasoning = "Reading the request against the current workspace and the team roster.", streaming = true))
        val needsAction = listOf("build", "clean", "delete", "deploy").any { prompt.lowercase().contains(it) }
        if (needsAction) {
            delay(400)
            val toolId = "demo-tool-${UUID.randomUUID()}"
            state = state.copy(messages = state.messages + ChatMessage(toolId, "tool",
                timestamp = System.currentTimeMillis(), toolName = "terminal",
                toolArgs = "{\n  \"command\": \"rm -rf ./build\"\n}",
                toolStatus = ToolStatus.RUNNING, toolPreview = "rm -rf ./build"))
            delay(500)
            state = state.copy(approvals = state.approvals + Approval("demo-${UUID.randomUUID()}", "demo-approval",
                state.sessionId ?: "demo", "rm -rf ./build",
                "Remove the generated build directory before a clean rebuild.", "terminal",
                listOf("once", "session", "always", "deny")))
            return
        }
        val reply = demoText(prompt)
        val chunked = reply.chunked(5)
        chunked.forEach { chunk ->
            delay(26)
            appendStreaming(chunk)
        }
        state = state.copy(messages = state.messages.map { if (it.id == id) it.copy(streaming = false) else it },
            turnActive = false)
        streamingId = null
        state.activeBot?.let {
            addActivity(ActivityItem(UUID.randomUUID().toString(), "message", "Reply from ${it.title}",
                reply.take(120), botName = it.name))
        }
    }

    private fun demoText(prompt: String): String {
        val lower = prompt.lowercase()
        if (lower.contains("status") || lower.contains("morning") || lower.contains("update")) return """
            Three things need you today.

            **1 · Build is green.** Forge finished the clean build and the release notes are ready for review. The only open thread is the signing profile for the TestFlight build.

            **2 · Scout found a signal.** Two competitors shipped agent memory this week. I saved the teardown in *Research › Memory landscape* — the useful difference is their retrieval UI, not the storage layer.

            **3 · One decision.** Athena recommends we ship Pixels before custom routines. I agree: the roster is the habit, routines are the power feature.

            Say the word and I’ll open the release checklist.
        """.trimIndent()
        if (lower.contains("research") || lower.contains("find") || lower.contains("search")) return """
            I ran a pass across the web and your saved sessions.

            The strongest angle is **quiet capability**: people don’t want another chat box, they want a team that keeps working while they’re away. Lead with the roster, then show one agent doing real work end to end.

            I’ve put the strongest three sources and a draft outline in the workspace. Want me to turn it into a one-page brief?
        """.trimIndent()
        return """
            I’m on it. I’ll keep the work scoped to what you asked and surface anything that needs a decision rather than guessing.

            I’ve noted the context in this bot’s memory, so the next turn starts where we left off.
        """.trimIndent()
    }
}
