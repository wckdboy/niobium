import SwiftUI

struct HomeView: View {
    var onShowPixels: () -> Void = {}
    @EnvironmentObject private var store: AppStore
    @State private var showNew = false
    @State private var showRemote = false
    @State private var showInstance = false
#if NIOBIUM_LOCAL_DSH
    @State private var showLocal = false
#endif
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
                    localCard
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 30)
            }
        }
        .scrollIndicators(.hidden)
        .frame(maxWidth: .infinity)
        .background(NB.void)
        .sheet(isPresented: $showInstance) {
            NavigationStack {
                Form {
                    Section("Gateway") {
                        LabeledContent("Host", value: host)
                        LabeledContent("Connection", value: store.connectionState.label)
                        LabeledContent("Transport", value: "WebSocket JSON-RPC")
                        LabeledContent("Access", value: "Enforced by your Hermes host")
                        if let ms = store.gatewayLatencyMs { LabeledContent("Last round trip", value: "\(ms) ms") }
                    }
                    Section { Button("Refresh gateway") { Task { await store.refreshAll() } } }
                }.navigationTitle("Hermes instance")
                    .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { showInstance = false } } }
            }
        }
        .sheet(isPresented: $showNew) { NewBotView().environmentObject(store) }
        .sheet(isPresented: $showRemote) { RemoteHarnessView() }
#if NIOBIUM_LOCAL_DSH
        .sheet(isPresented: $showLocal) { DSHLocalScreen() }
#endif
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
                LinearGradient(colors: [NB.void.opacity(0.90), NB.void.opacity(0.18), .clear, NB.void],
                               startPoint: .top, endPoint: .bottom)
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
                    Image(systemName: "server.rack").font(.system(size: 16, weight: .medium)).foregroundStyle(NB.ion)
                }
                .frame(width: 40, height: 40)
                VStack(alignment: .leading, spacing: 3) {
                    Text("Hermes Instance").font(.nbTitle(17)).foregroundStyle(NB.text)
                    Text(host).font(.nbMono(9)).foregroundStyle(NB.secondary).lineLimit(1)
                    Text(store.isDemo ? "Demo workspace" : "Your computer or VPS").font(.nbMono(8.5)).foregroundStyle(NB.tertiary)
                }
                Spacer()
                VStack(alignment: .trailing, spacing: 3) {
                    HStack(spacing: 5) {
                        Circle().fill(store.connectionState.color).frame(width: 6, height: 6)
                        Text(store.connectionState.label).font(.nbBody(10.5, weight: .medium)).foregroundStyle(NB.mint)
                    }
                    Text(store.isDemo ? "ready" : "live").font(.nbMono(9)).foregroundStyle(NB.tertiary)
                }
            }
            Divider().overlay(NB.line)
            HStack(spacing: 0) {
                stat("\(store.toolsets.count)", "Toolsets", "hammer")
                stat(store.isDemo ? "Demo" : "Host", "Policy", "shield")
                stat(store.gatewayLatencyMs.map { "\($0) ms" } ?? "—", "Latency", "waveform.path.ecg")
                stat("\(store.bots.filter(\.isLive).count)/\(store.bots.count)", "Working", "cpu")
            }
        }
        .padding(13)
        .pixelPanel(radius: 20, accented: true)
        .contentShape(RoundedRectangle(cornerRadius: 20))
        .onTapGesture { showInstance = true }
    }

    private func stat(_ value: String, _ label: String, _ icon: String) -> some View {
        VStack(spacing: 4) {
            HStack(spacing: 5) {
                Image(systemName: icon).font(.system(size: 12, weight: .medium)).foregroundStyle(NB.ion)
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
                        .background(Capsule().fill(LinearGradient(colors: [NB.ion, NB.ionDeep], startPoint: .leading, endPoint: .trailing)))
                }
                .buttonStyle(.plain)
            }
            if store.bots.isEmpty {
                Text("Give your first Pixel a purpose. Create one here or refresh your gateway roster.")
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
        let member = cast.member(for: bot.name, title: bot.title)
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

    private var localCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Local DSH").font(.nbTitle(19)).foregroundStyle(NB.text)
                    Text({
#if NIOBIUM_LOCAL_DSH
                        "Alpine Linux on your device. Your provider keys."
#else
                        "Your harness on a computer or VPS."
#endif
                    }()).font(.nbBody(10.5)).foregroundStyle(NB.tertiary)
                }
                Spacer()
                Button {
#if NIOBIUM_LOCAL_DSH
                    showLocal = true
#else
                    showRemote = true
#endif
                } label: {
                    Label("Open DSH", systemImage: "terminal")
                        .font(.nbBody(11, weight: .semibold)).foregroundStyle(NB.onIon)
                        .padding(.horizontal, 12).padding(.vertical, 8)
                        .background(Capsule().fill(NB.ion.opacity(0.9)))
                }
                .buttonStyle(.plain)
            }
            HStack(spacing: 10) {
                Image(systemName: "shippingbox").font(.system(size: 15)).foregroundStyle(NB.ion)
                VStack(alignment: .leading, spacing: 1) {
                    Text("Harness runtime").font(.nbBody(12, weight: .semibold)).foregroundStyle(NB.text)
#if NIOBIUM_LOCAL_DSH
                    Text("Alpine + iSH · boots on demand").font(.nbMono(8)).foregroundStyle(NB.tertiary)
#else
                    Text("Remote · computer or VPS").font(.nbMono(8)).foregroundStyle(NB.tertiary)
#endif
                }
                Spacer()
                HStack(spacing: 4) {
                    Text("Open to check").font(.nbMono(8)).foregroundStyle(NB.tertiary)
                }
            }
            .padding(10)
            .background(RoundedRectangle(cornerRadius: 12).fill(NB.fillSoft))
            Text("DSH is separate from Hermes. The local guest is iOS-only; remote works everywhere.")
                .font(.nbBody(9)).foregroundStyle(NB.tertiary).lineSpacing(1)
        }
        .padding(15)
        .pixelPanel(radius: 20, accented: true)
    }

    private func localStat(_ label: String, _ value: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label).font(.nbBody(8.5)).foregroundStyle(NB.tertiary)
            Text(value).font(.nbMono(8.5)).foregroundStyle(NB.text).lineLimit(1)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

struct LocalView: View {
    @EnvironmentObject private var store: AppStore
    @State private var showRemote = false
#if NIOBIUM_LOCAL_DSH
    @State private var showLocal = false
#endif

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                NBScreenHeader(eyebrow: "Harness · computer · VPS", title: "Local")
                VStack(alignment: .leading, spacing: 12) {
                    Text("DeepSeek Harness")
                        .font(.nbTitle(22)).foregroundStyle(NB.text)
                    Text("Run a full harness next to your Pixels. The iOS full build embeds Alpine + iSH; both platforms can open a DSH web UI on a computer or VPS.")
                        .font(.nbBody(12)).foregroundStyle(NB.secondary).lineSpacing(2)
                    Button {
#if NIOBIUM_LOCAL_DSH
                        showLocal = true
#else
                        showRemote = true
#endif
                    } label: { Text("Open DSH").frame(maxWidth: .infinity) }
                        .buttonStyle(NBPrimaryButtonStyle())
                    Button { showRemote = true } label: { Text("Connect to remote DSH").frame(maxWidth: .infinity) }
                        .buttonStyle(NBQuietButtonStyle())
                }
                .padding(18)
                .background(RoundedRectangle(cornerRadius: 22).fill(NB.surface.opacity(0.75)))
                .overlay(RoundedRectangle(cornerRadius: 22).strokeBorder(NB.line, lineWidth: 1))
                .padding(.horizontal, 16)

                VStack(alignment: .leading, spacing: 8) {
                    NBSectionHeader(title: "Your Pixels")
                    if store.bots.isEmpty {
                        Text("No Pixels attached yet.").font(.nbBody(11)).foregroundStyle(NB.tertiary)
                    } else {
                        ForEach(store.bots.prefix(3)) { bot in
                            HStack(spacing: 10) {
                                CastPortrait(member: CastStore.shared.member(for: bot.name, title: bot.title), size: 38, showCompanion: false, showGlow: false)
                                VStack(alignment: .leading, spacing: 1) {
                                    Text(bot.title).font(.nbBody(12, weight: .medium)).foregroundStyle(NB.text)
                                    Text("Hermes pixel · \(bot.model.isEmpty ? "inherited model" : bot.model)").font(.nbMono(8)).foregroundStyle(NB.tertiary)
                                }
                                Spacer()
                                Text(bot.isLive ? "Running" : "Available").font(.nbMono(8)).foregroundStyle(bot.isLive ? NB.mint : NB.tertiary)
                            }
                            .padding(10)
                            .background(RoundedRectangle(cornerRadius: 12).fill(NB.fillSoft))
                        }
                    }
                }
                .padding(.horizontal, 16)
            }
            .padding(.bottom, 30)
        }
        .background(NB.void)
        .sheet(isPresented: $showRemote) { RemoteHarnessView() }
#if NIOBIUM_LOCAL_DSH
        .sheet(isPresented: $showLocal) { DSHLocalScreen() }
#endif
    }
}
