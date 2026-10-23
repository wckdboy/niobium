import SwiftUI

struct HomeView: View {
    var onShowPixels: () -> Void = {}
    @EnvironmentObject private var store: AppStore
    @State private var showNew = false
    @State private var showInstance = false
    @ObservedObject private var cast = CastStore.shared

    private var host: String {
        guard let raw = store.connection?.baseURL, let url = URL(string: raw) else { return "self-hosted" }
        return url.host ?? raw
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                hero
                VStack(alignment: .leading, spacing: 18) {
                    instanceCard
                    pixelsSection
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 30)
            }
        }
        .scrollIndicators(.hidden)
        .frame(maxWidth: .infinity)
        .background(NB.void)
        .sheet(isPresented: $showInstance) { LocalView().environmentObject(store) }
        .sheet(isPresented: $showNew) { NewBotView().environmentObject(store) }
    }

    private var hero: some View {
        GeometryReader { proxy in
            ZStack(alignment: .bottom) {
                Image("TerraceHero")
                    .resizable()
                    .interpolation(.none)
                    .scaledToFill()
                    .frame(width: proxy.size.width, height: 112)
                    .clipped()
                LinearGradient(stops: [
                    .init(color: NB.void.opacity(0.99), location: 0),
                    .init(color: NB.void.opacity(0.98), location: 0.25),
                    .init(color: NB.void.opacity(0.94), location: 0.50),
                    .init(color: .clear, location: 0.78),
                    .init(color: NB.void, location: 1)
                ], startPoint: .top, endPoint: .bottom)
                VStack(alignment: .leading, spacing: 0) {
                    HStack(alignment: .center, spacing: 10) {
                        AtomicMark(size: 38)
                        VStack(alignment: .leading, spacing: 1) {
                            Text("Niobium").font(.system(size: 24, weight: .bold)).tracking(-0.8).foregroundStyle(NB.text)
                        }
                        Spacer()
                        Button { showInstance = true } label: { HStack(spacing: 6) {
                            Circle().fill(store.connectionState.color).frame(width: 6, height: 6)
                            Text(store.isDemo ? "Demo" : store.connectionState.label)
                                .font(.nbBody(11, weight: .medium)).lineLimit(1)
                            Image(systemName: "chevron.right").font(.system(size: 8))
                        }
                        .foregroundStyle(NB.secondary)
                        .padding(.horizontal, 10).padding(.vertical, 8)
                        .background(PixelButtonShape().fill(NB.ink.opacity(0.65)))
                        .overlay(PixelButtonShape().stroke(NB.ion.opacity(0.22), lineWidth: 1))
                        }.buttonStyle(.plain)
                    }
                    .padding(.horizontal, 18)
                    .padding(.top, 8)
                    Spacer()
                }
                .frame(width: proxy.size.width, height: 112)
            }
            .frame(width: proxy.size.width, height: 112)
        }
        .frame(height: 112)
    }

    private var instanceCard: some View {
        HStack(spacing: 12) {
            PixelIcon(.terminal, size: 24)
                .frame(width: 42, height: 42)
                .background(PixelButtonShape().fill(NB.ion.opacity(0.08)))
            VStack(alignment: .leading, spacing: 4) {
                Text(store.engine == .dsh ? "On-device engine" : "Hermes instance")
                    .font(.nbTitle(15)).foregroundStyle(NB.text)
                Text(store.engine == .dsh ? store.localConnectionError ?? store.localStatus : host)
                    .font(.nbBody(11)).foregroundStyle(NB.secondary).lineLimit(2)
            }
            Spacer(minLength: 4)
            VStack(alignment: .trailing, spacing: 4) {
                NBPill(text: store.isDemo ? "Demo" : store.connectionState.label,
                       tint: store.connectionState.color)
                if let latency = store.gatewayLatencyMs {
                    Text("\(latency) ms").font(.nbMono(10)).foregroundStyle(NB.tertiary)
                }
            }
            PixelIcon(.back, size: 12, tint: NB.tertiary).rotationEffect(.degrees(180))
        }
        .padding(13).pixelPanel(accented: true)
        .contentShape(PixelButtonShape())
        .onTapGesture { showInstance = true }
    }

    private var pixelsSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .firstTextBaseline) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Your Pixels").font(.nbTitle(21)).foregroundStyle(NB.text)
                }
                Spacer()
                Button { showNew = true } label: {
                    HStack(spacing: 6) { PixelIcon(.spark, size: 14, tint: NB.onIon); Text("New Pixel") }
                        .font(.nbBody(11, weight: .semibold))
                        .foregroundStyle(NB.onIon)
                        .padding(.horizontal, 12).frame(minHeight: 44)
                        .background(PixelButtonShape().fill(NB.ion))
                }
                .buttonStyle(.plain)
                .disabled(!store.connectionState.isConnected && !store.isDemo)
            }
            if store.bots.isEmpty {
                Text(store.engine == .dsh && !store.connectionState.isConnected ? store.localConnectionError ?? store.localStatus : "Give your first Pixel a purpose. Create one here or refresh your roster.")
                    .font(.nbBody(13)).foregroundStyle(NB.secondary).padding(16).pixelPanel()
            }
            LazyVGrid(columns: [GridItem(.adaptive(minimum: 155), spacing: 10)], spacing: 10) {
                ForEach(store.bots.prefix(6)) { bot in
                    homePixel(bot)
                }
            }
            if store.bots.count > 6 {
                Button(action: onShowPixels) {
                    HStack {
                        Text("All \(store.bots.count) Pixels")
                        Spacer()
                        Image(systemName: "arrow.right")
                    }.font(.nbBody(11, weight: .medium)).foregroundStyle(NB.secondary).padding(.horizontal, 4)
                }.buttonStyle(.plain)
            }
        }
    }

    private func homePixel(_ bot: BotProfile) -> some View {
        let member = cast.member(for: bot.castKey, title: bot.title)
        return Button { store.openBot(bot) } label: {
            VStack(alignment: .leading, spacing: 8) {
                CastPortrait(member: member, size: 96, showCompanion: true, showGlow: false)
                    .frame(maxWidth: .infinity)
                HStack(spacing: 6) {
                    Text(bot.title).font(.nbTitle(15)).foregroundStyle(NB.text).lineLimit(1)
                    Spacer(minLength: 0)
                    Rectangle().fill(bot.isLive ? NB.mint : NB.tertiary.opacity(0.4)).frame(width: 5, height: 5)
                }
                Text(bot.subtitle).font(.nbBody(11)).foregroundStyle(NB.secondary).lineLimit(1)
            }
            .padding(12).frame(maxWidth: .infinity, alignment: .leading).pixelPanel(accented: bot.isLive)
        }
        .buttonStyle(.plain)
        .accessibilityLabel("\(bot.title), \(bot.subtitle), \(bot.isLive ? "working" : "ready")")
    }

}

struct LocalView: View {
    @EnvironmentObject private var store: AppStore
    @State private var advanced = false
    @State private var showRemote = false
    @State private var provider = "DeepSeek"
    @State private var apiKey = ""
    @State private var saving = false
#if NIOBIUM_LOCAL_DSH
    @State private var showLocal = false
#endif

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                HStack {
                    VStack(alignment: .leading, spacing: 5) {
                        Text("Engines").font(.nbTitle(30)).foregroundStyle(NB.text)

                    }
                    Spacer()
                    PixelIcon(.terminal, size: 26)
                }.padding(.vertical, 12)

                engineCard("On device", subtitle: "DSH · private Linux workspace", icon: .pixels, selected: store.engine == .dsh) {

#if NIOBIUM_LOCAL_DSH
                    let local = store.engineHub?.local ?? store
                    HStack(spacing: 8) {
                        if local.isConnecting { ProgressView().tint(NB.ion) }
                        Text(local.connectionState.isConnected ? "Connected" : local.localConnectionError ?? local.localStatus)
                            .font(.nbBody(12)).foregroundStyle(local.connectionState.isConnected ? NB.mint : NB.secondary)
                    }
                    if store.engine != .dsh || (!store.connectionState.isConnected && !store.isConnecting) {
                        Button { store.useLocal() } label: { PixelActionLabel("Use on-device DSH", icon: .play) }
                            .buttonStyle(NBPrimaryButtonStyle())
                    }
#else
                    Text("Available in the full iOS build.").font(.nbBody(11)).foregroundStyle(NB.tertiary)
#endif
                }
                engineCard("Hermes", subtitle: "Your computer or VPS", icon: .link, selected: store.engine == .hermes) {

                    let remote = store.engineHub?.hermes ?? store
                    Text(remote.connectionState.isConnected && !remote.isDemo ? "Connected" : remote.isConnecting ? "Connecting…" : "Offline")
                        .font(.nbBody(12)).foregroundStyle(remote.connectionState.isConnected && !remote.isDemo ? NB.mint : NB.secondary)
                    if store.engine != .hermes {
                        Button { Task { await store.useHermes() } } label: { PixelActionLabel("Use Hermes", icon: .link) }
                            .buttonStyle(NBQuietButtonStyle())
                    }
                }
                if store.engine == .dsh && store.connectionState.isConnected {
                    VStack(alignment: .leading, spacing: 12) {
                        Text("Model provider").font(.nbTitle(15)).foregroundStyle(NB.text)
                        Picker("Provider", selection: $provider) {
                            ForEach(["DeepSeek", "OpenAI", "Anthropic"], id: \.self) { Text($0) }
                        }.pickerStyle(.segmented).onChange(of: provider) { _, _ in apiKey = "" }
                        SecureField(provider + " API key", text: $apiKey)
                            .textInputAutocapitalization(.never).autocorrectionDisabled()
                            .padding(12).background(NB.fillSoft).clipShape(RoundedRectangle(cornerRadius: 10))
                        Button {
                            saving = true
                            let ref = provider == "OpenAI" ? "OPENAI_API_KEY" : provider == "Anthropic" ? "ANTHROPIC_API_KEY" : "DEEPSEEK_API_KEY"
                            Task {
                                defer { saving = false }
                                do { try await store.saveLocalCredential(ref: ref, value: apiKey); apiKey = "" }
                                catch { store.showError(error.localizedDescription) }
                            }
                        } label: { PixelActionLabel(saving ? "Saving…" : "Save API key", icon: .shield) }
                            .buttonStyle(NBQuietButtonStyle()).disabled(saving || apiKey.isEmpty)
                        Text("Keys stay in DSH app storage. Choose a model from the model picker in your Pixel’s chat.")
                            .font(.nbBody(11)).foregroundStyle(NB.tertiary).lineSpacing(3)
                    }.padding(18).pixelPanel()
                }
                DisclosureGroup(isExpanded: $advanced) {
                    VStack(alignment: .leading, spacing: 12) {
#if NIOBIUM_LOCAL_DSH
                        if store.localReady {
                            Button { showLocal = true } label: { PixelActionLabel("Advanced DSH workspace", icon: .terminal) }
                                .buttonStyle(NBQuietButtonStyle())
                        }
                        if store.localReady || store.isConnecting && store.engine == .dsh {
                            Button { store.stopLocal() } label: { PixelActionLabel("Stop local engine", icon: .stop) }
                                .buttonStyle(NBQuietButtonStyle()).foregroundStyle(NB.rose)
                        }
#endif
                        Button { showRemote = true } label: { PixelActionLabel("Remote DSH workspace", icon: .link) }
                            .buttonStyle(NBQuietButtonStyle())
                        Text("Local Pixels share a Linux workspace. Tool choices limit their available tools; workspace scopes are operating instructions. iOS may suspend local tasks when Niobium is in the background. Local rooms run on this device. Scheduled jobs require Hermes.")
                            .font(.nbBody(11)).foregroundStyle(NB.tertiary).lineSpacing(3)
                    }.padding(.top, 12)
                } label: {
                    HStack(spacing: 10) {
                        PixelIcon(.settings, size: 18, tint: NB.tertiary)
                        Text("Advanced controls").font(.nbBody(13)).foregroundStyle(NB.secondary)
                    }
                }.tint(NB.ion).padding(.vertical, 10)
            }
            .padding(.horizontal, 20).padding(.bottom, 30)
        }
        .background(NB.void)
        .sheet(isPresented: $showRemote) { RemoteHarnessView() }
#if NIOBIUM_LOCAL_DSH
        .sheet(isPresented: $showLocal) { DSHLocalScreen() }
#endif
    }

    private func engineCard<Content: View>(_ title: String, subtitle: String, icon: PixelGlyph,
                                          selected: Bool, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 12) {
                PixelIcon(icon)
                VStack(alignment: .leading, spacing: 3) {
                    Text(title).font(.nbTitle(17)).foregroundStyle(NB.text)
                    Text(subtitle).font(.nbBody(11)).foregroundStyle(NB.tertiary)
                }
                Spacer()
                if selected { Text("Selected").font(.nbBody(10, weight: .semibold)).foregroundStyle(NB.ion) }
            }
            content()
        }.padding(18).pixelPanel(accented: selected)
    }
}
