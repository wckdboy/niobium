package io.sovware.niobium

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

data class PixelSpec(
    val name: String, val title: String, val role: String, val personality: List<String>,
    val soul: String, val toolsets: List<String>, val skills: List<String>, val connectors: List<String>,
    val archetype: String, val companion: String
)

object PixelBuilder {
    fun fallback(prompt: String): PixelSpec {
        val p = prompt.lowercase()
        val archetype: String; val role: String; val tools: List<String>
        when {
            listOf("secur", "watch", "audit", "log").any { it in p } -> { archetype = "nyx"; role = "Security and anomaly operator"; tools = listOf("web", "terminal", "filesystem") }
            listOf("build", "code", "deploy", "release").any { it in p } -> { archetype = "forge"; role = "Builder and release operator"; tools = listOf("terminal", "filesystem", "web") }
            listOf("design", "creative", "image", "brand").any { it in p } -> { archetype = "nova"; role = "Creative director"; tools = listOf("web", "image_gen", "vision") }
            listOf("memory", "note", "knowledge", "research").any { it in p } -> { archetype = "sage"; role = "Knowledge and memory keeper"; tools = listOf("memory", "web", "filesystem") }
            listOf("vision", "photo", "camera").any { it in p } -> { archetype = "astra"; role = "Vision and field observer"; tools = listOf("vision", "web", "filesystem") }
            listOf("field", "travel", "monitor").any { it in p } -> { archetype = "rook"; role = "Field researcher"; tools = listOf("web", "filesystem") }
            else -> { archetype = "orion"; role = "Research and strategy partner"; tools = listOf("web", "filesystem") }
        }
        val m = Cast.all.firstOrNull { it.id == archetype } ?: Cast.all[0]
        val slugBase = role.lowercase().split(" ").take(2).joinToString("-").filter { it.isLetterOrDigit() || it == '-' }
        return PixelSpec((slugBase.ifBlank { "pixel" }) + "-" + Random.nextInt(10, 99), role.split(" ").take(2).joinToString(" "),
            role, listOf("direct", "careful", "useful"),
            "You are $role. You work in the open, ask before irreversible actions, keep notes short, and tell the user what changed. When a task needs another set of hands, call your companion ${m.companionName}. You never pretend a tool succeeded when it did not.",
            tools, emptyList(), emptyList(), archetype, m.companion)
    }

    fun parse(text: String): PixelSpec? {
        val raw = text.substringAfter('{', "").let { if (it.isEmpty()) "" else "{" + it.substringBeforeLast('}') + "}" }
        if (raw.isBlank()) return null
        return runCatching {
            val j = JSONObject(raw)
            fun arr(k: String): List<String> = j.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList()
            val c = j.optJSONObject("character") ?: JSONObject()
            val comp = j.optJSONObject("companion") ?: JSONObject()
            PixelSpec(
                j.optString("name", "pixel").lowercase().replace(" ", "-"),
                j.optString("title", "Pixel"), j.optString("role", "Hermes Pixel"),
                arr("personality").ifEmpty { listOf("direct", "careful", "useful") },
                j.optString("soul", "You are a careful, useful Hermes Pixel."),
                arr("toolsets"), arr("skills"), arr("connectors"),
                c.optString("archetype", "orion"), comp.optString("kind", "owl")
            )
        }.getOrNull()
    }

    suspend fun build(context: Context, client: GatewayClient?, prompt: String): Pair<PixelSpec, Boolean> {
        if (client == null) return fallback(prompt) to false
        val skill = runCatching {
            context.assets.open("bot_build.md").bufferedReader().use { it.readText() }
        }.getOrDefault("Return one strict PixelSpec JSON object.")
        val instructions = "You are the Niobium Pixel builder. Follow this skill exactly. Return ONLY one JSON object, no fences, no prose.\n\n$skill"
        val result = runCatching {
            client.request("llm.oneshot", JSONObject()
                .put("profile", "default").put("instructions", instructions)
                .put("input", prompt).put("max_tokens", 1800).put("temperature", 0.7), 90_000)
        }.getOrNull()
        val text = result?.s("text") ?: return fallback(prompt) to false
        return (parse(text) ?: fallback(prompt)) to true
    }
}
