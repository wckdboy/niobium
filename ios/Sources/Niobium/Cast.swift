import SwiftUI

struct CastMember: Identifiable, Equatable {
    let id: String
    let name: String
    let companion: String
    let companionName: String
    let accentHex: String
    var accent: Color { NB.color(for: accentHex) }

    static let all: [CastMember] = [
        .init(id: "orion", name: "Orion", companion: "owl", companionName: "Strix", accentHex: "#A78BFA"),
        .init(id: "nova", name: "Nova", companion: "cat", companionName: "Miso", accentHex: "#F0ABFC"),
        .init(id: "forge", name: "Forge", companion: "drone", companionName: "Cog", accentHex: "#F5C97A"),
        .init(id: "sage", name: "Sage", companion: "slime", companionName: "Moss", accentHex: "#5EEAD4"),
        .init(id: "nyx", name: "Nyx", companion: "bat", companionName: "Dusk", accentHex: "#FB7185"),
        .init(id: "astra", name: "Astra", companion: "bunny", companionName: "Pip", accentHex: "#7DD3FC"),
        .init(id: "vesper", name: "Vesper", companion: "moth", companionName: "Lumen", accentHex: "#C4B5FF"),
        .init(id: "rook", name: "Rook", companion: "hawk", companionName: "Kite", accentHex: "#D6A45E")
    ]

    static func seeded(_ profile: String, title: String = "") -> CastMember {
        let key = profile.lowercased()
        if let match = all.first(where: { $0.id == key || $0.name.lowercased() == key }) { return match }
        var hash: UInt64 = 41
        for scalar in (key + title).unicodeScalars { hash = hash &* 31 &+ UInt64(scalar.value) }
        return all[Int(hash % UInt64(all.count))]
    }
}

@MainActor
final class CastStore: ObservableObject {
    static let shared = CastStore()
    @Published private(set) var revision = 0
    private let key = "niobium.cast.v1"

    private func all() -> [String: String] {
        (UserDefaults.standard.dictionary(forKey: key) as? [String: String]) ?? [:]
    }

    func member(for profile: String, title: String = "") -> CastMember {
        let id = all()[profile.lowercased()]
        var member = id.flatMap { chosen in CastMember.all.first(where: { $0.id == chosen }) } ?? .seeded(profile, title: title)
        let companions = UserDefaults.standard.dictionary(forKey: "niobium.companions.v1") as? [String: String] ?? [:]
        if let kind = companions[profile.lowercased()] {
            member = CastMember(id: member.id, name: member.name, companion: kind,
                                companionName: CastMember.all.first(where: { $0.companion == kind })?.companionName ?? member.companionName,
                                accentHex: member.accentHex)
        }
        return member
    }

    func select(_ member: CastMember, for profile: String) {
        var map = all(); map[profile.lowercased()] = member.id
        UserDefaults.standard.set(map, forKey: key)
        var companions = UserDefaults.standard.dictionary(forKey: "niobium.companions.v1") as? [String: String] ?? [:]
        companions[profile.lowercased()] = member.companion
        UserDefaults.standard.set(companions, forKey: "niobium.companions.v1")
        revision += 1
    }

    func setCompanion(_ kind: String, name: String?, for profile: String) {
        var companions = UserDefaults.standard.dictionary(forKey: "niobium.companions.v1") as? [String: String] ?? [:]
        companions[profile.lowercased()] = kind
        UserDefaults.standard.set(companions, forKey: "niobium.companions.v1")
        revision += 1
    }

    func random(for profile: String) -> CastMember {
        let current = member(for: profile)
        let next = CastMember.all.filter { $0.id != current.id }.randomElement() ?? CastMember.all[0]
        select(next, for: profile)
        return next
    }
}

struct CastPortrait: View {
    var member: CastMember
    var size: CGFloat = 132
    var showCompanion = true
    var showGlow = true

    var body: some View {
        ZStack(alignment: .bottomTrailing) {
            RoundedRectangle(cornerRadius: size * 0.20, style: .continuous)
                .fill(LinearGradient(colors: [member.accent.opacity(0.24), NB.surface.opacity(0.9)],
                                     startPoint: .topLeading, endPoint: .bottomTrailing))
            RoundedRectangle(cornerRadius: size * 0.20, style: .continuous)
                .strokeBorder(LinearGradient(colors: [NB.lineStrong, NB.fillSoft],
                                             startPoint: .topLeading, endPoint: .bottomTrailing), lineWidth: 1)
            Image("Char_\(member.id)")
                .resizable()
                .interpolation(.none)
                .scaledToFit()
                .padding(size * 0.04)
                .shadow(color: showGlow ? member.accent.opacity(0.28) : .clear, radius: size * 0.10, y: size * 0.04)
            if showCompanion {
                Image("Comp_\(member.companion)")
                    .resizable()
                    .interpolation(.none)
                    .frame(width: size * 0.29, height: size * 0.29)
                    .padding(size * 0.035)
                    .background(Circle().fill(NB.ink.opacity(0.92)))
                    .overlay(Circle().strokeBorder(member.accent.opacity(0.55), lineWidth: 1))
                    .offset(x: size * 0.04, y: size * 0.02)
            }
        }
        .frame(width: size, height: size)
        .clipShape(RoundedRectangle(cornerRadius: size * 0.20, style: .continuous))
    }
}

struct CastName: View {
    var member: CastMember
    var bot: BotProfile
    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            HStack(spacing: 5) {
                Text(bot.title).font(.nbTitle(14)).foregroundStyle(NB.text).lineLimit(1)
                if bot.isLive { Circle().fill(NB.mint).frame(width: 5, height: 5) }
            }
            Text(bot.subtitle).font(.nbBody(10)).foregroundStyle(NB.tertiary).lineLimit(1)
            HStack(spacing: 4) {
                Image("Comp_\(member.companion)").resizable().interpolation(.none).frame(width: 11, height: 11)
                Text(member.companionName).font(.nbMono(8)).foregroundStyle(NB.secondary)
            }
        }
    }
}

struct CastStudioView: View {
    @Environment(\.dismiss) private var dismiss
    let profile: String
    let title: String
    @State private var selected: CastMember
    @ObservedObject private var store = CastStore.shared

    init(profile: String, title: String) {
        self.profile = profile
        self.title = title
        _selected = State(initialValue: CastStore.shared.member(for: profile, title: title))
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    HStack {
                        Spacer()
                        VStack(spacing: 8) {
                            CastPortrait(member: selected, size: 150)
                            Text(title).font(.nbTitle(20)).foregroundStyle(NB.text)
                            Text("\(selected.companionName) · companion").font(.nbMono(9)).foregroundStyle(NB.tertiary)
                        }
                        Spacer()
                    }
                    NBSectionHeader(title: "The cast", detail: "32 × 32")
                    LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 10) {
                        ForEach(CastMember.all) { member in
                            Button {
                                selected = member
                                CastStore.shared.select(member, for: profile)
                                Haptics.tap()
                            } label: {
                                HStack(spacing: 10) {
                                    CastPortrait(member: member, size: 54, showCompanion: false, showGlow: false)
                                    CastName(member: member, bot: BotProfile(name: profile, title: member.name, role: "", provider: "", model: "", skillCount: 0, canonicalSessionID: nil, canonicalResolvedID: nil, lastSessionID: nil, preview: "", activityAt: nil, workerActive: false, isDefault: false, hidden: false, accentHex: member.accentHex, hasAvatar: false))
                                    Spacer(minLength: 0)
                                }
                                .padding(8)
                                .background(RoundedRectangle(cornerRadius: 14).fill(selected.id == member.id ? NB.ion.opacity(0.10) : NB.fillSoft))
                                .overlay(RoundedRectangle(cornerRadius: 14).strokeBorder(selected.id == member.id ? NB.ion.opacity(0.4) : NB.line, lineWidth: 1))
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    VStack(alignment: .leading, spacing: 9) {
                        NBSectionHeader(title: "Companion")
                        LazyVGrid(columns: Array(repeating: GridItem(.flexible()), count: 4), spacing: 9) {
                            ForEach(Array(Set(CastMember.all.map(\.companion))).sorted(), id: \.self) { kind in
                                Button {
                                    selected = CastMember(id: selected.id, name: selected.name, companion: kind,
                                                          companionName: CastMember.all.first(where: { $0.companion == kind })?.companionName ?? selected.companionName,
                                                          accentHex: selected.accentHex)
                                    CastStore.shared.select(selected, for: profile)
                                } label: {
                                    VStack(spacing: 4) {
                                        Image("Comp_\(kind)").resizable().interpolation(.none).frame(width: 34, height: 34)
                                        Text(kind).font(.nbMono(7.5)).foregroundStyle(NB.tertiary)
                                    }
                                    .frame(maxWidth: .infinity).padding(7)
                                    .background(RoundedRectangle(cornerRadius: 10).fill(selected.companion == kind ? NB.ion.opacity(0.10) : NB.fillSoft))
                                    .overlay(RoundedRectangle(cornerRadius: 10).strokeBorder(selected.companion == kind ? NB.ion.opacity(0.35) : NB.line, lineWidth: 1))
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    }

                    Button {
                        selected = CastStore.shared.random(for: profile)
                    } label: { Label("Surprise me", systemImage: "dice").frame(maxWidth: .infinity) }
                        .buttonStyle(NBQuietButtonStyle())
                }
                .padding(20)
            }
            .background(NBMeshBackground(intensity: 0.5))
            .navigationTitle("Cast")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }.foregroundStyle(NB.ion)
                }
            }
        }
        .presentationDetents([.large])
    }
}
