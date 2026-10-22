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
                    .frame(width: proxy.size.width, height: 152)
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
                            Text("YOUR PIXELS, EVERYWHERE.").font(.nbMono(7.5)).tracking(1.4).foregroundStyle(NB.secondary)
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
                        .background(Capsule().fill(NB.ink.opacity(0.65)))
                        .overlay(Capsule().strokeBorder(NB.ion.opacity(0.22), lineWidth: 1))
                        }.buttonStyle(.plain)
                    }
                    .padding(.horizontal, 18)
                    .padding(.top, 8)
                    Spacer()
                }
                .frame(width: proxy.size.width, height: 152)
            }
            .frame(width: proxy.size.width, height: 152)
        }
        .frame(height: 152)
    }

    private var instanceCard: some View {
        VStack(alignment: .leading, spacing: 11) {
            HStack(spacing: 12) {
                ZStack {
                    RoundedRectangle(cornerRadius: 12).fill(NB.ion.opacity(0.12))
                    PixelIcon(.terminal, size: 24)
                }
                .frame(width: 40, height: 40)
                VStack(alignment: .leading, spacing: 3) {
                    Text(store.engine == .dsh ? "On-device engine" : "Hermes instance").font(.nbTitle(17)).foregroundStyle(NB.text)
                    Text(store.engine == .dsh ? "On this device" : host).font(.nbMono(9)).foregroundStyle(NB.secondary).lineLimit(1)
                    Text(store.isDemo ? "Demo workspace" : store.engine == .dsh ? "DSH · private Linux workspace" : "Your computer or VPS").font(.nbMono(8.5)).foregroundStyle(NB.tertiary)
                }
                Spacer()
                VStack(alignment: .trailing, spacing: 3) {
                    HStack(spacing: 5) {
                        Circle().fill(store.connectionState.color).frame(width: 6, height: 6)
                        Text(store.connectionState.label).font(.nbBody(10.5, weight: .medium)).foregroundStyle(store.connectionState.color)
                    }
                    Text(store.isDemo ? "preview" : store.engine == .dsh ? "local" : "gateway").font(.nbMono(9)).foregroundStyle(NB.tertiary)
                }
            }
            Divider().overlay(NB.line)
            HStack(spacing: 0) {
                stat(store.engine == .dsh ? "DSH" : "\(store.toolsets.count)", store.engine == .dsh ? "Engine" : "Toolsets", .terminal)
                stat(store.isDemo ? "Demo" : store.engine == .dsh ? "Local" : "Host", "Policy", .shield)
                stat(store.gatewayLatencyMs.map { "\($0) ms" } ?? "—", "Latency", .activity)
                stat("\(store.bots.filter(\.isLive).count)/\(store.bots.count)", "Working", .pixels)
            }
        }
        .padding(13)
        .pixelPanel(radius: 20, accented: true)
        .contentShape(RoundedRectangle(cornerRadius: 20))
        .onTapGesture { showInstance = true }
    }

    private func stat(_ value: String, _ label: String, _ icon: PixelGlyph) -> some View {
        VStack(spacing: 4) {
            HStack(spacing: 5) {
                PixelIcon(icon, size: 16)
                Text(value).font(.nbTitle(13)).foregroundStyle(NB.text)
            }
            Text(label).font(.nbBody(9)).foregroundStyle(NB.tertiary)
        }
        .frame(maxWidth: .infinity)
    }

    private var pixelsSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .firstTextBaseline) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Your Pixels").font(.nbTitle(21)).foregroundStyle(NB.text)
                    Text("Autonomous agents, each with a purpose.").font(.nbBody(11)).foregroundStyle(NB.tertiary)
                }
                Spacer()
                Button { showNew = true } label: {
                    Label("New Pixel", systemImage: "plus")
                        .font(.nbBody(11, weight: .semibold))
                        .foregroundStyle(NB.onIon)
                        .padding(.horizontal, 12).padding(.vertical, 8)
                        .background(PixelButtonShape().fill(LinearGradient(colors: [NB.ion, NB.ionDeep], startPoint: .leading, endPoint: .trailing)))
                }
                .buttonStyle(.plain)
                .disabled(!store.connectionState.isConnected && !store.isDemo)
            }
            if store.bots.isEmpty {
                Text(store.engine == .dsh && !store.connectionState.isConnected ? store.localStatus : "Give your first Pixel a purpose. Create one here or refresh your roster.")
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
            HStack(alignment: .center, spacing: 8) {
                CastPortrait(member: member, size: 64, showCompanion: true, showGlow: false)
                VStack(alignment: .leading, spacing: 5) {
                    Text(bot.title).font(.nbTitle(13)).foregroundStyle(NB.text).lineLimit(1).minimumScaleFactor(0.8)
                    Text(bot.subtitle).font(.nbBody(10)).foregroundStyle(NB.secondary).lineLimit(2)
                    HStack(spacing: 4) {
                        Circle().fill(bot.isLive ? NB.mint : NB.tertiary).frame(width: 5, height: 5)
                        Text(bot.isLive ? "Working" : "Ready").font(.nbBody(9, weight: .medium)).foregroundStyle(bot.isLive ? NB.mint : NB.tertiary)
                    }
                    .padding(.horizontal, 6).padding(.vertical, 3)
                    .background(Capsule().fill((bot.isLive ? NB.mint : NB.secondary).opacity(0.08)))
                }.frame(maxWidth: .infinity, alignment: .leading)
            }
            .frame(minHeight: 70).padding(8).pixelPanel(radius: 16)
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
                        Text("A home for every Pixel.").font(.nbBody(13)).foregroundStyle(NB.secondary)
                    }
                    Spacer()
                    PixelIcon(.terminal, size: 26)
                }.padding(.vertical, 12)
                Text("Choose an engine. Your dashboard, Pixels and chats work the same way.")
                    .font(.nbBody(13)).foregroundStyle(NB.secondary).lineSpacing(4)
                engineCard("On device", subtitle: "DSH · private Linux workspace", icon: .pixels, selected: store.engine == .dsh) {
                    Text("Run your Pixels here with your own model API key. The engine works behind Niobium while you use the app.")
                        .font(.nbBody(12)).foregroundStyle(NB.secondary).lineSpacing(4)
#if NIOBIUM_LOCAL_DSH
                    if store.engine == .dsh {
                        HStack(spacing: 8) {
                            if store.isConnecting { ProgressView().tint(NB.ion) }
                            Text(store.connectionState.isConnected ? "Ready · chats and tasks run in Niobium" : store.localStatus)
                                .font(.nbBody(12)).foregroundStyle(store.connectionState.isConnected ? NB.mint : NB.secondary)
                        }
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
                    Text("Connect to your existing agents, tools and group rooms. Your local engine can stay running alongside Hermes.")
                        .font(.nbBody(12)).foregroundStyle(NB.secondary).lineSpacing(4)
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
                        Text("Local Pixels share a Linux workspace. Tool choices limit their available tools; workspace scopes are operating instructions. iOS may suspend local tasks when Niobium is in the background. Hermes provides group rooms and scheduled jobs.")
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
