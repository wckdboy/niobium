import SwiftUI

struct PixelSpec: Codable {
    struct Permissions: Codable {
        var approvalMode: String = "ask"
        var network = false
        var filesystem = "workspace"
        var shell = false
        enum CodingKeys: String, CodingKey { case approvalMode = "approval_mode", network, filesystem, shell }
    }
    struct Character: Codable {
        var archetype = "orion"
        var gender = "woman"
        var palette = "ion"
        var hair = ""
        var outfit = ""
    }
    struct Companion: Codable {
        var kind = "owl"
        var name = "Strix"
        var trait = ""
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

    static func parse(_ text: String) -> PixelSpec? {
        var raw = text.trimmingCharacters(in: .whitespacesAndNewlines)
        if let first = raw.firstIndex(of: "{"), let last = raw.lastIndex(of: "}") {
            raw = String(raw[first...last])
        }
        guard let data = raw.data(using: .utf8) else { return nil }
        return try? JSONDecoder().decode(PixelSpec.self, from: data)
    }

    static func fallback(for prompt: String) -> PixelSpec {
        let p = prompt.lowercased()
        let archetype: String
        let role: String
        let tools: [String]
        if p.contains("secur") || p.contains("watch") || p.contains("audit") {
            archetype = "nyx"; role = "Security and anomaly operator"; tools = ["web", "terminal", "filesystem"]
        } else if p.contains("build") || p.contains("code") || p.contains("deploy") {
            archetype = "forge"; role = "Builder and release operator"; tools = ["terminal", "filesystem", "web"]
        } else if p.contains("design") || p.contains("creative") || p.contains("image") {
            archetype = "nova"; role = "Creative director"; tools = ["web", "image_gen", "vision"]
        } else if p.contains("memory") || p.contains("note") || p.contains("knowledge") {
            archetype = "sage"; role = "Knowledge and memory keeper"; tools = ["memory", "web", "filesystem"]
        } else if p.contains("vision") || p.contains("photo") || p.contains("camera") {
            archetype = "astra"; role = "Vision and field observer"; tools = ["vision", "web", "filesystem"]
        } else if p.contains("field") || p.contains("travel") || p.contains("research") {
            archetype = "rook"; role = "Field researcher"; tools = ["web", "filesystem"]
        } else {
            archetype = "orion"; role = "Research and strategy partner"; tools = ["web", "filesystem"]
        }
        let member = CastMember.all.first(where: { $0.id == archetype }) ?? CastMember.all[0]
        let slugBase = role.lowercased().split(separator: " ").prefix(2).joined(separator: "-")
            .replacingOccurrences(of: "[^a-z0-9-]", with: "", options: .regularExpression)
        let slug = (slugBase.isEmpty ? "pixel" : slugBase) + "-" + String(Int.random(in: 10...99))
        return PixelSpec(
            name: slug,
            title: role.split(separator: " ").prefix(2).joined(separator: " "),
            role: role,
            personality: ["direct", "careful", "useful"],
            soul: "You are \(role). You work in the open, ask before irreversible actions, keep your notes short, and tell the user what changed. When a task needs another set of hands, call your companion \(member.companionName). You do not pretend a tool succeeded when it did not.",
            permissions: .init(),
            toolsets: tools,
            skills: [],
            connectors: [],
            reasoning: "medium",
            character: .init(archetype: archetype, gender: "woman", palette: "ion", hair: "", outfit: ""),
            companion: .init(kind: member.companion, name: member.companionName, trait: "quiet, loyal, good at the job")
        )
    }
}

@MainActor
final class PixelBuilder {
    static let shared = PixelBuilder()
    private var skill: String {
        guard let url = Bundle.main.url(forResource: "bot-build", withExtension: "md"),
              let text = try? String(contentsOf: url, encoding: .utf8) else {
            return "Return one strict PixelSpec JSON object."
        }
        return text
    }

    func spec(for prompt: String, api: HermesRPCClient?) async -> (PixelSpec, Bool) {
        guard let api else { return (.fallback(for: prompt), false) }
        let instructions = """
        You are the Niobium Pixel builder. Follow this skill exactly.
        Return ONLY one JSON object, no fences, no prose.

        \(skill)
        """
        do {
            let result = try await api.request("llm.oneshot", params: [
                "profile": "default",
                "instructions": instructions,
                "input": prompt,
                "max_tokens": 1800,
                "temperature": 0.7
            ], timeout: 90) as? JSONObject
            if let text = result?.string("text"), let parsed = PixelSpec.parse(text) {
                return (parsed, true)
            }
        } catch { }
        return (.fallback(for: prompt), false)
    }
}
