package io.sovware.niobium

import android.content.Context
import org.json.JSONObject
import org.json.JSONTokener
import java.util.UUID

object PixelBuilder {
    val companions = listOf("owl", "cat", "drone", "slime", "bat", "bunny", "moth", "hawk")
    private val companionNames = listOf("Strix", "Miso", "Cog", "Moss", "Dusk", "Pip", "Lumen", "Kite")
    fun fallback(prompt: String): PixelSpec {
        val p = prompt.lowercase()
        val archetype: String; val role: String; val tools: List<String>
        when {
            listOf("secur", "watch", "audit", "log").any { it in p } -> { archetype = "nyx"; role = "Security and anomaly operator"; tools = listOf("web", "file", "delegation") }
            listOf("build", "code", "deploy", "release").any { it in p } -> { archetype = "forge"; role = "Builder and release operator"; tools = listOf("file", "delegation") }
            listOf("design", "creative", "image", "brand").any { it in p } -> { archetype = "nova"; role = "Creative director"; tools = listOf("web", "image_gen", "vision", "delegation") }
            listOf("memory", "note", "knowledge").any { it in p } -> { archetype = "sage"; role = "Knowledge and memory keeper"; tools = listOf("memory", "file", "delegation") }
            listOf("vision", "photo", "camera").any { it in p } -> { archetype = "astra"; role = "Vision and field observer"; tools = listOf("vision", "delegation") }
            else -> { archetype = "orion"; role = "Research and strategy partner"; tools = listOf("web", "file", "delegation") }
        }
        val index = CharacterRecipe.cast.keys.indexOf(archetype).coerceAtLeast(0)
        val kind = companions.firstOrNull { it in p } ?: companions[index]
        val companionName = companionNames[companions.indexOf(kind)]
        val soul = """
You are a ${role.lowercase()}. Turn the user's request into a clear goal, identify the information you need, and work in small verifiable steps. Explain material assumptions before acting and keep progress reports brief. You have a dry wit, a mischievous streak, and a soft spot for difficult problems. Your playful banter never obscures a serious decision. Match the user's tone and keep flirting opt-in, adult, and non-explicit.

Ask before irreversible actions, external messages, deployments, spending money, or expanding access. Work only with the tools and accounts the owner has enabled. Never claim that a requested permission is a host sandbox. If a necessary capability is unavailable, explain the missing access and continue with useful independent work. Learn new skills only when they are available from the gateway and the owner wants them.

Your companion $companionName helps with bounded subtasks when a job becomes complex. Give every delegated task a concrete result to return, keep its access no broader than yours, and verify the result before integrating it. Use real delegation tools and report failures honestly. Collaborate with teammates in group chats or attributed direct messages when their expertise helps. Finish with what changed, how it was checked, and any remaining work.
""".trim()
        return PixelSpec("$archetype-${UUID.randomUUID().toString().take(6)}", archetype.replaceFirstChar { it.uppercase() },
            role, listOf("direct", "mischievous", "resourceful"), soul,
            PixelPermissions(network = "web" in tools || "vision" in tools), tools, emptyList(), emptyList(), "medium",
            PixelCharacter(archetype, 28, CharacterRecipe.cast.getValue(archetype)),
            PixelCompanion(kind, companionName, "Curious, loyal, and excellent at checking a second angle."))
    }
    fun parse(text: String): PixelSpec? = runCatching {
        val raw = text.trim()
        require(raw.startsWith("{") && raw.endsWith("}"))
        val input = JSONTokener(raw)
        val j = input.nextValue() as? JSONObject ?: error("Expected a Pixel object")
        require(input.nextClean() == '\u0000') { "Trailing content" }
        fun string(objectValue: JSONObject, key: String): String = objectValue.get(key) as? String ?: error("Expected a string: $key")
        fun boolean(objectValue: JSONObject, key: String): Boolean = objectValue.get(key) as? Boolean ?: error("Expected a boolean: $key")
        fun arr(key: String): List<String> {
            val a = j.getJSONArray(key)
            return (0 until a.length()).map { a.get(it) as? String ?: error("Expected string entries") }
        }
        val p = j.getJSONObject("permissions")
        val c = j.getJSONObject("character")
        val companion = j.getJSONObject("companion")
        val age = c.get("age") as? Number ?: error("Expected numeric adult age")
        require(age.toDouble() == age.toInt().toDouble()) { "Expected whole adult age" }
        PixelSpec(string(j, "name"), string(j, "title"), string(j, "role"), arr("personality"), string(j, "soul"),
            PixelPermissions(string(p, "approval_mode"), boolean(p, "network"), string(p, "filesystem"), boolean(p, "shell")),
            arr("toolsets"), arr("skills"), arr("connectors"), string(j, "reasoning"),
            PixelCharacter(string(c, "archetype"), age.toInt(), CharacterRecipe.parse(c.getJSONObject("recipe"))),
            PixelCompanion(string(companion, "kind"), string(companion, "name"), string(companion, "trait")))
            .takeIf { it.validationError == null }
    }.getOrNull()

    suspend fun draft(context: Context, client: GatewayClient?, prompt: String): PixelDraft {
        val clean = prompt.trim()
        require(clean.isNotEmpty() && clean.length <= 4000) { "Describe the job in 1–4,000 characters." }
        val defaults = setOf("web", "file", "memory", "todo", "skills", "vision", "image_gen", "terminal", "delegation")
        if (client == null) return PixelDraft(fallback(clean), false, defaults, emptySet(), listOf("Local template. Review the briefing and access before creating."))
        val catalog = client.request("profiles.describe", JSONObject().put("name", "default"))
        val tools = catalog.objects("toolsets").mapNotNull { it.s("name") }.toSet()
        val skills = catalog.objects("skills").mapNotNull { it.s("name") }.toSet()
        val skill = context.assets.open("bot_build.md").bufferedReader().use { it.readText() }
        val notes = mutableListOf<String>()
        val parsed = try {
            val result = client.request("llm.oneshot", JSONObject().put("profile", "default")
                .put("instructions", "$skill\nAvailable toolsets: ${tools.sorted().joinToString()}. Available skills: ${skills.sorted().joinToString()}. Only request listed capabilities.")
                .put("input", clean).put("max_tokens", 2600).put("temperature", 0.7), 90_000)
            parse(result.s("text") ?: "").also { if (it == null) notes.add("The model returned an invalid PixelSpec. This is a local template.") }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { notes.add("The model was unavailable. This is a local template."); null }
        var spec = parsed ?: fallback(clean)
        val removed = spec.toolsets.filter { it !in tools || it !in spec.permittedToolsets }
        if (removed.isNotEmpty()) notes.add("Excluded unavailable or disallowed tools: ${removed.joinToString()}.")
        if (spec.connectors.isNotEmpty()) notes.add("Connections are requests. Authorize accounts separately on your Hermes host.")
        val missing = spec.skills.filter { it !in skills }
        if (missing.isNotEmpty()) notes.add("Unavailable skills excluded: ${missing.joinToString()}.")
        spec = spec.copy(skills = spec.skills.filter { it in skills })
        return PixelDraft(spec, parsed != null, tools, skills, notes)
    }
}
