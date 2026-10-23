import Foundation

@MainActor
final class PixelBuilder {
    static let shared = PixelBuilder()
    func preview(for prompt: String) -> PixelDraft {
        .init(spec: .fallback(for: prompt), usedModel: false,
              availableToolsets: ["web", "file", "memory", "todo", "skills", "vision", "image_gen", "terminal", "delegation", "messaging"],
              availableSkills: [], notes: [])
    }
    func draft(for prompt: String, api: (any HermesRPC)?) async throws -> PixelDraft {
        let clean = prompt.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !clean.isEmpty, clean.count <= 4000 else { throw RPCError.transport("Describe the job in 1–4,000 characters.") }
        guard let api else { return preview(for: clean) }
        guard let catalog = try await api.request("profiles.describe", params: ["name": "default"], timeout: 12) as? JSONObject else { throw RPCError.malformed }
        let tools = Set(catalog.objects("toolsets").compactMap { $0.string("name") })
        let skills = Set(catalog.objects("skills").compactMap { $0.string("name") })
        guard let url = Bundle.main.url(forResource: "bot-build", withExtension: "md"), let skill = try? String(contentsOf: url, encoding: .utf8) else { throw RPCError.transport("The bot-build skill is missing from this build.") }
        var spec = PixelSpec.fallback(for: clean)
        var usedModel = false
        var notes: [String] = []
        do {
            let result = try await api.request("llm.oneshot", params: ["profile": "default", "instructions": "\(skill)\nAvailable toolsets: \(tools.sorted().joined(separator: ", ")). Available skills: \(skills.sorted().joined(separator: ", ")). Only request listed capabilities. Keep the briefing between 120 and 220 words.", "input": clean, "max_tokens": 1800, "temperature": 0.7], timeout: 35) as? JSONObject
            if let text = result?.string("text"), let parsed = PixelSpec.parse(text) { spec = parsed; usedModel = true }
            else { notes.append("The model returned an invalid PixelSpec. This is a local template.") }
        } catch is CancellationError { throw CancellationError() }
        catch { notes.append("The model was unavailable. This is a local template.") }
        try Task.checkCancellation()
        if spec.character.look == nil { spec.character.look = .generated(archetype: spec.character.archetype, body: spec.character.recipe.body, seed: spec.name) }
        let removed = spec.toolsets.filter { !tools.contains($0) || !spec.permittedToolsets.contains($0) }
        if !removed.isEmpty { notes.append("Excluded unavailable or disallowed tools: \(removed.joined(separator: ", ")).") }
        if !spec.connectors.isEmpty { notes.append("Connections are requests. Authorize accounts separately on your Hermes host.") }
        let missing = spec.skills.filter { !skills.contains($0) }
        if !missing.isEmpty { notes.append("Unavailable skills excluded: \(missing.joined(separator: ", ")).") }
        spec.skills = spec.skills.filter { skills.contains($0) }
        if catalog.string("provider") == "DSH" {
            if tools.contains("messaging"), !spec.toolsets.contains("messaging") { spec.toolsets.append("messaging") }
            if !spec.connectors.isEmpty { notes.append("Local connections need separate DSH setup. This Pixel starts without connector access.") }
            spec.connectors = []
            notes.append("Local reasoning follows the selected model’s DSH settings. Skills share the local workspace.")
        }
        return .init(spec: spec, usedModel: usedModel, availableToolsets: tools, availableSkills: skills, notes: notes)
    }
}
