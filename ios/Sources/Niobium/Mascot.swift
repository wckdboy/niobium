import SwiftUI

struct Mascot: Codable, Equatable {
    var patternID: String
    var rows: [String]
    var accentHex: String?

    var isValid: Bool { rows.count == 8 && rows.allSatisfy { $0.count == 8 } }

    static let curated: [Mascot] = [
        Mascot(patternID: "ghost", rows: [
            "..A..A..", ".AAAAAA.", "AAAAAAAA", "AADAADAA",
            "AAAAAAAA", "AAADDAAA", ".AAAAAA.", "..A..A.."
        ], accentHex: nil),
        Mascot(patternID: "cat", rows: [
            "A......A", "AA....AA", "AAAAAAAA", "AALAALAA",
            "AAAAAAAA", "AAADDAAA", ".AAAAAA.", "..A..A.."
        ], accentHex: nil),
        Mascot(patternID: "owl", rows: [
            "..AAAA..", ".AAAAAA.", "AALAALAA", "AADAADAA",
            "AALAALAA", "AADDDDAA", ".AAAAAA.", "..A..A.."
        ], accentHex: nil),
        Mascot(patternID: "robot", rows: [
            "A..AA..A", "AAAAAAAA", "AADDDDAA", "AADDDDAA",
            "AAAAAAAA", "A.DDDD.A", "..AAAA..", "..A..A.."
        ], accentHex: nil),
        Mascot(patternID: "slime", rows: [
            "........", "..AAAA..", ".AAAAAA.", "AAAAAAAA",
            "AALAALAA", "AADDDDAA", ".AAAAAA.", "..A..A.."
        ], accentHex: nil),
        Mascot(patternID: "bloom", rows: [
            "..A..A..", ".AAAAAA.", "AALAAAAL", "AADDDDAA",
            "AALAAAAL", ".AAAAAA.", "..AAAA..", "...AA..."
        ], accentHex: nil),
        Mascot(patternID: "comet", rows: [
            ".....A..", "....AA..", "...AAA..", "..AAAA..",
            ".AAAA...", "..AA....", ".A......", "........"
        ], accentHex: nil),
        Mascot(patternID: "ore", rows: [
            "..AAA...", ".AAAAA..", "AALAAAA.", "AALAADAA",
            "AAADDAAA", ".AAAAAA.", "..AAAA..", "...AA..."
        ], accentHex: nil)
    ]

    static func seeded(_ seed: String) -> Mascot {
        switch seed.lowercased() {
        case "athena", "default": return curated[0]
        case "forge": return curated[3]
        case "scout": return curated[2]
        case "hermes": return curated[5]
        default: break
        }
        var hash: UInt64 = 41
        for scalar in seed.unicodeScalars { hash = hash &* 31 &+ UInt64(scalar.value) }
        return curated[Int(hash % UInt64(curated.count))]
    }

    static func random(excluding currentID: String? = nil) -> Mascot {
        curated.filter { $0.patternID != currentID }.randomElement() ?? curated[0]
    }
}

@MainActor
final class MascotStore: ObservableObject {
    static let shared = MascotStore()
    @Published private(set) var revision = 0

    private let defaults = UserDefaults.standard
    private let key = "niobium.mascots.v1"

    private func records() -> [String: Mascot] {
        guard let data = defaults.data(forKey: key),
              let decoded = try? JSONDecoder().decode([String: Mascot].self, from: data) else { return [:] }
        return decoded
    }

    func mascot(for profile: String, accentHex: String? = nil) -> Mascot {
        let key = profile.lowercased()
        if var record = records()[key], record.isValid {
            if record.accentHex == nil { record.accentHex = accentHex }
            return record
        }
        var mascot = Mascot.seeded(key.isEmpty ? "niobium" : key)
        mascot.accentHex = accentHex
        return mascot
    }

    func save(_ mascot: Mascot, for profile: String) {
        guard mascot.isValid else { return }
        var all = records()
        all[profile.lowercased()] = mascot
        if let data = try? JSONEncoder().encode(all) { defaults.set(data, forKey: key) }
        revision += 1
    }

    func reset(for profile: String) {
        var all = records()
        all.removeValue(forKey: profile.lowercased())
        if let data = try? JSONEncoder().encode(all) { defaults.set(data, forKey: key) }
        revision += 1
    }
}

struct MascotFace: View {
    var pattern: Mascot
    var accent: Color

    var body: some View {
        Canvas { context, size in
            let cell = min(size.width, size.height) / 8
            for (row, string) in pattern.rows.enumerated() {
                for (col, char) in string.enumerated() {
                    guard char != "." else { continue }
                    let rect = CGRect(x: CGFloat(col) * cell, y: CGFloat(row) * cell, width: cell, height: cell)
                    let color: Color
                    switch char {
                    case "D": color = Color(hex: 0x170D24)
                    case "L": color = NB.ink.opacity(0.92)
                    default: color = row >= 6 ? accent.opacity(0.78) : accent
                    }
                    context.fill(Path(rect), with: .color(color))
                }
            }
        }
        .aspectRatio(1, contentMode: .fit)
    }
}

struct MascotTile: View {
    var mascot: Mascot
    var accent: Color
    var size: CGFloat

    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: size * 0.28, style: .continuous)
                .fill(LinearGradient(colors: [accent.opacity(0.28), NB.ink],
                                     startPoint: .topLeading, endPoint: .bottomTrailing))
            MascotFace(pattern: mascot, accent: accent)
                .padding(size * 0.10)
        }
        .frame(width: size, height: size)
        .overlay(
            RoundedRectangle(cornerRadius: size * 0.28, style: .continuous)
                .strokeBorder(LinearGradient(colors: [NB.lineStrong, NB.fillSoft],
                                             startPoint: .topLeading, endPoint: .bottomTrailing),
                              lineWidth: 1)
        )
    }
}

struct MascotStudioView: View {
    @Environment(\.dismiss) private var dismiss
    let profile: String
    let title: String
    let initialAccent: String
    var onSave: (Mascot) -> Void

    @State private var selected: Mascot
    @State private var accentHex: String
    @State private var customRows: [[Bool]]
    @State private var editingCustom = false

    private let palette = ["#A78BFA", "#F0ABFC", "#5EEAD4", "#F5C97A", "#FDA4AF", "#C4B5FD", "#7C3AED", "#F472B6"]

    init(profile: String, title: String, initialAccent: String, onSave: @escaping (Mascot) -> Void) {
        self.profile = profile
        self.title = title
        self.initialAccent = initialAccent
        self.onSave = onSave
        let start = MascotStore.shared.mascot(for: profile, accentHex: initialAccent)
        _selected = State(initialValue: start)
        _accentHex = State(initialValue: start.accentHex ?? initialAccent)
        _customRows = State(initialValue: start.rows.map { $0.map { $0 != "." } })
    }

    private var accent: Color { NB.color(for: accentHex) }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 22) {
                    preview
                    curatedSection
                    colorSection
                    customSection
                    saveButton
                }
                .padding(20)
            }
            .background(NBMeshBackground(intensity: 0.45))
            .navigationTitle("Mascot studio")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }.foregroundStyle(NB.secondary)
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        selected = .random(excluding: selected.patternID)
                        customRows = selected.rows.map { $0.map { $0 != "." } }
                        editingCustom = false
                    } label: {
                        Label("Surprise me", systemImage: "dice")
                    }
                    .foregroundStyle(NB.ion)
                }
            }
        }
    }

    private var preview: some View {
        VStack(spacing: 10) {
            MascotTile(mascot: selected, accent: accent, size: 104)
                .shadow(color: accent.opacity(0.24), radius: 22, y: 8)
            Text(title).font(.nbTitle(19)).foregroundStyle(NB.text)
            Text("\(profile) · choose, randomise or draw an 8×8 mascot")
                .font(.nbBody(10.5)).foregroundStyle(NB.tertiary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 8)
    }

    private var curatedSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            NBSectionHeader(title: "Chosen", detail: "8 × 8")
            LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 10), count: 4), spacing: 10) {
                ForEach(Mascot.curated, id: \.patternID) { item in
                    Button {
                        var next = item
                        next.accentHex = accentHex
                        selected = next
                        customRows = next.rows.map { $0.map { $0 != "." } }
                        editingCustom = false
                        Haptics.tap()
                    } label: {
                        VStack(spacing: 6) {
                            MascotTile(mascot: item, accent: accent, size: 62)
                                .overlay(
                                    RoundedRectangle(cornerRadius: 17, style: .continuous)
                                        .strokeBorder(selected.patternID == item.patternID ? NB.ion : .clear, lineWidth: 2)
                                )
                            Text(item.patternID.capitalized).font(.nbMono(8.5)).foregroundStyle(NB.secondary)
                        }
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    private var colorSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            NBSectionHeader(title: "Ink")
            HStack(spacing: 10) {
                ForEach(palette, id: \.self) { hex in
                    Button {
                        accentHex = hex
                        selected.accentHex = hex
                        Haptics.tap()
                    } label: {
                        Circle().fill(NB.color(for: hex)).frame(width: 30, height: 30)
                            .overlay(Circle().strokeBorder(accentHex == hex ? NB.text : NB.lineStrong,
                                                           lineWidth: accentHex == hex ? 2 : 1))
                            .scaleEffect(accentHex == hex ? 1.1 : 1)
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    private var customSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                NBSectionHeader(title: "Custom", detail: editingCustom ? "tap cells" : nil)
                Spacer()
                Button(editingCustom ? "Done" : "Draw") {
                    if !editingCustom {
                        customRows = selected.rows.map { $0.map { $0 != "." } }
                    }
                    editingCustom.toggle()
                }
                .font(.nbBody(12, weight: .medium)).foregroundStyle(NB.ion)
            }
            if editingCustom {
                PixelEditor(rows: $customRows, accent: accent)
                    .frame(maxWidth: 300)
                    .frame(maxWidth: .infinity)
                    .onChange(of: customRows) { _, rows in
                        var next = selected
                        next.patternID = "custom"
                        next.rows = rows.map { row in String(row.map { $0 ? "A" : "." }) }
                        next.accentHex = accentHex
                        selected = next
                    }
            } else {
                Text("Start from any mascot, then tap **Draw** to edit the 8×8 grid.")
                    .font(.nbBody(10.5)).foregroundStyle(NB.tertiary)
            }
        }
    }

    private var saveButton: some View {
        VStack(spacing: 8) {
            Button {
                selected.accentHex = accentHex
                MascotStore.shared.save(selected, for: profile)
                onSave(selected)
                Haptics.tap()
                dismiss()
            } label: {
                Text("Use this mascot").frame(maxWidth: .infinity)
            }
            .buttonStyle(NBPrimaryButtonStyle())
            Button("Reset to a name-seeded mascot") {
                MascotStore.shared.reset(for: profile)
                let seeded = MascotStore.shared.mascot(for: profile, accentHex: initialAccent)
                selected = seeded
                accentHex = seeded.accentHex ?? initialAccent
                customRows = seeded.rows.map { $0.map { $0 != "." } }
            }
            .font(.nbBody(10.5)).foregroundStyle(NB.tertiary)
        }
    }
}

private struct PixelEditor: View {
    @Binding var rows: [[Bool]]
    var accent: Color

    var body: some View {
        VStack(spacing: 2) {
            ForEach(0..<8, id: \.self) { row in
                HStack(spacing: 2) {
                    ForEach(0..<8, id: \.self) { col in
                        Button {
                            rows[row][col].toggle()
                        } label: {
                            Rectangle()
                                .fill(rows[row][col] ? accent : NB.fillSoft)
                                .aspectRatio(1, contentMode: .fit)
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
        .padding(8)
        .background(RoundedRectangle(cornerRadius: 16).fill(NB.surface))
        .overlay(RoundedRectangle(cornerRadius: 16).strokeBorder(NB.line, lineWidth: 1))
    }
}
