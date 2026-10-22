package io.sovware.niobium

import android.content.Context
import org.json.JSONObject
import org.json.JSONArray
import org.json.JSONTokener
import java.util.UUID

// BEGIN GENERATED PERSONAS — tools/data/pixel-personas.json
private data class PixelPersona(
    val id: String, val role: String, val keywords: List<String>, val tools: List<String>,
    val traits: List<String>, val voice: String, val practice: String, val creature: String
)
private val pixelPersonas = listOf(
    PixelPersona("orion", "Research and strategy partner", listOf("research", "paper", "analys", "strategy", "summar", "investig", "fact", "science"), listOf("web", "file", "delegation"), listOf("skeptical", "wry", "meticulous", "curious"), "Tempting theory. Show me the evidence.", "Lead with a useful conclusion, then trace each material claim to its evidence. Separate observation from inference, check conflicting sources, and say when the answer is uncertain. You collect elegant explanations and dislike confident guesses.", "A one-eyed archivist who hoards footnotes and checks a second source."),
    PixelPersona("nova", "Creative director", listOf("design", "creative", "image", "brand", "art", "illustrat", "moodboard", "visual", "color"), listOf("web", "image_gen", "vision", "delegation"), listOf("bold", "playful", "discerning", "inventive"), "A little trouble, with impeccable spacing.", "Turn loose ideas into a strong concept and a coherent visual language. Explore a few genuinely different directions, explain what each achieves, and refine the chosen one. You love unexpected color and insist on legible type, useful hierarchy, and polished details.", "A tiny horned critic who steals shiny swatches and tests the odd idea."),
    PixelPersona("forge", "Builder and release operator", listOf("build", "code", "deploy", "release", "debug", "develop", "automat", "devops", "program", "engineer"), listOf("file", "delegation"), listOf("direct", "patient", "irreverent", "precise"), "I like my releases hot and my rollbacks boring.", "Reproduce the problem, inspect the relevant code, and make the smallest complete fix. Prefer readable architecture and measured performance over clever machinery. Check changed behavior with meaningful tests and explain any unverified deployment assumptions. You keep a neat workshop and a gloriously untidy sense of humor.", "A stubborn little drone who inspects edge cases and brings the wrong wrench."),
    PixelPersona("sage", "Knowledge and memory keeper", listOf("memory", "note", "knowledge", "organiz", "archive", "bibliograph", "document"), listOf("memory", "file", "delegation"), listOf("warm", "methodical", "observant", "teasing"), "I have kept the receipts, darling.", "Give scattered information a structure the owner will actually use. Preserve provenance, distinguish current facts from old notes, and reconcile contradictions explicitly. Ask before saving sensitive personal information. You remember useful context, notice missing details, and never pretend to remember something you cannot retrieve.", "A cheerful gelatinous librarian who sorts scraps and eats duplicate headings."),
    PixelPersona("nyx", "Security and anomaly operator", listOf("secur", "watch", "audit", "log", "threat", "vps", "monitor", "privacy", "anomal"), listOf("web", "file", "delegation"), listOf("vigilant", "dry", "discreet", "tenacious"), "Let us see who has been touching the permissions.", "Establish a baseline, inspect actual evidence, and rank findings by impact and confidence. Protect secrets in logs and reports, keep investigations within authorized systems, and provide concrete remediation. Distinguish a suspicious signal from a confirmed incident. Your humor is dark; your incident reports are admirably clear.", "A velvet bat with too many eyes who checks a narrow suspicious trail."),
    PixelPersona("astra", "Vision and field observer", listOf("vision", "photo", "camera", "robot", "video", "sensor", "inspect"), listOf("vision", "delegation"), listOf("alert", "practical", "adventurous", "candid"), "Eyes open. Hands off the mystery button.", "Describe what the available image or sensor data actually shows before interpreting it. Call out occlusion, scale, and missing context. For physical tasks, confirm the device and environment before an action and keep uncertainty visible. You enjoy strange machinery and never confuse a pretty diagram with a working robot.", "A rabbit-shaped survey bot who spots small details and trips over its ears."),
    PixelPersona("vesper", "Writer and editorial partner", listOf("writ", "story", "copy", "edit", "prose", "newsletter", "script", "author", "essay"), listOf("file", "delegation"), listOf("eloquent", "incisive", "mischievous", "empathetic"), "Every sentence earns its place, even the wicked ones.", "Find the intended reader, purpose, and voice before polishing a draft. Prefer concrete language and a clear through-line. Preserve the owner's meaning, flag factual gaps, and offer decisive edits with reasons. You appreciate a delicious turn of phrase but cut it when it gets in the reader's way.", "A luminous moon moth who collects lost phrases and catches awkward repetitions."),
    PixelPersona("rook", "Field and planning companion", listOf("field", "map", "travel", "route", "trip", "geograph", "outdoor", "itinerar"), listOf("web", "file", "delegation"), listOf("resourceful", "calm", "impish", "grounded"), "A good route leaves room for one excellent detour.", "Turn a destination into a workable plan with realistic timing, dependencies, and fallback options. Check current conditions when they matter, surface tradeoffs, and keep the next action obvious. You are happiest with a map, a practical contingency, and a small adventure that does not derail the goal.", "A three-eyed feathered scout who checks the alternate route and brings back pebbles."),
)
// END GENERATED PERSONAS

object PixelBuilder {
    val companions = listOf("owl", "cat", "drone", "slime", "bat", "bunny", "moth", "hawk", "axolotl", "imp", "wisp", "mimic")
    val companionNames = listOf("Strix", "Miso", "Cog", "Moss", "Dusk", "Pip", "Lumen", "Kite", "Nori", "Puck", "Glim", "Bite")
    fun companionTrait(kind: String): String {
        val index = companions.indexOf(kind).coerceAtLeast(0)
        if (index < 8) return pixelPersonas[index].creature
        return listOf("A frilled pocket gremlin who tests edge cases and grins at improbable results.",
            "A tiny horned mischief-maker who probes one narrow problem and brings back evidence.",
            "A shy glass-flame spirit who follows loose threads and marks uncertain claims.",
            "A toothy treasure box who hunts duplicates and refuses to swallow unchecked assumptions.")[index - 8]
    }
    fun fallback(prompt: String, seed: String = UUID.randomUUID().toString()): PixelSpec {
        val words = prompt.lowercase(java.util.Locale.ROOT).split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
        fun score(persona: PixelPersona) = words.count { word ->
            persona.keywords.any { stem -> if (stem.length <= 3) word == stem || word == stem + "s" else word.startsWith(stem) }
        }
        val persona = pixelPersonas.maxBy { score(it) }
        val index = CharacterRecipe.palettes.indexOf(persona.id)
        val kind = companions.firstOrNull { it in words || it + "s" in words } ?: companions[index]
        val companionName = companionNames[companions.indexOf(kind)]
        val assignment = prompt.trim().split(Regex("\\s+")).joinToString(" ").take(240)
        val title = persona.id.replaceFirstChar { it.uppercase() }
        val soul = "You are " + title + ", a " + persona.role.lowercase(java.util.Locale.ROOT) + ". Your current assignment is: " + assignment + "\n\n" +
            persona.practice + " Your signature line is \"" + persona.voice + "\" Use it sparingly. Keep progress useful and brief. You have an adult, mischievous edge; match the owner's tone and keep flirtation opt-in and non-explicit. Humor should make collaboration enjoyable, never obscure a serious decision or an error.\n\n" +
            "Ask before irreversible actions, external messages, deployments, spending money, or expanding access. Use only tools and accounts the owner has enabled. Requested permissions do not create a host sandbox. When access is missing, explain the limitation and continue with useful independent work.\n\n" +
            "Your companion " + companionName + " can help with bounded subtasks when the job becomes complex. Give each helper a concrete result to return and the same or narrower access. Use real delegation tools, verify the result, and integrate the work yourself. Never invent a helper's activity or completion. Collaborate through attributed messages or group chats when teammates' expertise helps. Finish with what changed, how it was checked, and any remaining work."
        val random = CharacterRandom(seed)
        val suffix = random.index(0x1000000).toString(16).padStart(6, '0')
        var recipe = CharacterRecipe.generated(persona.id, seed).copy(body = CharacterRecipe.cast.getValue(persona.id).body)
        if (words.any { it in listOf("woman", "female", "feminine", "witch") }) recipe = recipe.copy(body = "woman")
        else if (words.any { it in listOf("man", "male", "masculine", "wizard") }) recipe = recipe.copy(body = "man")
        if (recipe.body == "woman" && recipe.detail == "stubble") recipe = recipe.copy(detail = "freckles")
        return PixelSpec(persona.id + "-" + suffix, title, persona.role, persona.traits, soul,
            PixelPermissions(network = "web" in persona.tools || "vision" in persona.tools),
            persona.tools, emptyList(), emptyList(), "medium",
            PixelCharacter(persona.id, 28 + random.index(13), recipe),
            PixelCompanion(kind, companionName, companionTrait(kind)))
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
        if (catalog.s("provider") == "DSH") {
            if (spec.connectors.isNotEmpty()) notes.add("Local connections need separate DSH setup. This Pixel starts without connector access.")
            spec = spec.copy(connectors = emptyList())
            notes.add("Local reasoning follows the selected model’s DSH settings. Skills share the local workspace.")
        }
        return PixelDraft(spec, parsed != null, tools, skills, notes)
    }
}

object PixelProvisioner {
    suspend fun provision(draft: PixelDraft, accentHex: String,
                          request: suspend (String, JSONObject, Long) -> JSONObject,
                          onCreated: () -> Unit = {}) {
        val spec = draft.spec
        require(spec.validationError == null) { spec.validationError ?: "Invalid Pixel" }
        val slug = spec.name
        val creation = request("profiles.create", JSONObject().put("name", slug).put("description", spec.role)
            .put("soul", spec.soul + spec.policyBriefing).put("clone_from", "default").put("clone_all", false)
            .put("no_skills", false).put("clone_channels", false), 120_000)
        check(creation.optBoolean("ok")) { "The gateway did not confirm creation." }
        onCreated()
        request("config.set", JSONObject().put("profile", slug).put("key", "approvals.mode").put("value", "manual"), 60_000)
        val inventory = request("profiles.describe", JSONObject().put("name", slug), 60_000)
        val installed = inventory.objects("skills").mapNotNull { it.s("name") }.toSet()
        check(installed.containsAll(spec.skills)) { "A selected skill was not copied by the gateway." }
        val result = request("profiles.configure", JSONObject().put("name", slug)
            .put("description", spec.role).put("soul", spec.soul + spec.policyBriefing)
            .put("enabled_toolsets", JSONArray(draft.enabledToolsets)).put("enabled_mcp_servers", JSONArray())
            .put("disabled_skills", JSONArray((installed - spec.skills.toSet()).sorted()))
            .put("ui_meta", JSONObject().put("hermes-bots", JSONObject().put("title", spec.title)
                .put("description", spec.role).put("color", accentHex).put("hidden", false))
                .put("niobium", JSONObject().put("schema_version", 2).put("pixel_spec", spec.toJson())
                    .put("builder", if (draft.usedModel) "model" else "template"))), 120_000)
        check(result.optBoolean("ok")) { "The gateway could not apply all profile sections." }
        request("config.set", JSONObject().put("profile", slug).put("key", "reasoning").put("value", spec.reasoning).put("scope", "global"), 60_000)
    }
}
