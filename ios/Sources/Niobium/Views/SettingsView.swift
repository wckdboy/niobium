import SwiftUI

struct SettingsView: View {
    @EnvironmentObject private var store: AppStore
    var onOpenTools: () -> Void = {}
    @AppStorage("niobium.haptics") private var haptics = true
    @AppStorage("niobium.showReasoning") private var showReasoning = true
    @AppStorage("niobium.appearance") private var appearance = NBAppearance.system.rawValue
    @State private var confirmForget = false
    @State private var showDiagnostics = false
#if NIOBIUM_LOCAL_DSH
    @State private var showDSH = false
#endif
    @State private var showRemoteHarness = false

    private var appVersion: String {
        Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.1"
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                HStack {
                    Wordmark()
                    Spacer()
                    Text("v\(appVersion)")
                        .font(.nbMono(9))
                        .foregroundStyle(NB.tertiary)
                }
                .padding(.horizontal, 20).padding(.top, 6)

                NBScreenHeader(eyebrow: "Niobium · element 41", title: "Settings")
                    .padding(.top, 24)

                connectionSection
                    .padding(.horizontal, 20)
                    .padding(.top, 22)

                preferencesSection
                    .padding(.horizontal, 20)
                    .padding(.top, 26)

#if NIOBIUM_LOCAL_DSH
                localHarnessSection
                    .padding(.horizontal, 20)
                    .padding(.top, 26)
#else
                remoteHarnessSection
                    .padding(.horizontal, 20)
                    .padding(.top, 26)
#endif

                privacySection
                    .padding(.horizontal, 20)
                    .padding(.top, 26)

                aboutSection
                    .padding(.horizontal, 20)
                    .padding(.top, 26)

                Button {
                    confirmForget = true
                } label: {
                    Text("Forget this gateway")
                        .font(.nbBody(13, weight: .medium))
                        .foregroundStyle(NB.rose)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 13)
                        .background(RoundedRectangle(cornerRadius: 13).fill(NB.rose.opacity(0.06)))
                        .overlay(RoundedRectangle(cornerRadius: 13).strokeBorder(NB.rose.opacity(0.15), lineWidth: 1))
                }
                .buttonStyle(.plain)
                .padding(.horizontal, 20)
                .padding(.top, 24)
                .padding(.bottom, 36)
            }
        }
        .scrollIndicators(.hidden)
        .confirmationDialog("Forget this gateway?", isPresented: $confirmForget, titleVisibility: .visible) {
            Button("Forget and disconnect", role: .destructive) {
                store.disconnect(forget: true)
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("The access token is removed from the iOS Keychain. Your Hermes instance is not changed.")
        }
        .sheet(isPresented: $showDiagnostics) {
            DiagnosticsSheet().environmentObject(store)
        }
#if NIOBIUM_LOCAL_DSH
        .sheet(isPresented: $showDSH) {
            DSHLocalScreen()
        }
#endif
        .sheet(isPresented: $showRemoteHarness) {
            RemoteHarnessView()
        }
    }

    private var connectionSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            NBSectionHeader(title: "Connection", detail: store.connectionState.label.uppercased())
            VStack(spacing: 0) {
                HStack(spacing: 12) {
                    ZStack {
                        Circle().fill(store.connectionState.color.opacity(0.12)).frame(width: 36, height: 36)
                        Image(systemName: store.isDemo ? "sparkles" : "network")
                            .font(.system(size: 13)).foregroundStyle(store.connectionState.color)
                    }
                    VStack(alignment: .leading, spacing: 2) {
                        Text(store.connection?.name ?? "No gateway").font(.nbBody(13.5, weight: .semibold)).foregroundStyle(NB.text)
                        Text(store.isDemo ? "Demo workspace · local" : (store.connection?.baseURL ?? "—"))
                            .font(.nbMono(9)).foregroundStyle(NB.tertiary).lineLimit(1)
                    }
                    Spacer()
                    StatusDot(color: store.connectionState.color, pulse: store.isConnecting)
                }
                .padding(14)
                NBDivider()
                HStack(spacing: 10) {
                    Button {
                        if store.isDemo {
                            store.disconnect(forget: true)
                        } else {
                            Task { await store.reconnect() }
                        }
                    } label: {
                        Label(store.isDemo ? "Connect a gateway" : "Reconnect", systemImage: "arrow.triangle.2.circlepath")
                            .font(.nbBody(12, weight: .medium))
                            .foregroundStyle(NB.text)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 9)
                            .background(RoundedRectangle(cornerRadius: 9).fill(NB.fillSoft))
                    }
                    .buttonStyle(.plain)
                    if store.isDemo {
                        Button { store.enterDemo() } label: {
                            Label("Reset demo", systemImage: "arrow.counterclockwise")
                                .font(.nbBody(12, weight: .medium))
                                .foregroundStyle(NB.secondary)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 9)
                                .background(RoundedRectangle(cornerRadius: 9).fill(NB.fillSoft))
                        }
                        .buttonStyle(.plain)
                    } else {
                        Button { showDiagnostics = true } label: {
                            Label("Diagnostics", systemImage: "waveform.path.ecg")
                                .font(.nbBody(12, weight: .medium))
                                .foregroundStyle(NB.secondary)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 9)
                                .background(RoundedRectangle(cornerRadius: 9).fill(NB.fillSoft))
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(10)
            }
            .background(RoundedRectangle(cornerRadius: 18).fill(NB.surface.opacity(0.65)))
            .overlay(RoundedRectangle(cornerRadius: 18).strokeBorder(NB.line, lineWidth: 1))
        }
    }

    private var preferencesSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            NBSectionHeader(title: "Experience")
            VStack(spacing: 0) {
                toggleRow(icon: "hand.tap", title: "Haptics", detail: "A light touch on key actions", value: $haptics)
                NBDivider()
                toggleRow(icon: "brain", title: "Show reasoning", detail: "Reveal the model’s thinking block", value: $showReasoning)
                NBDivider()
                Button(action: onOpenTools) {
                    HStack(spacing: 11) {
                        Image(systemName: "hammer").font(.system(size: 12)).foregroundStyle(NB.ion)
                            .frame(width: 24)
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Toolbox").font(.nbBody(13, weight: .medium)).foregroundStyle(NB.text)
                            Text("Every toolset on your gateway").font(.nbBody(10.5)).foregroundStyle(NB.tertiary)
                        }
                        Spacer()
                        Image(systemName: "chevron.right").font(.system(size: 10)).foregroundStyle(NB.tertiary)
                    }
                    .padding(14)
                }
                .buttonStyle(.plain)
                NBDivider()
                Menu {
                    ForEach(NBAppearance.allCases) { option in
                        Button {
                            appearance = option.rawValue
                            Haptics.tap()
                        } label: {
                            Label(option.title, systemImage: option.icon)
                        }
                    }
                } label: {
                    HStack(spacing: 11) {
                        Image(systemName: NBAppearance(rawValue: appearance)?.icon ?? "circle.lefthalf.filled")
                            .font(.system(size: 12)).foregroundStyle(NB.violet)
                            .frame(width: 24)
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Appearance").font(.nbBody(13, weight: .medium)).foregroundStyle(NB.text)
                            Text(NBAppearance(rawValue: appearance)?.title ?? "System")
                                .font(.nbBody(10.5)).foregroundStyle(NB.tertiary)
                        }
                        Spacer()
                        NBPill(text: (NBAppearance(rawValue: appearance)?.title ?? "System").lowercased(), tint: NB.violet)
                        Image(systemName: "chevron.up.chevron.down").font(.system(size: 9)).foregroundStyle(NB.tertiary)
                    }
                    .padding(14)
                }
                .buttonStyle(.plain)
            }
            .background(RoundedRectangle(cornerRadius: 18).fill(NB.surface.opacity(0.65)))
            .overlay(RoundedRectangle(cornerRadius: 18).strokeBorder(NB.line, lineWidth: 1))
        }
    }

    private func toggleRow(icon: String, title: String, detail: String, value: Binding<Bool>) -> some View {
        HStack(spacing: 11) {
            Image(systemName: icon).font(.system(size: 12)).foregroundStyle(NB.ion).frame(width: 24)
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(.nbBody(13, weight: .medium)).foregroundStyle(NB.text)
                Text(detail).font(.nbBody(10.5)).foregroundStyle(NB.tertiary)
            }
            Spacer()
            Toggle("", isOn: value).labelsHidden().tint(NB.ion).scaleEffect(0.82)
        }
        .padding(14)
    }

#if NIOBIUM_LOCAL_DSH
    private var localHarnessSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            NBSectionHeader(title: "On device")
            Button { showDSH = true } label: {
                HStack(spacing: 12) {
                    ZStack {
                        RoundedRectangle(cornerRadius: 11, style: .continuous)
                            .fill(LinearGradient(colors: [NB.ion.opacity(0.22), NB.ionDeep.opacity(0.12)],
                                                 startPoint: .topLeading, endPoint: .bottomTrailing))
                        Image(systemName: "terminal.fill")
                            .font(.system(size: 13, weight: .medium))
                            .foregroundStyle(NB.ion)
                    }
                    .frame(width: 38, height: 38)
                    VStack(alignment: .leading, spacing: 3) {
                        HStack(spacing: 6) {
                            Text("DeepSeek Harness").font(.nbBody(13, weight: .semibold)).foregroundStyle(NB.text)
                            NBPill(text: "beta", tint: NB.amber)
                        }
                        Text("The full harness in Alpine, running on this device")
                            .font(.nbBody(11)).foregroundStyle(NB.tertiary)
                    }
                    Spacer()
                    Image(systemName: "chevron.right").font(.system(size: 10)).foregroundStyle(NB.tertiary)
                }
                .padding(14)
                .background(RoundedRectangle(cornerRadius: 18).fill(NB.surface.opacity(0.65)))
                .overlay(RoundedRectangle(cornerRadius: 18).strokeBorder(NB.ion.opacity(0.15), lineWidth: 1))
            }
            .buttonStyle(.plain)

            Text("Experimental. Boots an Alpine Linux guest through the embedded iSH-ARM64 emulator, then serves the real DSH web UI on loopback. First launch can take a minute.")
                .font(.nbBody(10.5))
                .foregroundStyle(NB.tertiary)
                .lineSpacing(2)
                .padding(.horizontal, 2)

            Button { showRemoteHarness = true } label: {
                HStack(spacing: 12) {
                    Image(systemName: "network").font(.system(size: 13)).foregroundStyle(NB.ion)
                        .frame(width: 38, height: 38)
                        .background(RoundedRectangle(cornerRadius: 11).fill(NB.fillSoft))
                    VStack(alignment: .leading, spacing: 3) {
                        Text("Remote harness").font(.nbBody(13, weight: .semibold)).foregroundStyle(NB.text)
                        Text("Open DSH on your computer or VPS").font(.nbBody(11)).foregroundStyle(NB.tertiary)
                    }
                    Spacer()
                    Image(systemName: "chevron.right").font(.system(size: 10)).foregroundStyle(NB.tertiary)
                }
                .padding(14)
                .background(RoundedRectangle(cornerRadius: 18).fill(NB.surface.opacity(0.65)))
                .overlay(RoundedRectangle(cornerRadius: 18).strokeBorder(NB.line, lineWidth: 1))
            }
            .buttonStyle(.plain)
        }
    }
#endif

    private var remoteHarnessSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            NBSectionHeader(title: "Harness")
            Button { showRemoteHarness = true } label: {
                HStack(spacing: 12) {
                    Image(systemName: "network").font(.system(size: 13)).foregroundStyle(NB.ion)
                        .frame(width: 38, height: 38)
                        .background(RoundedRectangle(cornerRadius: 11).fill(NB.fillSoft))
                    VStack(alignment: .leading, spacing: 3) {
                        Text("Remote DeepSeek Harness").font(.nbBody(13, weight: .semibold)).foregroundStyle(NB.text)
                        Text("Computer or VPS · works on both platforms").font(.nbBody(11)).foregroundStyle(NB.tertiary)
                    }
                    Spacer()
                    Image(systemName: "chevron.right").font(.system(size: 10)).foregroundStyle(NB.tertiary)
                }
                .padding(14)
                .background(RoundedRectangle(cornerRadius: 18).fill(NB.surface.opacity(0.65)))
                .overlay(RoundedRectangle(cornerRadius: 18).strokeBorder(NB.line, lineWidth: 1))
            }
            .buttonStyle(.plain)
        }
    }

    private var privacySection: some View {
        VStack(alignment: .leading, spacing: 12) {
            NBSectionHeader(title: "Privacy")
            HStack(alignment: .top, spacing: 12) {
                ZStack {
                    Circle().fill(NB.mint.opacity(0.1)).frame(width: 34, height: 34)
                    Image(systemName: "lock.shield.fill").font(.system(size: 12)).foregroundStyle(NB.mint)
                }
                VStack(alignment: .leading, spacing: 5) {
                    Text("Your gateway. No middleman.").font(.nbBody(13, weight: .semibold)).foregroundStyle(NB.text)
                    Text("Messages stream directly from this device to the Hermes instance you entered. Niobium has no account, no backend, no analytics and no telemetry.")
                        .font(.nbBody(11.5)).foregroundStyle(NB.secondary).lineSpacing(2)
                }
            }
            .padding(14)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(RoundedRectangle(cornerRadius: 18).fill(NB.surface.opacity(0.65)))
            .overlay(RoundedRectangle(cornerRadius: 18).strokeBorder(NB.line, lineWidth: 1))
        }
    }

    private var aboutSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            NBSectionHeader(title: "About")
            HStack(spacing: 14) {
                AtomicMark(size: 54)
                VStack(alignment: .leading, spacing: 4) {
                    Text("Niobium").font(.nbTitle(18)).foregroundStyle(NB.text)
                    Text("The pocket command deck for Hermes Agent.")
                        .font(.nbBody(11.5)).foregroundStyle(NB.secondary)
                    Text("v\(appVersion) · Nb 41")
                        .font(.nbMono(8.5)).foregroundStyle(NB.tertiary)
                }
                Spacer()
            }
            .padding(14)
            .background(RoundedRectangle(cornerRadius: 18).fill(NB.surface.opacity(0.65)))
            .overlay(RoundedRectangle(cornerRadius: 18).strokeBorder(NB.line, lineWidth: 1))

            HStack(spacing: 9) {
                linkChip("Hermes docs", icon: "book") {
                    UIApplication.shared.open(URL(string: "https://hermes-agent.nousresearch.com/docs")!)
                }
                linkChip("GitHub", icon: "chevron.left.forwardslash.chevron.right") {
                    UIApplication.shared.open(URL(string: "https://github.com/NousResearch/hermes-agent")!)
                }
                linkChip("Nous", icon: "globe") {
                    UIApplication.shared.open(URL(string: "https://nousresearch.com")!)
                }
            }
        }
    }

    private func linkChip(_ title: String, icon: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 5) {
                Image(systemName: icon).font(.system(size: 9))
                Text(title).font(.nbBody(10.5, weight: .medium))
            }
            .foregroundStyle(NB.secondary)
            .padding(.horizontal, 10).padding(.vertical, 8)
            .background(Capsule().fill(NB.fillSoft))
            .overlay(Capsule().strokeBorder(NB.line, lineWidth: 1))
        }
        .buttonStyle(.plain)
    }
}

struct DiagnosticsSheet: View {
    @EnvironmentObject private var store: AppStore
    private var appVersion: String {
        Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.1"
    }
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ZStack {
                NBMeshBackground(intensity: 0.4)
                ScrollView {
                    VStack(alignment: .leading, spacing: 14) {
                        diag("Gateway", store.connection?.baseURL ?? "—")
                        diag("Name", store.connection?.name ?? "—")
                        diag("State", store.connectionState.label)
                        diag("Pixels", "\(store.bots.count)")
                        diag("Rooms", "\(store.groups.count)")
                        diag("Toolsets", "\(store.toolsets.count)")
                        diag("Last sync", store.lastSyncedAt.map { $0.formatted(.dateTime.hour().minute().second()) } ?? "—")
                        diag("Transport", "WebSocket JSON-RPC 2.0")
                        Text("Connection diagnostics never include message contents or token material.")
                            .font(.nbBody(10.5)).foregroundStyle(NB.tertiary).padding(.top, 4)
                        Button {
                            UIPasteboard.general.string = """
                            Niobium v\(appVersion)
                            Gateway: \(store.connection?.baseURL ?? "—")
                            State: \(store.connectionState.label)
                            Pixels: \(store.bots.count)
                            Rooms: \(store.groups.count)
                            Toolsets: \(store.toolsets.count)
                            """
                            store.toastMessage("Diagnostics copied")
                        } label: {
                            Label("Copy diagnostics", systemImage: "doc.on.doc").frame(maxWidth: .infinity)
                        }
                        .buttonStyle(NBPrimaryButtonStyle(compact: true))
                    }
                    .padding(20)
                }
            }
            .navigationTitle("Diagnostics")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }.foregroundStyle(NB.ion)
                }
            }
            .toolbarBackground(NB.ink, for: .navigationBar)
        }
        .presentationDetents([.medium])
        .presentationBackground(NB.ink)
    }

    private func diag(_ label: String, _ value: String) -> some View {
        HStack {
            Text(label).font(.nbBody(11.5)).foregroundStyle(NB.tertiary)
            Spacer()
            Text(value).font(.nbMono(9.5)).foregroundStyle(NB.secondary).lineLimit(1)
        }
        .padding(.vertical, 7)
    }
}
