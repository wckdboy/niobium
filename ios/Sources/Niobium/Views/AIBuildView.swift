import SwiftUI

struct AIBuildView: View {
    @EnvironmentObject private var store: AppStore
    @Environment(\.dismiss) private var dismiss
    var onBuilt: (BotProfile) -> Void
    @State private var prompt = ""
    @State private var draft: PixelDraft?
    @State private var error: String?
    @State private var working = false
    @State private var refining = false
    @State private var section = "Look"
    @State private var showSoul = false
    @State private var showDetails = false
    @State private var history: [PixelLook] = []
    @State private var designTask: Task<Void, Never>?
    @State private var refinementID = UUID()
    @FocusState private var writing: Bool

    private let sections = ["Look", "Creature", "Persona", "Access"]
    private let ideas: [(String, String, String)] = [
        ("Security", "bat", "A sharp-tongued security witch for my VPS logs, with a tiny bat"),
        ("Creative", "cat", "A mischievous cyber creative director who makes images and moodboards"),
        ("Builder", "drone", "A calm release engineer with a wonky drone; asks before deploying"),
        ("Research", "slime", "A scholarly forest mage with a pocket slime who reads papers and keeps notes")
    ]

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button {
                    designTask?.cancel(); refinementID = UUID(); refining = false
                    if draft != nil { draft = nil; error = nil; history = [] } else { dismiss() }
                } label: {
                    HStack(spacing: 6) { PixelIcon(.back, size: 16); Text(draft == nil ? "Close" : "Back").font(.nbBody(13)) }
                        .foregroundStyle(NB.secondary).frame(minWidth: 60, minHeight: 44)
                }.buttonStyle(.plain).disabled(working)
                Spacer()
                Text(draft == nil ? "New Pixel" : "Your Pixel").font(.nbTitle(17)).foregroundStyle(NB.text)
                Spacer()
                PixelIcon(.spark, size: 20).frame(width: 60, height: 44)
            }.padding(.horizontal, 16).padding(.top, 8)
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    if let current = draft {
                        identity(current)
                        HStack(spacing: 4) {
                            ForEach(sections, id: \.self) { name in
                                Button { section = name } label: {
                                    Text(name).font(.nbBody(12, weight: .semibold))
                                        .foregroundStyle(section == name ? NB.ion : NB.secondary)
                                        .frame(maxWidth: .infinity, minHeight: 44)
                                        .background(PixelButtonShape().fill(section == name ? NB.ion.opacity(0.12) : NB.fillSoft))
                                }.buttonStyle(.plain).accessibilityIdentifier("builder.tab." + name.lowercased())
                                    .accessibilityAddTraits(section == name ? .isSelected : [])
                            }
                        }
                        details(current).disabled(working)
                    } else {
                        promptForm
                    }
                    if let error { Text(error).font(.nbBody(12)).foregroundStyle(NB.rose).textSelection(.enabled) }
                }.padding(.horizontal, 20).padding(.vertical, 16)
            }.scrollDismissesKeyboard(.interactively)
        }
        .background(NB.void)
        .safeAreaInset(edge: .bottom, spacing: 0) {
            VStack(spacing: 8) {
                if let current = draft {
                    Text(accessSummary(current.spec)).font(.nbBody(10)).foregroundStyle(NB.secondary).lineLimit(2)
                    Button(action: create) {
                        HStack(spacing: 8) {
                            if working { ProgressView().tint(NB.onIon) } else { PixelIcon(.pixels, size: 18, tint: NB.onIon) }
                            Text(working ? "Creating…" : "Create " + current.spec.title).frame(maxWidth: .infinity)
                        }
                    }.buttonStyle(NBPrimaryButtonStyle()).disabled(working || current.spec.validationError != nil)
                    if let invalid = current.spec.validationError { Text(invalid).font(.nbBody(11)).foregroundStyle(NB.rose) }
                } else {
                    Button(action: preview) { PixelActionLabel("Preview Pixel", icon: .spark) }
                        .buttonStyle(NBPrimaryButtonStyle()).disabled(prompt.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || prompt.count > 4000)
                }
            }.padding(.horizontal, 20).padding(.top, 12).padding(.bottom, 10).background(NB.void)
        }
        .task {
            if store.isDemo && ProcessInfo.processInfo.arguments.contains("--pixel-review") {
                prompt = "A sharp-tongued security witch for VPS logs, with a bat"
                preview()
            }
        }
        .interactiveDismissDisabled(working)
        .onDisappear { designTask?.cancel() }
    }

    private var promptForm: some View {
        VStack(alignment: .leading, spacing: 18) {
            Text("Give them a job.").font(.nbDisplay(28)).foregroundStyle(NB.text)
            ZStack(alignment: .topLeading) {
                if prompt.isEmpty { Text("A creative witch for my next project…").font(.nbBody(15)).foregroundStyle(NB.tertiary).padding(16) }
                TextEditor(text: $prompt).font(.nbBody(15)).foregroundStyle(NB.text)
                    .scrollContentBackground(.hidden).frame(minHeight: 110).padding(10)
                    .accessibilityLabel("Describe your Pixel").focused($writing)
            }.pixelPanel()
            LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 8) {
                ForEach(ideas, id: \.0) { idea in
                    Button { prompt = idea.2; Haptics.tap() } label: {
                        HStack(spacing: 10) {
                            FamiliarSprite(kind: idea.1, size: 24)
                            Text(idea.0).font(.nbBody(12, weight: .medium)).foregroundStyle(NB.text)
                            Spacer(minLength: 0)
                        }.padding(12).pixelPanel()
                    }.buttonStyle(.plain)
                }
            }
        }
    }

    private func identity(_ current: PixelDraft) -> some View {
        let spec = current.spec
        let base = CastMember.all.first { $0.id == spec.character.archetype } ?? CastMember.all[0]
        let member = CastMember(id: base.id, name: spec.title, portrait: base.portrait, companion: spec.companion.kind,
                                companionName: spec.companion.name, accentHex: base.accentHex,
                                recipe: spec.character.recipe, look: spec.character.look)
        return HStack(spacing: 16) {
            CastPortrait(member: member, size: 128, showGlow: false)
            VStack(alignment: .leading, spacing: 8) {
                NBPill(text: current.usedModel ? "AI persona" : "Local template")
                TextField("Name", text: Binding(get: { draft?.spec.title ?? "" }, set: { draft?.spec.title = $0 }))
                    .font(.nbTitle(23)).foregroundStyle(NB.text).accessibilityLabel("Pixel name").disabled(working)
                Text(spec.role).font(.nbBody(12)).foregroundStyle(NB.secondary).lineLimit(2)
                Text("\(spec.character.age) · adult").font(.nbMono(10)).foregroundStyle(NB.tertiary)
                Text(spec.companion.name + " · " + spec.companion.kind).font(.nbBody(11)).foregroundStyle(NB.secondary)
            }.frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    @ViewBuilder
    private func details(_ current: PixelDraft) -> some View {
        let spec = current.spec
        switch section {
        case "Look":
            PixelLookEditor(look: Binding(get: { draft?.spec.character.look ?? PixelLook(portrait: spec.character.archetype) },
                                         set: { draft?.spec.character.look = $0 }), history: $history, enabled: !working)
        case "Creature":
            FamiliarPicker(kind: Binding(get: { draft?.spec.companion.kind ?? spec.companion.kind }, set: {
                draft?.spec.companion.kind = $0
                draft?.spec.companion.trait = PixelSpec.companionTrait($0)
            }), enabled: !working)
            TextField("Creature name", text: Binding(get: { draft?.spec.companion.name ?? "" }, set: { draft?.spec.companion.name = $0 }))
                .font(.nbBody(14)).padding(12).pixelPanel().accessibilityLabel("Creature name")
            Text(spec.companion.trait).font(.nbBody(12)).foregroundStyle(NB.secondary)
        case "Persona":
            TextField("Role", text: Binding(get: { draft?.spec.role ?? "" }, set: { draft?.spec.role = $0 }))
                .font(.nbBody(14)).padding(12).pixelPanel()
            Text(spec.personality.joined(separator: " · ")).font(.nbBody(12)).foregroundStyle(NB.secondary)
            DisclosureGroup("Briefing", isExpanded: $showSoul) {
                Text(spec.soul).font(.nbBody(12)).foregroundStyle(NB.secondary).textSelection(.enabled).padding(.top, 10)
            }.font(.nbBody(13)).foregroundStyle(NB.text)
            Button {
                if refining { designTask?.cancel(); refinementID = UUID(); refining = false } else { refine() }
            } label: {
                HStack(spacing: 8) {
                    if refining { ProgressView().tint(NB.ion) } else { PixelIcon(.spark, size: 18) }
                    Text(refining ? "Cancel AI" : "Refine persona with AI").frame(maxWidth: .infinity)
                }
            }.buttonStyle(NBQuietButtonStyle()).disabled(working)
            if !current.notes.isEmpty {
                DisclosureGroup("Details", isExpanded: $showDetails) {
                    ForEach(current.notes, id: \.self) { Text($0).font(.nbBody(11)).foregroundStyle(NB.secondary) }
                }.font(.nbBody(12)).foregroundStyle(NB.secondary)
            }
        default:
            VStack(spacing: 12) {
                Toggle("Network", isOn: Binding(get: { draft?.spec.permissions.network ?? false }, set: { draft?.spec.permissions.network = $0 }))
                Toggle("Shell", isOn: Binding(get: { draft?.spec.permissions.shell ?? false }, set: { draft?.spec.permissions.shell = $0 }))
                Picker("Files", selection: Binding(get: { draft?.spec.permissions.filesystem ?? "workspace" }, set: { draft?.spec.permissions.filesystem = $0 })) {
                    Text("None").tag("none"); Text("Workspace").tag("workspace"); Text("Full").tag("unrestricted")
                }
            }.font(.nbBody(13)).tint(NB.ion).padding(14).pixelPanel()
            Text("Tools · " + current.enabledToolsets.joined(separator: ", ")).font(.nbBody(11)).foregroundStyle(NB.secondary)
            if !spec.skills.isEmpty { Text("Skills · " + spec.skills.joined(separator: ", ")).font(.nbBody(11)).foregroundStyle(NB.secondary) }
            if !spec.connectors.isEmpty { Text("Requested · " + spec.connectors.joined(separator: ", ")).font(.nbBody(11)).foregroundStyle(NB.secondary) }
            DisclosureGroup("Access details") {
                VStack(alignment: .leading, spacing: 10) {
                    Text("Tool choices are applied to the selected engine. Workspace scope is an instruction; the engine enforces sandboxing. Connections need separate account authorization.")
                        .font(.nbBody(11)).foregroundStyle(NB.secondary)
                    TextField("Profile ID", text: Binding(get: { draft?.spec.name ?? "" }, set: { draft?.spec.name = $0 }))
                        .textInputAutocapitalization(.never).autocorrectionDisabled().font(.nbMono(12))
                }.padding(.top, 10)
            }.font(.nbBody(12)).foregroundStyle(NB.secondary)
        }
    }

    private func accessSummary(_ spec: PixelSpec) -> String {
        [spec.permissions.network ? "Network" : "No network",
         spec.permissions.filesystem == "workspace" ? "Workspace" : spec.permissions.filesystem == "none" ? "No files" : "Full files",
         spec.permissions.shell ? "Shell" : "No shell"].joined(separator: " · ")
    }

    private func preview() {
        writing = false; error = nil; history = []; section = "Look"
        draft = PixelBuilder.shared.preview(for: prompt.trimmingCharacters(in: .whitespacesAndNewlines))
    }
    private func refine() {
        guard let start = draft else { return }
        refining = true; error = nil
        let requestID = UUID(); refinementID = requestID
        designTask = Task {
            defer { if refinementID == requestID { refining = false } }
            do {
                let result = try await store.draftPixel(prompt: prompt)
                try Task.checkCancellation()
                guard refinementID == requestID, var current = draft else { return }
                if current.spec.title == start.spec.title { current.spec.title = result.spec.title }
                if current.spec.role == start.spec.role { current.spec.role = result.spec.role }
                if current.spec.soul == start.spec.soul { current.spec.soul = result.spec.soul }
                if current.spec.personality == start.spec.personality { current.spec.personality = result.spec.personality }
                if current.spec.toolsets == start.spec.toolsets { current.spec.toolsets = result.spec.toolsets }
                if current.spec.skills == start.spec.skills { current.spec.skills = result.spec.skills }
                if current.spec.connectors == start.spec.connectors { current.spec.connectors = result.spec.connectors }
                current.availableToolsets = result.availableToolsets
                current.availableSkills = result.availableSkills
                current.usedModel = result.usedModel
                current.notes = result.notes
                draft = current
            } catch is CancellationError { }
            catch { self.error = error.localizedDescription }
        }
    }
    private func create() {
        guard let draft else { return }
        designTask?.cancel(); refinementID = UUID(); refining = false
        working = true; error = nil
        Task {
            do { let bot = try await store.buildPixel(draft: draft); onBuilt(bot); dismiss() }
            catch { self.error = error.localizedDescription; working = false }
        }
    }
}
