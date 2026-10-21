import SwiftUI

struct AIBuildView: View {
    @EnvironmentObject private var store: AppStore
    @Environment(\.dismiss) private var dismiss
    var onBuilt: (BotProfile) -> Void
    @State private var prompt = ""
    @State private var error: String?
    @State private var working = false

    private let ideas = [
        "A night-shift security pixel for my VPS logs, with a bat",
        "A creative director who makes image prompts and moodboards",
        "A release engineer who runs builds and asks before deploys",
        "A research partner who reads papers and keeps notes"
    ]

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    VStack(alignment: .leading, spacing: 6) {
                        Text("Build a Pixel").font(.nbDisplay(30)).foregroundStyle(NB.text)
                        Text("One sentence. The bot-build skill writes the personality, permissions, toolsets, connectors, character and companion.")
                            .font(.nbBody(12)).foregroundStyle(NB.secondary).lineSpacing(2)
                    }
                    TextEditor(text: $prompt)
                        .font(.nbBody(14)).foregroundStyle(NB.text)
                        .scrollContentBackground(.hidden)
                        .frame(minHeight: 120)
                        .padding(12)
                        .background(RoundedRectangle(cornerRadius: 14).fill(NB.surface))
                        .overlay(RoundedRectangle(cornerRadius: 14).strokeBorder(NB.line, lineWidth: 1))
                    FlowChips(items: ideas) { prompt = $0 }
                    if let error { Text(error).font(.nbBody(11)).foregroundStyle(NB.rose) }
                    Button {
                        build()
                    } label: {
                        HStack(spacing: 8) {
                            if working { ProgressView().tint(NB.onIon).scaleEffect(0.8) }
                            Text(working ? "Designing…" : "Build with AI").frame(maxWidth: .infinity)
                        }
                    }
                    .buttonStyle(NBPrimaryButtonStyle())
                    .disabled(working || prompt.trimmingCharacters(in: .whitespaces).isEmpty)
                    Text("If the gateway has no model configured, Niobium uses a local template and labels it. Nothing is created until you confirm.")
                        .font(.nbBody(9.5)).foregroundStyle(NB.tertiary).lineSpacing(2)
                }
                .padding(20)
            }
            .background(NBMeshBackground(intensity: 0.5))
            .navigationTitle("AI builder")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }.foregroundStyle(NB.secondary)
                }
            }
        }
        .interactiveDismissDisabled(working)
    }

    private func build() {
        working = true; error = nil
        Task {
            do {
                let bot = try await store.buildPixel(prompt: prompt)
                onBuilt(bot)
                dismiss()
            } catch {
                self.error = error.localizedDescription
                working = false
            }
        }
    }
}

private struct FlowChips: View {
    var items: [String]
    var onTap: (String) -> Void
    var body: some View {
        VStack(alignment: .leading, spacing: 7) {
            Text("TRY").font(.nbLabel(9)).tracking(1.5).foregroundStyle(NB.tertiary)
            ForEach(items, id: \.self) { item in
                Button { onTap(item) } label: {
                    HStack(spacing: 6) {
                        Text(item).font(.nbBody(10.5)).foregroundStyle(NB.secondary).multilineTextAlignment(.leading)
                        Spacer()
                        Image(systemName: "arrow.up.right").font(.system(size: 8)).foregroundStyle(NB.ion)
                    }
                    .padding(10)
                    .background(RoundedRectangle(cornerRadius: 10).fill(NB.fillSoft))
                }
                .buttonStyle(.plain)
            }
        }
    }
}
