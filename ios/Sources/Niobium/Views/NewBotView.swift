import SwiftUI

struct NewBotView: View {
    @EnvironmentObject private var store: AppStore
    @Environment(\.dismiss) private var dismiss
    @State private var name = ""
    @State private var title = ""
    @State private var role = ""
    @State private var color = "#A78BFA"
    @State private var saving = false
    @State private var error: String?
    @State private var showMascot = false

    private let colors: [(String, String)] = [
        ("Ion", "#A78BFA"), ("Orchid", "#F0ABFC"), ("Mint", "#5EEAD4"),
        ("Gold", "#F5C97A"), ("Rose", "#FDA4AF"), ("Ice", "#C4B5FD")
    ]

    var body: some View {
        NavigationStack {
            ZStack {
                NBMeshBackground(intensity: 0.6)
                ScrollView {
                    VStack(alignment: .leading, spacing: 20) {
                        preview
                            .frame(maxWidth: .infinity)
                            .padding(.top, 4)

                        input("PROFILE NAME", placeholder: "athena", text: $name, hint: "Lowercase, no spaces. This becomes the Hermes profile.")
                        input("DISPLAY TITLE", placeholder: "Athena", text: $title)
                        input("ROLE", placeholder: "Research, strategy and long-horizon thinking.", text: $role, multiline: true)

                        VStack(alignment: .leading, spacing: 9) {
                            NBEyebrow(text: "Signal colour")
                            HStack(spacing: 9) {
                                ForEach(colors, id: \.1) { item in
                                    Button {
                                        color = item.1
                                    } label: {
                                        Circle()
                                            .fill(Color(hex: UInt32(item.1.dropFirst(), radix: 16) ?? 0x7C9CFF))
                                            .frame(width: 30, height: 30)
                                            .overlay(Circle().strokeBorder(color == item.1 ? NB.onIon : NB.lineStrong, lineWidth: color == item.1 ? 2 : 1))
                                            .scaleEffect(color == item.1 ? 1.08 : 1)
                                    }
                                    .buttonStyle(.plain)
                                }
                            }
                        }

                        HStack(alignment: .top, spacing: 9) {
                            Image(systemName: "person.2.badge.plus").font(.system(size: 11)).foregroundStyle(NB.ion)
                            Text("Creates a Hermes profile with its own memory, sessions and toolsets. It appears in your pixel roster instantly.")
                                .font(.nbBody(10.5)).foregroundStyle(NB.tertiary).lineSpacing(2)
                        }

                        if let error { Text(error).font(.nbBody(11)).foregroundStyle(NB.rose) }

                        Button {
                            saving = true; error = nil
                            Task {
                                do {
                                    try await store.createBot(name: name, title: title.isEmpty ? name.capitalized : title,
                                                              role: role, colorHex: color)
                                    dismiss()
                                } catch {
                                    self.error = error.localizedDescription
                                    saving = false
                                }
                            }
                        } label: {
                            HStack {
                                if saving { ProgressView().tint(NB.onIon).scaleEffect(0.8) }
                                Text(saving ? "Creating pixel…" : "Create pixel").frame(maxWidth: .infinity)
                            }
                        }
                        .buttonStyle(NBPrimaryButtonStyle())
                        .disabled(name.trimmingCharacters(in: .whitespaces).isEmpty || saving)
                    }
                    .padding(20)
                }
            }
            .navigationTitle("New pixel")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }.foregroundStyle(NB.secondary)
                }
            }
            .toolbarBackground(NB.ink, for: .navigationBar)
        }
        .presentationDetents([.large])
        .presentationBackground(NB.ink)
    }

    private var preview: some View {
        let profile = BotProfile(name: name.isEmpty ? "niobium" : name,
                                 title: title.isEmpty ? (name.isEmpty ? "New Bot" : name.capitalized) : title,
                                 role: role.isEmpty ? "A new specialist on your Hermes team." : role,
                                 provider: "", model: "Inherited model", skillCount: 0,
                                 canonicalSessionID: nil, canonicalResolvedID: nil, lastSessionID: nil,
                                 preview: "Ready to introduce itself.", activityAt: nil,
                                 workerActive: false, isDefault: false, hidden: false,
                                 accentHex: color, hasAvatar: false)
        return VStack(spacing: 10) {
            BotAvatar(bot: profile, size: 72, showStatus: false)
                .shadow(color: profile.accent.opacity(0.3), radius: 24, y: 8)
            Text(profile.title).font(.nbTitle(20)).foregroundStyle(NB.text)
            Text(profile.role).font(.nbBody(11.5)).foregroundStyle(NB.secondary)
                .multilineTextAlignment(.center).frame(maxWidth: 280)
            Button {
                showMascot = true
            } label: {
                Label("Customise mascot", systemImage: "paintpalette")
                    .font(.nbBody(11, weight: .medium))
                    .foregroundStyle(NB.ion)
            }
            .buttonStyle(.plain)
        }
        .padding(.vertical, 12)
        .sheet(isPresented: $showMascot) {
            MascotStudioView(profile: name.isEmpty ? "niobium" : name,
                             title: title.isEmpty ? (name.isEmpty ? "New Pixel" : name.capitalized) : title,
                             initialAccent: color, onSave: { _ in })
        }
    }

    private func input(_ label: String, placeholder: String, text: Binding<String>, multiline: Bool = false, hint: String? = nil) -> some View {
        VStack(alignment: .leading, spacing: 7) {
            NBEyebrow(text: label)
            TextField(placeholder, text: text, axis: multiline ? .vertical : .horizontal)
                .lineLimit(multiline ? 2...4 : 1...1)
                .textInputAutocapitalization(multiline ? .sentences : .never)
                .autocorrectionDisabled(multiline == false)
                .font(.nbBody(14))
                .foregroundStyle(NB.text)
                .padding(12)
                .background(RoundedRectangle(cornerRadius: 11).fill(NB.surface))
                .overlay(RoundedRectangle(cornerRadius: 11).strokeBorder(NB.line, lineWidth: 1))
            if let hint { Text(hint).font(.nbBody(9.5)).foregroundStyle(NB.tertiary) }
        }
    }
}
