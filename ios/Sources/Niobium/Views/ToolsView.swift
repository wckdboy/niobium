import SwiftUI

struct ToolsView: View {
    @EnvironmentObject private var store: AppStore
    var onClose: (() -> Void)? = nil
    @State private var search = ""
    @State private var showOnlyEnabled = false
    @State private var mode = 0

    private var filtered: [Toolset] {
        store.toolsets.filter { toolset in
            if showOnlyEnabled && !toolset.enabled { return false }
            guard !search.isEmpty else { return true }
            return toolset.name.localizedCaseInsensitiveContains(search)
                || toolset.description.localizedCaseInsensitiveContains(search)
                || toolset.tools.contains { $0.localizedCaseInsensitiveContains(search) }
        }
    }

    private var totalTools: Int { store.toolsets.reduce(0) { $0 + $1.tools.count } }

    var body: some View {
        if store.engine == .dsh { LocalToolsView(onClose: onClose) }
        else { hermesTools }
    }

    private var hermesTools: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                HStack {
                    if let onClose {
                        NBIconButton(symbol: "chevron.left", size: 36, tint: NB.secondary) { onClose() }
                        Spacer().frame(width: 10)
                    }
                    Wordmark()
                    Spacer()
                    Button { Task { await store.loadToolsets() } } label: {
                        Image(systemName: "arrow.clockwise").font(.system(size: 13, weight: .semibold))
                            .foregroundStyle(NB.secondary)
                            .frame(width: 38, height: 38)
                            .background(Circle().fill(NB.fill))
                            .overlay(Circle().strokeBorder(NB.line, lineWidth: 1))
                    }
                }
                .padding(.horizontal, 20).padding(.top, 6)

                NBScreenHeader(eyebrow: "Everything the agent can touch", title: "Toolbox")
                    .padding(.top, 24)

                Picker("Mode", selection: $mode) {
                    Text("Toolsets").tag(0)
                    Text("Skills").tag(1)
                    Text("MCP").tag(2)
                }
                .pickerStyle(.segmented)
                .padding(.horizontal, 20)
                .padding(.top, 18)

                if mode == 1 {
                    skillsSection
                } else if mode == 2 {
                    mcpSection
                } else {
                VStack(alignment: .leading, spacing: 5) {
                    Text("\(totalTools)")
                        .font(.nbDisplay(52))
                        .tracking(-2)
                        .foregroundStyle(
                            LinearGradient(colors: [NB.text, NB.secondary], startPoint: .top, endPoint: .bottom)
                        )
                    Text("tools across \(store.toolsets.count) toolsets on this gateway")
                        .font(.nbBody(12.5))
                        .foregroundStyle(NB.secondary)
                }
                .padding(.horizontal, 20)
                .padding(.top, 10)

                if !store.isDemo && store.chatSessionID == nil {
                    HStack(spacing: 8) {
                        Image(systemName: "info.circle").font(.system(size: 11)).foregroundStyle(NB.amber)
                        Text("Open a pixel chat to enable or disable a toolset for that live session.")
                            .font(.nbBody(11)).foregroundStyle(NB.amber.opacity(0.9))
                    }
                    .padding(11)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(RoundedRectangle(cornerRadius: 11).fill(NB.amber.opacity(0.07)))
                    .padding(.horizontal, 20)
                    .padding(.top, 16)
                }

                HStack(spacing: 8) {
                    HStack(spacing: 7) {
                        Image(systemName: "magnifyingglass").font(.system(size: 11)).foregroundStyle(NB.tertiary)
                        TextField("Search the toolbox", text: $search)
                            .font(.nbBody(13)).foregroundStyle(NB.text)
                    }
                    .padding(.horizontal, 12).frame(height: 38)
                    .background(RoundedRectangle(cornerRadius: 11).fill(NB.surface))
                    .overlay(RoundedRectangle(cornerRadius: 11).strokeBorder(NB.line, lineWidth: 1))

                    Button {
                        withAnimation(.easeOut(duration: 0.2)) { showOnlyEnabled.toggle() }
                    } label: {
                        Image(systemName: showOnlyEnabled ? "line.3.horizontal.decrease.circle.fill" : "line.3.horizontal.decrease.circle")
                            .font(.system(size: 17))
                            .foregroundStyle(showOnlyEnabled ? NB.ion : NB.tertiary)
                            .frame(width: 38, height: 38)
                            .background(RoundedRectangle(cornerRadius: 11).fill(NB.surface))
                            .overlay(RoundedRectangle(cornerRadius: 11).strokeBorder(NB.line, lineWidth: 1))
                    }
                    .buttonStyle(.plain)
                }
                .padding(.horizontal, 20)
                .padding(.top, 20)

                VStack(spacing: 9) {
                    ForEach(filtered) { toolset in
                        toolsetRow(toolset)
                    }
                }
                .padding(.horizontal, 20)
                .padding(.top, 14)
                .padding(.bottom, 36)
                }
            }
        }
        .scrollIndicators(.hidden)
        .refreshable { await store.loadToolsets(); await store.loadSkills(); await store.loadMCP() }
        .task {
            if store.toolsets.isEmpty { await store.loadToolsets() }
            await store.loadSkills()
            await store.loadMCP()
        }
    }

    private var mcpSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text("\(store.mcpServers.count) configured MCP servers").font(.nbBody(11)).foregroundStyle(NB.tertiary)
                Spacer()
                Button { Task { await store.loadMCP() } } label: {
                    Text("Refresh").font(.nbBody(11, weight: .medium)).foregroundStyle(NB.ion)
                }.buttonStyle(.plain)
            }
            if store.mcpServers.isEmpty {
                Text("No MCP servers configured for this profile.").font(.nbBody(11)).foregroundStyle(NB.tertiary)
            } else {
                ForEach(store.mcpServers) { server in
                    HStack(spacing: 10) {
                        Circle().fill(server.connected ? NB.mint : (server.enabled ? NB.amber : NB.tertiary))
                            .frame(width: 7, height: 7)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(server.name).font(.nbMono(11, weight: .semibold)).foregroundStyle(NB.text)
                            Text("\(server.transport) · \(server.tools) tools · \(server.status)")
                                .font(.nbMono(8)).foregroundStyle(NB.tertiary)
                        }
                        Spacer()
                    }
                    .padding(11)
                    .background(RoundedRectangle(cornerRadius: 12).fill(NB.fillSoft))
                    .overlay(RoundedRectangle(cornerRadius: 12).strokeBorder(NB.line, lineWidth: 1))
                }
            }
        }
        .padding(.horizontal, 20)
        .padding(.top, 14)
        .padding(.bottom, 36)
    }

    private var skillsSection: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack {
                Text("\(store.skills.values.reduce(0) { $0 + $1.count }) skills across \(store.skills.count) categories")
                    .font(.nbBody(11)).foregroundStyle(NB.tertiary)
                Spacer()
                Button { Task { await store.reloadSkills() } } label: {
                    Text("Reload").font(.nbBody(11, weight: .medium)).foregroundStyle(NB.ion)
                }.buttonStyle(.plain)
            }
            if store.skills.isEmpty {
                Text("No skills returned. The gateway may be in demo mode or the profile has no skill directory.")
                    .font(.nbBody(11)).foregroundStyle(NB.tertiary)
            } else {
                ForEach(store.skills.keys.sorted(), id: \.self) { category in
                    VStack(alignment: .leading, spacing: 7) {
                        Text(category.uppercased()).font(.nbLabel(9)).tracking(1.2).foregroundStyle(NB.tertiary)
                        ForEach(store.skills[category] ?? [], id: \.self) { skill in
                            Text(skill).font(.nbMono(10.5)).foregroundStyle(NB.secondary)
                                .padding(.horizontal, 10).padding(.vertical, 7)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .background(RoundedRectangle(cornerRadius: 9).fill(NB.fillSoft))
                        }
                    }
                }
            }
        }
        .padding(.horizontal, 20)
        .padding(.top, 14)
        .padding(.bottom, 36)
    }

    private func toolsetRow(_ toolset: Toolset) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 11) {
                ZStack {
                    RoundedRectangle(cornerRadius: 11, style: .continuous)
                        .fill((toolset.enabled ? NB.ion : NB.tertiary).opacity(0.10))
                    Image(systemName: toolset.icon)
                        .font(.system(size: 13, weight: .medium))
                        .foregroundStyle(toolset.enabled ? NB.ion : NB.tertiary)
                }
                .frame(width: 36, height: 36)

                VStack(alignment: .leading, spacing: 3) {
                    HStack(spacing: 7) {
                        Text(toolset.name)
                            .font(.nbMono(11.5, weight: .semibold))
                            .foregroundStyle(NB.text)
                        Text("\(toolset.tools.count)")
                            .font(.nbMono(8))
                            .foregroundStyle(NB.tertiary)
                    }
                    Text(toolset.description)
                        .font(.nbBody(11.5))
                        .foregroundStyle(NB.secondary)
                        .lineLimit(2)
                }
                Spacer()
                Button {
                    Task { await store.toggleToolset(toolset) }
                } label: {
                    ZStack(alignment: toolset.enabled ? .trailing : .leading) {
                        Capsule().fill(toolset.enabled ? NB.ion : NB.fillStrong)
                            .frame(width: 38, height: 22)
                        Circle().fill(toolset.enabled ? Color(hex: 0x0A0D14) : NB.tertiary)
                            .frame(width: 17, height: 17)
                            .padding(.horizontal, 2.5)
                    }
                }
                .buttonStyle(.plain)
            }
            if !toolset.tools.isEmpty {
                FlowLayout(spacing: 5) {
                    ForEach(toolset.tools.prefix(6), id: \.self) { tool in
                        Text(tool)
                            .font(.nbMono(7.5))
                            .foregroundStyle(toolset.enabled ? NB.secondary : NB.tertiary)
                            .padding(.horizontal, 7).padding(.vertical, 3)
                            .background(Capsule().fill(NB.fillSoft))
                    }
                    if toolset.tools.count > 6 {
                        Text("+\(toolset.tools.count - 6)")
                            .font(.nbMono(7.5)).foregroundStyle(NB.tertiary)
                            .padding(.horizontal, 7).padding(.vertical, 3)
                    }
                }
                .padding(.leading, 47)
            }
        }
        .padding(13)
        .background(
            RoundedRectangle(cornerRadius: 17, style: .continuous)
                .fill(toolset.enabled ? NB.surface.opacity(0.8) : NB.surface.opacity(0.4))
                .overlay(RoundedRectangle(cornerRadius: 17).strokeBorder(toolset.enabled ? NB.ion.opacity(0.14) : NB.line, lineWidth: 1))
        )
        .opacity(toolset.enabled ? 1 : 0.72)
    }
}
