package ai.wckd.niobium.desktop

import io.sovware.niobium.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class AgentPrompt(val id: Any, val method: String, val session: String, val params: JSONObject)
data class DesktopState(
    val connected: Boolean = false, val connecting: Boolean = false, val address: String = "",
    val bots: List<BotProfile> = emptyList(), val specs: Map<String, PixelSpec> = emptyMap(),
    val groups: List<GroupRoom> = emptyList(), val room: GroupRoom? = null, val events: List<RoomEvent> = emptyList(),
    val bot: BotProfile? = null, val session: String? = null, val messages: List<ChatMessage> = emptyList(),
    val loading: Boolean = false, val working: Boolean = false, val sending: Boolean = false,
    val prompts: List<AgentPrompt> = emptyList(), val error: String? = null,
    val modelChoices: List<ModelChoice> = emptyList(), val model: String = "", val groupsSupported: Boolean = false
)

/** One store per engine. A switch never transfers sessions, requests or tokens. */
class DesktopAgent(val local: Boolean, private val scope: CoroutineScope, private val outbox: RoomOutbox = RoomOutbox()) {
    private val mutable = MutableStateFlow(DesktopState())
    val state = mutable.asStateFlow()
    private var gateway: GatewayClient? = null
    private var connectionEpoch = 0
    private var chatEpoch = 0
    private var streamId: String? = null
    private var roomPoll: Job? = null
    private var groupSessionIDs = emptySet<String>()
    private var source = "gui"
    private var declinesNotShown = false
    // Reuse a receipt after an ambiguous room send; changing engines cannot mix it.
    private var receiptScope = ""

    fun connect(address: String, token: String) {
        disconnect()
        val epoch = connectionEpoch
        receiptScope = if (local) "dsh" else RoomOutbox.digest(address.trim().trimEnd('/') + "|" + token)
        mutable.value = DesktopState(connecting = true, address = address.trim().trimEnd('/'))
        val ready = CompletableDeferred<Unit>()
        lateinit var client: GatewayClient
        client = GatewayClient(object : GatewayClient.Listener {
            override fun onReady(payload: JSONObject) { ready.complete(Unit) }
            override fun onNotification(type: String, sessionId: String, payload: JSONObject) { scope.launch {
                if (owns(client, epoch)) event(type, sessionId, payload)
            } }
            override fun onServerRequest(id: Any, method: String, params: JSONObject) { scope.launch {
                if (owns(client, epoch)) receivePrompt(client, id, method, params)
            } }
            override fun onClosed(reason: String) { ready.completeExceptionally(GatewayException(reason)); scope.launch {
                if (owns(client, epoch)) mutable.value = mutable.value.copy(connected = false, connecting = false, loading = false, working = false, prompts = emptyList(), error = reason)
            } }
        })
        gateway = client
        scope.launch {
            try {
                GatewayClient.probe(mutable.value.address, token)
                if (!owns(client, epoch)) return@launch
                client.connect(mutable.value.address, token)
                withTimeout(20_000) { ready.await() }
                val runtime = client.request("runtime.describe", timeoutMs = 12_000)
                val selectedSource = runtime.o("session_create")?.strings("sources")?.firstOrNull { it in listOf("gui", "web") } ?: "gui"
                val caps = client.request("client.capabilities", JSONObject().put("server_requests", true), 12_000)
                if (!owns(client, epoch)) return@launch
                source = selectedSource
                declinesNotShown = caps.optBoolean("declines_not_shown", false)
                mutable.value = mutable.value.copy(connected = true, connecting = false)
                loadRoster(client, epoch)
                loadGroups(client, epoch)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (owns(client, epoch)) { client.disconnect(true); mutable.value = mutable.value.copy(connected = false, connecting = false, error = e.message) } }
        }
    }
    private fun owns(client: GatewayClient, epoch: Int) = gateway === client && connectionEpoch == epoch
    fun dismissError() { mutable.value = mutable.value.copy(error = null) }
    fun disconnect() {
        ++connectionEpoch; ++chatEpoch
        roomPoll?.cancel(); roomPoll = null
        gateway?.disconnect(true); gateway = null
        streamId = null; groupSessionIDs = emptySet()
        mutable.value = DesktopState()
    }
    fun refresh() { val client = gateway ?: return; val epoch = connectionEpoch; scope.launch {
        try { loadRoster(client, epoch); loadGroups(client, epoch) }
        catch (e: Exception) { if (owns(client, epoch)) mutable.value = mutable.value.copy(error = e.message) }
    } }
    private suspend fun loadRoster(client: GatewayClient, epoch: Int) {
        val response = client.request("profiles.list", JSONObject().put("include_sessions", true))
        if (!owns(client, epoch)) return
        val rows = response.objects("profiles")
        val specs = rows.mapNotNull { row -> row.o("ui_meta")?.o("niobium")?.o("pixel_spec")?.let { spec ->
            PixelBuilder.parse(spec.toString())?.let { row.getString("name") to it }
        } }.toMap()
        mutable.value = mutable.value.copy(bots = rows.mapNotNull(BotProfile::parse).filterNot { it.hidden }, specs = specs)
    }
    private suspend fun loadGroups(client: GatewayClient, epoch: Int) {
        val caps = try { client.request("groups.capabilities", timeoutMs = 10_000) } catch (_: Exception) { JSONObject() }
        if (!owns(client, epoch)) return
        val supported = "groups.list" in caps.strings("methods")
        val rooms = if (supported) client.request("groups.list").objects("rooms").mapNotNull(GroupRoom::parse).filterNot { it.disbanded } else emptyList()
        if (owns(client, epoch)) mutable.value = mutable.value.copy(groups = rooms, groupsSupported = supported)
    }
    fun open(bot: BotProfile) {
        val client = gateway ?: return
        if (!mutable.value.connected || mutable.value.loading) return
        val epoch = ++chatEpoch; val connection = connectionEpoch
        roomPoll?.cancel(); groupSessionIDs = emptySet(); streamId = null
        mutable.value = mutable.value.copy(bot = bot, room = null, events = emptyList(), session = null, messages = emptyList(), loading = true, working = false, prompts = emptyList(), error = null)
        scope.launch {
            try {
                val stored = bot.canonicalSessionId ?: client.request("session.list", JSONObject().put("profile", bot.name).put("title", "Bot Chat")).objects("sessions").firstOrNull()?.s("id")
                if (!owns(client, connection) || chatEpoch != epoch) return@launch
                val result = client.request(if (stored == null) "session.create" else "session.resume", JSONObject().put("profile", bot.name).put("title", "Bot Chat").put("source", source).apply { stored?.let { put("session_id", it) } }, 90_000)
                if (!owns(client, connection) || chatEpoch != epoch) return@launch
                restore(result)
                if (local) client.request("local.attach", JSONObject().put("session_id", mutable.value.session))
                if (!owns(client, connection) || chatEpoch != epoch) return@launch
                mutable.value = mutable.value.copy(loading = false)
                loadModels(client, epoch)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (owns(client, connection) && chatEpoch == epoch) mutable.value = mutable.value.copy(loading = false, session = null, error = "Could not open ${bot.title}. ${e.message}") }
        }
    }
    private fun restore(snapshot: JSONObject) {
        val inflight = snapshot.o("inflight")
        val running = snapshot.optBoolean("running", inflight?.optBoolean("streaming") ?: false)
        streamId = if (running) UUID.randomUUID().toString() else null
        val messages = snapshot.objects("messages").mapIndexed { index, row -> ChatMessage(
            id = "history-${row.i("row_id") ?: index}", role = row.s("role") ?: "system", text = row.s("text") ?: jstr(row.opt("content")),
            reasoning = row.s("reasoning"), toolName = row.s("name"), toolResult = row.s("content"), rowId = row.i("row_id")) }
        mutable.value = mutable.value.copy(session = snapshot.s("session_id"), messages = messages + listOfNotNull(streamId?.let { ChatMessage(it, "assistant", inflight?.s("assistant") ?: "", streaming = true) }), working = running)
        if (local) snapshot.objects("local_requests").forEach { recoverLocalRequest(it) }
        snapshot.objects("prompts").filter { it.s("kind") == "approval" }.forEach { prompt ->
            val params = JSONObject(prompt.toString()).put("session_id", mutable.value.session).put("_recovered", true)
            mutable.value = mutable.value.copy(prompts = mutable.value.prompts + AgentPrompt(prompt.s("prompt_id") ?: prompt.s("request_id") ?: "", "approval", mutable.value.session ?: "", params))
        }
        snapshot.objects("prompts").filter { it.s("kind") == "clarify" && it.s("prompt_id") != null && it.has("execution_generation") }.forEach { prompt ->
            val id = prompt.getString("prompt_id")
            val question = JSONObject(prompt.toString()).put("qid", id)
            val params = JSONObject(prompt.toString()).put("session_id", mutable.value.session).put("_recovered", true).put("questions", JSONArray().put(question))
            mutable.value = mutable.value.copy(prompts = mutable.value.prompts + AgentPrompt(id, "clarify", mutable.value.session.orEmpty(), params))
        }
        if (mutable.value.prompts.none { it.method == "approval" }) snapshot.o("pending_approval")?.let { prompt ->
            val params = JSONObject(prompt.toString()).put("session_id", mutable.value.session).put("_recovered", true)
            mutable.value = mutable.value.copy(prompts = mutable.value.prompts + AgentPrompt(prompt.s("request_id").orEmpty(), "approval", mutable.value.session.orEmpty(), params))
        }
    }
    private suspend fun loadModels(client: GatewayClient, epoch: Int) {
        val session = mutable.value.session ?: return
        val result = try { client.request("model.options", JSONObject().put("session_id", session).put("profile", mutable.value.bot?.name), 12_000) } catch (_: Exception) { return }
        if (gateway !== client || epoch != chatEpoch) return
        val choices = result.objects("providers").flatMap { provider ->
            val slug = provider.s("slug") ?: provider.s("id") ?: ""
            provider.strings("models").map { model -> ModelChoice(slug, provider.s("name") ?: slug, model, provider.optBoolean("is_current") && result.s("model") == model) }
        }
        mutable.value = mutable.value.copy(modelChoices = choices, model = result.s("model").orEmpty())
    }
    fun selectModel(choice: ModelChoice) { perform { client ->
        val session = mutable.value.session ?: error("Open a chat first.")
        val profile = mutable.value.bot?.name ?: error("Open a Pixel first.")
        val snapshot = client.request("session.resume", JSONObject().put("session_id", session).put("profile", profile).put("source", source).put("omit_messages", true))
        check(gateway === client && mutable.value.session == session) { "This chat changed. Choose the model again." }
        client.request("session.mutate", JSONObject().put("session_id", session).put("request_id", UUID.randomUUID().toString()).put("expected_revision", snapshot.optInt("revision"))
            .put("operation", "model").put("payload", JSONObject().put("provider", choice.provider).put("model", choice.model))
            .apply { if (snapshot.has("execution_generation")) put("expected_generation", snapshot.getInt("execution_generation")) })
        loadModels(client, chatEpoch)
    } }
    fun send(text: String, onAccepted: () -> Unit = {}) {
        val value = text.trim(); val snapshot = mutable.value
        if (value.isEmpty() || value.length > 32000 || snapshot.loading || snapshot.sending || !snapshot.connected || snapshot.room != null || snapshot.session == null || snapshot.bot == null) return
        val client = gateway ?: return; val connection = connectionEpoch; val session = snapshot.session
        mutable.value = snapshot.copy(sending = true, working = true, messages = snapshot.messages + ChatMessage(UUID.randomUUID().toString(), "user", value), error = null)
        scope.launch {
            try {
                val receipt = client.request("prompt.submit", JSONObject().put("session_id", session).put("profile", snapshot.bot.name).put("text", value).put("submission_id", UUID.randomUUID().toString()).put("clarify_batch", true), 30_000)
                check(receipt.optBoolean("accepted", true)) { "The engine did not accept this message." }
                if (owns(client, connection) && mutable.value.session == session) onAccepted()
            } catch (e: Exception) { if (owns(client, connection) && mutable.value.session == session) mutable.value = mutable.value.copy(working = false, error = "Message receipt unavailable. Reopen this chat to check before sending again. ${e.message}") }
            finally { if (owns(client, connection) && mutable.value.session == session) mutable.value = mutable.value.copy(sending = false) }
        }
    }
    fun stopTurn() { perform { client ->
        val snapshot = mutable.value
        if (snapshot.room != null) client.request("groups.stop", JSONObject().put("room_id", snapshot.room.roomId))
        else snapshot.session?.let { session ->
            val current = client.request("session.resume", JSONObject().put("session_id", session).put("profile", snapshot.bot?.name).put("source", source).put("omit_messages", true))
            check(gateway === client && mutable.value.session == session) { "The chat changed. Stop it from its current window." }
            client.request("session.interrupt", JSONObject().put("session_id", session).put("profile", snapshot.bot?.name)
                .apply { if (current.has("execution_generation")) put("execution_generation", current.getInt("execution_generation")) })
            if (gateway === client && mutable.value.session == session) mutable.value = mutable.value.copy(working = false)
        }
    } }
    private fun event(type: String, session: String, payload: JSONObject) {
        if (session.isNotBlank() && session != mutable.value.session && session !in groupSessionIDs) return
        if (session in groupSessionIDs && session != mutable.value.session) return
        fun stream(): String {
            streamId?.let { return it }
            val id = UUID.randomUUID().toString(); streamId = id
            mutable.value = mutable.value.copy(messages = mutable.value.messages + ChatMessage(id, "assistant", streaming = true))
            return id
        }
        when (type) {
            "message.start" -> { stream(); mutable.value = mutable.value.copy(working = true) }
            "message.delta", "message.interim" -> { val id = stream(); mutable.value = mutable.value.copy(working = true, messages = mutable.value.messages.map { if (it.id == id) it.copy(text = it.text + (payload.s("text") ?: "")) else it }) }
            "message.segment", "message.complete" -> {
                val id = streamId
                val text = payload.s("text").orEmpty()
                mutable.value = mutable.value.copy(working = type != "message.complete", messages = mutable.value.messages.map { if (it.id == id) it.copy(text = it.text.ifEmpty { text }, streaming = false) else it } + if (id == null && text.isNotEmpty()) listOf(ChatMessage(UUID.randomUUID().toString(), "assistant", text)) else emptyList())
                streamId = null
                payload.s("error")?.let { mutable.value = mutable.value.copy(error = it) }
            }
            "tool.start" -> mutable.value = mutable.value.copy(messages = mutable.value.messages + ChatMessage(payload.s("tool_id") ?: UUID.randomUUID().toString(), "tool", toolName = payload.s("name"), toolArgs = jstr(payload.opt("args")), toolStatus = ToolStatus.RUNNING))
            "tool.complete" -> mutable.value = mutable.value.copy(messages = mutable.value.messages.map { if (it.id == payload.s("tool_id")) it.copy(toolResult = jstr(payload.opt("result")), toolStatus = if (payload.optBoolean("success", true)) ToolStatus.DONE else ToolStatus.FAILED) else it })
            "request.cancel" -> mutable.value = mutable.value.copy(prompts = mutable.value.prompts.filterNot { it.id.toString() == (payload.opt("id") ?: payload.s("request_id")).toString() })
            "error" -> mutable.value = mutable.value.copy(working = false, error = payload.s("message") ?: "The engine returned an error.")
        }
    }
    private fun receivePrompt(client: GatewayClient, id: Any, method: String, params: JSONObject) {
        val session = params.s("session_id").orEmpty()
        if (session.isNotEmpty() && session != mutable.value.session && session !in groupSessionIDs) {
            if (declinesNotShown) client.respondError(id, "This window does not show that session.", 4404)
            return
        }
        if (method !in listOf("approval", "clarify", "sudo", "secret", "vault.code", "vault.unlock_prompt", "vault.save_login")) { client.respondError(id, "This desktop client does not handle $method yet."); return }
        if (mutable.value.prompts.size >= 16) { client.respondError(id, "Too many pending requests."); return }
        mutable.value = mutable.value.copy(prompts = mutable.value.prompts.filterNot { it.id == id } + AgentPrompt(id, method, session, params))
    }
    private fun recoverLocalRequest(request: JSONObject) {
        val payload = request.o("payload") ?: return
        val approval = payload.s("type") == "approval/requested"
        val params = JSONObject().put("session_id", payload.s("sessionId") ?: mutable.value.session)
        if (approval) params.put("description", payload.s("reason")).put("command", payload.s("toolName")).put("choices", JSONArray(listOf("once", "deny")))
        else params.put("questions", JSONArray(payload.objects("questions").map { q -> JSONObject().put("qid", q.s("id")).put("question", q.s("question")).put("choices", JSONArray(q.objects("options").mapNotNull { it.s("label") })).put("multi_select", q.optBoolean("multiSelect")) }))
        gateway?.let { receivePrompt(it, request.s("rpcId") ?: return, if (approval) "approval" else "clarify", params) }
    }
    fun answer(prompt: AgentPrompt, result: JSONObject) { perform { client ->
        if (prompt !in mutable.value.prompts) return@perform
        if (prompt.params.optBoolean("_recovered")) {
            client.request(if (prompt.method == "clarify") "clarify.respond" else "approval.respond", JSONObject().put("session_id", prompt.session)
                .apply { if (prompt.method == "clarify") put("answer", result.o("answers")?.s(prompt.id.toString()).orEmpty()) else put("choice", result.s("choice")) }
                .apply { for (key in listOf("prompt_id", "execution_generation", "request_id")) if (prompt.params.has(key)) put(key, prompt.params.get(key)) })
        } else client.respond(prompt.id, result)
        if (gateway !== client) return@perform
        mutable.value = mutable.value.copy(prompts = mutable.value.prompts.filterNot { it == prompt })
    } }
    fun defer(prompt: AgentPrompt) { mutable.value = mutable.value.copy(prompts = mutable.value.prompts.filterNot { it == prompt }) }
    fun openRoom(room: GroupRoom) {
        ++chatEpoch; streamId = null; groupSessionIDs = emptySet()
        mutable.value = mutable.value.copy(room = room, bot = null, session = null, messages = emptyList(), prompts = emptyList(), events = emptyList(), loading = false, working = false)
        roomPoll?.cancel()
        val client = gateway ?: return; val connection = connectionEpoch
        roomPoll = scope.launch { while (isActive && owns(client, connection) && mutable.value.room?.roomId == room.roomId) {
            try { refreshRoom(client, room.roomId) } catch (e: Exception) { if (e is CancellationException) throw e; mutable.value = mutable.value.copy(error = e.message) }
            delay(2500)
        } }
    }
    private suspend fun refreshRoom(client: GatewayClient, roomID: String) {
        val result = client.request("groups.log", JSONObject().put("room_id", roomID).put("since_seq", 0).put("limit", 500))
        if (gateway !== client || mutable.value.room?.roomId != roomID) return
        val events = result.objects("events").mapNotNull(RoomEvent::parse)
        outbox.acknowledge(events.filter { it.kind == "message.user" }.map { it.eventId }.toSet())
        mutable.value = mutable.value.copy(events = events)
        if (local) {
            groupSessionIDs = result.strings("session_ids").toSet()
            val requests = result.objects("requests")
            val ids = requests.mapNotNull { it.opt("id")?.toString() }.toSet()
            mutable.value = mutable.value.copy(prompts = mutable.value.prompts.filterNot { it.session in groupSessionIDs && it.id.toString() !in ids })
            requests.forEach { row -> row.o("params")?.let { receivePrompt(client, row.get("id"), row.getString("method"), it) } }
            val receipts = result.strings("receipt_ids")
            if (receipts.isNotEmpty()) client.request("groups.ack", JSONObject().put("room_id", roomID).put("event_ids", JSONArray(receipts)))
        }
    }
    fun sendRoom(text: String, onAccepted: () -> Unit) {
        val room = mutable.value.room ?: return
        if (text.isBlank() || mutable.value.sending) return
        val value = text.trim(); val connection = connectionEpoch
        mutable.value = mutable.value.copy(sending = true)
        perform { client ->
            try {
                val receipt = outbox.reserve(receiptScope, room.roomId, value)
                val result = client.request("groups.send", JSONObject().put("room_id", room.roomId).put("profile", "default").put("event_id", receipt).put("payload", JSONObject().put("text", value).put("thread_id", receipt)))
                check(result.optBoolean("accepted", true)) { "The room did not accept this message." }
                outbox.acknowledge(setOf(receipt)); if (owns(client, connection) && mutable.value.room?.roomId == room.roomId) onAccepted()
                refreshRoom(client, room.roomId)
            } finally { if (owns(client, connection)) mutable.value = mutable.value.copy(sending = false) }
        }
    }
    fun createRoom(name: String, members: List<BotProfile>, onCreated: () -> Unit) { perform { client ->
        require(name.trim().length in 1..60 && members.size in 2..6)
        val roster = JSONArray(members.map { JSONObject().put("member_id", it.name).put("profile", it.name).put("handle", it.name).put("display_name", it.title).put("target", JSONObject().put("kind", "local").put("profile", it.name)) })
        val result = client.request("groups.create", JSONObject().put("room_id", UUID.randomUUID().toString()).put("name", name.trim()).put("members", roster))
        if (gateway !== client) return@perform
        val room = result.o("room")?.let(GroupRoom::parse) ?: error("The engine did not return a room.")
        mutable.value = mutable.value.copy(groups = listOf(room) + mutable.value.groups)
        openRoom(room); onCreated()
    } }
    suspend fun draft(prompt: String): PixelDraft {
        val client = gateway ?: error("Connect an engine first.")
        val skill = javaClass.getResourceAsStream("/bot_build.md")!!.bufferedReader().use { it.readText() }
        return PixelBuilder.draft(skill, client, prompt)
    }
    suspend fun createPixel(draft: PixelDraft) {
        val client = gateway ?: error("Connect an engine first."); val connection = connectionEpoch
        val spec = draft.spec; require(spec.validationError == null) { spec.validationError.orEmpty() }
        val catalog = client.request("profiles.describe", JSONObject().put("name", "default"))
        check(owns(client, connection)) { "The engine changed. Review the Pixel again." }
        val reviewed = draft.copy(availableToolsets = draft.availableToolsets.intersect(catalog.objects("toolsets").mapNotNull { it.s("name") }.toSet()))
        if (local) {
            val result = client.request("pixel.create", JSONObject().put("spec", spec.toJson()).put("enabled_toolsets", JSONArray(reviewed.enabledToolsets)).put("briefing", spec.soul + spec.policyBriefing).put("accent", "#8557F4").put("builder", if (draft.usedModel) "model" else "template"), 120_000)
            check(result.optBoolean("ok")) { "The engine did not confirm creation. Refresh before retrying." }
        } else PixelProvisioner.provision(reviewed, "#8557F4", client::request)
        check(owns(client, connection)) { "Pixel created on the previous engine. Reconnect there to open it." }
        loadRoster(client, connection)
        mutable.value.bots.firstOrNull { it.name == spec.name }?.let(::open)
    }
    fun saveCredential(ref: String, value: String, onSaved: () -> Unit) { if (!local) return; perform { client ->
        client.request("local.credential", JSONObject().put("ref", ref).put("value", value), 20_000); if (gateway === client) onSaved()
    } }
    private fun perform(block: suspend (GatewayClient) -> Unit) {
        val client = gateway ?: return; val epoch = connectionEpoch
        scope.launch { if (!owns(client, epoch)) return@launch; try { block(client) } catch (e: CancellationException) { throw e } catch (e: Exception) { if (owns(client, epoch)) mutable.value = mutable.value.copy(error = e.message) } }
    }
}
