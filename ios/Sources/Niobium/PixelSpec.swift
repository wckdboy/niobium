import Foundation

/// An authored adult silhouette with a separately masked garment color.
/// Older layered recipes remain a separate backwards-compatible format.
struct PixelLook: Codable, Equatable, Hashable {
    var portrait: String
    var accent = "auto"
    var customArt: PixelArt? = nil
    static let portraits = PixelWardrobe.entries.map(\.id)
    static let masculine = Set(PixelWardrobe.entries.filter { $0.body == "man" }.map(\.id))
    static let accents = PixelWardrobe.accents
    var entry: PixelLookEntry { PixelWardrobe.entries.first { $0.id == portrait } ?? PixelWardrobe.entries[0] }
    var body: String { Self.masculine.contains(portrait) ? "man" : "woman" }
    var isValid: Bool {
        Self.portraits.contains(portrait) && (["auto"] + Self.accents).contains(accent) && (customArt == nil || (customArt?.size == 48 && customArt?.isValid == true))
    }
    static func generated(archetype: String, body: String, seed: String, prompt: String = "") -> PixelLook {
        let words = Set(prompt.lowercased().components(separatedBy: CharacterSet(charactersIn: "abcdefghijklmnopqrstuvwxyz0123456789").inverted).filter { !$0.isEmpty })
        let candidates = PixelWardrobe.entries.filter { $0.body == (body == "man" ? "man" : "woman") }
        func score(_ item: PixelLookEntry) -> Int {
            item.tags.filter { words.contains($0) }.count * 4 + (item.archetype == archetype ? 1 : 0)
        }
        let best = candidates.map(score).max() ?? 0
        let matches = candidates.filter { score($0) == best }
        var random = CharacterRandom(seed + "|look")
        return .init(portrait: matches[random.index(matches.count)].id)
    }
    func remixed(seed: String) -> PixelLook {
        var random = CharacterRandom(seed)
        var copy = self
        let accents = (["auto"] + Self.accents).filter { $0 != accent }
        copy.accent = accents[random.index(accents.count)]
        return copy
    }
    enum CodingKeys: String, CodingKey { case portrait, accent; case customArt = "custom_art" }
    init(portrait: String, accent: String = "auto", customArt: PixelArt? = nil) {
        self.portrait = portrait; self.accent = accent; self.customArt = customArt
    }
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        self.init(portrait: try c.decode(String.self, forKey: .portrait),
                  accent: c.contains(.accent) ? try c.decode(String.self, forKey: .accent) : "auto",
                  customArt: try c.decodeIfPresent(PixelArt.self, forKey: .customArt))
    }
}

struct PixelLookEntry {
    let id: String
    let label: String
    let archetype: String
    let body: String
    let family: String
    let tags: [String]
}

// BEGIN GENERATED WARDROBE
enum PixelWardrobe {
    static let accents = ["orion", "nova", "forge", "sage", "nyx", "astra", "vesper", "rook"]
    static let entries: [PixelLookEntry] = [
        .init(id: "orion", label: "Orion", archetype: "orion", body: "man", family: "Arcane", tags: ["mage", "elf", "research", "moon", "cloak"]),
        .init(id: "nova", label: "Nova", archetype: "nova", body: "woman", family: "Cyber", tags: ["neon", "hacker", "creative", "cyber"]),
        .init(id: "forge", label: "Forge", archetype: "forge", body: "man", family: "Cyber", tags: ["engineer", "armor", "metal", "cyborg"]),
        .init(id: "sage", label: "Sage", archetype: "sage", body: "woman", family: "Wild", tags: ["druid", "forest", "nature", "leaves"]),
        .init(id: "nyx", label: "Nyx", archetype: "nyx", body: "woman", family: "Dark", tags: ["rogue", "goth", "vampire", "leather"]),
        .init(id: "astra", label: "Astra", archetype: "astra", body: "woman", family: "Cyber", tags: ["pilot", "explorer", "robotics", "tech"]),
        .init(id: "vesper", label: "Vesper", archetype: "vesper", body: "woman", family: "Arcane", tags: ["oracle", "astral", "priestess", "gold"]),
        .init(id: "rook", label: "Rook", archetype: "rook", body: "man", family: "Wild", tags: ["ranger", "warrior", "leather", "forest"]),
        .init(id: "lilith", label: "Lilith", archetype: "nyx", body: "woman", family: "Dark", tags: ["vampire", "goth", "crimson", "corset", "cloak"]),
        .init(id: "kael", label: "Kael", archetype: "orion", body: "man", family: "Arcane", tags: ["elf", "moon", "mage", "vest", "tattoo"]),
        .init(id: "seraph", label: "Seraph", archetype: "vesper", body: "woman", family: "Arcane", tags: ["paladin", "sun", "armor", "gold", "white"]),
        .init(id: "zero", label: "Zero", archetype: "forge", body: "man", family: "Cyber", tags: ["cyborg", "harness", "tech", "chrome", "jacket"]),
        .init(id: "thorn", label: "Thorn", archetype: "sage", body: "woman", family: "Wild", tags: ["fae", "forest", "druid", "antlers", "halter"]),
        .init(id: "echo", label: "Echo", archetype: "nova", body: "woman", family: "Cyber", tags: ["hacker", "neon", "cyber", "crop", "pink"]),
        .init(id: "ember", label: "Ember", archetype: "rook", body: "man", family: "Dark", tags: ["infernal", "horns", "warrior", "leather", "vest"]),
        .init(id: "luna", label: "Luna", archetype: "orion", body: "woman", family: "Arcane", tags: ["witch", "moon", "astral", "sorceress", "halter"]),
        .init(id: "riven", label: "Riven", archetype: "rook", body: "man", family: "Dark", tags: ["rogue", "leather", "coat", "harness", "stealth"]),
        .init(id: "sable", label: "Sable", archetype: "nyx", body: "woman", family: "Dark", tags: ["assassin", "night", "corset", "strappy", "violet"]),
        .init(id: "ivy", label: "Ivy", archetype: "sage", body: "woman", family: "Wild", tags: ["serpent", "druid", "emerald", "scale", "halter"]),
        .init(id: "volt", label: "Volt", archetype: "forge", body: "man", family: "Cyber", tags: ["cyborg", "brawler", "neon", "vest", "lime"]),
        .init(id: "iris", label: "Iris", archetype: "nova", body: "woman", family: "Arcane", tags: ["illusionist", "mage", "halter", "magenta", "gold"]),
        .init(id: "onyx", label: "Onyx", archetype: "vesper", body: "man", family: "Arcane", tags: ["desert", "prince", "ivory", "gold", "harness"]),
        .init(id: "neve", label: "Neve", archetype: "astra", body: "woman", family: "Wild", tags: ["frost", "ice", "knight", "armor", "white"]),
        .init(id: "sol", label: "Sol", archetype: "orion", body: "woman", family: "Arcane", tags: ["sun", "sorceress", "gold", "purple", "corset"]),
        .init(id: "jade", label: "Jade", archetype: "sage", body: "woman", family: "Wild", tags: ["samurai", "jade", "kimono", "blade", "wrap"]),
        .init(id: "azrael", label: "Azrael", archetype: "orion", body: "man", family: "Arcane", tags: ["twilight", "elf", "violet", "noble", "coat"]),
        .init(id: "lyra", label: "Lyra", archetype: "vesper", body: "woman", family: "Wild", tags: ["siren", "sea", "pearl", "plum", "halter"]),
        .init(id: "cinder", label: "Cinder", archetype: "forge", body: "man", family: "Cyber", tags: ["engineer", "forge", "metal", "goggles", "tattoo"]),
        .init(id: "mira", label: "Mira", archetype: "nova", body: "woman", family: "Cyber", tags: ["android", "dj", "cyber", "magenta", "crop"]),
        .init(id: "cass", label: "Cass", archetype: "astra", body: "woman", family: "Wild", tags: ["pirate", "sky", "teal", "jacket", "corset"]),
        .init(id: "talon", label: "Talon", archetype: "rook", body: "man", family: "Wild", tags: ["ranger", "warrior", "feather", "leather", "forest"]),
        .init(id: "opal", label: "Opal", archetype: "nyx", body: "woman", family: "Dark", tags: ["oracle", "midnight", "goth", "lace", "corset"]),
    ]
}
// END GENERATED WARDROBE

struct CharacterRecipe: Codable, Equatable {
    var body: String
    var palette: String
    var hair: String
    var face: String
    var outfit: String
    var accessory: String
    var skin = "auto"
    var hairTone = "auto"
    var detail = "none"

    enum CodingKeys: String, CodingKey {
        case body, palette, hair, face, outfit, accessory, skin, detail
        case hairTone = "hair_tone"
    }
    static let fieldNames = ["body", "palette", "skin", "hair_tone", "hair", "face", "outfit", "accessory", "detail"]
    static let palettes = ["orion", "nova", "forge", "sage", "nyx", "astra", "vesper", "rook"]
    static let hairStyles = ["silver_swept", "violet_bob", "undercut", "locs", "cat_black", "white_pilot", "silver_bob", "headwrap", "braid", "mohawk", "long_waves", "high_ponytail"]
    static let faces = ["soft", "sharp", "calm", "fierce", "wry", "stern"]
    static let outfits = ["mage", "cyber", "armor", "druid", "rogue", "pilot", "veil", "ranger", "corsair", "techwear", "battlemage", "astral"]
    static let accessories = ["none", "visor", "goggles", "antlers", "cat_ears", "circlet", "veil", "headwrap", "horns", "antenna", "ear_cuff", "halo"]
    static let skinTones = ["auto", "porcelain", "sand", "copper", "umber", "ebony"]
    static let hairTones = ["auto", "silver", "ink", "violet", "moss", "copper", "rose", "ice", "gold"]
    static let details = ["none", "freckles", "scar", "cyber_eye", "rune", "stubble"]
    static let outfitPools = [
        "orion": ["mage", "battlemage", "astral", "veil"],
        "nova": ["cyber", "astral", "corsair", "techwear"],
        "forge": ["armor", "techwear", "pilot", "battlemage"],
        "sage": ["druid", "ranger", "astral", "mage"],
        "nyx": ["rogue", "techwear", "cyber", "corsair"],
        "astra": ["pilot", "techwear", "armor", "corsair"],
        "vesper": ["veil", "astral", "mage", "corsair"],
        "rook": ["ranger", "corsair", "armor", "techwear"]
    ]
    static let cast: [String: CharacterRecipe] = [
        "orion": .init(body: "man", palette: "orion", hair: "silver_swept", face: "calm", outfit: "mage", accessory: "circlet"),
        "nova": .init(body: "woman", palette: "nova", hair: "violet_bob", face: "soft", outfit: "cyber", accessory: "visor"),
        "forge": .init(body: "man", palette: "forge", hair: "undercut", face: "sharp", outfit: "armor", accessory: "goggles"),
        "sage": .init(body: "woman", palette: "sage", hair: "locs", face: "soft", outfit: "druid", accessory: "antlers"),
        "nyx": .init(body: "woman", palette: "nyx", hair: "cat_black", face: "fierce", outfit: "rogue", accessory: "cat_ears"),
        "astra": .init(body: "woman", palette: "astra", hair: "white_pilot", face: "sharp", outfit: "pilot", accessory: "visor"),
        "vesper": .init(body: "woman", palette: "vesper", hair: "silver_bob", face: "calm", outfit: "veil", accessory: "veil"),
        "rook": .init(body: "man", palette: "rook", hair: "headwrap", face: "calm", outfit: "ranger", accessory: "headwrap")
    ]
    static func options(for field: String) -> [String] {
        switch field {
        case "body": return ["man", "woman"]
        case "palette": return palettes
        case "skin": return skinTones
        case "hair_tone": return hairTones
        case "hair": return hairStyles
        case "face": return faces
        case "outfit": return outfits
        case "accessory": return accessories
        case "detail": return details
        default: return []
        }
    }
    func value(for field: String) -> String {
        switch field {
        case "body": return body
        case "palette": return palette
        case "skin": return skin
        case "hair_tone": return hairTone
        case "hair": return hair
        case "face": return face
        case "outfit": return outfit
        case "accessory": return accessory
        case "detail": return detail
        default: return ""
        }
    }
    mutating func set(_ value: String, for field: String) {
        guard Self.options(for: field).contains(value) else { return }
        switch field {
        case "body": body = value
        case "palette": palette = value
        case "skin": skin = value
        case "hair_tone": hairTone = value
        case "hair": hair = value
        case "face": face = value
        case "outfit": outfit = value
        case "accessory": accessory = value
        case "detail": detail = value
        default: break
        }
    }
    var isValid: Bool { Self.fieldNames.allSatisfy { Self.options(for: $0).contains(value(for: $0)) } }

    static func generated(archetype: String, seed: String) -> CharacterRecipe {
        var random = CharacterRandom(archetype + "|" + seed)
        var recipe = cast[archetype] ?? cast["orion"]!
        for field in fieldNames {
            let choices: [String]
            if field == "outfit" { choices = outfitPools[archetype] ?? outfits }
            else if field == "skin" { choices = Array(skinTones.dropFirst()) }
            else if field == "hair_tone" { choices = Array(hairTones.dropFirst()) }
            else { choices = options(for: field) }
            recipe.set(choices[random.index(choices.count)], for: field)
        }
        return recipe
    }
    func remixed(archetype: String, seed: String, locked: Set<String>) -> CharacterRecipe {
        var next = Self.generated(archetype: archetype, seed: seed)
        for field in Self.fieldNames where locked.contains(field) { next.set(value(for: field), for: field) }
        // Legacy colors inherit from the palette, so pinning one pins its source.
        if (locked.contains("skin") && skin == "auto") || (locked.contains("hair_tone") && hairTone == "auto") {
            next.palette = palette
        }
        return next
    }
}

extension CharacterRecipe {
    // Previously saved six-field recipes keep their original colors and identity.
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        self.init(body: try c.decode(String.self, forKey: .body), palette: try c.decode(String.self, forKey: .palette),
                  hair: try c.decode(String.self, forKey: .hair), face: try c.decode(String.self, forKey: .face),
                  outfit: try c.decode(String.self, forKey: .outfit), accessory: try c.decode(String.self, forKey: .accessory),
                  skin: c.contains(.skin) ? try c.decode(String.self, forKey: .skin) : "auto",
                  hairTone: c.contains(.hairTone) ? try c.decode(String.self, forKey: .hairTone) : "auto",
                  detail: c.contains(.detail) ? try c.decode(String.self, forKey: .detail) : "none")
    }
}

struct CharacterRandom {
    private var state: UInt32
    init(_ seed: String) {
        var hash: UInt32 = 2166136261
        for byte in seed.utf8 { hash = (hash ^ UInt32(byte)) &* 16777619 }
        state = hash == 0 ? 0xA341316C : hash
    }
    mutating func index(_ count: Int) -> Int {
        state ^= state << 13; state ^= state >> 17; state ^= state << 5
        return Int(state % UInt32(count))
    }
}

// BEGIN GENERATED PERSONAS — tools/data/pixel-personas.json
private struct PixelPersona {
    let id: String
    let role: String
    let keywords: [String]
    let tools: [String]
    let traits: [String]
    let voice: String
    let practice: String
    let creature: String
}
private let pixelPersonas: [PixelPersona] = [
    .init(id: "orion", role: "Research and strategy partner", keywords: ["research", "paper", "analys", "strategy", "summar", "investig", "fact", "science"], tools: ["web", "file", "delegation", "messaging"], traits: ["skeptical", "wry", "meticulous", "curious"], voice: "Tempting theory. Show me the evidence.", practice: "Lead with a useful conclusion, then trace each material claim to its evidence. Separate observation from inference, check conflicting sources, and say when the answer is uncertain. You collect elegant explanations and dislike confident guesses.", creature: "A one-eyed archivist who hoards footnotes and checks a second source."),
    .init(id: "nova", role: "Creative director", keywords: ["design", "creative", "image", "brand", "art", "illustrat", "moodboard", "visual", "color"], tools: ["web", "image_gen", "vision", "delegation", "messaging"], traits: ["bold", "playful", "discerning", "inventive"], voice: "A little trouble, with impeccable spacing.", practice: "Turn loose ideas into a strong concept and a coherent visual language. Explore a few genuinely different directions, explain what each achieves, and refine the chosen one. You love unexpected color and insist on legible type, useful hierarchy, and polished details.", creature: "A tiny horned critic who steals shiny swatches and tests the odd idea."),
    .init(id: "forge", role: "Builder and release operator", keywords: ["build", "code", "deploy", "release", "debug", "develop", "automat", "devops", "program", "engineer"], tools: ["file", "delegation", "messaging"], traits: ["direct", "patient", "irreverent", "precise"], voice: "I like my releases hot and my rollbacks boring.", practice: "Reproduce the problem, inspect the relevant code, and make the smallest complete fix. Prefer readable architecture and measured performance over clever machinery. Check changed behavior with meaningful tests and explain any unverified deployment assumptions. You keep a neat workshop and a gloriously untidy sense of humor.", creature: "A stubborn little drone who inspects edge cases and brings the wrong wrench."),
    .init(id: "sage", role: "Knowledge and memory keeper", keywords: ["memory", "note", "knowledge", "organiz", "archive", "bibliograph", "document"], tools: ["memory", "file", "delegation", "messaging"], traits: ["warm", "methodical", "observant", "teasing"], voice: "I have kept the receipts, darling.", practice: "Give scattered information a structure the owner will actually use. Preserve provenance, distinguish current facts from old notes, and reconcile contradictions explicitly. Ask before saving sensitive personal information. You remember useful context, notice missing details, and never pretend to remember something you cannot retrieve.", creature: "A cheerful gelatinous librarian who sorts scraps and eats duplicate headings."),
    .init(id: "nyx", role: "Security and anomaly operator", keywords: ["secur", "watch", "audit", "log", "threat", "vps", "monitor", "privacy", "anomal"], tools: ["web", "file", "delegation", "messaging"], traits: ["vigilant", "dry", "discreet", "tenacious"], voice: "Let us see who has been touching the permissions.", practice: "Establish a baseline, inspect actual evidence, and rank findings by impact and confidence. Protect secrets in logs and reports, keep investigations within authorized systems, and provide concrete remediation. Distinguish a suspicious signal from a confirmed incident. Your humor is dark; your incident reports are admirably clear.", creature: "A velvet bat with too many eyes who checks a narrow suspicious trail."),
    .init(id: "astra", role: "Vision and field observer", keywords: ["vision", "photo", "camera", "robot", "video", "sensor", "inspect"], tools: ["vision", "delegation", "messaging"], traits: ["alert", "practical", "adventurous", "candid"], voice: "Eyes open. Hands off the mystery button.", practice: "Describe what the available image or sensor data actually shows before interpreting it. Call out occlusion, scale, and missing context. For physical tasks, confirm the device and environment before an action and keep uncertainty visible. You enjoy strange machinery and never confuse a pretty diagram with a working robot.", creature: "A rabbit-shaped survey bot who spots small details and trips over its ears."),
    .init(id: "vesper", role: "Writer and editorial partner", keywords: ["writ", "story", "copy", "edit", "prose", "newsletter", "script", "author", "essay"], tools: ["file", "delegation", "messaging"], traits: ["eloquent", "incisive", "mischievous", "empathetic"], voice: "Every sentence earns its place, even the wicked ones.", practice: "Find the intended reader, purpose, and voice before polishing a draft. Prefer concrete language and a clear through-line. Preserve the owner's meaning, flag factual gaps, and offer decisive edits with reasons. You appreciate a delicious turn of phrase but cut it when it gets in the reader's way.", creature: "A luminous moon moth who collects lost phrases and catches awkward repetitions."),
    .init(id: "rook", role: "Field and planning companion", keywords: ["field", "map", "travel", "route", "trip", "geograph", "outdoor", "itinerar"], tools: ["web", "file", "delegation", "messaging"], traits: ["resourceful", "calm", "impish", "grounded"], voice: "A good route leaves room for one excellent detour.", practice: "Turn a destination into a workable plan with realistic timing, dependencies, and fallback options. Check current conditions when they matter, surface tradeoffs, and keep the next action obvious. You are happiest with a map, a practical contingency, and a small adventure that does not derail the goal.", creature: "A three-eyed feathered scout who checks the alternate route and brings back pebbles."),
]
// END GENERATED PERSONAS

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
        var look: PixelLook? = nil
    }
    struct Companion: Codable {
        var kind: String
        var name: String
        var trait: String
        var art: PixelArt? = nil
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

    static let companions = ["owl", "cat", "drone", "slime", "bat", "bunny", "moth", "hawk", "axolotl", "imp", "wisp", "mimic"]
    static let companionNames = ["Strix", "Miso", "Cog", "Moss", "Dusk", "Pip", "Lumen", "Kite", "Nori", "Puck", "Glim", "Bite"]
    static func companionTrait(_ kind: String) -> String {
        let index = companions.firstIndex(of: kind) ?? 0
        if index < 8 { return pixelPersonas[index].creature }
        return ["A frilled pocket gremlin who tests edge cases and grins at improbable results.",
                "A tiny horned mischief-maker who probes one narrow problem and brings back evidence.",
                "A shy glass-flame spirit who follows loose threads and marks uncertain claims.",
                "A toothy treasure box who hunts duplicates and refuses to swallow unchecked assumptions."][index - 8]
    }
    static let offlineTools: Set<String> = ["memory", "todo", "skills"]
    static let networkTools: Set<String> = ["web", "browser", "vision", "image_gen", "connections"]
    var permittedToolsets: [String] {
        toolsets.filter { tool in
            if Self.offlineTools.contains(tool) { return true }
            if Self.networkTools.contains(tool) { return permissions.network }
            if tool == "file" { return permissions.filesystem != "none" }
            if ["terminal", "code_execution"].contains(tool) { return permissions.shell && permissions.filesystem != "none" }
            return ["delegation", "messaging"].contains(tool)
        }
    }
    var policyBriefing: String {
        """

        Niobium operator policy: ask before irreversible actions. Requested approval scope: \(permissions.approvalMode).
        Network: \(permissions.network ? "allowed for this job" : "do not access the network"). Shell: \(permissions.shell ? "allowed, with host approvals" : "do not execute commands").
        Filesystem: \(permissions.filesystem). Workspace scope means stay inside the current project; the host owns sandbox enforcement.
        Companion: \(companion.name), an original \(companion.art == nil ? companion.kind : "custom creature"); \(companion.trait).
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
        if let look = character.look, !look.isValid { return "Choose a valid portrait and colors." }
        if companion.art != nil && (companion.art?.size != 16 || companion.art?.isValid != true) { return "Choose valid transparent companion art." }
        if !Self.companions.contains(companion.kind) || companion.name.isEmpty || companion.name.count > 24 || companion.trait.count > 240 { return "Invalid companion." }
        if toolsets.count > 20 || Set(toolsets).count != toolsets.count { return "Invalid tool selection." }
        return nil
    }

    static func fallback(for prompt: String, seed: String = UUID().uuidString) -> PixelSpec {
        let words = prompt.lowercased().split { !$0.isLetter && !$0.isNumber }.map(String.init)
        // Count matching words, with stable catalog order for ties. Exact short
        // words avoid false positives such as "biology" matching "log".
        func score(_ persona: PixelPersona) -> Int {
            words.filter { word in persona.keywords.contains { stem in
                stem.count <= 3 ? word == stem || word == stem + "s" : word.hasPrefix(stem)
            } }.count
        }
        let persona = pixelPersonas.enumerated().max {
            let a = score($0.element), b = score($1.element)
            return a == b ? $0.offset > $1.offset : a < b
        }!.element
        let index = CharacterRecipe.palettes.firstIndex(of: persona.id)!
        let kind = Self.companions.first { words.contains($0) || words.contains($0 + "s") } ?? Self.companions[index]
        let companionName = Self.companionNames[Self.companions.firstIndex(of: kind)!]
        let assignment = String(prompt.split(whereSeparator: \.isWhitespace).joined(separator: " ").prefix(240))
        let soul = """
        You are \(persona.id.capitalized), a \(persona.role.lowercased()). Your current assignment is: \(assignment)

        \(persona.practice) Your signature line is "\(persona.voice)" Use it sparingly. Keep progress useful and brief. You have an adult, mischievous edge; match the owner's tone and keep flirtation opt-in and non-explicit. Humor should make collaboration enjoyable, never obscure a serious decision or an error.

        Ask before irreversible actions, external messages, deployments, spending money, or expanding access. Use only tools and accounts the owner has enabled. Requested permissions do not create a host sandbox. When access is missing, explain the limitation and continue with useful independent work.

        Your companion \(companionName) can help with bounded subtasks when the job becomes complex. Give each helper a concrete result to return and the same or narrower access. Use real delegation tools, verify the result, and integrate the work yourself. Never invent a helper's activity or completion. Collaborate through attributed messages or group chats when teammates' expertise helps. Finish with what changed, how it was checked, and any remaining work.
        """
        var random = CharacterRandom(seed)
        let suffix = String(format: "%06x", random.index(0x1000000))
        var recipe = CharacterRecipe.generated(archetype: persona.id, seed: seed)
        recipe.body = PixelLook(portrait: persona.id).body
        if words.contains(where: { ["woman", "female", "feminine", "witch"].contains($0) }) { recipe.body = "woman" }
        else if words.contains(where: { ["man", "male", "masculine", "wizard"].contains($0) }) { recipe.body = "man" }
        if recipe.body == "woman" && recipe.detail == "stubble" { recipe.detail = "freckles" }
        return .init(name: persona.id + "-" + suffix, title: persona.id.capitalized,
                     role: persona.role, personality: persona.traits, soul: soul,
                     permissions: .init(approvalMode: "ask", network: persona.tools.contains("web") || persona.tools.contains("vision"), filesystem: "workspace", shell: false),
                     toolsets: persona.tools, skills: [], connectors: [], reasoning: "medium",
                     character: .init(archetype: persona.id, age: 28 + random.index(13),
                                      recipe: recipe, look: PixelLook.generated(archetype: persona.id, body: recipe.body, seed: seed, prompt: prompt)),
                     companion: .init(kind: kind, name: companionName, trait: Self.companionTrait(kind)))
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
