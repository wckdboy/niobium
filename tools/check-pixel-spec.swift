import Foundation

@main
struct PixelSpecChecks {
    static func main() throws {
        var count = 0
        func check(_ condition: @autoclosure () -> Bool, _ message: String) {
            guard condition() else { fatalError("FAIL: " + message) }
            count += 1
        }
        let prompts = ["security logs with a bat", "build a release", "creative images", "memory and notes", "vision camera", "research papers"]
        for prompt in prompts {
            let spec = PixelSpec.fallback(for: prompt)
            check(spec.validationError == nil, "fallback is valid: " + prompt)
            check(!spec.permissions.shell, "template never grants shell")
            check(spec.character.age >= 18, "adult character")
            let encoded = try JSONEncoder().encode(spec)
            check(PixelSpec.parse(String(data: encoded, encoding: .utf8)!) != nil, "round trip")
        }
        var spec = PixelSpec.fallback(for: "creative design with a bat")
        spec.toolsets = ["web", "file", "terminal", "code_execution", "browser", "memory", "delegation", "future_root_tool"]
        spec.permissions = .init(approvalMode: "ask", network: false, filesystem: "none", shell: false)
        check(spec.permittedToolsets == ["memory", "delegation"], "deny network, shell, files and unknown tools")
        spec.permissions = .init(approvalMode: "ask", network: true, filesystem: "workspace", shell: false)
        check(!spec.permittedToolsets.contains("terminal") && !spec.permittedToolsets.contains("code_execution"), "shell is independent of network")
        spec.permissions.shell = true
        check(spec.permittedToolsets.contains("terminal"), "explicit shell tool")
        var draft = PixelDraft(spec: spec, usedModel: false, availableToolsets: ["memory", "file"], availableSkills: [], notes: [])
        check(draft.enabledToolsets == ["file", "memory"], "gateway catalog gates tools")
        draft.spec.permissions.filesystem = "none"
        check(draft.enabledToolsets == ["memory"], "review changes recalculate allowed tools")
        spec = PixelSpec.fallback(for: "security")
        spec.name = "../../bad"; check(spec.validationError != nil, "invalid slug")
        spec = PixelSpec.fallback(for: "security")
        spec.character.age = 17; check(spec.validationError != nil, "reject underage character")
        spec.character.age = 28
        spec.character.recipe.hair = "invented"; check(spec.validationError != nil, "reject missing layer")
        spec = PixelSpec.fallback(for: "security")
        spec.soul = "Too short."; check(spec.validationError != nil, "briefing length")
        spec = PixelSpec.fallback(for: "security")
        spec.connectors = ["unknown"]; check(spec.validationError != nil, "reject connector")
        spec = PixelSpec.fallback(for: "security")
        spec.toolsets = ["web", "web"]; check(spec.validationError != nil, "duplicate tools")
        spec = PixelSpec.fallback(for: "security")
        let text = String(data: try JSONEncoder().encode(spec), encoding: .utf8)!
        check(PixelSpec.parse("Here is your bot: " + text) == nil, "reject prose")
        check(PixelSpec.parse(text + text) == nil, "reject trailing object")
        let data = Data(text.replacingOccurrences(of: "\"shell\":false", with: "\"shell\":\"false\"").utf8)
        check((try? JSONDecoder().decode(PixelSpec.self, from: data)) == nil, "strict permission boolean")
        if CommandLine.arguments.count > 1 {
            spec.name = "cinder-fixture"
            try JSONEncoder().encode(spec).write(to: URL(fileURLWithPath: CommandLine.arguments[1]))
        }
        print("PASS: \(count) PixelSpec checks")
    }
}

