import SwiftUI

struct CastMember: Identifiable, Equatable {
    let id: String
    let name: String
    let portrait: String
    let companion: String
    let companionName: String
    let accentHex: String
    var recipe: CharacterRecipe? = nil
    var accent: Color { NB.color(for: accentHex) }

    static let all: [CastMember] = [
        .init(id: "orion", name: "Orion", portrait: "p02", companion: "owl", companionName: "Strix", accentHex: "#A78BFA", recipe: CharacterRecipe.cast["orion"]),
        .init(id: "nova", name: "Nova", portrait: "p01", companion: "cat", companionName: "Miso", accentHex: "#F0ABFC", recipe: CharacterRecipe.cast["nova"]),
        .init(id: "forge", name: "Forge", portrait: "p20", companion: "drone", companionName: "Cog", accentHex: "#F5C97A", recipe: CharacterRecipe.cast["forge"]),
        .init(id: "sage", name: "Sage", portrait: "p04", companion: "slime", companionName: "Moss", accentHex: "#5EEAD4", recipe: CharacterRecipe.cast["sage"]),
        .init(id: "nyx", name: "Nyx", portrait: "p08", companion: "bat", companionName: "Dusk", accentHex: "#FB7185", recipe: CharacterRecipe.cast["nyx"]),
        .init(id: "astra", name: "Astra", portrait: "p14", companion: "bunny", companionName: "Pip", accentHex: "#7DD3FC", recipe: CharacterRecipe.cast["astra"]),
        .init(id: "vesper", name: "Vesper", portrait: "p13", companion: "moth", companionName: "Lumen", accentHex: "#C4B5FF", recipe: CharacterRecipe.cast["vesper"]),
        .init(id: "rook", name: "Rook", portrait: "p19", companion: "hawk", companionName: "Kite", accentHex: "#D6A45E", recipe: CharacterRecipe.cast["rook"])
    ]

    static let portraitIDs: [String] = (1...24).map { String(format: "p%02d", $0) }

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
        let portraits = UserDefaults.standard.dictionary(forKey: "niobium.portraits.v1") as? [String: String] ?? [:]
        if let portrait = portraits[profile.lowercased()] {
            member = CastMember(id: member.id, name: member.name, portrait: portrait,
                                companion: member.companion, companionName: member.companionName, accentHex: member.accentHex, recipe: member.recipe)
        }
        let companions = UserDefaults.standard.dictionary(forKey: "niobium.companions.v1") as? [String: String] ?? [:]
        if let kind = companions[profile.lowercased()] {
            member = CastMember(id: member.id, name: member.name, portrait: member.portrait, companion: kind,
                                companionName: CastMember.all.first(where: { $0.companion == kind })?.companionName ?? member.companionName,
                                accentHex: member.accentHex, recipe: member.recipe)
        }
        if let data = UserDefaults.standard.data(forKey: "niobium.recipe." + profile.lowercased()),
           let recipe = try? JSONDecoder().decode(CharacterRecipe.self, from: data), recipe.isValid {
            member.recipe = recipe
        }
        let names = UserDefaults.standard.dictionary(forKey: "niobium.companionNames.v1") as? [String: String] ?? [:]
        if let name = names[profile.lowercased()] {
            member = CastMember(id: member.id, name: member.name, portrait: member.portrait, companion: member.companion,
                                companionName: name, accentHex: member.accentHex, recipe: member.recipe)
        }
        return member
    }

    func restore(profile: String, metadata: JSONObject) {
        let spec = metadata.dict("pixel_spec")
        let appearance = metadata.dict("appearance")
        let character = spec?.dict("character")
        guard let archetype = appearance?.string("archetype") ?? character?.string("archetype"),
              var member = CastMember.all.first(where: { $0.id == archetype }) else { return }
        if let raw = appearance?.dict("recipe") ?? character?.dict("recipe"),
           let data = try? JSONSerialization.data(withJSONObject: raw),
           let recipe = try? JSONDecoder().decode(CharacterRecipe.self, from: data), recipe.isValid { member.recipe = recipe }
        let companion = appearance?.string("companion") ?? spec?.dict("companion")?.string("kind") ?? member.companion
        let name = appearance?.string("companion_name") ?? spec?.dict("companion")?.string("name") ?? member.companionName
        guard PixelSpec.companions.contains(companion), !name.isEmpty, name.count <= 24 else { return }
        member = CastMember(id: member.id, name: member.name, portrait: member.portrait, companion: companion,
                            companionName: name, accentHex: member.accentHex, recipe: member.recipe)
        if self.member(for: profile) != member { select(member, for: profile) }
    }

    func select(_ member: CastMember, for profile: String) {
        var map = all(); map[profile.lowercased()] = member.id
        UserDefaults.standard.set(map, forKey: key)
        var portraits = UserDefaults.standard.dictionary(forKey: "niobium.portraits.v1") as? [String: String] ?? [:]
        portraits[profile.lowercased()] = member.portrait
        UserDefaults.standard.set(portraits, forKey: "niobium.portraits.v1")
        var companions = UserDefaults.standard.dictionary(forKey: "niobium.companions.v1") as? [String: String] ?? [:]
        companions[profile.lowercased()] = member.companion
        UserDefaults.standard.set(companions, forKey: "niobium.companions.v1")
        if let recipe = member.recipe, let data = try? JSONEncoder().encode(recipe) {
            UserDefaults.standard.set(data, forKey: "niobium.recipe." + profile.lowercased())
        } else { UserDefaults.standard.removeObject(forKey: "niobium.recipe." + profile.lowercased()) }
        var names = UserDefaults.standard.dictionary(forKey: "niobium.companionNames.v1") as? [String: String] ?? [:]
        names[profile.lowercased()] = member.companionName
        UserDefaults.standard.set(names, forKey: "niobium.companionNames.v1")
        revision += 1
    }

    func setCompanion(_ kind: String, name: String?, for profile: String) {
        var companions = UserDefaults.standard.dictionary(forKey: "niobium.companions.v1") as? [String: String] ?? [:]
        companions[profile.lowercased()] = kind
        UserDefaults.standard.set(companions, forKey: "niobium.companions.v1")
        if let name, !name.isEmpty {
            var names = UserDefaults.standard.dictionary(forKey: "niobium.companionNames.v1") as? [String: String] ?? [:]
            names[profile.lowercased()] = name
            UserDefaults.standard.set(names, forKey: "niobium.companionNames.v1")
        }
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
            CharacterSprite(member: member)
                .padding(size * 0.04)
                .shadow(color: showGlow ? member.accent.opacity(0.28) : .clear, radius: size * 0.10, y: size * 0.04)
            if showCompanion && !CharacterEngine.shared.usesAuthored(member) {
                Image("Comp_\(member.companion)")
                    .resizable()
                    .interpolation(.none)
                    .frame(width: size * 0.34, height: size * 0.34)
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
    @EnvironmentObject private var app: AppStore
    @State private var saving = false
    @Environment(\.dismiss) private var dismiss
    let profile: String
    let title: String
    @State private var selected: CastMember
    @ObservedObject private var store = CastStore.shared

    init(profile: String, title: String, castKey: String? = nil) {
        self.profile = profile
        self.title = title
        _selected = State(initialValue: CastStore.shared.member(for: castKey ?? profile, title: title))
    }

    private func layerOptions(_ field: String) -> [String] {
        switch field {
        case "palette": return CharacterRecipe.cast.keys.sorted()
        case "hair": return Array(Set(CharacterRecipe.cast.values.map(\.hair))).sorted()
        case "outfit": return Array(Set(CharacterRecipe.cast.values.map(\.outfit))).sorted()
        default: return ["none", "visor", "goggles", "antlers", "cat_ears", "circlet", "veil", "headwrap"]
        }
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
                    NBSectionHeader(title: "The cast", detail: "48 × 48")
                    LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 10) {
                        ForEach(CastMember.all) { member in
                            Button {
                                selected = member
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
                    VStack(alignment: .leading, spacing: 10) {
                        NBSectionHeader(title: "Make it yours", detail: "48 × 48 layers")
                        ForEach(["palette", "hair", "outfit", "accessory"], id: \.self) { field in
                            Menu {
                                ForEach(layerOptions(field), id: \.self) { option in
                                    Button(option.replacingOccurrences(of: "_", with: " ").capitalized) {
                                        var recipe = selected.recipe ?? CharacterRecipe.cast[selected.id]!
                                        switch field {
                                        case "palette": recipe.palette = option
                                        case "hair": recipe.hair = option
                                        case "outfit": recipe.outfit = option
                                        default: recipe.accessory = option
                                        }
                                        selected.recipe = recipe
                                    }
                                }
                            } label: {
                                HStack {
                                    Text(field.capitalized)
                                    Spacer()
                                    Image(systemName: "chevron.up.chevron.down")
                                }.font(.nbBody(13)).foregroundStyle(NB.secondary).padding(12)
                                    .background(RoundedRectangle(cornerRadius: 10).fill(NB.fillSoft))
                            }
                        }
                    }

                    VStack(alignment: .leading, spacing: 9) {
                        NBSectionHeader(title: "Companion")
                        LazyVGrid(columns: Array(repeating: GridItem(.flexible()), count: 4), spacing: 9) {
                            ForEach(Array(Set(CastMember.all.map(\.companion))).sorted(), id: \.self) { kind in
                                Button {
                                    selected = CastMember(id: selected.id, name: selected.name, portrait: selected.portrait, companion: kind,
                                                          companionName: CastMember.all.first(where: { $0.companion == kind })?.companionName ?? selected.companionName,
                                                          accentHex: selected.accentHex, recipe: selected.recipe)
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
                        selected = CastMember.all.filter { $0.id != selected.id }.randomElement() ?? selected
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
                    Button(saving ? "Saving…" : "Done") {
                        saving = true
                        Task { if await app.saveAppearance(selected, profile: profile) { dismiss() }; saving = false }
                    }.foregroundStyle(NB.ion).disabled(saving)
                }
            }
        }
        .presentationDetents([.large])
    }
}
