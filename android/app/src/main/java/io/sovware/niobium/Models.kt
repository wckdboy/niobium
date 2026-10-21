package io.sovware.niobium

import androidx.compose.ui.graphics.Color
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun JSONObject.s(key: String): String? = if (has(key) && !isNull(key)) optString(key) else null
fun JSONObject.o(key: String): JSONObject? = optJSONObject(key)
fun JSONObject.a(key: String): JSONArray? = optJSONArray(key)
fun JSONObject.i(key: String): Int? = if (has(key) && !isNull(key)) optInt(key) else null
fun JSONObject.d(key: String): Double? = if (has(key) && !isNull(key)) optDouble(key) else null
fun JSONObject.b(key: String): Boolean? = if (has(key) && !isNull(key)) optBoolean(key) else null
fun JSONObject.objects(key: String): List<JSONObject> {
    val arr = optJSONArray(key) ?: return emptyList()
    return (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
}
fun JSONObject.strings(key: String): List<String> {
    val arr = optJSONArray(key) ?: return emptyList()
    return (0 until arr.length()).mapNotNull { arr.optString(it).takeIf(String::isNotBlank) }
}
fun jstr(value: Any?): String = when (value) {
    null, JSONObject.NULL -> ""
    is String -> value
    is JSONObject -> value.s("text") ?: value.s("summary") ?: value.toString(2)
    is JSONArray -> value.toString(2)
    else -> value.toString()
}
fun epochDate(value: Double?): Long? = value?.takeIf { it > 0 }?.let { (if (it > 10_000_000_000) it / 1000 else it).toLong() }

data class Connection(val name: String, val url: String, val token: String, val isDemo: Boolean = false)

data class BotProfile(
    val name: String,
    val title: String,
    val role: String,
    val provider: String,
    val model: String,
    val skillCount: Int,
    val canonicalSessionId: String?,
    val preview: String,
    val activityAt: Long?,
    val workerActive: Boolean,
    val isDefault: Boolean,
    val hidden: Boolean,
    val accentHex: String?,
    val hasAvatar: Boolean
) {
    val id get() = name
    val accent: Color get() = Nb.colorFor(accentHex ?: name)
    val initials: String
        get() = title.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1) }.uppercase().ifEmpty { name.take(2).uppercase() }
    val isLive: Boolean get() = workerActive && activityAt != null && System.currentTimeMillis() / 1000 - activityAt < 120
    val subtitle: String get() = role.ifBlank { "Hermes agent" }
    val statusLine: String get() = if (isLive) "Working now" else if (activityAt != null) formatRelative(activityAt) else "Ready"

    companion object {
        fun parse(j: JSONObject): BotProfile? {
            val name = j.s("name")?.takeIf { it.isNotBlank() } ?: return null
            val meta = j.o("ui_meta")?.o("hermes-bots")
            val canonical = j.o("canonical_session")
            val last = j.o("last_session")
            val worker = j.o("worker_session")
            val fallback = if (name == "default") "Hermes" else name.replaceFirstChar { it.uppercase() }
            val title = meta?.s("title")?.takeIf { it.isNotBlank() }
                ?: j.s("display_name")?.takeIf { it.isNotBlank() } ?: fallback
            val role = meta?.s("description")?.takeIf { it.isNotBlank() } ?: j.s("description") ?: ""
            val activity = epochDate(canonical?.d("last_active"))
                ?: epochDate(last?.d("last_active")) ?: epochDate(worker?.d("last_active"))
            val workerAt = epochDate(worker?.d("last_active"))
            return BotProfile(
                name = name, title = title, role = role,
                provider = j.s("provider") ?: "", model = j.s("model") ?: "",
                skillCount = j.i("skill_count") ?: 0,
                canonicalSessionId = canonical?.s("id"),
                preview = canonical?.s("preview") ?: last?.s("preview") ?: "",
                activityAt = activity,
                workerActive = workerAt?.let { System.currentTimeMillis() / 1000 - it < 120 } ?: false,
                isDefault = j.b("is_default") ?: false,
                hidden = meta?.b("hidden") ?: false,
                accentHex = meta?.s("color"),
                hasAvatar = j.b("has_avatar") ?: false
            )
        }
    }
}

data class GroupMember(val id: String, val profile: String, val handle: String, val displayName: String) {
    val name: String get() = displayName.ifBlank { handle.replaceFirstChar { it.uppercase() } }
    companion object {
        fun parse(j: JSONObject): GroupMember? {
            val profile = j.s("profile") ?: j.s("member_id") ?: return null
            return GroupMember(j.s("member_id") ?: profile, profile, j.s("handle") ?: profile, j.s("display_name") ?: "")
        }
    }
}

data class GroupRoom(
    val roomId: String,
    val name: String,
    val members: List<GroupMember>,
    val updatedAt: Long?,
    val latestSeq: Int,
    val preview: String = "",
    val disbanded: Boolean = false
) {
    val id get() = roomId
    companion object {
        fun parse(j: JSONObject): GroupRoom? {
            val id = j.s("room_id")?.takeIf { it.isNotBlank() } ?: return null
            return GroupRoom(id, j.s("name") ?: "Group Chat",
                j.objects("members").mapNotNull(GroupMember::parse),
                epochDate(j.d("updated_at")), j.i("latest_seq") ?: 0, "",
                (j.d("disbanded_at") ?: 0.0) > 0)
        }
    }
}

data class RoomEvent(
    val seq: Int,
    val roomId: String,
    val eventId: String,
    val kind: String,
    val actorKind: String,
    val actorId: String,
    val actorName: String,
    val text: String,
    val timestamp: Long?,
    val status: String?,
    val reason: String?
) {
    val id get() = eventId.ifBlank { "$roomId-$seq" }
    val isMessage get() = kind == "message.user" || kind == "message.member" || kind == "message"
    val isNotice get() = kind.startsWith("turn.") || kind.startsWith("member.") || kind == "room.stop_requested" || kind == "room.disbanded"
    val displayName get() = actorName.ifBlank { if (actorId == "user" || actorKind == "user") "You" else actorId.replace("_", " ").replaceFirstChar { it.uppercase() } }
    companion object {
        fun parse(j: JSONObject): RoomEvent? {
            val seq = j.i("seq") ?: return null
            val payload = j.o("payload") ?: JSONObject()
            val actor = j.o("actor") ?: JSONObject()
            return RoomEvent(seq, j.s("room_id") ?: "", j.s("event_id") ?: "", j.s("kind") ?: "",
                actor.s("kind") ?: "", actor.s("id") ?: "", actor.s("display_name") ?: "",
                payload.s("text") ?: payload.s("content") ?: "", epochDate(j.d("created_at")),
                payload.s("status"), payload.s("reason") ?: payload.s("reason_code") ?: payload.s("error"))
        }
    }
}

enum class ToolStatus { RUNNING, DONE, FAILED }

data class ChatMessage(
    val id: String,
    val role: String, // user, assistant, tool, system
    val text: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val reasoning: String? = null,
    val toolName: String? = null,
    val toolArgs: String? = null,
    val toolResult: String? = null,
    val toolStatus: ToolStatus = ToolStatus.DONE,
    val toolPreview: String? = null,
    val toolDuration: Double? = null,
    val rowId: Int? = null,
    val streaming: Boolean = false
) {
    val isTool get() = role == "tool" || toolName != null
    val toolSummary: String get() {
        toolPreview?.takeIf { it.isNotBlank() }?.let { return it }
        val args = toolArgs
        if (!args.isNullOrBlank()) {
            runCatching {
                val j = JSONObject(args)
                for (key in listOf("command", "path", "query", "url", "description", "prompt", "name", "task")) {
                    j.s(key)?.takeIf { it.isNotBlank() }?.let { return it }
                }
            }
        }
        return toolName ?: "Tool call"
    }
}

data class Approval(
    val rpcId: Any,
    val requestId: String,
    val sessionId: String,
    val command: String,
    val description: String,
    val toolName: String,
    val choices: List<String>
) {
    val id get() = requestId.ifBlank { rpcId.toString() }
}

data class ClarifyQuestion(val id: String, val question: String, val choices: List<String>, val multiSelect: Boolean)
data class ClarifyRequest(val rpcId: Any, val sessionId: String, val questions: List<ClarifyQuestion>)

data class Toolset(val name: String, val description: String, val tools: List<String>, val enabled: Boolean) {
    val id get() = name
    companion object {
        fun parse(j: JSONObject): Toolset = Toolset(
            j.s("name") ?: "", j.s("description") ?: "", j.strings("tools"), j.b("enabled") ?: false
        )
    }
}

data class ActivityItem(
    val id: String,
    val kind: String, // tool, message, approval, bot, system
    val title: String,
    val detail: String,
    val date: Long = System.currentTimeMillis(),
    val botName: String = "",
    val success: Boolean = true
)

data class Routine(val id: String, val name: String, val schedule: String, val prompt: String, val enabled: Boolean, val nextRun: String, val bot: String) {
    companion object {
        fun parse(j: JSONObject, bot: String): Routine? {
            val name = j.s("name") ?: j.s("job_id") ?: j.s("id") ?: return null
            return Routine(name, name, j.s("schedule") ?: j.s("schedule_display") ?: "",
                j.s("prompt") ?: "", j.optBoolean("enabled", !j.optBoolean("paused", false)),
                j.s("next_run") ?: j.s("next_run_display") ?: "", bot)
        }
    }
}

fun clockTime(epochMillis: Long): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(epochMillis))

data class ModelChoice(val provider: String, val providerName: String, val model: String, val current: Boolean) {
    val id get() = "$provider/$model"
    val label get() = model.substringAfterLast('/')
}

data class SessionRow(val sessionId: String, val title: String, val messageCount: Int, val model: String, val startedAt: Long?, val running: Boolean) {
    val id get() = sessionId
    companion object {
        fun parse(j: JSONObject): SessionRow? {
            val id = j.s("session_id") ?: j.s("id") ?: return null
            return SessionRow(id, j.s("title") ?: "Untitled", j.optInt("message_count", 0),
                j.s("model") ?: "", epochDate(j.d("started_at")), j.optBoolean("running", false))
        }
    }
}

data class UsageStats(
    val inputTokens: Int, val outputTokens: Int, val cacheRead: Int, val totalTokens: Int,
    val apiCalls: Int, val contextTokens: Int, val contextLength: Int?, val compressionCount: Int,
    val avgLatency: Double?, val activeSubagents: Int, val model: String
) {
    val contextFraction: Float get() = if (contextLength == null || contextLength <= 0) 0f else (contextTokens.toFloat() / contextLength).coerceIn(0f, 1f)
    companion object {
        fun parse(j: JSONObject): UsageStats? {
            if (!j.has("total_tokens") && !j.has("input_tokens")) return null
            return UsageStats(j.optInt("input_tokens"), j.optInt("output_tokens"), j.optInt("cache_read_tokens"),
                j.optInt("total_tokens"), j.optInt("api_calls"), j.optInt("context_tokens"),
                if (j.has("context_length") && !j.isNull("context_length")) j.optInt("context_length") else null,
                j.optInt("compression_count"), if (j.has("avg_latency_s") && !j.isNull("avg_latency_s")) j.optDouble("avg_latency_s") else null,
                j.optInt("active_subagents"), j.optString("model"))
        }
    }
}
