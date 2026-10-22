import Foundation

@MainActor
final class PixelBuilder {
    static let shared = PixelBuilder()
    func draft(for prompt: String, api: (any HermesRPC)?) async throws -> PixelDraft {
        let clean = prompt.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !clean.isEmpty, clean.count <= 4000 else { throw RPCError.transport("Describe the job in 1–4,000 characters.") }
        let defaults: Set<String> = ["web", "file", "memory", "todo", "skills", "vision", "image_gen", "terminal", "delegation"]
        guard let api else { return .init(spec: .fallback(for: clean), usedModel: false, availableToolsets: defaults, availableSkills: [], notes: ["Local template. Review the briefing and access before creating."]) }
        guard let catalog = try await api.request("profiles.describe", params: ["name": "default"]) as? JSONObject else { throw RPCError.malformed }
        let tools = Set(catalog.objects("toolsets").compactMap { $0.string("name") })
        let skills = Set(catalog.objects("skills").compactMap { $0.string("name") })
        guard let url = Bundle.main.url(forResource: "bot-build", withExtension: "md"), let skill = try? String(contentsOf: url, encoding: .utf8) else { throw RPCError.transport("The bot-build skill is missing from this build.") }
        var spec = PixelSpec.fallback(for: clean)
        var usedModel = false
        var notes: [String] = []
        do {
            let result = try await api.request("llm.oneshot", params: ["profile": "default", "instructions": "\(skill)\nAvailable toolsets: \(tools.sorted().joined(separator: ", ")). Available skills: \(skills.sorted().joined(separator: ", ")). Only request listed capabilities.", "input": clean, "max_tokens": 2600, "temperature": 0.7], timeout: 90) as? JSONObject
            if let text = result?.string("text"), let parsed = PixelSpec.parse(text) { spec = parsed; usedModel = true }
            else { notes.append("The model returned an invalid PixelSpec. This is a local template.") }
        } catch is CancellationError { throw CancellationError() }
        catch { notes.append("The model was unavailable. This is a local template.") }
        try Task.checkCancellation()
        let removed = spec.toolsets.filter { !tools.contains($0) || !spec.permittedToolsets.contains($0) }
        if !removed.isEmpty { notes.append("Excluded unavailable or disallowed tools: \(removed.joined(separator: ", ")).") }
        if !spec.connectors.isEmpty { notes.append("Connections are requests. Authorize accounts separately on your Hermes host.") }
        let missing = spec.skills.filter { !skills.contains($0) }
        if !missing.isEmpty { notes.append("Unavailable skills excluded: \(missing.joined(separator: ", ")).") }
        spec.skills = spec.skills.filter { skills.contains($0) }
        return .init(spec: spec, usedModel: usedModel, availableToolsets: tools, availableSkills: skills, notes: notes)
    }
}
