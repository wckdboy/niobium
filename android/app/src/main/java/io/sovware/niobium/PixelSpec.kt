package io.sovware.niobium

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class PixelLook(val portrait: String, val accent: String = "auto", val customArt: PixelArt? = null) {
    val entry get() = PixelWardrobe.entries.firstOrNull { it.id == portrait } ?: PixelWardrobe.entries.first()
    val body get() = if (portrait in masculine) "man" else "woman"
    val valid get() = portrait in portraits && accent in listOf("auto") + accents && (customArt == null || (customArt.size == 48 && customArt.valid))
    fun toJson() = JSONObject().put("portrait", portrait).put("accent", accent).apply { customArt?.let { put("custom_art", it.toJson()) } }
    fun remixed(seed: String): PixelLook {
        val random = CharacterRandom(seed)
        val accents = (listOf("auto") + PixelLook.accents).filter { it != accent }
        return copy(accent = accents[random.index(accents.size)])
    }
    companion object {
        val portraits = PixelWardrobe.entries.map { it.id }
        val masculine = PixelWardrobe.entries.filter { it.body == "man" }.map { it.id }.toSet()
        val accents = PixelWardrobe.accents
        fun generated(archetype: String, body: String, seed: String, prompt: String = ""): PixelLook {
            val words = prompt.lowercase(java.util.Locale.ROOT).split(Regex("[^a-z0-9]+" )).filter { it.isNotEmpty() }.toSet()
            val choices = PixelWardrobe.entries.filter { it.body == if (body == "man") "man" else "woman" }
            fun score(item: PixelLookEntry) = item.tags.count { it in words } * 4 + if (item.archetype == archetype) 1 else 0
            val best = choices.maxOf { score(it) }
            val matches = choices.filter { score(it) == best }
            val random = CharacterRandom(seed + "|look")
            return PixelLook(matches[random.index(matches.size)].id)
        }
        fun parse(j: JSONObject): PixelLook {
            fun value(key: String, fallback: String = "auto") = if (j.has(key)) j.get(key) as? String ?: error("Expected $key") else fallback
            return PixelLook(value("portrait", ""), value("accent"), if (j.has("custom_art") && !j.isNull("custom_art")) PixelArt.parse(j.getJSONObject("custom_art")) else null)
        }
    }
}

data class PixelLookEntry(val id: String, val label: String, val archetype: String, val body: String, val family: String, val tags: List<String>)

// BEGIN GENERATED WARDROBE
object PixelWardrobe {
    val accents = listOf("orion", "nova", "forge", "sage", "nyx", "astra", "vesper", "rook")
    val entries = listOf(
        PixelLookEntry("orion", "Orion", "orion", "man", "Arcane", listOf("mage", "elf", "research", "moon", "cloak")),
        PixelLookEntry("nova", "Nova", "nova", "woman", "Cyber", listOf("neon", "hacker", "creative", "cyber")),
        PixelLookEntry("forge", "Forge", "forge", "man", "Cyber", listOf("engineer", "armor", "metal", "cyborg")),
        PixelLookEntry("sage", "Sage", "sage", "woman", "Wild", listOf("druid", "forest", "nature", "leaves")),
        PixelLookEntry("nyx", "Nyx", "nyx", "woman", "Dark", listOf("rogue", "goth", "vampire", "leather")),
        PixelLookEntry("astra", "Astra", "astra", "woman", "Cyber", listOf("pilot", "explorer", "robotics", "tech")),
        PixelLookEntry("vesper", "Vesper", "vesper", "woman", "Arcane", listOf("oracle", "astral", "priestess", "gold")),
        PixelLookEntry("rook", "Rook", "rook", "man", "Wild", listOf("ranger", "warrior", "leather", "forest")),
        PixelLookEntry("lilith", "Lilith", "nyx", "woman", "Dark", listOf("vampire", "goth", "crimson", "corset", "cloak")),
        PixelLookEntry("kael", "Kael", "orion", "man", "Arcane", listOf("elf", "moon", "mage", "vest", "tattoo")),
        PixelLookEntry("seraph", "Seraph", "vesper", "woman", "Arcane", listOf("paladin", "sun", "armor", "gold", "white")),
        PixelLookEntry("zero", "Zero", "forge", "man", "Cyber", listOf("cyborg", "harness", "tech", "chrome", "jacket")),
        PixelLookEntry("thorn", "Thorn", "sage", "woman", "Wild", listOf("fae", "forest", "druid", "antlers", "halter")),
        PixelLookEntry("echo", "Echo", "nova", "woman", "Cyber", listOf("hacker", "neon", "cyber", "crop", "pink")),
        PixelLookEntry("ember", "Ember", "rook", "man", "Dark", listOf("infernal", "horns", "warrior", "leather", "vest")),
        PixelLookEntry("luna", "Luna", "orion", "woman", "Arcane", listOf("witch", "moon", "astral", "sorceress", "halter")),
        PixelLookEntry("riven", "Riven", "rook", "man", "Dark", listOf("rogue", "leather", "coat", "harness", "stealth")),
        PixelLookEntry("sable", "Sable", "nyx", "woman", "Dark", listOf("assassin", "night", "corset", "strappy", "violet")),
        PixelLookEntry("ivy", "Ivy", "sage", "woman", "Wild", listOf("serpent", "druid", "emerald", "scale", "halter")),
        PixelLookEntry("volt", "Volt", "forge", "man", "Cyber", listOf("cyborg", "brawler", "neon", "vest", "lime")),
        PixelLookEntry("iris", "Iris", "nova", "woman", "Arcane", listOf("illusionist", "mage", "halter", "magenta", "gold")),
        PixelLookEntry("onyx", "Onyx", "vesper", "man", "Arcane", listOf("desert", "prince", "ivory", "gold", "harness")),
        PixelLookEntry("neve", "Neve", "astra", "woman", "Wild", listOf("frost", "ice", "knight", "armor", "white")),
        PixelLookEntry("sol", "Sol", "orion", "woman", "Arcane", listOf("sun", "sorceress", "gold", "purple", "corset")),
        PixelLookEntry("jade", "Jade", "sage", "woman", "Wild", listOf("samurai", "jade", "kimono", "blade", "wrap")),
        PixelLookEntry("azrael", "Azrael", "orion", "man", "Arcane", listOf("twilight", "elf", "violet", "noble", "coat")),
        PixelLookEntry("lyra", "Lyra", "vesper", "woman", "Wild", listOf("siren", "sea", "pearl", "plum", "halter")),
        PixelLookEntry("cinder", "Cinder", "forge", "man", "Cyber", listOf("engineer", "forge", "metal", "goggles", "tattoo")),
        PixelLookEntry("mira", "Mira", "nova", "woman", "Cyber", listOf("android", "dj", "cyber", "magenta", "crop")),
        PixelLookEntry("cass", "Cass", "astra", "woman", "Wild", listOf("pirate", "sky", "teal", "jacket", "corset")),
        PixelLookEntry("talon", "Talon", "rook", "man", "Wild", listOf("ranger", "warrior", "feather", "leather", "forest")),
        PixelLookEntry("opal", "Opal", "nyx", "woman", "Dark", listOf("oracle", "midnight", "goth", "lace", "corset")),
    )
}
// END GENERATED WARDROBE

data class CharacterRecipe(
    val body: String, val palette: String, val hair: String, val face: String,
    val outfit: String, val accessory: String, val skin: String = "auto",
    val hairTone: String = "auto", val detail: String = "none"
) {
    fun toJson() = JSONObject().put("body", body).put("palette", palette).put("hair", hair)
        .put("face", face).put("outfit", outfit).put("accessory", accessory)
        .put("skin", skin).put("hair_tone", hairTone).put("detail", detail)
    val valid: Boolean get() = fields.all { value(it) in options(it) }
    fun value(field: String): String = when (field) {
        "body" -> body; "palette" -> palette; "skin" -> skin; "hair_tone" -> hairTone
        "hair" -> hair; "face" -> face; "outfit" -> outfit; "accessory" -> accessory; "detail" -> detail
        else -> ""
    }
    fun withValue(field: String, value: String): CharacterRecipe {
        if (value !in options(field)) return this
        return when (field) {
            "body" -> copy(body = value); "palette" -> copy(palette = value); "skin" -> copy(skin = value)
            "hair_tone" -> copy(hairTone = value); "hair" -> copy(hair = value); "face" -> copy(face = value)
            "outfit" -> copy(outfit = value); "accessory" -> copy(accessory = value); "detail" -> copy(detail = value)
            else -> this
        }
    }
    fun remixed(archetype: String, seed: String, locked: Set<String>): CharacterRecipe {
        var next = generated(archetype, seed)
        fields.filter { it in locked }.forEach { next = next.withValue(it, value(it)) }
        if (("skin" in locked && skin == "auto") || ("hair_tone" in locked && hairTone == "auto")) {
            next = next.copy(palette = palette)
        }
        return next
    }
    companion object {
        val fields = listOf("body", "palette", "skin", "hair_tone", "hair", "face", "outfit", "accessory", "detail")
        val palettes = listOf("orion", "nova", "forge", "sage", "nyx", "astra", "vesper", "rook")
        val hairStyles = listOf("silver_swept", "violet_bob", "undercut", "locs", "cat_black", "white_pilot", "silver_bob", "headwrap", "braid", "mohawk", "long_waves", "high_ponytail")
        val faces = listOf("soft", "sharp", "calm", "fierce", "wry", "stern")
        val outfits = listOf("mage", "cyber", "armor", "druid", "rogue", "pilot", "veil", "ranger", "corsair", "techwear", "battlemage", "astral")
        val accessories = listOf("none", "visor", "goggles", "antlers", "cat_ears", "circlet", "veil", "headwrap", "horns", "antenna", "ear_cuff", "halo")
        val skinTones = listOf("auto", "porcelain", "sand", "copper", "umber", "ebony")
        val hairTones = listOf("auto", "silver", "ink", "violet", "moss", "copper", "rose", "ice", "gold")
        val details = listOf("none", "freckles", "scar", "cyber_eye", "rune", "stubble")
        val outfitPools = mapOf(
            "orion" to listOf("mage", "battlemage", "astral", "veil"), "nova" to listOf("cyber", "astral", "corsair", "techwear"),
            "forge" to listOf("armor", "techwear", "pilot", "battlemage"), "sage" to listOf("druid", "ranger", "astral", "mage"),
            "nyx" to listOf("rogue", "techwear", "cyber", "corsair"), "astra" to listOf("pilot", "techwear", "armor", "corsair"),
            "vesper" to listOf("veil", "astral", "mage", "corsair"), "rook" to listOf("ranger", "corsair", "armor", "techwear")
        )
        val cast = linkedMapOf(
            "orion" to CharacterRecipe("man", "orion", "silver_swept", "calm", "mage", "circlet"),
            "nova" to CharacterRecipe("woman", "nova", "violet_bob", "soft", "cyber", "visor"),
            "forge" to CharacterRecipe("man", "forge", "undercut", "sharp", "armor", "goggles"),
            "sage" to CharacterRecipe("woman", "sage", "locs", "soft", "druid", "antlers"),
            "nyx" to CharacterRecipe("woman", "nyx", "cat_black", "fierce", "rogue", "cat_ears"),
            "astra" to CharacterRecipe("woman", "astra", "white_pilot", "sharp", "pilot", "visor"),
            "vesper" to CharacterRecipe("woman", "vesper", "silver_bob", "calm", "veil", "veil"),
            "rook" to CharacterRecipe("man", "rook", "headwrap", "calm", "ranger", "headwrap")
        )
        fun options(field: String): List<String> = when (field) {
            "body" -> listOf("man", "woman"); "palette" -> palettes; "skin" -> skinTones; "hair_tone" -> hairTones
            "hair" -> hairStyles; "face" -> faces; "outfit" -> outfits; "accessory" -> accessories; "detail" -> details
            else -> emptyList()
        }
        fun generated(archetype: String, seed: String): CharacterRecipe {
            val random = CharacterRandom(archetype + "|" + seed)
            var recipe = cast[archetype] ?: cast.getValue("orion")
            fields.forEach { field ->
                val choices = when (field) {
                    "outfit" -> outfitPools[archetype] ?: outfits
                    "skin" -> skinTones.drop(1); "hair_tone" -> hairTones.drop(1)
                    else -> options(field)
                }
                recipe = recipe.withValue(field, choices[random.index(choices.size)])
            }
            return recipe
        }
        fun parse(j: JSONObject): CharacterRecipe {
            fun string(key: String, fallback: String? = null): String =
                if (!j.has(key) && fallback != null) fallback else j.get(key) as? String ?: error("Expected a string: " + key)
            return CharacterRecipe(string("body"), string("palette"), string("hair"), string("face"), string("outfit"),
                string("accessory"), string("skin", "auto"), string("hair_tone", "auto"), string("detail", "none"))
        }
    }
}

internal class CharacterRandom(seed: String) {
    private var state: UInt = 2166136261u
    init {
        seed.toByteArray(Charsets.UTF_8).forEach { state = (state xor it.toUByte().toUInt()) * 16777619u }
        if (state == 0u) state = 0xA341316Cu
    }
    fun index(count: Int): Int {
        state = state xor (state shl 13); state = state xor (state shr 17); state = state xor (state shl 5)
        return (state % count.toUInt()).toInt()
    }
}

data class PixelPermissions(val approvalMode: String = "ask", val network: Boolean = false, val filesystem: String = "workspace", val shell: Boolean = false) {
    fun toJson() = JSONObject().put("approval_mode", approvalMode).put("network", network).put("filesystem", filesystem).put("shell", shell)
}
data class PixelCharacter(val archetype: String, val age: Int, val recipe: CharacterRecipe, val look: PixelLook? = null)
data class PixelCompanion(val kind: String, val name: String, val trait: String, val art: PixelArt? = null)
data class PixelSpec(
    val name: String, val title: String, val role: String, val personality: List<String>, val soul: String,
    val permissions: PixelPermissions, val toolsets: List<String>, val skills: List<String>, val connectors: List<String>,
    val reasoning: String, val character: PixelCharacter, val companion: PixelCompanion
) {
    val permittedToolsets: List<String> get() = toolsets.filter {
        when (it) {
            "memory", "todo", "skills", "delegation", "messaging" -> true
            "web", "browser", "vision", "image_gen", "connections" -> permissions.network
            "file" -> permissions.filesystem != "none"
            "terminal", "code_execution" -> permissions.shell && permissions.filesystem != "none"
            else -> false
        }
    }
    val validationError: String? get() = when {
        !name.matches(Regex("^[a-z][a-z0-9-]{1,30}$")) -> "Use a lowercase name of 2–31 letters, digits or hyphens."
        title.isEmpty() || title.length > 24 || role.isEmpty() || role.length > 80 -> "The Pixel needs a short title and role."
        personality.size !in 3..6 || personality.any { it.isEmpty() || it.length > 60 } -> "Choose 3–6 short personality traits."
        soul.trim().split(Regex("\\s+")).size !in 120..600 -> "The operator briefing must be 120–600 words."
        permissions.approvalMode !in listOf("ask", "workspace_session", "session") ||
            permissions.filesystem !in listOf("none", "workspace", "unrestricted") -> "Invalid permission policy."
        skills.size > 6 || connectors.size > 4 || reasoning !in listOf("low", "medium", "high") -> "Invalid capability selection."
        connectors.any { it !in listOf("github", "notion", "linear", "slack", "google", "filesystem") } -> "Unknown connector."
        character.archetype !in CharacterRecipe.cast || character.age !in 18..120 || !character.recipe.valid -> "Choose an adult character and valid 48×48 layers."
        character.look?.valid == false -> "Choose a valid portrait and colors."
        companion.art != null && (companion.art.size != 16 || !companion.art.valid) -> "Choose valid transparent companion art."
        companion.kind !in PixelBuilder.companions || companion.name.isEmpty() || companion.name.length > 24 || companion.trait.length > 240 -> "Invalid companion."
        toolsets.size > 20 || toolsets.distinct().size != toolsets.size -> "Invalid tool selection."
        else -> null
    }
    val policyBriefing: String get() = """

Niobium operator policy: ask before irreversible actions. Requested approval scope: ${permissions.approvalMode}.
Network: ${if (permissions.network) "allowed for this job" else "do not access the network"}. Shell: ${if (permissions.shell) "allowed, with host approvals" else "do not execute commands"}.
Filesystem: ${permissions.filesystem}. Workspace scope means stay inside the current project; the host owns sandbox enforcement.
Companion: ${companion.name}, an original ${if (companion.art == null) companion.kind else "custom creature"}; ${companion.trait}.
For a complex task, isolate bounded subtasks with delegate_task when available. Give each child a concrete goal, the same or narrower tool access, and report its actual result. Never invent a helper's activity or completion. Keep responsibility for integrating and checking the work.
Collaborate with other Pixels through attributed message_agent calls in Bot Chat or @mentions in rooms. A queued delivery is not a completed reply.
Requested connections: ${connectors.joinToString(", ")}. A request does not authorize an account: use only accounts the owner has connected, and ask for any missing access.
"""
    fun toJson() = JSONObject().put("name", name).put("title", title).put("role", role)
        .put("personality", JSONArray(personality)).put("soul", soul).put("permissions", permissions.toJson())
        .put("toolsets", JSONArray(toolsets)).put("skills", JSONArray(skills)).put("connectors", JSONArray(connectors))
        .put("reasoning", reasoning).put("character", JSONObject().put("archetype", character.archetype)
            .put("age", character.age).put("recipe", character.recipe.toJson()).apply { character.look?.let { put("look", it.toJson()) } })
        .put("companion", JSONObject().put("kind", companion.kind).put("name", companion.name).put("trait", companion.trait).apply { companion.art?.let { put("art", it.toJson()) } })
}
data class PixelDraft(val spec: PixelSpec, val usedModel: Boolean, val availableToolsets: Set<String>, val availableSkills: Set<String>, val notes: List<String>) {
    val enabledToolsets get() = spec.permittedToolsets.filter { it in availableToolsets }
}
