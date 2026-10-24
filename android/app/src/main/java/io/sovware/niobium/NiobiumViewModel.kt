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
import io.sovware.niobium.runtime.LocalRuntime
import io.sovware.niobium.runtime.LocalRuntimeState
import io.sovware.niobium.runtime.RuntimePhase

enum class AgentEngine { HERMES, DSH }

data class ChatContext(val owner: String, val connection: Int, val chat: Int, val engine: AgentEngine, val profile: String?, val session: String?)

data class NbState(
    val engine: AgentEngine = AgentEngine.HERMES,
    val engineStatus: String = "",
    val needsConnection: Boolean = true,
    val connection: Connection? = null,
    val connecting: Boolean = false,
    val connected: Boolean = false,
    val demo: Boolean = false,
    val gatewayVersion: String = "",
    val bots: List<BotProfile> = emptyList(),
    val groups: List<GroupRoom> = emptyList(),
    val groupsHaveDriver: Boolean = false,
    val toolsets: List<Toolset> = emptyList(),
    val routines: List<Routine> = emptyList(),
    val companionTasks: List<CompanionTask> = emptyList(),
    val companionError: String? = null,
    val companionsLoading: Boolean = false,
    val gatewayLatencyMs: Int? = null,
    val modelChoices: List<ModelChoice> = emptyList(),
    val sessions: List<SessionRow> = emptyList(),
    val usage: UsageStats? = null,
    val skills: Map<String, List<String>> = emptyMap(),
    val mcpServers: List<MCPServerRow> = emptyList(),
    val sessionRevision: Int = 0,
    val sessionGeneration: Int? = null,
    val sessionReasoning: String? = null,
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
    val credentialRequests: List<CredentialRequest> = emptyList(),
    val loadingChat: Boolean = false,
    val sendingChat: Boolean = false,
    val deliveryUnconfirmed: Boolean = false,
    val deliveryAccepted: Boolean = false,
    val submissionMessageId: String? = null,
    val historyHasMore: Boolean = false,
    val historyBeforeSeq: Int? = null,
    val loadingHistory: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
    val toast: String? = null,
    val lastSync: Long? = null
) {
    val canSubmitChat: Boolean
        get() = activeBot != null && !loadingChat && !sendingChat && !deliveryUnconfirmed && (demo || (connected && sessionId != null))
}

class NiobiumViewModel(app: Application, private val managedEngine: AgentEngine? = null) : AndroidViewModel(app) {
    var engineHub: NiobiumEngineHub? = null
    private val prefs = SecurePrefs(app)
    private var gateway: GatewayClient? = null
    private var chatSource = "gui"
    private var readySignal: CompletableDeferred<Unit>? = null
    private var streamingId: String? = null
    private var groupPoll: Job? = null
    private var toastJob: Job? = null
    private var demoTurn: Job? = null
    private var launchBotName: String? = null
    private var bootstrapped = false
    private var declinesNotShown = false
    private var connectionEpoch = 0
    private var chatEpoch = 0
    private var groupEpoch = 0
    private var groupSessionIds = emptySet<String>()
    private val roomReceipts = RoomReceiptBook(app)
    private val providerPrefs = app.getSharedPreferences("niobium", android.content.Context.MODE_PRIVATE)
    private var localToken: String? = null
    private val deliveryPrefs = app.getSharedPreferences("niobium.chat.deliveries", android.content.Context.MODE_PRIVATE)
    private val pendingChats = PendingChatDeliveries({ deliveryPrefs.getString("pending", null) }, { deliveryPrefs.edit().putString("pending", it).commit() })
    private val textDrafts = linkedMapOf<String,String>()
    val chatDraftScope: String? get() = deliveryScope()
    fun draftText(scope: String?): String = scope?.let { textDrafts[it] }.orEmpty()
    fun retainDraft(scope: String?, value: String) {
        if (scope == null) return
        if (value.isEmpty()) textDrafts.remove(scope) else { textDrafts.remove(scope); textDrafts[scope] = value }
        while (textDrafts.size > 64) textDrafts.remove(textDrafts.keys.first())
    }
    private val chatOwner = UUID.randomUUID().toString()
    val chatContext: ChatContext get() = ChatContext(chatOwner, connectionEpoch, chatEpoch, state.engine, state.activeBot?.id, state.sessionId)
    private fun deliveryScope(snapshot: NbState = state): String? {
        val session = snapshot.sessionId ?: return null
        val bot = snapshot.activeBot ?: return null
        val connection = snapshot.connection ?: return null
        return PendingChatDeliveries.scope(snapshot.engine == AgentEngine.DSH, connection.url, connection.token, bot.name, session)
    }
    fun acknowledgeUnconfirmedDelivery() {
        if (state.sendingChat) return
        val scope = deliveryScope() ?: return
        val context = chatContext
        state = state.copy(sendingChat = true)
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { pendingChats.resolve(scope) }
                if (chatContext == context) state = state.copy(deliveryUnconfirmed = false, deliveryAccepted = false, submissionMessageId = null)
            } catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; if (chatContext == context) showError(e.message ?: "Delivery receipt could not be cleared.") }
            finally { if (chatContext == context) state = state.copy(sendingChat = false) }
        }
    }

    var state by mutableStateOf(NbState(engine = managedEngine ?: AgentEngine.HERMES))
        private set

    init {
        viewModelScope.launch {
            LocalRuntime.state.collect { runtime ->
                if (state.engine != AgentEngine.DSH || state.demo) return@collect
                state = state.copy(engineStatus = when (runtime.phase) {
                    RuntimePhase.INSTALLING -> "Preparing Linux · " + runtime.progress + "%"
                    RuntimePhase.STARTING -> "Starting your Pixels…"
                    RuntimePhase.RUNNING -> "Running"
                    RuntimePhase.STOPPING -> "Stopping local tasks…"
                    RuntimePhase.ERROR -> runtime.message ?: "Local engine stopped."
                    RuntimePhase.OFF -> "Start the local engine under Engines."
                })
                if (runtime.phase == RuntimePhase.RUNNING && runtime.cookie != localToken && !state.connecting) connectLocal(runtime)
                else if (runtime.phase in listOf(RuntimePhase.OFF, RuntimePhase.ERROR, RuntimePhase.STOPPING)) {
                    localToken = null
                    if (state.connected) disconnect()
                    if (runtime.phase == RuntimePhase.ERROR) showError(runtime.message ?: "Local engine stopped.")
                }
            }
        }
    }

    fun bootstrap(demoRequested: Boolean = false, botName: String? = null, initialConnection: Connection? = null) {
        if (bootstrapped) return
        bootstrapped = true
        launchBotName = botName
        val saved = initialConnection ?: prefs.loadConnection()
        when {
            demoRequested -> enterDemo(botName)
            managedEngine == null && (providerPrefs.getString("agent-provider", null) == "dsh" || providerPrefs.getBoolean("local-mode", false)) -> useLocal()
            saved != null && saved.isDemo -> enterDemo(botName)
            saved != null -> {
                state = state.copy(needsConnection = false, connection = saved)
                connect(saved.url, saved.token, saved.name, remember = initialConnection != null)
            }
            else -> state = state.copy(needsConnection = true)
        }
    }

    fun connect(rawUrl: String, token: String, name: String, remember: Boolean = true, local: Boolean = false, cookie: String? = null) {
        engineHub?.let { hub ->
            val owner = if (local) hub.local else hub.hermes
            if (this !== owner) { hub.activate(if (local) AgentEngine.DSH else AgentEngine.HERMES); owner.connect(rawUrl, token, name, remember, local, cookie); return }
        }
        if (state.connecting) return
        viewModelScope.launch {
            val normalized = normalizeUrl(rawUrl)
            if (normalized == null) {
                state = state.copy(error = "Enter a full HTTPS gateway address, or HTTP on literal loopback.")
                return@launch
            }
            if (token.isBlank()) {
                state = state.copy(error = "A gateway access token is required. Use the session token from hermes serve.")
                return@launch
            }
            val epoch = ++connectionEpoch
            gateway?.disconnect(silent = true)
            chatEpoch++; groupEpoch++; groupPoll?.cancel(); demoTurn?.cancel()
            state = NbState(engine = if (local) AgentEngine.DSH else AgentEngine.HERMES,
                needsConnection = false, connection = state.connection)
            state = state.copy(connecting = true, credentialRequests = emptyList(), approvals = emptyList(), clarify = null, error = null)
            try {
                val status = withContext(Dispatchers.IO) { GatewayClient.probe(normalized, token, cookie) }
                if (epoch != connectionEpoch) return@launch
                val ready = CompletableDeferred<Unit>()
                readySignal = ready
                val client = GatewayClient(object : GatewayClient.Listener {
                    override fun onReady(payload: JSONObject) {
                        ready.complete(Unit)
                    }

                    override fun onNotification(type: String, sessionId: String, payload: JSONObject) {
                        viewModelScope.launch(Dispatchers.Main) { if (epoch == connectionEpoch) handleEvent(type, sessionId, payload) }
                    }

                    override fun onServerRequest(id: Any, method: String, params: JSONObject) {
                        viewModelScope.launch(Dispatchers.Main) { if (epoch == connectionEpoch) handleServerRequest(id, method, params) }
                    }

                    override fun onClosed(reason: String) {
                        viewModelScope.launch(Dispatchers.Main) {
                            if (state.demo || epoch != connectionEpoch) return@launch
                            streamingId = null
                            state = state.copy(connected = false, credentialRequests = emptyList(),
                                approvals = emptyList(), clarify = null, turnActive = false,
                                turnStatus = "Disconnected · reconnect to check",
                                messages = state.messages.map { it.copy(streaming = false) }, error = reason)
                        }
                    }
                })
                gateway = client
                client.connect(normalized, token, cookie)
                withTimeout(16_000) { ready.await() }
                if (epoch != connectionEpoch) { client.disconnect(silent = true); return@launch }
                runCatching {
                    declinesNotShown = client.request("client.capabilities", JSONObject().put("server_requests", true), 5_000).optBoolean("declines_not_shown")
                }
                runCatching {
                    val runtime = client.request("runtime.describe", JSONObject(), 10_000)
                    val sourcesArray = runtime.o("session_create")?.a("sources")
                    val sources = if (sourcesArray != null) (0 until sourcesArray.length()).map { sourcesArray.optString(it) } else emptyList()
                    chatSource = if ("gui" in sources) "gui" else if ("tui" in sources) "tui" else "gui"
                }
                val probeStart = android.os.SystemClock.elapsedRealtime()
                val caps = runCatching { client.request("gateway.capabilities", JSONObject(), 6_000) }.getOrNull()
                if (epoch != connectionEpoch || gateway !== client) return@launch
                val latency = if (caps != null) (android.os.SystemClock.elapsedRealtime() - probeStart).toInt().coerceAtLeast(1) else null
                val saved = Connection(name.ifBlank { "My Hermes" }, normalized, token)
                if (remember && !local) prefs.saveConnection(saved)
                if (engineHub == null) providerPrefs.edit().putString("agent-provider", if (local) "dsh" else "hermes").remove("local-mode").apply()
                state = state.copy(
                    needsConnection = false, connection = saved, connecting = false, connected = true,
                    demo = false, gatewayLatencyMs = latency, gatewayVersion = status.s("release_date") ?: status.s("version") ?: "Hermes",
                    error = null
                )
                refreshAll()
                launchBotName?.let { nameToOpen ->
                    state.bots.firstOrNull { it.name == nameToOpen }?.let { openBot(it) }
                }
                launchBotName = null
                showToast("Connected to ${saved.name}")
            } catch (e: Exception) {
                if (epoch != connectionEpoch) return@launch
                if (local) localToken = null
                gateway?.disconnect(silent = true)
                gateway = null
                state = state.copy(connecting = false, connected = false,
                    error = e.message ?: "Could not connect to the gateway.")
            }
        }
    }

    fun reconnect() {
        if (state.engine == AgentEngine.DSH) useLocal()
        else state.connection?.let { connect(it.url, it.token, it.name, remember = false) }
    }

    fun useLocal() {
        engineHub?.let { hub -> if (this !== hub.local) { hub.activate(AgentEngine.DSH); hub.local.useLocal(); return } }
        providerPrefs.edit().putBoolean("dsh-enabled", true).apply()
        if (state.engine != AgentEngine.DSH || state.demo || state.needsConnection) {
            disconnect()
            state = NbState(engine = AgentEngine.DSH, needsConnection = false, engineStatus = "Preparing your local engine…")
        }
        if (engineHub == null) providerPrefs.edit().putString("agent-provider", "dsh").remove("local-mode").apply()
        val runtime = LocalRuntime.state.value
        if (runtime.phase == RuntimePhase.RUNNING && !state.connected && !state.connecting) connectLocal(runtime)
        else if (runtime.phase in listOf(RuntimePhase.OFF, RuntimePhase.ERROR)) LocalRuntime.start(getApplication())
    }

    private fun connectLocal(runtime: LocalRuntimeState) {
        val origin = runtime.origin ?: return
        val key = runtime.cookie ?: return
        localToken = key
        connect(origin + "/niobium", key, "On device · DSH", remember = false, local = true, cookie = key)
    }

    fun useHermes() {
        engineHub?.let { hub -> if (this !== hub.hermes) { hub.activate(AgentEngine.HERMES); hub.hermes.useHermes(); return } }
        if (state.engine == AgentEngine.HERMES && state.connected && !state.demo) return
        val saved = prefs.loadConnection()?.takeUnless { it.isDemo }
        disconnect()
        if (engineHub == null) providerPrefs.edit().putString("agent-provider", "hermes").remove("local-mode").apply()
        state = NbState(needsConnection = saved == null, connection = saved)
        saved?.let { connect(it.url, it.token, it.name, remember = false) }
    }

    fun saveLocalCredential(ref: String, key: String, onSaved: () -> Unit) {
        val client = gateway ?: return
        if (state.engine != AgentEngine.DSH) return
        viewModelScope.launch {
            try {
                client.request("local.credential", JSONObject().put("ref", ref).put("value", key), 20_000)
                if (gateway === client) { loadModels(); onSaved(); showToast("Provider key saved on this device") }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { if (gateway === client) showError(e.message ?: "Could not save provider key.") }
        }
    }

    fun disconnect(forget: Boolean = false) {
        connectionEpoch++; chatEpoch++; groupEpoch++
        groupPoll?.cancel(); demoTurn?.cancel()
        gateway?.disconnect(silent = true)
        gateway = null
        state = state.copy(connected = false, activeBot = null, activeGroup = null,
            bots = emptyList(), groups = emptyList(), toolsets = emptyList(), messages = emptyList(),
            connecting = false, sessionId = null, modelChoices = emptyList(), approvals = emptyList(), clarify = null,
            credentialRequests = emptyList(), turnActive = false, activities = emptyList(), routines = emptyList(), companionTasks = emptyList())
        if (forget) {
            if (state.engine == AgentEngine.HERMES) prefs.clear()
            providerPrefs.edit().remove("agent-provider").remove("local-mode").apply()
            state = NbState(needsConnection = true)
        }
    }

    fun prepareStoppedLocal(botName: String? = null) {
        launchBotName = botName
        state = state.copy(needsConnection = false, engineStatus = "Stopped")
    }

    fun stopLocal() {
        engineHub?.let { hub -> if (this !== hub.local) { hub.local.stopLocal(); return } }
        providerPrefs.edit().putBoolean("dsh-enabled", false).apply()
        if (state.engine == AgentEngine.DSH) disconnect()
        LocalRuntime.stop(getApplication())
    }

    fun enterDemo(botName: String? = null) {
        engineHub?.let { hub -> if (this !== hub.hermes) { hub.activate(AgentEngine.HERMES); hub.hermes.enterDemo(botName); return } }
        connectionEpoch++; chatEpoch++; groupEpoch++
        gateway?.disconnect(silent = true)
        gateway = null
        state = NbState(
            needsConnection = false,
            connection = Connection("Nb Studio", "demo://niobium", "", isDemo = true),
            connected = true, demo = true, gatewayVersion = "Demo workspace",
            bots = DemoData.bots, groups = DemoData.groups, toolsets = DemoData.toolsets,
            routines = DemoData.routines, activities = DemoData.activity, lastSync = System.currentTimeMillis()
        )
        providerPrefs.edit().putString("agent-provider", "hermes").remove("local-mode").apply()
        botName?.let { n -> state.bots.firstOrNull { it.name == n }?.let { openBot(it) } }
    }

    fun runDemoAction() {
        viewModelScope.launch {
            delay(600)
            send("Run a clean build and report blockers")
        }
    }

    fun leaveDemoToConnect() {
        useHermes()
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

    override fun onCleared() {
        gateway?.disconnect(silent = true)
        gateway = null
        super.onCleared()
    }

    suspend fun refreshRoster() {
        val client = gateway ?: return
        try {
            val result = client.request("profiles.list", JSONObject().put("include_sessions", true))
            if (gateway !== client) return
            for (row in result.objects("profiles")) {
                val name = row.s("name") ?: continue
                val meta = row.o("ui_meta")?.o("niobium") ?: continue
                CastStore.restore(getApplication(), if (state.engine == AgentEngine.DSH) "dsh:" + name else name, meta)
            }
            val parsed = result.objects("profiles").mapNotNull(BotProfile::parse).filter { !it.hidden }
                .sortedWith(compareByDescending<BotProfile> { it.isLive }
                    .thenByDescending { it.activityAt ?: 0L })
            state = state.copy(bots = parsed, lastSync = System.currentTimeMillis())
        } catch (e: Exception) {
            if (gateway !== client) return
            state = state.copy(error = "Could not load bots. ${e.message}")
        }
    }

    suspend fun loadGroups() {
        val client = gateway ?: return
        try {
            val caps = client.request("groups.capabilities")
            if (gateway !== client) return
            state = state.copy(groupsHaveDriver = caps.optBoolean("driver"))
            val canList = caps.a("methods")?.let { arr ->
                (0 until arr.length()).any { arr.optString(it) == "groups.list" }
            } ?: false
            if (!canList) {
                state = state.copy(groups = emptyList())
                return
            }
            val result = client.request("groups.list", JSONObject().put("limit", 100).put("offset", 0))
            if (gateway !== client) return
            state = state.copy(groups = result.objects("rooms").mapNotNull(GroupRoom::parse).filter { !it.disbanded })
        } catch (_: Exception) {
            if (gateway !== client) return
            state = state.copy(groups = emptyList())
        }
    }

    suspend fun loadToolsets() {
        val client = gateway ?: return
        val session = state.sessionId
        try {
            val params = JSONObject()
            session?.let { params.put("session_id", it) }
            val result = client.request("tools.list", params)
            if (gateway !== client || state.sessionId != session) return
            state = state.copy(toolsets = result.objects("toolsets").mapNotNull { j ->
                if (j.s("name").isNullOrBlank()) null else Toolset.parse(j)
            })
        } catch (_: Exception) {
        }
    }

    // ── Direct chats ─────────────────────────────────────────────────────────

    fun openBot(bot: BotProfile) {
        if (state.activeBot?.id == bot.id && state.loadingChat) return
        val epoch = ++chatEpoch
        demoTurn?.cancel()
        streamingId = null
        state.credentialRequests.forEach { gateway?.respond(it.rpcId, JSONObject().put("value", "")) }
        state = state.copy(activeBot = bot, messages = emptyList(), sessionId = null, companionTasks = emptyList(), companionError = null,
            sessionRevision = 0, sessionGeneration = null, sessionReasoning = null, modelChoices = emptyList(), usage = null, credentialRequests = emptyList(),
            loadingChat = true, sendingChat = false, deliveryUnconfirmed = false, deliveryAccepted = false, submissionMessageId = null, historyHasMore = false, historyBeforeSeq = null, loadingHistory = false,
            approvals = emptyList(), clarify = null, turnActive = false, turnStatus = "")
        viewModelScope.launch {
            if (state.demo) {
                delay(260)
                if (chatEpoch != epoch) return@launch
                state = state.copy(messages = DemoData.messages(bot), sessionId = "demo-${bot.name}", loadingChat = false)
                return@launch
            }
            attachBot(bot, epoch)
        }
    }

    private suspend fun attachBot(bot: BotProfile, epoch: Int) {
        if (chatEpoch != epoch) return
        val client = gateway ?: run { state = state.copy(loadingChat = false); return }
        try {
            var stored = bot.canonicalSessionId
            if (stored == null) {
                val listed = client.request("session.list", JSONObject().put("title", "Bot Chat").put("profile", bot.name))
                if (chatEpoch != epoch || gateway !== client) return
                stored = listed.objects("sessions").firstOrNull()?.s("id")
            }
            val result = if (stored != null) {
                client.request("session.resume", JSONObject()
                    .put("session_id", stored).put("profile", bot.name).put("source", chatSource), 90_000)
            } else {
                client.request("session.create", JSONObject()
                    .put("profile", bot.name).put("title", "Bot Chat").put("source", chatSource), 90_000)
            }
            if (chatEpoch != epoch || gateway !== client) return
            restoreChat(result)
            recoverPrompts(result)
            if (state.engine == AgentEngine.DSH) client.request("local.attach", JSONObject().put("session_id", state.sessionId))
            if (chatEpoch != epoch || gateway !== client) return
            state = state.copy(loadingChat = false)
            loadToolsets()
            loadModels()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            if (chatEpoch != epoch || gateway !== client) return
            state = state.copy(loadingChat = false, sessionId = null, turnActive = false,
                error = "Could not open ${bot.title}. ${e.message}")
        }
    }

    fun closeBot() {
        chatEpoch++
        demoTurn?.cancel()
        streamingId = null
        state.credentialRequests.forEach { gateway?.respond(it.rpcId, JSONObject().put("value", "")) }
        state = state.copy(activeBot = null, sessionId = null, credentialRequests = emptyList(), companionTasks = emptyList(), companionError = null, sessionRevision = 0, modelChoices = emptyList(),
            usage = null, sendingChat = false, deliveryUnconfirmed = false, deliveryAccepted = false, submissionMessageId = null, messages = emptyList(), approvals = emptyList(), clarify = null, turnActive = false, loadingChat = false)
    }

    private fun restoreChat(snapshot: JSONObject, fallbackSessionId: String? = null) {
        val inflight = snapshot.o("inflight")
        val streaming = inflight?.optBoolean("streaming", false) ?: false
        val partial = inflight?.s("assistant") ?: ""
        val restored = parseTranscript(snapshot.objects("messages"))
        streamingId = if (streaming) "inflight" else null
        state = state.copy(sessionId = snapshot.s("session_id") ?: fallbackSessionId,
            sessionRevision = snapshot.optInt("revision"), sessionReasoning = snapshot.o("info")?.s("reasoning_effort"),
            sessionGeneration = snapshot.optInt("execution_generation").takeIf { snapshot.has("execution_generation") },
            messages = restored + if (streaming || partial.isNotEmpty()) listOf(
                ChatMessage("inflight", "assistant", partial, streaming = streaming)) else emptyList(),
            turnActive = snapshot.optBoolean("running", streaming), turnStatus = "")
        val pending = if (state.demo) false else deliveryScope()?.let { scope -> runCatching { pendingChats.pending(scope) }.getOrElse { true } } ?: false
        state = state.copy(sendingChat = false, deliveryUnconfirmed = pending, deliveryAccepted = false, submissionMessageId = null)
        state = state.copy(historyHasMore = snapshot.optBoolean("history_has_more"), historyBeforeSeq = snapshot.i("history_before_seq"), loadingHistory = false)
    }

    fun loadOlderMessages() {
        val client = gateway ?: return
        val session = state.sessionId ?: return
        val before = state.historyBeforeSeq ?: return
        if (!state.historyHasMore || state.loadingHistory) return
        state = state.copy(loadingHistory = true)
        viewModelScope.launch {
            try {
                val result = client.request("session.history", JSONObject().put("session_id", session).put("before_seq", before))
                if (gateway !== client || state.sessionId != session) return@launch
                val older = parseTranscript(result.objects("messages"))
                state = state.copy(messages = (older + state.messages).distinctBy { it.id }, historyHasMore = result.optBoolean("has_more"),
                    historyBeforeSeq = result.i("before_seq"), loadingHistory = false)
            } catch (e: Exception) {
                if (gateway === client && state.sessionId == session) state = state.copy(loadingHistory = false, error = e.message ?: "Could not load older messages.")
            }
        }
    }

    fun send(text: String, imageBase64: String? = null, mime: String = "image/jpeg", onAccepted: () -> Unit = {}) {
        val snapshot = state
        if (!snapshot.canSubmitChat || (!snapshot.demo && gateway == null)) { showError("Wait for this chat or review its pending delivery before sending."); return }
        val value = text
        if (value.isBlank() && imageBase64 == null) return
        if (value.length > 32000 || imageBase64 != null && (imageBase64.length > 6 * 1024 * 1024 || mime !in listOf("image/jpeg", "image/png", "image/webp", "image/gif"))) {
            showError("Use a shorter message or a smaller supported image."); return
        }
        val displayed = value.ifBlank { "Look at this image." }
        val message = ChatMessage(UUID.randomUUID().toString(), "user", displayed)
        if (snapshot.demo) {
            state = snapshot.copy(messages = snapshot.messages + message, turnActive = true, turnStatus = "")
            onAccepted(); demoTurn?.cancel(); demoTurn = viewModelScope.launch { demoReply(displayed) }; return
        }
        val client = gateway ?: return
        val session = snapshot.sessionId ?: return
        val bot = snapshot.activeBot ?: return
        val scope = deliveryScope(snapshot) ?: return
        val context = chatContext
        fun owns() = gateway === client && chatContext == context
        state = snapshot.copy(sendingChat = true, submissionMessageId = message.id)
        viewModelScope.launch {
            var receiptId: String? = null
            var attempted = false
            var accepted = false
            try {
                receiptId = withContext(Dispatchers.IO) { pendingChats.reserve(scope) }
                if (!owns()) { withContext(Dispatchers.IO) { pendingChats.resolve(scope, receiptId) }; return@launch }
                state = state.copy(messages = state.messages + message, turnActive = true, turnStatus = "")
                attempted = true
                if (imageBase64 != null) {
                    client.request("image.attach_bytes", JSONObject().put("session_id", session).put("profile", bot.name)
                        .put("data", imageBase64).put("mime", mime).put("filename", "niobium-photo." + when (mime) { "image/png" -> "png"; "image/webp" -> "webp"; "image/gif" -> "gif"; else -> "jpg" }), 30_000)
                    if (!owns()) return@launch
                }
                val result = client.request("prompt.submit", JSONObject().put("session_id", session).put("profile", bot.name)
                    .put("text", displayed).put("submission_id", receiptId).put("clarify_batch", true), 30_000)
                check(result.optBoolean("accepted", true)) { "The engine did not confirm acceptance." }
                accepted = true
                if (textDrafts[scope] == value) textDrafts.remove(scope)
                if (owns()) {
                    if (result.has("execution_generation") && !result.isNull("execution_generation")) state = state.copy(sessionGeneration = result.optInt("execution_generation"))
                    state = state.copy(submissionMessageId = null, deliveryAccepted = true)
                    onAccepted()
                }
                withContext(Dispatchers.IO) { pendingChats.resolve(scope, receiptId) }
                if (owns()) state = state.copy(deliveryUnconfirmed = false, deliveryAccepted = false)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (owns()) state = state.copy(turnActive = if (accepted) state.turnActive else false, deliveryAccepted = accepted, deliveryUnconfirmed = attempted || runCatching { pendingChats.pending(scope) }.getOrDefault(true),
                    error = if (accepted) "Message accepted. A local receipt could not be cleared; sending stays locked." else if (attempted) "Delivery unconfirmed. Your draft is kept. Check this conversation before sending again." else e.message ?: "Message was not sent.")
            } finally { if (owns()) state = state.copy(sendingChat = false) }
        }
    }

    fun loadMCP() {
        val client = gateway ?: return
        if (state.demo) { state = state.copy(mcpServers = emptyList()); return }
        viewModelScope.launch {
            runCatching {
                val profile = state.activeBot?.name ?: "default"
                val listed = client.request("mcp.servers.list", JSONObject().put("profile", profile), 20_000)
                val statuses = runCatching { client.request("mcp.servers.status", JSONObject().put("profile", profile), 20_000) }
                    .getOrNull()?.a("servers")?.let { arr -> (0 until arr.length()).mapNotNull { arr.optJSONObject(it) } } ?: emptyList()
                val byName = statuses.associateBy { it.s("name") ?: "" }
                val arr = listed.a("servers") ?: JSONArray()
                val rows = (0 until arr.length()).mapNotNull { i ->
                    val j = arr.optJSONObject(i) ?: return@mapNotNull null
                    val name = j.s("name") ?: return@mapNotNull null
                    val st = byName[name]
                    MCPServerRow(name, j.s("transport") ?: "stdio", st?.s("status") ?: if (j.optBoolean("enabled", true)) "configured" else "disabled",
                        st?.optBoolean("connected", false) ?: false, j.optBoolean("enabled", true),
                        st?.optInt("tools", 0) ?: 0, j.s("source") ?: "")
                }
                state = state.copy(mcpServers = rows)
            }
        }
    }

    fun loadSkills() {
        val client = gateway ?: return
        if (state.demo) { state = state.copy(skills = emptyMap()); return }
        viewModelScope.launch {
            runCatching {
                val result = client.request("skills.manage", JSONObject()
                    .put("action", "list").put("profile", state.activeBot?.name ?: "default"), 20_000)
                val obj = result.o("skills") ?: JSONObject()
                val map = mutableMapOf<String, List<String>>()
                obj.keys().forEach { key ->
                    val arr = obj.optJSONArray(key) ?: return@forEach
                    map[key] = (0 until arr.length()).map { arr.optString(it) }
                }
                state = state.copy(skills = map)
            }
        }
    }

    fun reloadSkills() {
        val client = gateway ?: return
        viewModelScope.launch {
            runCatching {
                client.request("skills.reload", JSONObject().put("profile", state.activeBot?.name ?: "default"), 30_000)
                loadSkills()
                showToast("Skills reloaded")
            }
        }
    }

    fun loadUsage() {
        val client = gateway ?: return
        val sid = state.sessionId ?: return
        if (state.demo) return
        viewModelScope.launch {
            runCatching {
                val result = client.request("session.stats", JSONObject().put("session_id", sid), 15_000)
                if (state.sessionId != sid || gateway !== client) return@runCatching
                state = state.copy(usage = UsageStats.parse(result))
            }
        }
    }

    fun loadSessions(bot: BotProfile) {
        val client = gateway ?: return
        if (state.demo) { state = state.copy(sessions = emptyList()); return }
        viewModelScope.launch {
            runCatching {
                val result = client.request("session.list", JSONObject()
                    .put("profile", bot.name).put("scope", "stored").put("limit", 60), 20_000)
                val arr = result.a("sessions") ?: JSONArray()
                state = state.copy(sessions = (0 until arr.length()).mapNotNull { SessionRow.parse(arr.optJSONObject(it) ?: return@mapNotNull null) })
            }.onFailure { state = state.copy(sessions = emptyList()) }
        }
    }

    fun resumeSession(row: SessionRow, bot: BotProfile) = resumeChat(row.sessionId, bot)
    fun refreshChat() { val bot = state.activeBot ?: return; state.sessionId?.let { resumeChat(it, bot) } }
    private fun resumeChat(session: String, bot: BotProfile) {
        val client = gateway ?: return
        val epoch = ++chatEpoch
        state = state.copy(loadingChat = true, sendingChat = false, deliveryUnconfirmed = false, deliveryAccepted = false, submissionMessageId = null)
        viewModelScope.launch {
            runCatching {
                val result = client.request("session.resume", JSONObject()
                    .put("session_id", session).put("profile", bot.name).put("source", chatSource), 90_000)
                if (chatEpoch != epoch || state.activeBot?.id != bot.id || gateway !== client) return@runCatching
                restoreChat(result, session)
                recoverPrompts(result)
                if (state.engine == AgentEngine.DSH) client.request("local.attach", JSONObject().put("session_id", state.sessionId))
                if (chatEpoch != epoch || gateway !== client || state.sessionId != session) return@runCatching
                state = state.copy(loadingChat = false)
                loadModels(); loadToolsets()
            }.onFailure {
                if (it is kotlinx.coroutines.CancellationException) throw it
                if (chatEpoch == epoch && gateway === client) {
                    state = state.copy(loadingChat = false, sessionId = null, turnActive = false)
                    showError("Could not open chat. ${it.message}")
                }
            }
        }
    }

    fun loadModels() {
        val client = gateway ?: return
        val sessionId = state.sessionId ?: return
        if (state.demo) return
        viewModelScope.launch {
            runCatching {
                val result = client.request("model.options", JSONObject()
                    .put("profile", state.activeBot?.name ?: "default")
                    .put("session_id", sessionId), 20_000)
                if (state.sessionId != sessionId || gateway !== client) return@runCatching
                val rows = mutableListOf<ModelChoice>()
                val providers = result.a("providers") ?: JSONArray()
                for (i in 0 until providers.length()) {
                    val p = providers.optJSONObject(i) ?: continue
                    val slug = p.s("slug") ?: p.s("id") ?: ""
                    val name = p.s("name") ?: slug.replaceFirstChar { it.uppercase() }
                    val currentProvider = p.optBoolean("is_current", false)
                    val models = p.a("models") ?: JSONArray()
                    for (j in 0 until models.length()) {
                        val model = models.optString(j)
                        if (model.isBlank()) continue
                        rows += ModelChoice(slug, name, model, currentProvider && result.s("model") == model)
                    }
                }
                state = state.copy(modelChoices = rows)
            }.onFailure { if (state.sessionId == sessionId && gateway === client) state = state.copy(modelChoices = emptyList()) }
        }
    }

    fun switchModel(choice: ModelChoice) {
        val client = gateway ?: return
        val sessionId = state.sessionId ?: return
        viewModelScope.launch {
            runCatching {
                val snapshot = controlSnapshot()
                check(state.sessionId == sessionId) { "This chat has changed." }
                val params = JSONObject().put("session_id", sessionId).put("request_id", UUID.randomUUID().toString())
                    .put("expected_revision", snapshot.optInt("revision", state.sessionRevision)).put("operation", "model")
                    .put("payload", JSONObject().put("model", choice.model).put("provider", choice.provider))
                if (snapshot.has("execution_generation")) params.put("expected_generation", snapshot.getInt("execution_generation"))
                val result = client.request("session.mutate", params, 30_000)
                if (state.sessionId != sessionId) return@runCatching
                val revision = result.optInt("revision", state.sessionRevision)
                val bot = state.activeBot
                state = state.copy(sessionRevision = revision,
                    bots = state.bots.map { if (it.name == bot?.name) it.copy(model = choice.model, provider = choice.provider) else it },
                    activeBot = bot?.copy(model = choice.model, provider = choice.provider),
                    modelChoices = state.modelChoices.map { it.copy(current = it.id == choice.id) })
                showToast("Model switched to ${choice.label}")
            }.onFailure { showError("Could not switch model. ${it.message}") }
        }
    }

    fun setReasoning(effort: String?) {
        if (state.demo || (effort != null && effort !in listOf("minimal", "low", "medium", "high", "xhigh", "max", "ultra"))) return
        val client = gateway ?: return
        val session = state.sessionId ?: return
        viewModelScope.launch {
            try {
                val snapshot = controlSnapshot()
                if (gateway !== client || state.sessionId != session || !snapshot.has("execution_generation")) return@launch
                val result = client.request("session.mutate", JSONObject().put("session_id", session).put("request_id", UUID.randomUUID().toString())
                    .put("expected_revision", snapshot.optInt("revision", state.sessionRevision)).put("expected_generation", snapshot.getInt("execution_generation"))
                    .put("operation", "reasoning").put("payload", JSONObject().put("effort", effort ?: JSONObject.NULL)))
                if (gateway !== client || state.sessionId != session) return@launch
                state = state.copy(sessionRevision = result.optInt("revision", state.sessionRevision), sessionReasoning = effort)
                showToast("Reasoning: " + (effort ?: "profile default"))
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { showError("Could not change reasoning. " + e.message) }
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
            try {
                val snapshot = controlSnapshot()
                val params = JSONObject().put("session_id", sessionId).put("profile", bot.name)
                if (snapshot.has("execution_generation")) params.put("execution_generation", snapshot.getInt("execution_generation"))
                client.request("session.interrupt", params)
                if (state.sessionId == sessionId) state = state.copy(turnActive = false)
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { showError("Could not stop the turn. " + e.message) }
        }
    }

    // ── Events ───────────────────────────────────────────────────────────────

    private fun handleEvent(type: String, sessionId: String, payload: JSONObject) {
        if (sessionId.isNotBlank() && !showsSession(sessionId)) return
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
            "request.cancel" -> {
                val id = payload.opt("id")?.toString() ?: payload.s("request_id") ?: ""
                state = state.copy(approvals = state.approvals.filterNot { it.rpcId.toString() == id || it.requestId == id },
                    clarify = state.clarify?.takeUnless { it.rpcId.toString() == id },
                    credentialRequests = state.credentialRequests.filterNot { it.rpcId.toString() == id })
            }
            "message.segment" -> {
                ensureStreaming()
                val id = streamingId
                state = state.copy(messages = state.messages.map { if (it.id == id) it.copy(text = it.text.ifEmpty { payload.s("text") ?: "" }, streaming = false) else it })
                streamingId = null
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
                (payload.s("summary") ?: text).take(120), botName = bot.name))
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

    fun saveAppearance(context: android.content.Context, member: CastMember, profile: String, onResult: (Boolean) -> Unit) {
        if (state.demo) { CastStore.put(context, profile, member); onResult(true); return }
        val client = gateway ?: run { showError("Connect a gateway before saving this character."); onResult(false); return }
        viewModelScope.launch {
            try {
                val inventory = client.request("profiles.list", JSONObject().put("include_sessions", false))
                val row = inventory.objects("profiles").firstOrNull { it.s("name") == profile }
                    ?: error("The profile has changed. Refresh and try again.")
                check(gateway === client) { "The connection changed. Try again." }
                val meta = row.o("ui_meta")?.o("niobium") ?: JSONObject()
                val appearance = JSONObject().put("archetype", member.id).put("companion", member.companion).put("companion_name", member.companionName)
                member.recipe?.let { appearance.put("recipe", it.toJson()) }
                appearance.put("look", member.look?.toJson() ?: JSONObject.NULL)
                appearance.put("companion_art", member.companionArt?.toJson() ?: JSONObject.NULL)
                meta.put("appearance", appearance)
                val revision = row.o("ui_meta_revisions")?.optInt("niobium") ?: 0
                val result = client.request("profiles.configure", JSONObject().put("name", profile)
                    .put("ui_meta", JSONObject().put("niobium", meta))
                    .put("ui_meta_expected_revisions", JSONObject().put("niobium", revision)))
                check(gateway === client && result.optBoolean("ok")) { "This Pixel changed elsewhere. Refresh before saving again." }
                CastStore.put(context, if (state.engine == AgentEngine.DSH) "dsh:" + profile else profile, member); showToast("Character saved"); onResult(true)
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { showError("Could not save the character. " + e.message); onResult(false) }
        }
    }

    fun resolveCredential(request: CredentialRequest, value: String) {
        if (request.transportEpoch != connectionEpoch || state.credentialRequests.none { it.id == request.id }) return
        gateway?.respond(request.rpcId, JSONObject().put("value", value))
        state = state.copy(credentialRequests = state.credentialRequests.filterNot { it.id == request.id })
    }

    private suspend fun controlSnapshot(): JSONObject {
        val client = gateway ?: error("Connect a gateway first.")
        val session = state.sessionId ?: error("Open a Pixel chat first.")
        val profile = state.activeBot?.name ?: error("Open a Pixel chat first.")
        val snapshot = client.request("session.resume", JSONObject().put("session_id", session).put("profile", profile)
            .put("source", chatSource).put("omit_messages", true))
        check(state.sessionId == session && state.activeBot?.name == profile) { "This chat has changed. Try again in the current chat." }
        state = state.copy(sessionRevision = snapshot.optInt("revision", state.sessionRevision),
            sessionGeneration = snapshot.optInt("execution_generation").takeIf { snapshot.has("execution_generation") })
        return snapshot
    }
    private fun recoverPrompts(snapshot: JSONObject) {
        val session = snapshot.s("session_id") ?: state.sessionId ?: return
        val prompts = snapshot.objects("prompts").filter { it.s("kind") == "approval" }.map { j ->
            Approval("", "", session, j.s("command") ?: "", j.s("description") ?: "", "terminal",
                j.a("choices")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: listOf("deny"),
                j.s("prompt_id"), j.optInt("execution_generation"), true)
        }
        val legacy = snapshot.o("pending_approval")?.takeIf { prompts.isEmpty() }?.let { j ->
            Approval("", j.s("request_id") ?: "", session, j.s("command") ?: "", j.s("description") ?: "", j.s("tool_name") ?: "terminal",
                j.a("choices")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: listOf("deny"), recovered = true)
        }
        val clarify = snapshot.objects("prompts").filter { it.s("kind") == "clarify" && it.has("execution_generation") && it.s("prompt_id") != null }
        val questions = clarify.map { j -> ClarifyQuestion(j.getString("prompt_id"), j.s("question") ?: "More information requested",
            j.a("choices")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList(), j.optBoolean("multi_select")) }
        state = state.copy(approvals = if (legacy == null) prompts else listOf(legacy),
            clarify = questions.takeIf { it.isNotEmpty() }?.let { ClarifyRequest("", session, it,
                clarify.associate { j -> j.getString("prompt_id") to j.getInt("execution_generation") }) })
        if (state.engine == AgentEngine.DSH) snapshot.objects("local_requests").forEach { request ->
            val payload = request.o("payload") ?: return@forEach
            val type = if (payload.s("type") == "approval/requested") "approval" else "clarify"
            val converted = if (type == "approval") JSONObject().put("session_id", state.sessionId)
                .put("request_id", request.s("rpcId")).put("command", payload.s("toolName"))
                .put("description", payload.s("reason")).put("tool_name", payload.s("toolName"))
                .put("choices", JSONArray(listOf("once", "deny")))
            else JSONObject().put("session_id", state.sessionId).put("questions", JSONArray(payload.objects("questions").map { q ->
                JSONObject().put("qid", q.s("id")).put("question", q.s("question"))
                    .put("choices", JSONArray(q.objects("options").mapNotNull { it.s("label") })).put("multi_select", q.b("multiSelect") ?: false)
            }))
            handleServerRequest(request.s("rpcId") ?: return@forEach, type, converted)
        }
    }

    private fun showsSession(id: String) = state.sessionId == id || (state.engine == AgentEngine.DSH && state.activeGroup != null && id in groupSessionIds)

    private fun handleServerRequest(id: Any, method: String, params: JSONObject) {
        val sessionId = params.s("session_id") ?: ""
        if (sessionId.isNotBlank() && !showsSession(sessionId)) {
            if (declinesNotShown) gateway?.respondError(id, "This window does not show that session.", 4404)
            return
        }
        when (method) {
            "approval" -> {
                val choices = params.a("choices")?.let { arr -> (0 until arr.length()).map { arr.optString(it) } }
                    ?: listOf("once", "deny")
                state = state.copy(approvals = state.approvals.filterNot { it.rpcId.toString() == id.toString() } + Approval(id,
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
            "sudo", "secret", "vault.code", "vault.unlock_prompt", "vault.save_login" -> {
                val title = when (method) {
                    "sudo" -> "Host password requested"
                    "secret" -> params.s("env_var") ?: "Secret requested"
                    "vault.unlock_prompt" -> "Unlock " + (params.s("display_name") ?: "credential vault")
                    "vault.save_login" -> "Save a login on your host"
                    else -> "Verification code"
                }
                val detail = params.s("prompt") ?: params.s("hint") ?: params.s("command") ?: params.s("site") ?: "Your Hermes host is waiting for this value."
                if (state.credentialRequests.size < 16) state = state.copy(credentialRequests = state.credentialRequests +
                    CredentialRequest(id, sessionId, method, title, detail, method == "vault.save_login", connectionEpoch))
                else gateway?.respond(id, JSONObject().put("value", ""))
            }
            else -> gateway?.respondError(id, "Niobium does not handle $method yet.")
        }
    }

    fun resolveApproval(approval: Approval, choice: String) {
        if (choice !in approval.choices) return
        if (state.demo) {
            state = state.copy(approvals = state.approvals.filterNot { it.id == approval.id })
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
        val client = gateway ?: return
        if (!showsSession(approval.sessionId)) return
        if (approval.promptId != null && approval.executionGeneration != null) {
            viewModelScope.launch {
                try {
                    client.request("approval.respond", JSONObject().put("session_id", approval.sessionId)
                        .put("execution_generation", approval.executionGeneration).put("prompt_id", approval.promptId).put("choice", choice))
                    if (gateway !== client || !showsSession(approval.sessionId)) return@launch
                    state = state.copy(approvals = state.approvals.filterNot { it.id == approval.id })
                    showToast("Approval sent: $choice")
                } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (e: Exception) { showError("Approval was not accepted. " + e.message) }
            }
        } else if (approval.recovered) {
            viewModelScope.launch {
                try {
                    client.request("approval.respond", JSONObject().put("session_id", approval.sessionId)
                        .put("request_id", approval.requestId).put("choice", choice))
                    if (gateway !== client || !showsSession(approval.sessionId)) return@launch
                    state = state.copy(approvals = state.approvals.filterNot { it.id == approval.id })
                } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (e: Exception) { showError("Approval was not accepted. " + e.message) }
            }
        } else {
            client.respond(approval.rpcId, JSONObject().put("choice", choice))
            state = state.copy(approvals = state.approvals.filterNot { it.id == approval.id })
            showToast("Approval sent: $choice")
        }
    }

    fun resolveClarify(answers: Map<String, String>, selections: Map<String, List<String>> = emptyMap()) {
        val req = state.clarify ?: return
        val client = gateway ?: return
        if (!showsSession(req.sessionId)) return
        val flattened = answers + selections.mapValues { it.value.joinToString(", ") }
        if (req.questions.any { flattened[it.id].isNullOrBlank() }) {
            showError("Answer every question before sending. You can dismiss it and leave the task waiting.")
            return
        }
        if (req.promptGenerations.isNotEmpty()) {
            viewModelScope.launch {
                try {
                    for (question in req.questions) {
                        if (gateway !== client || !showsSession(req.sessionId)) return@launch
                        val generation = req.promptGenerations[question.id] ?: continue
                        if (state.clarify?.promptGenerations?.get(question.id) != generation) return@launch
                        client.request("clarify.respond", JSONObject().put("session_id", req.sessionId)
                            .put("execution_generation", generation).put("prompt_id", question.id).put("answer", flattened.getValue(question.id)))
                        if (gateway !== client || !showsSession(req.sessionId) ||
                            state.clarify?.promptGenerations?.get(question.id) != generation) return@launch
                        state = state.copy(clarify = state.clarify?.copy(questions = state.clarify!!.questions.filterNot { it.id == question.id },
                            promptGenerations = state.clarify!!.promptGenerations - question.id))
                    }
                    if (state.clarify?.questions?.isEmpty() == true) state = state.copy(clarify = null)
                } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (e: Exception) { showError("The answer was not accepted. " + e.message) }
            }
            return
        }
        val obj = JSONObject()
        flattened.forEach { (k, v) -> obj.put(k, v) }
        if (state.engine == AgentEngine.DSH) selections.forEach { (k, v) -> obj.put(k, JSONArray(v)) }
        client.respond(req.rpcId, JSONObject().put("answers", obj).put("outcome", "submitted"))
        state = state.copy(clarify = null)
    }

    fun cancelClarify() {
        val req = state.clarify ?: return
        val client = gateway ?: return
        if (!showsSession(req.sessionId) || req.promptGenerations.isNotEmpty()) return
        client.respond(req.rpcId, JSONObject().put("answers", JSONObject()).put("outcome", "cancelled"))
        state = state.copy(clarify = null)
    }

    // ── Groups ───────────────────────────────────────────────────────────────

    fun openGroup(room: GroupRoom) {
        groupEpoch++
        groupPoll?.cancel()
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
        groupEpoch++
        groupPoll?.cancel(); groupPoll = null
        state = state.copy(activeGroup = null, roomEvents = emptyList(), approvals = state.approvals.filterNot { it.sessionId in groupSessionIds },
            clarify = state.clarify?.takeUnless { it.sessionId in groupSessionIds })
        groupSessionIds = emptySet()
    }

    fun refreshGroup() {
        val client = gateway ?: return
        val room = state.activeGroup ?: return
        val epoch = groupEpoch
        if (state.demo) return
        viewModelScope.launch {
            runCatching {
                client.request("groups.log", JSONObject().put("room_id", room.roomId)
                    .put("since_seq", 0).put("limit", 500))
            }.onSuccess { result ->
                if (groupEpoch != epoch || gateway !== client) return@onSuccess
                state = state.copy(roomEvents = result.objects("events").mapNotNull(RoomEvent::parse))
                roomReceipts.acknowledge(state.roomEvents.filter { it.kind == "message.user" }.map { it.eventId }.toSet())
                if (state.engine == AgentEngine.DSH) {
                    groupSessionIds = result.a("session_ids")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } ?: emptySet()
                    val requests = result.objects("requests")
                    val ids = requests.map { it.opt("id")?.toString().orEmpty() }.toSet()
                    state = state.copy(approvals = state.approvals.filterNot { it.sessionId in groupSessionIds && it.rpcId.toString() !in ids },
                        clarify = state.clarify?.takeUnless { it.sessionId in groupSessionIds && it.rpcId.toString() !in ids })
                    requests.forEach { request -> request.o("params")?.let { handleServerRequest(request.get("id"), request.getString("method"), it) } }
                    val receipts = result.a("receipt_ids")
                    if (receipts != null) {
                        val ids = (0 until receipts.length()).map { receipts.getString(it) }.toSet()
                        roomReceipts.acknowledge(ids)
                        if (ids.isNotEmpty()) runCatching { client.request("groups.ack", JSONObject().put("room_id",room.roomId).put("event_ids",receipts),6_000) }
                    }
                }
            }
        }
    }

    fun clearLocalRoom(room: GroupRoom) {
        val client = gateway ?: return
        if (state.engine != AgentEngine.DSH) return
        viewModelScope.launch {
            try {
                client.request("groups.clear", JSONObject().put("room_id",room.roomId))
                if (gateway !== client) return@launch
                if (state.activeGroup?.roomId == room.roomId) closeGroup()
                state = state.copy(groups = state.groups.filterNot { it.roomId == room.roomId })
                refreshRoster()
            } catch (e: Exception) { showError(e.message ?: "Could not clear this room.") }
        }
    }

    fun stopRoom() {
        val room = state.activeGroup ?: return
        val client = gateway ?: return
        if (state.engine != AgentEngine.DSH) return
        viewModelScope.launch {
            try { client.request("groups.stop", JSONObject().put("room_id", room.roomId)); refreshGroup() }
            catch (e: Exception) { showError(e.message ?: "Could not stop this room.") }
        }
    }

    fun sendGroup(text: String, onAccepted: () -> Unit = {}, onSettled: () -> Unit = {}) {
        val value = text.trim()
        if (value.isEmpty()) { onSettled(); return }
        val room = state.activeGroup ?: run { onSettled(); return }
        if (state.demo) {
            val epoch = groupEpoch
            val next = (state.roomEvents.lastOrNull()?.seq ?: 0) + 1
            state = state.copy(roomEvents = state.roomEvents + RoomEvent(next, room.roomId,
                UUID.randomUUID().toString(), "message.user", "user", "user", "You", value,
                System.currentTimeMillis() / 1000, null, null))
            viewModelScope.launch {
                room.members.take(2).forEachIndexed { index, member ->
                    delay(900L + index * 500)
                    if (groupEpoch != epoch) return@launch
                    val seq = (state.roomEvents.lastOrNull()?.seq ?: 0) + 1
                    state = state.copy(roomEvents = state.roomEvents + RoomEvent(seq, room.roomId,
                        UUID.randomUUID().toString(), "message.member", "member", member.id, member.name,
                        if (index == 0) "On it. I’ll take the first pass and report back with a recommendation."
                        else "I’ll check the sources and flag anything that looks off.",
                        System.currentTimeMillis() / 1000, null, null))
                }
            }
            onAccepted(); onSettled(); return
        }
        val client = gateway ?: run { onSettled(); return }
        viewModelScope.launch {
            try {
                val scope = if (state.engine == AgentEngine.DSH) "dsh" else "hermes|" + state.connection?.url.orEmpty() + "|" + state.connection?.token.orEmpty()
                val eventId = roomReceipts.reserve(scope,room.roomId,value)
                val receipt = client.request("groups.send", JSONObject()
                    .put("room_id", room.roomId)
                    .put("profile", state.activeBot?.name ?: state.bots.firstOrNull()?.name ?: "default")
                    .put("event_id", eventId)
                    .put("payload", JSONObject().put("text", value).put("thread_id", eventId)))
                check(receipt.optBoolean("accepted",true)) { "The gateway did not accept that message." }
                roomReceipts.acknowledge(setOf(eventId))
                onAccepted()
                refreshGroup()
            } catch (e: Exception) {
                state = state.copy(error = "Group send failed. ${e.message}")
            } finally { onSettled() }
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

    fun draftPixel(context: android.content.Context, prompt: String, onDraft: (PixelDraft) -> Unit, onError: (String) -> Unit): Job {
        return viewModelScope.launch {
            try { onDraft(PixelBuilder.draft(context.assets.open("bot_build.md").bufferedReader().use { it.readText() }, if (state.demo) null else gateway, prompt)) }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { onError(e.message ?: "Could not design Pixel.") }
        }
    }

    fun buildPixel(context: android.content.Context, draft: PixelDraft, onCreated: (BotProfile) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            val spec = draft.spec
            spec.validationError?.let { onError(it); return@launch }
            val slug = spec.name
            if (state.bots.any { it.name == slug }) { onError("That Pixel name already exists. Choose another name."); return@launch }
            val base = Cast.all.first { it.id == spec.character.archetype }
            val member = base.copy(companion = spec.companion.kind, companionName = spec.companion.name, recipe = spec.character.recipe, look = spec.character.look, companionArt = spec.companion.art)
            if (state.demo) {
                val bot = BotProfile(slug, spec.title, spec.role, "", "Demo", spec.skills.size, "demo-$slug",
                    spec.companion.name + " and I are ready. What trouble are we solving?", System.currentTimeMillis() / 1000,
                    false, false, false, member.accentHex, false)
                CastStore.put(context, slug, member)
                state = state.copy(bots = listOf(bot) + state.bots)
                openBot(bot); onCreated(bot); return@launch
            }
            val client = gateway ?: run { onError("Connect a gateway first."); return@launch }
            var created = false
            try {
                val catalog = client.request("profiles.describe", JSONObject().put("name", "default"), 12_000)
                check(gateway === client) { "The engine changed. Try creating the Pixel again." }
                val reviewed = draft.copy(availableToolsets = draft.availableToolsets.intersect(catalog.objects("toolsets").mapNotNull { it.s("name") }.toSet()))
                if (state.engine == AgentEngine.DSH) {
                    val result = client.request("pixel.create", JSONObject().put("spec", spec.toJson())
                        .put("enabled_toolsets", JSONArray(reviewed.enabledToolsets)).put("briefing", spec.soul + spec.policyBriefing)
                        .put("accent", member.accentHex).put("builder", if (draft.usedModel) "model" else "template"), 120_000)
                    check(result.optBoolean("ok")) { "The local engine did not confirm creation." }
                    created = true
                } else PixelProvisioner.provision(reviewed, member.accentHex, client::request) { created = true }
                CastStore.put(context, if (state.engine == AgentEngine.DSH) "dsh:" + slug else slug, member)
                refreshRoster()
                val bot = state.bots.firstOrNull { it.name == slug } ?: error("The roster has not refreshed. Refresh before trying again.")
                openBot(bot); onCreated(bot)
                showToast(if (draft.usedModel) "Built " + spec.title + " with AI" else "Built " + spec.title + " from a template")
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) {
                if (created) { refreshRoster(); onError("Created $slug, but setup is incomplete: " + e.message + " Open the profile to repair it; creation was not retried.") }
                else onError("Could not create Pixel. " + e.message)
            }
        }
    }

    suspend fun loadCompanions() {
        if (state.demo) return
        val client = gateway ?: return
        val session = state.sessionId ?: return
        val profile = state.activeBot?.name ?: return
        state = state.copy(companionsLoading = true)
        try {
            val result = client.request("subagent.list", JSONObject().put("session_id", session).put("profile", profile))
            if (state.sessionId != session || state.activeBot?.name != profile) return
            val children = result.objects("subagents").mapNotNull(CompanionTask::parse) + result.objects("delegations").mapNotNull { j ->
                j.s("delegation_id")?.let { id -> CompanionTask(id + "-" + j.optInt("task_index"), j.s("goal") ?: "Delegated task",
                    j.s("status") ?: "failed", j.s("error") ?: "Task failed.", 0, false, true) }
            }
            state = state.copy(companionTasks = children, companionError = null)
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) {
            if (state.sessionId == session && state.activeBot?.name == profile) state = state.copy(companionError = "Companion work is unavailable on this gateway. " + e.message)
        } finally { state = state.copy(companionsLoading = false) }
    }
    suspend fun companionTranscript(id: String): String {
        val client = gateway ?: error("Connect a gateway first.")
        val session = state.sessionId ?: error("Open a Pixel chat first.")
        val profile = state.activeBot?.name ?: error("Open a Pixel chat first.")
        val result = client.request("subagent.tail", JSONObject().put("session_id", session).put("profile", profile).put("subagent_id", id))
        check(state.sessionId == session && state.activeBot?.name == profile) { "This chat has changed. Reopen companion tasks." }
        return result.s("text") ?: ""
    }
    suspend fun controlCompanion(id: String, action: String, text: String) {
        require(action in listOf("steer", "interrupt"))
        val client = gateway ?: error("Connect a gateway first.")
        val session = state.sessionId ?: error("Open a Pixel chat first.")
        val profile = state.activeBot?.name ?: error("Open a Pixel chat first.")
        val params = JSONObject().put("session_id", session).put("profile", profile).put("subagent_id", id)
        if (action == "steer") params.put("text", text)
        val result = client.request("subagent." + action, params)
        if (state.sessionId != session || state.activeBot?.name != profile) return
        if (action == "steer") {
            check(result.s("status") == "queued") { "The helper could not accept this correction." }
            showToast("Correction queued for the helper")
        } else showToast(if (result.optBoolean("found")) "Stop requested" else "The helper has already finished")
        loadCompanions()
    }

    fun loadRoutines(bot: BotProfile) {
        if (state.demo) {
            state = state.copy(routines = DemoData.routines.filter { it.bot == bot.name || it.bot.isEmpty() })
            return
        }
        val client = gateway ?: return
        viewModelScope.launch {
            runCatching {
                val result = client.request("cron.manage", JSONObject()
                    .put("action", "list").put("profile", bot.name).put("include_disabled", true), 20_000)
                val jobs = result.a("jobs")?.let { arr -> (0 until arr.length()).mapNotNull { arr.optJSONObject(it) } } ?: emptyList()
                state = state.copy(routines = jobs.mapNotNull { Routine.parse(it, bot.name) })
            }.onFailure { state = state.copy(routines = emptyList()) }
        }
    }

    fun createRoutine(name: String, schedule: String, prompt: String, bot: BotProfile, done: () -> Unit = {}) {
        if (state.demo) {
            state = state.copy(routines = state.routines + Routine(name, name, schedule, prompt, true, "Tomorrow, 08:00", bot.name))
            done(); return
        }
        val client = gateway ?: return
        viewModelScope.launch {
            runCatching {
                client.request("cron.manage", JSONObject().put("action", "add").put("name", name)
                    .put("schedule", schedule).put("prompt", prompt).put("profile", bot.name), 20_000)
                loadRoutines(bot)
                done()
            }.onFailure { showError("Could not create routine. ${it.message}") }
        }
    }

    fun deleteRoutine(routine: Routine, bot: BotProfile) {
        if (state.demo) {
            state = state.copy(routines = state.routines.filterNot { it.id == routine.id })
            return
        }
        val client = gateway ?: return
        viewModelScope.launch {
            runCatching {
                client.request("cron.manage", JSONObject().put("action", "remove")
                    .put("name", routine.id).put("profile", bot.name), 20_000)
                loadRoutines(bot)
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
        if (url.username.isNotEmpty() || url.password.isNotEmpty()) return null
        return url.newBuilder().query(null).fragment(null).build().toString().trimEnd('/')
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

/** Provider lifetimes are owned by the activity ViewModel, never by the selected tab. */
class NiobiumEngineHub(app: Application) : AndroidViewModel(app) {
    private val owned = androidx.lifecycle.ViewModelStore()
    private fun workspace(engine: AgentEngine): NiobiumViewModel {
        val factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T = NiobiumViewModel(getApplication(), engine) as T
        }
        return androidx.lifecycle.ViewModelProvider(owned, factory).get(engine.name, NiobiumViewModel::class.java)
    }
    val hermes = workspace(AgentEngine.HERMES)
    val local = workspace(AgentEngine.DSH)
    var active by mutableStateOf(hermes)
        private set
    private val prefs = app.getSharedPreferences("niobium", android.content.Context.MODE_PRIVATE)
    private var started = false
    init { hermes.engineHub = this; local.engineHub = this }
    fun bootstrap(demo: Boolean = false, bot: String? = null, initialConnection: Connection? = null) {
        if (started) return; started = true
        val localSelected = initialConnection == null && (prefs.getString("agent-provider", null) == "dsh" || prefs.getBoolean("local-mode", false))
        active = if (!demo && localSelected) local else hermes
        if (!demo && initialConnection != null) activate(AgentEngine.HERMES)
        hermes.bootstrap(demo, if (demo || !localSelected) bot else null, initialConnection)
        local.prepareStoppedLocal(if (!demo && localSelected) bot else null)
        if (!demo && prefs.getBoolean("dsh-enabled", localSelected)) local.useLocal()
    }
    fun activate(engine: AgentEngine) {
        active = if (engine == AgentEngine.DSH) local else hermes
        prefs.edit().putString("agent-provider", if (engine == AgentEngine.DSH) "dsh" else "hermes").remove("local-mode").apply()
    }
    fun resumeConnections() {
        if (started) {
            if (!hermes.state.demo && !hermes.state.needsConnection && !hermes.state.connected && !hermes.state.connecting) hermes.reconnect()
            if (prefs.getBoolean("dsh-enabled", false) && !local.state.connected && !local.state.connecting) local.useLocal()
        }
    }
    override fun onCleared() { owned.clear(); super.onCleared() }
}
