import SwiftUI

struct BotsView: View {
    @EnvironmentObject private var store: AppStore
    @Binding var newBot: Bool
    @State private var appeared = false

    private var activePixels: [BotProfile] { store.bots.filter(\.isLive) }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                topBar

                HStack(alignment: .center) {
                    VStack(alignment: .leading, spacing: 7) {
                        Text("Pixels")
                            .font(.nbDisplay(44))
                            .tracking(-1.8)
                            .foregroundStyle(NB.text)
                        Text("Your agents, close at hand.")
                            .font(.nbBody(14))
                            .foregroundStyle(NB.secondary)
                    }
                    Spacer()
                    NBIconButton(symbol: "plus", size: 42, tint: NB.ion) { newBot = true }
                        .background(Circle().fill(NB.ion.opacity(0.10)))
                }
                .padding(.horizontal, 20)
                .padding(.top, 26)

                HStack(spacing: 14) {
                    Label("\(store.bots.count) pixels", systemImage: "square.grid.2x2")
                    if !activePixels.isEmpty {
                        HStack(spacing: 5) {
                            Circle().fill(NB.mint).frame(width: 5, height: 5)
                            Text("\(activePixels.count) here now")
                        }
                    }
                    Spacer()
                    if store.isRefreshing {
                        ProgressView().tint(NB.tertiary).scaleEffect(0.7)
                    }
                }
                .font(.nbBody(11))
                .foregroundStyle(NB.tertiary)
                .padding(.horizontal, 20)
                .padding(.top, 14)

                if !store.connectionState.isConnected, store.connection != nil {
                    Button {
                        Task { await store.reconnect() }
                    } label: {
                        HStack(spacing: 8) {
                            Image(systemName: "arrow.clockwise").font(.system(size: 11, weight: .semibold))
                            Text("Reconnect to \(store.connection?.name ?? "gateway")")
                                .font(.nbBody(12, weight: .medium))
                            Spacer()
                            Text("offline").font(.nbBody(10)).foregroundStyle(NB.rose)
                        }
                        .foregroundStyle(NB.text)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 10)
                        .background(RoundedRectangle(cornerRadius: 12).fill(NB.rose.opacity(0.08)))
                        .overlay(RoundedRectangle(cornerRadius: 12).strokeBorder(NB.rose.opacity(0.18), lineWidth: 1))
                    }
                    .buttonStyle(.plain)
                    .padding(.horizontal, 16)
                    .padding(.top, 14)
                }

                if !activePixels.isEmpty {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 16) {
                            ForEach(activePixels) { pixel in
                                Button { store.openBot(pixel) } label: {
                                    VStack(spacing: 7) {
                                        BotAvatar(bot: pixel, size: 54)
                                        Text(pixel.title)
                                            .font(.nbBody(10.5, weight: .medium))
                                            .foregroundStyle(NB.secondary)
                                            .lineLimit(1)
                                    }
                                    .frame(width: 68)
                                }
                                .buttonStyle(.plain)
                            }
                        }
                        .padding(.horizontal, 20)
                    }
                    .padding(.top, 24)
                }

                HStack {
                    Text("All pixels")
                        .font(.nbBody(13, weight: .semibold))
                        .foregroundStyle(NB.text.opacity(0.9))
                    Spacer()
                    Button("New pixel") { newBot = true }
                        .font(.nbBody(12, weight: .medium))
                        .foregroundStyle(NB.ion)
                }
                .padding(.horizontal, 20)
                .padding(.top, 30)
                .padding(.bottom, 10)

                if store.bots.isEmpty {
                    EmptyStateView(icon: "square.grid.2x2",
                                   title: "Make your first pixel",
                                   detail: "A pixel is a Hermes profile with its own memory, model and tools. Give it a name and it stays with you.",
                                   actionTitle: "Create a pixel") { newBot = true }
                        .padding(.horizontal, 20)
                        .padding(.top, 8)
                } else {
                    LazyVStack(spacing: 7) {
                        ForEach(Array(store.bots.enumerated()), id: \.element.id) { index, pixel in
                            BotRow(bot: pixel) { store.openBot(pixel) }
                                .opacity(appeared ? 1 : 0)
                                .offset(y: appeared ? 0 : 8)
                                .animation(.easeOut(duration: 0.34).delay(Double(index) * 0.035), value: appeared)
                        }
                    }
                    .padding(.horizontal, 16)
                }

                HStack(spacing: 7) {
                    AtomicMark(size: 15)
                    Text(store.isDemo ? "Demo workspace" : "Connected to \(store.connection?.name ?? "your gateway")")
                        .font(.nbBody(10.5))
                        .foregroundStyle(NB.tertiary.opacity(0.75))
                }
                .frame(maxWidth: .infinity)
                .padding(.top, 30)
                .padding(.bottom, 30)
            }
        }
        .refreshable { await store.refreshAll() }
        .scrollIndicators(.hidden)
        .onAppear {
            withAnimation(.easeOut(duration: 0.45)) { appeared = true }
            Task { if !store.isDemo { await store.refreshRoster() } }
        }
    }

    private var topBar: some View {
        HStack {
            Wordmark()
            Spacer()
            HStack(spacing: 7) {
                StatusDot(color: store.connectionState.color, pulse: store.isConnecting)
                Text(store.connectionState.isConnected ? "online" : store.connectionState.label.lowercased())
                    .font(.nbBody(10.5))
                    .foregroundStyle(NB.tertiary)
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 6)
            .background(Capsule().fill(.white.opacity(0.035)))
            .overlay(Capsule().strokeBorder(NB.line, lineWidth: 1))
        }
        .padding(.horizontal, 20)
        .padding(.top, 6)
    }
}
