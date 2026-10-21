import SwiftUI

struct HomeView: View {
    @EnvironmentObject private var store: AppStore
    @State private var showNew = false
    @State private var showRemote = false
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
                VStack(alignment: .leading, spacing: 22) {
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
        .sheet(isPresented: $showNew) { NewBotView().environmentObject(store) }
        .sheet(isPresented: $showRemote) { RemoteHarnessView() }
#if NIOBIUM_LOCAL_DSH
        .sheet(isPresented: $showLocal) { DSHLocalScreen() }
#endif
    }

    private var hero: some View {
        GeometryReader { proxy in
            ZStack(alignment: .bottom) {
                Image("HeroPixels")
                    .resizable()
                    .interpolation(.none)
                    .scaledToFill()
                    .frame(width: proxy.size.width, height: 196)
                    .clipped()
                LinearGradient(colors: [NB.void.opacity(0.97), NB.void.opacity(0.55), NB.void.opacity(0.10), NB.void],
                               startPoint: .top, endPoint: .bottom)
                VStack(alignment: .leading, spacing: 0) {
                    HStack(alignment: .center, spacing: 10) {
                        AtomicMark(size: 38)
                        VStack(alignment: .leading, spacing: 1) {
                            Text("Niobium").font(.system(size: 24, weight: .bold)).tracking(-0.8).foregroundStyle(NB.text)
                            Text("YOUR PIXELS, EVERYWHERE.").font(.nbMono(7.5)).tracking(1.4).foregroundStyle(NB.secondary)
                        }
                        Spacer()
                        HStack(spacing: 6) {
                            Circle().fill(store.connectionState.color).frame(width: 6, height: 6)
                            Text(store.isDemo ? "Connected" : store.connectionState.label)
                                .font(.nbBody(11, weight: .medium)).lineLimit(1)
                            Image(systemName: "chevron.right").font(.system(size: 8))
                        }
                        .foregroundStyle(NB.secondary)
                        .padding(.horizontal, 10).padding(.vertical, 8)
                        .background(Capsule().fill(NB.ink.opacity(0.65)))
                        .overlay(Capsule().strokeBorder(NB.line, lineWidth: 1))
                    }
                    .padding(.horizontal, 18)
                    .padding(.top, 8)
                    Spacer()
                }
                .frame(width: proxy.size.width, height: 196)
            }
            .frame(width: proxy.size.width, height: 196)
        }
        .frame(height: 196)
    }

    private var instanceCard: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(spacing: 12) {
                ZStack {
                    RoundedRectangle(cornerRadius: 12).fill(NB.ion.opacity(0.12))
                    Image(systemName: "server.rack").font(.system(size: 16, weight: .medium)).foregroundStyle(NB.ion)
                }
                .frame(width: 46, height: 46)
                VStack(alignment: .leading, spacing: 3) {
                    Text("Hermes Instance").font(.nbTitle(17)).foregroundStyle(NB.text)
                    Text(host).font(.nbMono(9)).foregroundStyle(NB.secondary).lineLimit(1)
                    Text(store.isDemo ? "Demo · local" : "VPS · self-hosted").font(.nbMono(8.5)).foregroundStyle(NB.tertiary)
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
                stat("\(store.toolsets.count)", "Tools", "hammer")
                stat("Full", "Access", "shield")
                stat("—", "Latency", "waveform.path.ecg")
                stat("\(store.bots.filter(\.isLive).count)/\(store.bots.count)", "Local bots", "cpu")
            }
        }
        .padding(16)
        .background(RoundedRectangle(cornerRadius: 22, style: .continuous).fill(NB.surface.opacity(0.82)))
        .overlay(RoundedRectangle(cornerRadius: 22).strokeBorder(NB.line, lineWidth: 1))
    }

    private func stat(_ value: String, _ label: String, _ icon: String) -> some View {
        VStack(spacing: 4) {
            Image(systemName: icon).font(.system(size: 14, weight: .medium)).foregroundStyle(NB.ion)
            Text(value).font(.nbTitle(14)).foregroundStyle(NB.text)
            Text(label).font(.nbBody(9.5)).foregroundStyle(NB.tertiary)
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
            LazyVGrid(columns: [GridItem(.flexible(), spacing: 10), GridItem(.flexible(), spacing: 10)], spacing: 10) {
                ForEach(store.bots.prefix(8)) { bot in
                    homePixel(bot)
                }
            }
        }
    }

    private func homePixel(_ bot: BotProfile) -> some View {
        let member = cast.member(for: bot.name, title: bot.title)
        return Button { store.openBot(bot) } label: {
            VStack(alignment: .leading, spacing: 8) {
                HStack(alignment: .top, spacing: 8) {
                    CastPortrait(member: member, size: 62, showCompanion: true, showGlow: false)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(bot.title).font(.nbTitle(13)).foregroundStyle(NB.text).lineLimit(1)
                        Text(bot.subtitle).font(.nbBody(9)).foregroundStyle(NB.tertiary).lineLimit(2)
                        Spacer(minLength: 0)
                        HStack(spacing: 4) {
                            Circle().fill(bot.isLive ? NB.mint : NB.tertiary).frame(width: 4, height: 4)
                            Text(bot.statusLine).font(.nbMono(8)).foregroundStyle(bot.isLive ? NB.mint : NB.tertiary).lineLimit(1)
                        }
                    }
                    Spacer(minLength: 0)
                    Image(systemName: "ellipsis").font(.system(size: 10)).foregroundStyle(NB.tertiary)
                }
                Text(bot.preview.isEmpty ? bot.role : bot.preview)
                    .font(.nbBody(9.5)).foregroundStyle(NB.secondary).lineLimit(2)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            .padding(10)
            .background(RoundedRectangle(cornerRadius: 16).fill(NB.surface.opacity(0.7)))
            .overlay(RoundedRectangle(cornerRadius: 16).strokeBorder(NB.line, lineWidth: 1))
        }
        .buttonStyle(.plain)
    }

    private var localCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Local DSH").font(.nbTitle(19)).foregroundStyle(NB.text)
                    Text("Run local Pixels through DSH on your device.").font(.nbBody(10.5)).foregroundStyle(NB.tertiary)
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
                    Text("Local Runtime").font(.nbBody(12, weight: .semibold)).foregroundStyle(NB.text)
#if NIOBIUM_LOCAL_DSH
                    Text("Alpine + iSH · boots on demand").font(.nbMono(8)).foregroundStyle(NB.tertiary)
#else
                    Text("Remote · computer or VPS").font(.nbMono(8)).foregroundStyle(NB.tertiary)
#endif
                }
                Spacer()
                HStack(spacing: 4) {
                    Circle().fill(NB.mint).frame(width: 5, height: 5)
                    Text("on demand").font(.nbMono(8)).foregroundStyle(NB.mint)
                }
            }
            .padding(10)
            .background(RoundedRectangle(cornerRadius: 12).fill(NB.fillSoft))
            HStack(spacing: 0) {
                localStat("Model", "DSH")
                localStat("Resources", "shared")
                localStat("GPU", "device")
                localStat("Local Pixels", "\(store.bots.filter(\.isLive).count) running")
            }
            Text("DSH is separate from Hermes. The local guest is iOS-only; remote works everywhere.")
                .font(.nbBody(9)).foregroundStyle(NB.tertiary).lineSpacing(1)
        }
        .padding(15)
        .background(RoundedRectangle(cornerRadius: 22).fill(NB.surface.opacity(0.82)))
        .overlay(RoundedRectangle(cornerRadius: 22).strokeBorder(NB.line, lineWidth: 1))
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
