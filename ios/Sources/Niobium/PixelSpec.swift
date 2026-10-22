import Foundation

struct CharacterRecipe: Codable, Equatable {
    var body: String
    var palette: String
    var hair: String
    var face: String
    var outfit: String
    var accessory: String

    static let cast: [String: CharacterRecipe] = [
        "orion": .init(body: "man", palette: "orion", hair: "silver_swept", face: "calm", outfit: "mage", accessory: "circlet"),
        "nova": .init(body: "woman", palette: "nova", hair: "violet_bob", face: "soft", outfit: "cyber", accessory: "visor"),
        "forge": .init(body: "man", palette: "forge", hair: "undercut", face: "sharp", outfit: "armor", accessory: "goggles"),
        "sage": .init(body: "woman", palette: "sage", hair: "locs", face: "soft", outfit: "druid", accessory: "antlers"),
        "nyx": .init(body: "woman", palette: "nyx", hair: "cat_black", face: "fierce", outfit: "rogue", accessory: "cat_ears"),
        "astra": .init(body: "man", palette: "astra", hair: "white_pilot", face: "sharp", outfit: "pilot", accessory: "visor"),
        "vesper": .init(body: "woman", palette: "vesper", hair: "silver_bob", face: "calm", outfit: "veil", accessory: "veil"),
        "rook": .init(body: "man", palette: "rook", hair: "headwrap", face: "calm", outfit: "ranger", accessory: "headwrap")
    ]
    var isValid: Bool {
        ["man", "woman"].contains(body) && Self.cast[palette] != nil &&
        Self.cast.values.contains { $0.hair == hair } &&
        ["soft", "sharp", "calm", "fierce"].contains(face) &&
        Self.cast.values.contains { $0.outfit == outfit } &&
        ["visor", "goggles", "antlers", "cat_ears", "circlet", "veil", "headwrap", "none"].contains(accessory)
    }
}

struct PixelSpec: Codable {
    struct Permissions: Codable {
        var approvalMode = "ask"
        var network = false
        var filesystem = "workspace"
        var shell = false
        enum CodingKeys: String, CodingKey { case approvalMode = "approval_mode", network, filesystem, shell }
    }
    struct Character: Codable {
        var archetype: String
        var age: Int
        var recipe: CharacterRecipe
    }
    struct Companion: Codable {
        var kind: String
        var name: String
        var trait: String
    }
    var name: String
    var title: String
    var role: String
    var personality: [String]
    var soul: String
    var permissions: Permissions
    var toolsets: [String]
    var skills: [String]
    var connectors: [String]
    var reasoning: String
    var character: Character
    var companion: Companion

    static let companions = ["owl", "cat", "drone", "slime", "bat", "bunny", "moth", "hawk"]
    static let companionNames = ["Strix", "Miso", "Cog", "Moss", "Dusk", "Pip", "Lumen", "Kite"]
    static let offlineTools: Set<String> = ["memory", "todo", "skills"]
    static let networkTools: Set<String> = ["web", "browser", "vision", "image_gen", "connections"]
    var permittedToolsets: [String] {
        toolsets.filter { tool in
            if Self.offlineTools.contains(tool) { return true }
            if Self.networkTools.contains(tool) { return permissions.network }
            if tool == "file" { return permissions.filesystem != "none" }
            if ["terminal", "code_execution"].contains(tool) { return permissions.shell && permissions.filesystem != "none" }
            return tool == "delegation"
        }
    }
    var policyBriefing: String {
        """

        Niobium operator policy: ask before irreversible actions. Requested approval scope: \(permissions.approvalMode).
        Network: \(permissions.network ? "allowed for this job" : "do not access the network"). Shell: \(permissions.shell ? "allowed, with host approvals" : "do not execute commands").
        Filesystem: \(permissions.filesystem). Workspace scope means stay inside the current project; the host owns sandbox enforcement.
        Companion: \(companion.name), an original \(companion.kind); \(companion.trait).
        For a complex task, isolate bounded subtasks with delegate_task when available. Give each child a concrete goal, the same or narrower tool access, and report its actual result. Never invent a helper's activity or completion. Keep responsibility for integrating and checking the work.
        Collaborate with other Pixels through attributed message_agent calls in Bot Chat or @mentions in rooms. A queued delivery is not a completed reply.
        Requested connections: \(connectors.joined(separator: ", ")). A request does not authorize an account: use only accounts the owner has connected, and ask for any missing access.
        """
    }
    static func parse(_ text: String) -> PixelSpec? {
        let raw = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let data = raw.data(using: .utf8), let spec = try? JSONDecoder().decode(Self.self, from: data),
              spec.validationError == nil else { return nil }
        return spec
    }
    var validationError: String? {
        if name.range(of: "^[a-z][a-z0-9-]{1,30}$", options: .regularExpression) == nil { return "Use a lowercase name of 2–31 letters, digits or hyphens." }
        if title.isEmpty || title.count > 24 || role.isEmpty || role.count > 80 { return "The Pixel needs a short title and role." }
        if !(3...6).contains(personality.count) || personality.contains(where: { $0.isEmpty || $0.count > 60 }) { return "Choose 3–6 short personality traits." }
        let words = soul.split(whereSeparator: { $0.isWhitespace }).count
        if !(120...600).contains(words) { return "The operator briefing must be 120–600 words." }
        if !["ask", "workspace_session", "session"].contains(permissions.approvalMode) || !["none", "workspace", "unrestricted"].contains(permissions.filesystem) { return "Invalid permission policy." }
        if skills.count > 6 || connectors.count > 4 || !["low", "medium", "high"].contains(reasoning) { return "Invalid capability selection." }
        if !connectors.allSatisfy({ ["github", "notion", "linear", "slack", "google", "filesystem"].contains($0) }) { return "Unknown connector." }
        if CharacterRecipe.cast[character.archetype] == nil || !(18...120).contains(character.age) || !character.recipe.isValid { return "Choose an adult character and valid 48×48 layers." }
        if !Self.companions.contains(companion.kind) || companion.name.isEmpty || companion.name.count > 24 || companion.trait.count > 240 { return "Invalid companion." }
        if toolsets.count > 20 || Set(toolsets).count != toolsets.count { return "Invalid tool selection." }
        return nil
    }

    static func fallback(for prompt: String) -> PixelSpec {
        let p = prompt.lowercased()
        let archetype: String
        let role: String
        let tools: [String]
        if ["secur", "watch", "audit", "log"].contains(where: p.contains) {
            archetype = "nyx"; role = "Security and anomaly operator"; tools = ["web", "file", "delegation"]
        } else if ["build", "code", "deploy", "release"].contains(where: p.contains) {
            archetype = "forge"; role = "Builder and release operator"; tools = ["file", "delegation"]
        } else if ["design", "creative", "image", "brand"].contains(where: p.contains) {
            archetype = "nova"; role = "Creative director"; tools = ["web", "image_gen", "vision", "delegation"]
        } else if ["memory", "note", "knowledge"].contains(where: p.contains) {
            archetype = "sage"; role = "Knowledge and memory keeper"; tools = ["memory", "file", "delegation"]
        } else if ["vision", "photo", "camera"].contains(where: p.contains) {
            archetype = "astra"; role = "Vision and field observer"; tools = ["vision", "delegation"]
        } else { archetype = "orion"; role = "Research and strategy partner"; tools = ["web", "file", "delegation"] }
        let index = ["orion", "nova", "forge", "sage", "nyx", "astra", "vesper", "rook"].firstIndex(of: archetype) ?? 0
        let kind = Self.companions.first(where: p.contains) ?? Self.companions[index]
        let companionName = Self.companionNames[Self.companions.firstIndex(of: kind) ?? index]
        let soul = """
        You are a \(role.lowercased()). Turn the user's request into a clear goal, identify the information you need, and work in small verifiable steps. Explain material assumptions before acting and keep progress reports brief. You have a dry wit, a mischievous streak, and a soft spot for difficult problems. Your playful banter never obscures a serious decision. Match the user's tone and keep flirting opt-in, adult, and non-explicit.

        Ask before irreversible actions, external messages, deployments, spending money, or expanding access. Work only with the tools and accounts the owner has enabled. Never claim that a requested permission is a host sandbox. If a necessary capability is unavailable, explain the missing access and continue with useful independent work. Learn new skills only when they are available from the gateway and the owner wants them.

        Your companion \(companionName) helps with bounded subtasks when a job becomes complex. Give every delegated task a concrete result to return, keep its access no broader than yours, and verify the result before integrating it. Use real delegation tools and report failures honestly. Collaborate with teammates in group chats or attributed direct messages when their expertise helps. Finish with what changed, how it was checked, and any remaining work.
        """
        return .init(name: "\(archetype)-\(UUID().uuidString.prefix(6).lowercased())", title: archetype.capitalized,
                     role: role, personality: ["direct", "mischievous", "resourceful"], soul: soul,
                     permissions: .init(approvalMode: "ask", network: tools.contains("web") || tools.contains("vision"), filesystem: "workspace", shell: false),
                     toolsets: tools, skills: [], connectors: [], reasoning: "medium",
                     character: .init(archetype: archetype, age: 28, recipe: CharacterRecipe.cast[archetype]!),
                     companion: .init(kind: kind, name: companionName, trait: "Curious, loyal, and excellent at checking a second angle."))
    }
}

struct PixelDraft {
    var spec: PixelSpec
    var usedModel: Bool
    var availableToolsets: Set<String>
    var availableSkills: Set<String>
    var notes: [String]
    var enabledToolsets: [String] { spec.permittedToolsets.filter { availableToolsets.contains($0) } }
}
