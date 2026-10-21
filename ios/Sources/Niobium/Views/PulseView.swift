import SwiftUI

struct PulseView: View {
    @EnvironmentObject private var store: AppStore
    @State private var appeared = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                HStack {
                    Wordmark()
                    Spacer()
                    HStack(spacing: 7) {
                        StatusDot(color: store.connectionState.color)
                        Text(store.connectionState.isConnected ? "online" : "offline")
                            .font(.nbBody(10.5))
                            .foregroundStyle(NB.tertiary)
                    }
                }
                .padding(.horizontal, 20)
                .padding(.top, 6)

                VStack(alignment: .leading, spacing: 7) {
                    Text("Activity")
                        .font(.nbDisplay(40))
                        .tracking(-1.5)
                        .foregroundStyle(NB.text)
                    Text("What your pixels are doing.")
                        .font(.nbBody(14))
                        .foregroundStyle(NB.secondary)
                }
                .padding(.horizontal, 20)
                .padding(.top, 28)

                HStack(spacing: 16) {
                    activityStat("\(store.bots.filter(\.isLive).count)", "working", NB.mint)
                    activityStat("\(store.approvals.count)", "approvals", NB.amber)
                    activityStat("\(store.bots.count)", "pixels", NB.ion)
                }
                .padding(.horizontal, 20)
                .padding(.top, 18)

                if let pixel = store.bots.first(where: \.isLive) {
                    Button { store.openBot(pixel) } label: {
                        HStack(spacing: 12) {
                            BotAvatar(bot: pixel, size: 42)
                            VStack(alignment: .leading, spacing: 3) {
                                Text(pixel.title)
                                    .font(.nbBody(13, weight: .semibold))
                                    .foregroundStyle(NB.text)
                                Text(pixel.preview.isEmpty ? "Working on a turn" : pixel.preview)
                                    .font(.nbBody(12))
                                    .foregroundStyle(NB.secondary)
                                    .lineLimit(1)
                            }
                            Spacer()
                            TypingIndicator()
                        }
                        .padding(13)
                        .background(
                            RoundedRectangle(cornerRadius: 17, style: .continuous)
                                .fill(LinearGradient(colors: [NB.ion.opacity(0.13), NB.fillSoft],
                                                     startPoint: .leading, endPoint: .trailing))
                                .overlay(RoundedRectangle(cornerRadius: 17).strokeBorder(NB.ion.opacity(0.22), lineWidth: 1))
                        )
                    }
                    .buttonStyle(.plain)
                    .padding(.horizontal, 16)
                    .padding(.top, 16)
                }

                HStack {
                    Text("Recent")
                        .font(.nbBody(13, weight: .semibold))
                        .foregroundStyle(NB.text.opacity(0.9))
                    Spacer()
                    Text("\(store.activity.count)")
                        .font(.nbBody(11))
                        .foregroundStyle(NB.tertiary)
                }
                .padding(.horizontal, 20)
                .padding(.top, 30)
                .padding(.bottom, 8)

                if store.activity.isEmpty {
                    EmptyStateView(icon: "bolt.horizontal", title: "Quiet right now",
                                   detail: "Tool calls, approvals and pixel replies will show up here.")
                        .padding(.horizontal, 20)
                } else {
                    VStack(spacing: 0) {
                        ForEach(Array(store.activity.prefix(30).enumerated()), id: \.element.id) { index, item in
                            activityRow(item)
                            if index < min(29, store.activity.count - 1) {
                                NBDivider().padding(.leading, 44)
                            }
                        }
                    }
                    .padding(.horizontal, 14)
                    .background(RoundedRectangle(cornerRadius: 18).fill(NB.fillSoft))
                    .overlay(RoundedRectangle(cornerRadius: 18).strokeBorder(NB.line, lineWidth: 1))
                    .padding(.horizontal, 16)
                }

                Spacer(minLength: 34)
            }
            .opacity(appeared ? 1 : 0)
        }
        .scrollIndicators(.hidden)
        .refreshable { await store.refreshAll() }
        .onAppear {
            withAnimation(.easeOut(duration: 0.4)) { appeared = true }
        }
    }

    private func activityStat(_ value: String, _ label: String, _ color: Color) -> some View {
        HStack(spacing: 6) {
            Circle().fill(color).frame(width: 5, height: 5)
            Text(value).font(.nbBody(13, weight: .semibold)).foregroundStyle(NB.text)
            Text(label).font(.nbBody(11.5)).foregroundStyle(NB.tertiary)
        }
    }

    private func activityRow(_ item: ActivityItem) -> some View {
        HStack(alignment: .top, spacing: 11) {
            ZStack {
                RoundedRectangle(cornerRadius: 9, style: .continuous).fill(color(for: item).opacity(0.10))
                Image(systemName: icon(for: item)).font(.system(size: 11, weight: .medium)).foregroundStyle(color(for: item))
            }
            .frame(width: 30, height: 30)
            VStack(alignment: .leading, spacing: 3) {
                HStack {
                    Text(item.title).font(.nbBody(12.5, weight: .medium)).foregroundStyle(NB.text)
                    Spacer()
                    Text(item.date.relativeShort).font(.nbBody(10)).foregroundStyle(NB.tertiary)
                }
                Text(item.detail).font(.nbBody(11.5)).foregroundStyle(NB.secondary).lineLimit(2)
            }
        }
        .padding(.vertical, 11)
    }

    private func color(for item: ActivityItem) -> Color {
        switch item.kind {
        case .tool: return NB.mint
        case .message: return NB.ion
        case .approval: return NB.amber
        case .bot: return NB.violet
        case .system: return NB.tertiary
        }
    }

    private func icon(for item: ActivityItem) -> String {
        switch item.kind {
        case .tool: return "hammer"
        case .message: return "bubble.left"
        case .approval: return "hand.raised"
        case .bot: return "square.grid.2x2"
        case .system: return "gearshape"
        }
    }
}
