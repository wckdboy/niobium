import SwiftUI

struct ChatToolsSheet: View {
    @EnvironmentObject private var store: AppStore
    @Environment(\.dismiss) private var dismiss
    let bot: BotProfile
    @State private var search = ""

    private var filtered: [Toolset] {
        guard !search.isEmpty else { return store.toolsets }
        return store.toolsets.filter {
            $0.name.localizedCaseInsensitiveContains(search)
            || $0.description.localizedCaseInsensitiveContains(search)
            || $0.tools.contains { $0.localizedCaseInsensitiveContains(search) }
        }
    }

    var body: some View {
        NavigationStack {
            ZStack {
                NBMeshBackground(intensity: 0.4)
                ScrollView {
                    VStack(alignment: .leading, spacing: 14) {
                        HStack(spacing: 10) {
                            BotAvatar(bot: bot, size: 40, showStatus: false)
                            VStack(alignment: .leading, spacing: 2) {
                                Text("\(bot.title)’s toolkit").font(.nbTitle(16)).foregroundStyle(NB.text)
                                Text("\(store.toolsets.filter(\.enabled).count) of \(store.toolsets.count) toolsets enabled")
                                    .font(.nbMono(9)).foregroundStyle(NB.tertiary)
                            }
                        }
                        .padding(.bottom, 4)

                        HStack(spacing: 8) {
                            Image(systemName: "magnifyingglass").font(.system(size: 11)).foregroundStyle(NB.tertiary)
                            TextField("Search tools", text: $search)
                                .font(.nbBody(13))
                                .foregroundStyle(NB.text)
                        }
                        .padding(.horizontal, 12).frame(height: 38)
                        .background(RoundedRectangle(cornerRadius: 11).fill(NB.surface))
                        .overlay(RoundedRectangle(cornerRadius: 11).strokeBorder(NB.line, lineWidth: 1))

                        if filtered.isEmpty {
                            EmptyStateView(icon: "hammer", title: "No tools found",
                                           detail: search.isEmpty ? "The gateway didn’t return a tool catalog." : "Try a different search.")
                        } else {
                            ForEach(filtered) { toolset in
                                toolsetRow(toolset)
                            }
                        }
                    }
                    .padding(18)
                }
            }
            .navigationTitle("Tools")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }.foregroundStyle(NB.ion)
                }
            }
            .toolbarBackground(NB.ink, for: .navigationBar)
        }
        .presentationDetents([.large])
        .presentationBackground(NB.ink)
    }

    private func toolsetRow(_ toolset: Toolset) -> some View {
        VStack(alignment: .leading, spacing: 9) {
                HStack(spacing: 10) {
                    ZStack {
                        RoundedRectangle(cornerRadius: 10).fill((toolset.enabled ? NB.ion : NB.tertiary).opacity(0.11))
                        Image(systemName: toolset.icon)
                            .font(.system(size: 13, weight: .medium))
                            .foregroundStyle(toolset.enabled ? NB.ion : NB.tertiary)
                    }
                    .frame(width: 33, height: 33)
                    VStack(alignment: .leading, spacing: 2) {
                        HStack(spacing: 6) {
                            Text(toolset.name).font(.nbMono(11, weight: .semibold)).foregroundStyle(NB.text)
                            Text("\(toolset.tools.count)").font(.nbMono(8)).foregroundStyle(NB.tertiary)
                        }
                        Text(toolset.description)
                            .font(.nbBody(11))
                            .foregroundStyle(NB.secondary)
                            .lineLimit(2)
                            .multilineTextAlignment(.leading)
                    }
                    Spacer()
                    ZStack(alignment: toolset.enabled ? .trailing : .leading) {
                        Capsule().fill(toolset.enabled ? NB.ion : NB.fillStrong)
                            .frame(width: 38, height: 22)
                        Circle().fill(toolset.enabled ? Color(hex: 0x0A0D14) : NB.tertiary)
                            .frame(width: 17, height: 17)
                            .padding(.horizontal, 2.5)
                    }
                }
                if !toolset.tools.isEmpty {
                    Text(toolset.tools.prefix(4).joined(separator: "  ·  "))
                        .font(.nbMono(8))
                        .foregroundStyle(NB.tertiary)
                        .lineLimit(1)
                        .padding(.leading, 43)
                }
            }
            .padding(12)
            .background(RoundedRectangle(cornerRadius: 15).fill(NB.surface.opacity(0.72)))
            .overlay(RoundedRectangle(cornerRadius: 15).strokeBorder(toolset.enabled ? NB.ion.opacity(0.16) : NB.line, lineWidth: 1))
            .contentShape(Rectangle())
            .onTapGesture { Task { await store.toggleToolset(toolset) } }
    }
}

struct BotInfoSheet: View {
    @EnvironmentObject private var store: AppStore
    @Environment(\.dismiss) private var dismiss
    let bot: BotProfile

    var body: some View {
        NavigationStack {
            ZStack {
                NBMeshBackground(intensity: 0.4)
                ScrollView {
                    VStack(spacing: 18) {
                        BotAvatar(bot: bot, size: 80, showStatus: false)
                        VStack(spacing: 5) {
                            Text(bot.title).font(.nbDisplay(28)).foregroundStyle(NB.text)
                            Text("@\(bot.name)").font(.nbMono(10)).foregroundStyle(NB.tertiary)
                        }
                        Text(bot.role)
                            .font(.nbBody(13))
                            .foregroundStyle(NB.secondary)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, 20)

                        VStack(spacing: 0) {
                            infoRow("Model", bot.model.isEmpty ? "Inherited from gateway" : bot.model)
                            NBDivider()
                            infoRow("Provider", bot.provider.isEmpty ? "Inherited" : bot.provider)
                            NBDivider()
                            infoRow("Skills", "\(bot.skillCount)")
                            NBDivider()
                            infoRow("Canonical chat", bot.canonicalSessionID ?? "Not created yet")
                            NBDivider()
                            infoRow("Status", bot.statusLine)
                        }
                        .padding(.horizontal, 14)
                        .background(RoundedRectangle(cornerRadius: 18).fill(NB.surface.opacity(0.7)))
                        .overlay(RoundedRectangle(cornerRadius: 18).strokeBorder(NB.line, lineWidth: 1))

                        if !store.toolsets.isEmpty {
                            VStack(alignment: .leading, spacing: 10) {
                                NBSectionHeader(title: "Enabled toolsets")
                                FlowLayout(spacing: 6) {
                                    ForEach(store.toolsets.filter(\.enabled).prefix(12)) { toolset in
                                        NBPill(text: toolset.name, tint: NB.ion)
                                    }
                                }
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                        }
                    }
                    .padding(20)
                }
            }
            .navigationTitle("Pixel details")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }.foregroundStyle(NB.ion)
                }
            }
            .toolbarBackground(NB.ink, for: .navigationBar)
        }
        .presentationDetents([.large])
        .presentationBackground(NB.ink)
    }

    private func infoRow(_ label: String, _ value: String) -> some View {
        HStack(alignment: .top) {
            Text(label).font(.nbBody(12)).foregroundStyle(NB.tertiary)
            Spacer()
            Text(value)
                .font(.nbMono(10))
                .foregroundStyle(NB.secondary)
                .multilineTextAlignment(.trailing)
                .lineLimit(2)
        }
        .padding(.vertical, 12)
    }
}

struct RoutinesView: View {
    @EnvironmentObject private var store: AppStore
    @Environment(\.dismiss) private var dismiss
    let bot: BotProfile
    @State private var showCreate = false

    var body: some View {
        NavigationStack {
            ZStack {
                NBMeshBackground(intensity: 0.4)
                ScrollView {
                    VStack(alignment: .leading, spacing: 12) {
                        HStack {
                            VStack(alignment: .leading, spacing: 3) {
                                Text("Routines").font(.nbTitle(18)).foregroundStyle(NB.text)
                                Text("Recurring work that lands in \(bot.title)’s chat.")
                                    .font(.nbBody(12)).foregroundStyle(NB.secondary)
                            }
                            Spacer()
                            Button { showCreate = true } label: {
                                Image(systemName: "plus").foregroundStyle(NB.ion)
                            }
                        }
                        .padding(.bottom, 4)

                        if store.routines.isEmpty {
                            EmptyStateView(icon: "clock.arrow.circlepath", title: "No routines yet",
                                           detail: "A morning brief, a nightly sweep, a weekly review. Give \(bot.title) a rhythm.",
                                           actionTitle: "Create a routine") { showCreate = true }
                        } else {
                            ForEach(store.routines) { routine in
                                VStack(alignment: .leading, spacing: 8) {
                                    HStack {
                                        Text(routine.name).font(.nbTitle(14)).foregroundStyle(NB.text)
                                        Spacer()
                                        Text(routine.enabled ? "ON" : "PAUSED")
                                            .font(.nbMono(8, weight: .semibold)).tracking(0.8)
                                            .foregroundStyle(routine.enabled ? NB.mint : NB.tertiary)
                                    }
                                    Text(routine.prompt).font(.nbBody(12)).foregroundStyle(NB.secondary).lineLimit(2)
                                    HStack {
                                        Label(routine.schedule, systemImage: "repeat")
                                            .font(.nbMono(8.5)).foregroundStyle(NB.tertiary)
                                        Spacer()
                                        if !routine.nextRun.isEmpty {
                                            Text("next · \(routine.nextRun)").font(.nbMono(8)).foregroundStyle(NB.tertiary)
                                        }
                                        Button {
                                            Task { await store.deleteRoutine(routine, bot: bot) }
                                        } label: {
                                            Image(systemName: "trash").font(.system(size: 10)).foregroundStyle(NB.rose.opacity(0.7))
                                        }
                                    }
                                }
                                .padding(14)
                                .background(RoundedRectangle(cornerRadius: 16).fill(NB.surface.opacity(0.72)))
                                .overlay(RoundedRectangle(cornerRadius: 16).strokeBorder(NB.line, lineWidth: 1))
                            }
                        }
                    }
                    .padding(18)
                }
            }
            .navigationTitle("Routines")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Done") { dismiss() }.foregroundStyle(NB.ion)
                }
            }
            .toolbarBackground(NB.ink, for: .navigationBar)
        }
        .presentationDetents([.large])
        .presentationBackground(NB.ink)
        .sheet(isPresented: $showCreate) {
            NewRoutineView(bot: bot).environmentObject(store)
        }
        .task { await store.loadRoutines(for: bot) }
    }
}

struct NewRoutineView: View {
    @EnvironmentObject private var store: AppStore
    @Environment(\.dismiss) private var dismiss
    let bot: BotProfile
    @State private var name = ""
    @State private var prompt = ""
    @State private var schedule = "0 8 * * *"
    @State private var saving = false
    @State private var error: String?

    var body: some View {
        NavigationStack {
            ZStack {
                NBMeshBackground(intensity: 0.4)
                ScrollView {
                    VStack(alignment: .leading, spacing: 16) {
                        field("NAME", placeholder: "Morning brief", text: $name)
                        field("PROMPT", placeholder: "What should \(bot.title) do?", text: $prompt, multiline: true)
                        VStack(alignment: .leading, spacing: 8) {
                            NBEyebrow(text: "Schedule")
                            FlowLayout(spacing: 7) {
                                ForEach([("Every morning", "0 8 * * *"), ("Every evening", "0 19 * * *"),
                                         ("Nightly", "0 2 * * *"), ("Weekly", "0 9 * * 1")], id: \.1) { preset in
                                    Button {
                                        schedule = preset.1
                                    } label: {
                                        Text(preset.0)
                                            .font(.nbBody(11.5, weight: .medium))
                                            .foregroundStyle(schedule == preset.1 ? NB.ion : NB.secondary)
                                            .padding(.horizontal, 11).padding(.vertical, 7)
                                            .background(Capsule().fill(schedule == preset.1 ? NB.ion.opacity(0.12) : NB.fillSoft))
                                            .overlay(Capsule().strokeBorder(schedule == preset.1 ? NB.ion.opacity(0.35) : NB.line, lineWidth: 1))
                                    }.buttonStyle(.plain)
                                }
                            }
                            TextField("Cron expression", text: $schedule)
                                .font(.nbMono(11)).foregroundStyle(NB.text)
                                .padding(12).background(RoundedRectangle(cornerRadius: 10).fill(NB.surface))
                                .overlay(RoundedRectangle(cornerRadius: 10).strokeBorder(NB.line, lineWidth: 1))
                        }
                        if let error { Text(error).font(.nbBody(11)).foregroundStyle(NB.rose) }
                        Button {
                            saving = true; error = nil
                            Task {
                                do {
                                    try await store.createRoutine(name: name, schedule: schedule, prompt: prompt, bot: bot)
                                    dismiss()
                                } catch {
                                    self.error = error.localizedDescription
                                    saving = false
                                }
                            }
                        } label: {
                            HStack {
                                if saving { ProgressView().tint(NB.onIon).scaleEffect(0.8) }
                                Text(saving ? "Creating…" : "Create routine").frame(maxWidth: .infinity)
                            }
                        }
                        .buttonStyle(NBPrimaryButtonStyle())
                        .disabled(name.isEmpty || prompt.isEmpty || saving)
                    }
                    .padding(20)
                }
            }
            .navigationTitle("New routine")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }.foregroundStyle(NB.secondary)
                }
            }
            .toolbarBackground(NB.ink, for: .navigationBar)
        }
        .presentationBackground(NB.ink)
    }

    private func field(_ label: String, placeholder: String, text: Binding<String>, multiline: Bool = false) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            NBEyebrow(text: label)
            TextField(placeholder, text: text, axis: multiline ? .vertical : .horizontal)
                .lineLimit(multiline ? 3...6 : 1...1)
                .font(.nbBody(14))
                .foregroundStyle(NB.text)
                .padding(12)
                .background(RoundedRectangle(cornerRadius: 11).fill(NB.surface))
                .overlay(RoundedRectangle(cornerRadius: 11).strokeBorder(NB.line, lineWidth: 1))
        }
    }
}

struct FlowLayout: Layout {
    var spacing: CGFloat = 6
    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let maxWidth = proposal.width ?? 300
        var x: CGFloat = 0, y: CGFloat = 0, rowHeight: CGFloat = 0
        for view in subviews {
            let size = view.sizeThatFits(.unspecified)
            if x + size.width > maxWidth, x > 0 {
                x = 0; y += rowHeight + spacing; rowHeight = 0
            }
            x += size.width + spacing
            rowHeight = max(rowHeight, size.height)
        }
        return CGSize(width: maxWidth, height: y + rowHeight)
    }
    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var x = bounds.minX, y = bounds.minY, rowHeight: CGFloat = 0
        for view in subviews {
            let size = view.sizeThatFits(.unspecified)
            if x + size.width > bounds.maxX, x > bounds.minX {
                x = bounds.minX; y += rowHeight + spacing; rowHeight = 0
            }
            view.place(at: CGPoint(x: x, y: y), proposal: ProposedViewSize(size))
            x += size.width + spacing
            rowHeight = max(rowHeight, size.height)
        }
    }
}
