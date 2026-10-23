import SwiftUI

struct CastMember: Identifiable, Equatable {
    let id: String
    let name: String
    let portrait: String
    let companion: String
    let companionName: String
    let accentHex: String
    var recipe: CharacterRecipe? = nil
    var look: PixelLook? = nil
    var companionArt: PixelArt? = nil
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
        if let raw = UserDefaults.standard.data(forKey: "niobium.look." + profile.lowercased()),
           let look = try? JSONDecoder().decode(PixelLook.self, from: raw), look.isValid { member.look = look }
        if let raw = UserDefaults.standard.data(forKey: "niobium.companionArt." + profile.lowercased()),
           let art = try? JSONDecoder().decode(PixelArt.self, from: raw), art.size == 16, art.isValid { member.companionArt = art }
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
        let lookValue = appearance?.keys.contains("look") == true ? appearance?.dict("look") : character?.dict("look")
        if let raw = lookValue, let data = try? JSONSerialization.data(withJSONObject: raw),
           let look = try? JSONDecoder().decode(PixelLook.self, from: data), look.isValid { member.look = look }
        let companion = appearance?.string("companion") ?? spec?.dict("companion")?.string("kind") ?? member.companion
        let name = appearance?.string("companion_name") ?? spec?.dict("companion")?.string("name") ?? member.companionName
        guard PixelSpec.companions.contains(companion), !name.isEmpty, name.count <= 24 else { return }
        member = CastMember(id: member.id, name: member.name, portrait: member.portrait, companion: companion,
                            companionName: name, accentHex: member.accentHex, recipe: member.recipe, look: member.look)
        let artValue = appearance?.keys.contains("companion_art") == true ? appearance?.dict("companion_art") : spec?.dict("companion")?.dict("art")
        if let raw = artValue, let data = try? JSONSerialization.data(withJSONObject: raw),
           let art = try? JSONDecoder().decode(PixelArt.self, from: data), art.size == 16, art.isValid { member.companionArt = art }
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
        if let look = member.look, let data = try? JSONEncoder().encode(look) {
            UserDefaults.standard.set(data, forKey: "niobium.look." + profile.lowercased())
        } else { UserDefaults.standard.removeObject(forKey: "niobium.look." + profile.lowercased()) }
        if let art = member.companionArt, let data = try? JSONEncoder().encode(art) {
            UserDefaults.standard.set(data, forKey: "niobium.companionArt." + profile.lowercased())
        } else { UserDefaults.standard.removeObject(forKey: "niobium.companionArt." + profile.lowercased()) }
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
            PixelButtonShape().fill(member.accent.opacity(0.08))
            CharacterSprite(member: member, animate: size >= 96)
            if showCompanion {
                FamiliarSprite(kind: member.companion, size: size * 0.26, art: member.companionArt)
                    .frame(width: size * 0.30, height: size * 0.30)
                    .padding(size * 0.02)
            }
        }
        .frame(width: size, height: size)
        .clipShape(PixelButtonShape())
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

extension CharacterRecipe {
    static func label(for field: String) -> String {
        ["body": "Build", "palette": "Colors", "skin": "Skin tone", "hair_tone": "Hair color",
         "hair": "Hair style", "face": "Expression", "outfit": "Outfit", "accessory": "Accessory", "detail": "Detail"][field] ?? field.capitalized
    }
    static func label(_ value: String, field: String) -> String {
        if value == "auto" { return "Palette default" }
        if field == "body" { return value == "man" ? "Masculine" : "Feminine" }
        if field == "hair" {
            return ["silver_swept": "Side sweep", "violet_bob": "Bob", "cat_black": "Long layers",
                    "white_pilot": "Spiked crop", "silver_bob": "Soft bob"][value]
                ?? value.replacingOccurrences(of: "_", with: " ").capitalized
        }
        return value.replacingOccurrences(of: "_", with: " ").capitalized
    }
}

struct PixelLookEditor: View {
    @Binding var look: PixelLook
    @Binding var history: [PixelLook]
    var enabled = true
    @State private var browsing = false

    private func choose(_ value: PixelLook) {
        guard value != look else { return }
        history = Array((history + [look]).suffix(12))
        look = value
        Haptics.tap()
    }
    var body: some View {
        VStack(spacing: 12) {
            LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 8), count: 4), spacing: 8) {
                ForEach(Array(PixelWardrobe.entries.prefix(8)), id: \.id) { entry in
                    WardrobeTile(entry: entry, selected: look.portrait == entry.id) {
                        var next = look; next.portrait = entry.id; next.customArt = nil; choose(next)
                    }
                }
            }
            Button { browsing = true } label: {
                HStack(spacing: 10) {
                    PixelIcon(.pixels, size: 20)
                    Text("Browse 32 looks").font(.nbBody(13, weight: .semibold))
                    Spacer()
                    Text(look.entry.label).font(.nbBody(12)).lineLimit(1)
                }.foregroundStyle(NB.ion).padding(12).frame(minHeight: 48)
                    .background(PixelButtonShape().fill(NB.ion.opacity(0.08)))
            }.buttonStyle(.plain).accessibilityIdentifier("look.browse")
            PixelArtistButton(target: "pixel", referenceImage: CharacterEngine.shared.image(for: look)) { art in
                var next = look; next.customArt = art; next.accent = "auto"; choose(next)
            }
            if look.customArt == nil { colorMenu("Outfit", options: ["auto"] + PixelLook.accents, value: look.accent) { var next = look; next.accent = $0; choose(next) } }
            HStack(spacing: 8) {
                Button {
                    let alternatives = PixelLook.portraits.filter { $0 != look.portrait }
                    var random = CharacterRandom(UUID().uuidString)
                    choose(.init(portrait: alternatives[random.index(alternatives.count)]))
                } label: { PixelActionLabel("Surprise", icon: .spark) }
                    .buttonStyle(NBQuietButtonStyle(compact: true)).accessibilityIdentifier("look.surprise")
                Button { choose(look.remixed(seed: UUID().uuidString)) } label: { PixelActionLabel("Remix", icon: .spark) }
                    .buttonStyle(NBQuietButtonStyle(compact: true)).disabled(look.customArt != nil).accessibilityIdentifier("look.remix")
                Button {
                    if let previous = history.popLast() { look = previous; Haptics.tap() }
                } label: { PixelActionLabel("Undo", icon: .back) }
                    .buttonStyle(NBQuietButtonStyle(compact: true)).disabled(history.isEmpty).accessibilityIdentifier("look.undo")
            }
        }.disabled(!enabled)
        .sheet(isPresented: $browsing) {
            WardrobePicker(selected: look.portrait) { id in
                var next = look; next.portrait = id; next.customArt = nil; choose(next); browsing = false
            }
        }
    }
    private func colorMenu(_ title: String, options: [String], value: String, action: @escaping (String) -> Void) -> some View {
        Menu {
            ForEach(options, id: \.self) { option in
                Button(option == "auto" ? "Original" : option.capitalized) { action(option) }
            }
        } label: {
            HStack {
                Text(title).font(.nbBody(12)).foregroundStyle(NB.secondary)
                Spacer()
                Text(value == "auto" ? "Original" : value.capitalized).font(.nbBody(12, weight: .semibold)).foregroundStyle(NB.text)
                PixelIcon(.pixels, size: 14)
            }.padding(.horizontal, 12).frame(maxWidth: .infinity, minHeight: 44).background(PixelButtonShape().fill(NB.fillSoft))
        }.accessibilityLabel(title + " color").accessibilityValue(value).accessibilityIdentifier("look.color." + title.lowercased())
    }
}

private struct WardrobeTile: View {
    let entry: PixelLookEntry
    let selected: Bool
    var action: () -> Void
    var body: some View {
        let base = CastMember.all.first { $0.id == entry.archetype } ?? CastMember.all[0]
        Button(action: action) {
            VStack(spacing: 4) {
                CastPortrait(member: .init(id: base.id, name: entry.label, portrait: base.portrait,
                    companion: base.companion, companionName: base.companionName,
                    accentHex: base.accentHex, look: .init(portrait: entry.id)), size: 58, showCompanion: false, showGlow: false)
                Text(entry.label).font(.nbBody(10)).foregroundStyle(NB.text).lineLimit(1)
            }.frame(maxWidth: .infinity).padding(.vertical, 7)
                .background(PixelButtonShape().fill(selected ? NB.ion.opacity(0.12) : NB.fillSoft))
                .overlay(PixelButtonShape().stroke(selected ? NB.ion : .clear, lineWidth: 1))
        }.buttonStyle(.plain).accessibilityLabel(entry.label + " look")
            .accessibilityAddTraits(selected ? .isSelected : [])
            .accessibilityIdentifier("look." + entry.id)
    }
}

private struct WardrobePicker: View {
    let selected: String
    var onChoose: (String) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var family = "All"
    @State private var search = ""
    private var entries: [PixelLookEntry] {
        PixelWardrobe.entries.filter {
            (family == "All" || $0.family == family) && (search.isEmpty || ([$0.label, $0.family] + $0.tags).joined(separator: " ").localizedCaseInsensitiveContains(search))
        }
    }
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 6) {
                            ForEach(["All", "Arcane", "Cyber", "Wild", "Dark"], id: \.self) { item in
                                Button { family = item } label: {
                                    Text(item).font(.nbBody(12, weight: .semibold))
                                        .foregroundStyle(family == item ? NB.ion : NB.secondary)
                                        .padding(.horizontal, 14).frame(minHeight: 44)
                                        .background(PixelButtonShape().fill(family == item ? NB.ion.opacity(0.12) : NB.fillSoft))
                                }.buttonStyle(.plain).accessibilityAddTraits(family == item ? .isSelected : [])
                                    .accessibilityIdentifier("wardrobe.family." + item.lowercased())
                            }
                        }
                    }
                    LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 8), count: 4), spacing: 8) {
                        ForEach(entries, id: \.id) { entry in
                            WardrobeTile(entry: entry, selected: selected == entry.id) { onChoose(entry.id) }
                        }
                    }
                    if entries.isEmpty { Text("No matching looks").font(.nbBody(14)).foregroundStyle(NB.secondary) }
                }.padding(20)
            }.background(NB.void).navigationTitle("Wardrobe").navigationBarTitleDisplayMode(.inline)
                .searchable(text: $search, prompt: "Try vampire, cyborg, frost…")
                .toolbar { ToolbarItem(placement: .topBarTrailing) { Button("Done") { dismiss() } } }
        }
    }
}

struct CharacterCustomizer: View {
    @Binding var recipe: CharacterRecipe
    let archetype: String
    @Binding var pinned: Set<String>
    var enabled = true

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Button {
                recipe = recipe.remixed(archetype: archetype, seed: UUID().uuidString, locked: pinned)
                Haptics.tap()
            } label: { PixelActionLabel("Remix look", icon: .spark) }
                .buttonStyle(NBQuietButtonStyle())
                .disabled(!enabled || pinned.count == CharacterRecipe.fieldNames.count)
                .accessibilityIdentifier("character.remix")
            Text("Pin your favorites, then remix the rest. Pinning a palette-default color also keeps its palette.")
                .font(.nbBody(11)).foregroundStyle(NB.tertiary)
            ForEach(CharacterRecipe.fieldNames, id: \.self) { field in
                HStack(spacing: 8) {
                    Menu {
                        ForEach(CharacterRecipe.options(for: field), id: \.self) { option in
                            Button { recipe.set(option, for: field) } label: {
                                if option == recipe.value(for: field) {
                                    Label(CharacterRecipe.label(option, field: field), systemImage: "checkmark")
                                } else { Text(CharacterRecipe.label(option, field: field)) }
                            }
                        }
                    } label: {
                        HStack(spacing: 12) {
                            Text(CharacterRecipe.label(for: field)).font(.nbBody(12)).foregroundStyle(NB.secondary)
                            Spacer(minLength: 4)
                            Text(CharacterRecipe.label(recipe.value(for: field), field: field))
                                .font(.nbBody(12)).foregroundStyle(NB.text).lineLimit(1)
                            PixelIcon(.back, size: 12, tint: NB.tertiary).rotationEffect(.degrees(-90))
                        }.padding(.horizontal, 12).frame(minHeight: 46)
                            .background(RoundedRectangle(cornerRadius: 10).fill(NB.fillSoft))
                    }.accessibilityIdentifier("character." + field)
                    Button {
                        if pinned.contains(field) { pinned.remove(field) } else { pinned.insert(field) }
                    } label: {
                        PixelIcon(.pin, size: 20, tint: pinned.contains(field) ? NB.ion : NB.tertiary)
                            .frame(width: 46, height: 46)
                            .background(RoundedRectangle(cornerRadius: 10).fill(pinned.contains(field) ? NB.ion.opacity(0.12) : NB.fillSoft))
                    }.buttonStyle(.plain)
                        .accessibilityLabel((pinned.contains(field) ? "Unpin " : "Pin ") + CharacterRecipe.label(for: field).lowercased())
                        .accessibilityValue(pinned.contains(field) ? "Pinned" : "Unpinned")
                        .accessibilityIdentifier("character.pin." + field)
                }
            }
            Button {
                recipe = CharacterRecipe.cast[archetype] ?? CharacterRecipe.cast["orion"]!
                Haptics.tap()
            } label: { Text("Restore original cast look").font(.nbBody(12)).foregroundStyle(NB.secondary).frame(minHeight: 44) }
                .buttonStyle(.plain).accessibilityIdentifier("character.restore")
        }.disabled(!enabled)
    }
}

struct FamiliarPicker: View {
    @Binding var kind: String
    var enabled = true
    var body: some View {
        LazyVGrid(columns: Array(repeating: GridItem(.flexible()), count: 4), spacing: 8) {
            ForEach(PixelSpec.companions, id: \.self) { option in
                Button {
                    kind = option; Haptics.tap()
                } label: {
                    VStack(spacing: 6) {
                        FamiliarSprite(kind: option, size: 32)
                        Text(option.capitalized).font(.nbBody(10)).foregroundStyle(kind == option ? NB.text : NB.secondary)
                    }.frame(maxWidth: .infinity).padding(.vertical, 10)
                        .background(RoundedRectangle(cornerRadius: 12).fill(kind == option ? NB.ion.opacity(0.12) : NB.fillSoft))
                        .overlay(RoundedRectangle(cornerRadius: 12).strokeBorder(kind == option ? NB.ion.opacity(0.5) : NB.line, lineWidth: 1))
                }.buttonStyle(.plain).disabled(!enabled)
                    .accessibilityLabel(option.capitalized + " companion")
                    .accessibilityAddTraits(kind == option ? .isSelected : [])
                    .accessibilityIdentifier("familiar." + option)
            }
        }
    }
}

struct CastStudioView: View {
    @EnvironmentObject private var app: AppStore
    @Environment(\.dismiss) private var dismiss
    @State private var saving = false
    let profile: String
    let title: String
    @State private var selected: CastMember
    @State private var pinned: Set<String> = ["body", "skin"]
    @State private var lookHistory: [PixelLook] = []

    init(profile: String, title: String, castKey: String? = nil) {
        self.profile = profile; self.title = title
        _selected = State(initialValue: CastStore.shared.member(for: castKey ?? profile, title: title))
    }
    private var recipe: Binding<CharacterRecipe> {
        Binding(get: { selected.recipe ?? CharacterRecipe.cast[selected.id]! }, set: { selected.recipe = $0 })
    }
    private var companion: Binding<String> {
        Binding(get: { selected.companion }, set: { kind in
            selected = CastMember(id: selected.id, name: selected.name, portrait: selected.portrait,
                                  companion: kind, companionName: selected.companionName,
                                  accentHex: selected.accentHex, recipe: selected.recipe, look: selected.look)
        })
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 22) {
                    VStack(spacing: 10) {
                        CastPortrait(member: selected, size: 160)
                        Text(title).font(.nbTitle(22)).foregroundStyle(NB.text)
                        Text(selected.companionName + " · " + selected.companion.capitalized)
                            .font(.nbBody(11)).foregroundStyle(NB.secondary)
                    }.frame(maxWidth: .infinity)
                    if selected.look != nil {
                        PixelLookEditor(look: Binding(get: { selected.look! }, set: { selected.look = $0 }), history: $lookHistory, enabled: !saving)
                    } else {
                        Button("Choose a new portrait") { selected.look = .init(portrait: selected.id) }
                            .buttonStyle(NBQuietButtonStyle())
                        DisclosureGroup("Original controls") {
                            CharacterCustomizer(recipe: recipe, archetype: selected.id, pinned: $pinned, enabled: !saving)
                        }.foregroundStyle(NB.secondary)
                    }
                    NBSectionHeader(title: "Creature")
                    FamiliarPicker(kind: companion, enabled: !saving)
                    CompanionArtEditor(kind: selected.companion, art: $selected.companionArt).disabled(saving)
                    NBSectionHeader(title: "The cast", detail: "eight starting points")
                    LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 10) {
                        ForEach(CastMember.all) { member in
                            Button {
                                // Choosing a look preserves the existing companion's identity.
                                selected = CastMember(id: member.id, name: member.name, portrait: member.portrait,
                                                      companion: selected.companion, companionName: selected.companionName,
                                                      accentHex: member.accentHex, recipe: member.recipe, companionArt: selected.companionArt)
                                Haptics.tap()
                            } label: {
                                HStack(spacing: 10) {
                                    CastPortrait(member: member, size: 54, showCompanion: false, showGlow: false)
                                    Text(member.name).font(.nbTitle(13)).foregroundStyle(NB.text)
                                    Spacer(minLength: 0)
                                }.padding(8)
                                    .background(RoundedRectangle(cornerRadius: 14).fill(selected.id == member.id ? NB.ion.opacity(0.1) : NB.fillSoft))
                                    .overlay(RoundedRectangle(cornerRadius: 14).strokeBorder(selected.id == member.id ? NB.ion.opacity(0.4) : NB.line, lineWidth: 1))
                            }.buttonStyle(.plain).disabled(saving)
                        }
                    }
                }.padding(20)
            }.background(NBMeshBackground(intensity: 0.5))
                .navigationTitle("Pixel studio").navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .confirmationAction) {
                        Button(saving ? "Saving…" : "Done") {
                            saving = true
                            let appearance = selected
                            Task { if await app.saveAppearance(appearance, profile: profile) { dismiss() }; saving = false }
                        }.foregroundStyle(NB.ion).disabled(saving)
                    }
                }
        }.presentationDetents([.large]).interactiveDismissDisabled(saving)
    }
}
