import SwiftUI

struct AIBuildView: View {
    @EnvironmentObject private var store: AppStore
    @Environment(\.dismiss) private var dismiss
    var onBuilt: (BotProfile) -> Void
    @State private var prompt = ""
    @State private var draft: PixelDraft?
    @State private var error: String?
    @State private var working = false
    @State private var showSoul = false
    @State private var designTask: Task<Void, Never>?

    private let ideas = [
        "A sharp-tongued night-shift security witch for my VPS logs, with a tiny bat",
        "A mischievous cyber creative director who makes images and moodboards",
        "A calm release engineer with a wonky drone; asks before deploying",
        "A scholarly forest mage with a pocket slime who reads papers and keeps notes"
    ]

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    if let current = draft {
                        review(current)
                    } else {
                        Text("Meet your next Pixel.").font(.nbDisplay(30)).foregroundStyle(NB.text)
                        Text("Give them a purpose. The bot-build skill designs their voice, adult character, companion, skills and access.")
                            .font(.nbBody(13)).foregroundStyle(NB.secondary)
                        TextEditor(text: $prompt)
                            .font(.nbBody(15)).foregroundStyle(NB.text)
                            .scrollContentBackground(.hidden).frame(minHeight: 130).padding(12)
                            .background(RoundedRectangle(cornerRadius: 16).fill(NB.surface))
                            .overlay(RoundedRectangle(cornerRadius: 16).strokeBorder(NB.lineStrong))
                            .accessibilityLabel("Describe your Pixel")
                            .disabled(working)
                        ForEach(ideas, id: \.self) { idea in
                            Button { prompt = idea } label: {
                                HStack {
                                    Text(idea).font(.nbBody(12)).multilineTextAlignment(.leading)
                                    Spacer()
                                    Image(systemName: "arrow.up.left")
                                }.foregroundStyle(NB.secondary).padding(12)
                                    .background(RoundedRectangle(cornerRadius: 12).fill(NB.fillSoft))
                            }.buttonStyle(.plain).disabled(working)
                        }
                        Button(action: design) {
                            HStack {
                                if working { ProgressView().tint(NB.onIon) }
                                Text(working ? "Designing your Pixel…" : "Design a Pixel").frame(maxWidth: .infinity)
                            }
                        }.buttonStyle(NBPrimaryButtonStyle()).disabled(working || prompt.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                        Text("You’ll review their character, briefing and permissions before creation. If the model is unavailable, the preview is labelled as a local template.")
                            .font(.nbBody(11)).foregroundStyle(NB.tertiary)
                    }
                    if let error { Text(error).font(.nbBody(12)).foregroundStyle(NB.rose).textSelection(.enabled) }
                }.padding(20)
            }
            .background(NB.void)
            .navigationTitle(draft == nil ? "Pixel workshop" : "Review your Pixel")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(draft == nil ? "Cancel" : "Back") {
                        if draft != nil { draft = nil; error = nil } else { designTask?.cancel(); dismiss() }
                    }.disabled(working && draft != nil)
                }
            }
        }.task {
            if store.isDemo && ProcessInfo.processInfo.arguments.contains("--pixel-review") {
                draft = try? await store.draftPixel(prompt: "A sharp-tongued security witch for VPS logs, with a bat")
            }
        }.interactiveDismissDisabled(working && draft != nil)
            .onDisappear { designTask?.cancel() }
    }

    private func review(_ current: PixelDraft) -> some View {
        let spec = current.spec
        let base = CastMember.all.first { $0.id == spec.character.archetype } ?? CastMember.all[0]
        let member = CastMember(id: base.id, name: spec.title, portrait: base.portrait, companion: spec.companion.kind,
                                companionName: spec.companion.name, accentHex: base.accentHex, recipe: spec.character.recipe)
        return VStack(alignment: .leading, spacing: 16) {
            HStack(spacing: 16) {
                CastPortrait(member: member, size: 110)
                VStack(alignment: .leading, spacing: 6) {
                    NBPill(text: current.usedModel ? "AI designed" : "Local template", tint: NB.ion)
                    Text(spec.title).font(.nbTitle(23)).foregroundStyle(NB.text)
                    Text(spec.role).font(.nbBody(12)).foregroundStyle(NB.secondary)
                    Text("\(spec.character.age) · \(spec.personality.joined(separator: " · "))")
                        .font(.nbBody(10)).foregroundStyle(NB.tertiary)
                }
            }
            TextField("Profile name", text: Binding(get: { draft?.spec.name ?? "" }, set: { draft?.spec.name = $0 }))
                .textInputAutocapitalization(.never).autocorrectionDisabled().font(.nbMono(13))
                .padding(12).background(RoundedRectangle(cornerRadius: 10).fill(NB.surface))
            Text("\(spec.companion.name) · \(spec.companion.kind)").font(.nbTitle(15)).foregroundStyle(NB.text)
            Text(spec.companion.trait).font(.nbBody(12)).foregroundStyle(NB.secondary)
            VStack(alignment: .leading, spacing: 12) {
                NBSectionHeader(title: "Access", detail: "reviewed before creation")
                Toggle("Network tools", isOn: Binding(get: { draft?.spec.permissions.network ?? false }, set: { draft?.spec.permissions.network = $0 }))
                Toggle("Shell & code execution", isOn: Binding(get: { draft?.spec.permissions.shell ?? false }, set: { draft?.spec.permissions.shell = $0 }))
                Picker("Files", selection: Binding(get: { draft?.spec.permissions.filesystem ?? "workspace" }, set: { draft?.spec.permissions.filesystem = $0 })) {
                    Text("None").tag("none"); Text("Workspace").tag("workspace"); Text("Unrestricted").tag("unrestricted")
                }
                Text("Host approvals stay manual. Tool selection is applied to Hermes; network and workspace isolation are configured on the host. The Pixel inherits the default profile’s model credentials.")
                    .font(.nbBody(10)).foregroundStyle(NB.tertiary)
                Text("Tools: \(current.enabledToolsets.isEmpty ? "none" : current.enabledToolsets.joined(separator: ", "))")
                    .font(.nbMono(11)).foregroundStyle(NB.secondary)
                Text("Skills: \(spec.skills.isEmpty ? "none" : spec.skills.joined(separator: ", "))")
                    .font(.nbBody(12)).foregroundStyle(NB.secondary)
                Text("Requested connections: \(spec.connectors.isEmpty ? "none" : spec.connectors.joined(separator: ", "))")
                    .font(.nbBody(12)).foregroundStyle(NB.secondary)
            }.font(.nbBody(13)).tint(NB.ion).padding(16)
                .background(RoundedRectangle(cornerRadius: 16).fill(NB.surface))
            DisclosureGroup("Operator briefing", isExpanded: $showSoul) {
                Text(spec.soul).font(.nbBody(12)).foregroundStyle(NB.secondary).textSelection(.enabled).padding(.top, 8)
            }.foregroundStyle(NB.text)
            ForEach(current.notes, id: \.self) { note in
                Text(note).font(.nbBody(11)).foregroundStyle(NB.secondary)
            }
            Button(action: create) {
                HStack {
                    if working { ProgressView().tint(NB.onIon) }
                    Text(working ? "Creating…" : "Create \(spec.title)").frame(maxWidth: .infinity)
                }
            }.buttonStyle(NBPrimaryButtonStyle()).disabled(working || spec.validationError != nil)
            if let validation = spec.validationError { Text(validation).font(.nbBody(11)).foregroundStyle(NB.rose) }
        }
    }
    private func design() {
        working = true; error = nil
        designTask = Task {
            do {
                let result = try await store.draftPixel(prompt: prompt)
                try Task.checkCancellation()
                draft = result
            }
            catch { self.error = error.localizedDescription }
            working = false
        }
    }
    private func create() {
        guard let draft else { return }
        working = true; error = nil
        Task {
            do { let bot = try await store.buildPixel(draft: draft); onBuilt(bot); dismiss() }
            catch { self.error = error.localizedDescription; working = false }
        }
    }
}
