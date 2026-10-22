package io.sovware.niobium

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class CharacterRecipe(val body: String, val palette: String, val hair: String, val face: String, val outfit: String, val accessory: String) {
    fun toJson() = JSONObject().put("body", body).put("palette", palette).put("hair", hair)
        .put("face", face).put("outfit", outfit).put("accessory", accessory)
    val valid: Boolean get() = body in listOf("man", "woman") && palette in cast &&
        cast.values.any { it.hair == hair } && face in listOf("soft", "sharp", "calm", "fierce") &&
        cast.values.any { it.outfit == outfit } && accessory in listOf("visor", "goggles", "antlers", "cat_ears", "circlet", "veil", "headwrap", "none")
    companion object {
        val cast = linkedMapOf(
            "orion" to CharacterRecipe("man", "orion", "silver_swept", "calm", "mage", "circlet"),
            "nova" to CharacterRecipe("woman", "nova", "violet_bob", "soft", "cyber", "visor"),
            "forge" to CharacterRecipe("man", "forge", "undercut", "sharp", "armor", "goggles"),
            "sage" to CharacterRecipe("woman", "sage", "locs", "soft", "druid", "antlers"),
            "nyx" to CharacterRecipe("woman", "nyx", "cat_black", "fierce", "rogue", "cat_ears"),
            "astra" to CharacterRecipe("man", "astra", "white_pilot", "sharp", "pilot", "visor"),
            "vesper" to CharacterRecipe("woman", "vesper", "silver_bob", "calm", "veil", "veil"),
            "rook" to CharacterRecipe("man", "rook", "headwrap", "calm", "ranger", "headwrap")
        )
        fun parse(j: JSONObject) = CharacterRecipe(j.getString("body"), j.getString("palette"), j.getString("hair"),
            j.getString("face"), j.getString("outfit"), j.getString("accessory"))
    }
}
data class PixelPermissions(val approvalMode: String = "ask", val network: Boolean = false, val filesystem: String = "workspace", val shell: Boolean = false) {
    fun toJson() = JSONObject().put("approval_mode", approvalMode).put("network", network).put("filesystem", filesystem).put("shell", shell)
}
data class PixelCharacter(val archetype: String, val age: Int, val recipe: CharacterRecipe)
data class PixelCompanion(val kind: String, val name: String, val trait: String)
data class PixelSpec(
    val name: String, val title: String, val role: String, val personality: List<String>, val soul: String,
    val permissions: PixelPermissions, val toolsets: List<String>, val skills: List<String>, val connectors: List<String>,
    val reasoning: String, val character: PixelCharacter, val companion: PixelCompanion
) {
    val permittedToolsets: List<String> get() = toolsets.filter {
        when (it) {
            "memory", "todo", "skills", "delegation" -> true
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
        companion.kind !in PixelBuilder.companions || companion.name.isEmpty() || companion.name.length > 24 || companion.trait.length > 240 -> "Invalid companion."
        toolsets.size > 20 || toolsets.distinct().size != toolsets.size -> "Invalid tool selection."
        else -> null
    }
    val policyBriefing: String get() = """

Niobium operator policy: ask before irreversible actions. Requested approval scope: ${permissions.approvalMode}.
Network: ${if (permissions.network) "allowed for this job" else "do not access the network"}. Shell: ${if (permissions.shell) "allowed, with host approvals" else "do not execute commands"}.
Filesystem: ${permissions.filesystem}. Workspace scope means stay inside the current project; the host owns sandbox enforcement.
Companion: ${companion.name}, an original ${companion.kind}; ${companion.trait}.
For a complex task, isolate bounded subtasks with delegate_task when available. Give each child a concrete goal, the same or narrower tool access, and report its actual result. Never invent a helper's activity or completion. Keep responsibility for integrating and checking the work.
Collaborate with other Pixels through attributed message_agent calls in Bot Chat or @mentions in rooms. A queued delivery is not a completed reply.
Requested connections: ${connectors.joinToString(", ")}. A request does not authorize an account: use only accounts the owner has connected, and ask for any missing access.
"""
    fun toJson() = JSONObject().put("name", name).put("title", title).put("role", role)
        .put("personality", JSONArray(personality)).put("soul", soul).put("permissions", permissions.toJson())
        .put("toolsets", JSONArray(toolsets)).put("skills", JSONArray(skills)).put("connectors", JSONArray(connectors))
        .put("reasoning", reasoning).put("character", JSONObject().put("archetype", character.archetype)
            .put("age", character.age).put("recipe", character.recipe.toJson()))
        .put("companion", JSONObject().put("kind", companion.kind).put("name", companion.name).put("trait", companion.trait))
}
data class PixelDraft(val spec: PixelSpec, val usedModel: Boolean, val availableToolsets: Set<String>, val availableSkills: Set<String>, val notes: List<String>) {
    val enabledToolsets get() = spec.permittedToolsets.filter { it in availableToolsets }
}
