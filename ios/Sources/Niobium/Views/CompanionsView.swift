import SwiftUI

struct CompanionTask: Identifiable, Equatable {
    var id: String
    var goal: String
    var status: String
    var lastTool: String
    var toolCount: Int
    var canSteer: Bool
    var failed: Bool
    static func parse(_ json: JSONObject) -> CompanionTask? {
        guard let id = json.string("subagent_id") else { return nil }
        return .init(id: id, goal: json.string("goal") ?? "Delegated task",
                     status: json.string("status") ?? "working", lastTool: json.string("last_tool") ?? "",
                     toolCount: json.int("tool_count") ?? 0, canSteer: json.bool("accepting_steer") ?? false, failed: false)
    }
}

struct CompanionsView: View {
    @EnvironmentObject private var store: AppStore
    @Environment(\.dismiss) private var dismiss
    let bot: BotProfile
    @State private var task = ""
    @State private var selected: CompanionTask?
    @State private var transcript = ""
    @State private var steer = ""
    @State private var actionError: String?
    @State private var working = false

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    let member = CastStore.shared.member(for: bot.name, title: bot.title)
                    HStack(spacing: 14) {
                        Image("Comp_\(member.companion)").resizable().interpolation(.none).frame(width: 60, height: 60)
                        VStack(alignment: .leading, spacing: 4) {
                            Text("\(member.companionName) & friends").font(.nbTitle(23)).foregroundStyle(NB.text)
                            Text("Small creatures. Proper teamwork.").font(.nbBody(12)).foregroundStyle(NB.secondary)
                        }
                    }
                    Text("Your Pixel delegates bounded tasks through Hermes. Follow the real work here, send a correction, or stop a helper.")
                        .font(.nbBody(12)).foregroundStyle(NB.secondary)
                    TextField("A task to split with companions…", text: $task, axis: .vertical)
                        .lineLimit(2...5).font(.nbBody(14)).padding(12)
                        .background(RoundedRectangle(cornerRadius: 12).fill(NB.surface))
                    Button {
                        let text = task.trimmingCharacters(in: .whitespacesAndNewlines)
                        guard !text.isEmpty else { return }
                        dismiss()
                        Task { await store.send("Work on this task: \(text)\nIf it benefits from delegation, use delegate_task to give your companions bounded subtasks with concrete outputs and the same or narrower permissions. Integrate and verify their results. If delegation is unavailable or unnecessary, explain briefly and do the work yourself.") }
                    } label: { Label("Ask your Pixel", systemImage: "sparkles").frame(maxWidth: .infinity) }
                        .buttonStyle(NBPrimaryButtonStyle()).disabled(task.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || store.isLoadingChat)
                    NBSectionHeader(title: "Companion work", detail: "\(store.companionTasks.count) tasks")
                    if store.isDemo { NBPill(text: "Demo · live work appears on a connected gateway", tint: NB.ion) }
                    if let error = store.companionError { Text(error).font(.nbBody(12)).foregroundStyle(NB.rose) }
                    if store.companionTasks.isEmpty && store.companionError == nil {
                        Text(store.companionsLoading ? "Checking the gateway…" : "No delegated tasks in this chat. Helpers appear when your Pixel calls them.")
                            .font(.nbBody(12)).foregroundStyle(NB.tertiary).padding(.vertical, 16)
                    }
                    ForEach(store.companionTasks) { child in
                        Button {
                            selected = child; transcript = ""; steer = ""; actionError = nil
                            loadTranscript(child)
                        } label: {
                            HStack(spacing: 12) {
                                Image("Comp_\(member.companion)").resizable().interpolation(.none).frame(width: 38, height: 38)
                                VStack(alignment: .leading, spacing: 4) {
                                    Text(child.goal).font(.nbBody(13, weight: .semibold)).foregroundStyle(NB.text).lineLimit(3)
                                    Text("\(child.status) · \(child.toolCount) tools\(child.lastTool.isEmpty ? "" : " · \(child.lastTool)")")
                                        .font(.nbMono(9)).foregroundStyle(child.failed ? NB.rose : NB.secondary)
                                }
                                Spacer()
                                Image(systemName: "chevron.right").font(.system(size: 11)).foregroundStyle(NB.tertiary)
                            }.padding(14).background(RoundedRectangle(cornerRadius: 14).fill(NB.surface))
                        }.buttonStyle(.plain)
                    }
                }.padding(20)
            }
            .background(NB.void).navigationTitle("Companions").navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } } }
            .task {
                while !Task.isCancelled {
                    await store.loadCompanions()
                    do { try await Task.sleep(for: .seconds(3)) } catch { break }
                }
            }
            .sheet(item: $selected) { child in
                NavigationStack {
                    ScrollView {
                        VStack(alignment: .leading, spacing: 16) {
                            Text(child.goal).font(.nbTitle(18)).foregroundStyle(NB.text)
                            Text(child.status).font(.nbMono(11)).foregroundStyle(NB.secondary)
                            Text(transcript.isEmpty ? "No live transcript is available yet." : transcript)
                                .font(.nbMono(11)).foregroundStyle(NB.secondary).textSelection(.enabled)
                            if !child.failed {
                                TextField("A correction for this helper…", text: $steer, axis: .vertical)
                                    .font(.nbBody(13)).padding(12).background(NB.fillSoft)
                                Button("Send correction") { control(child, action: "steer") }
                                    .buttonStyle(NBPrimaryButtonStyle()).disabled(working || !child.canSteer || steer.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                                Button("Stop helper", role: .destructive) { control(child, action: "interrupt") }
                                    .buttonStyle(NBQuietButtonStyle()).disabled(working)
                                Button("Refresh transcript") { loadTranscript(child) }.disabled(working)
                            }
                            if let actionError { Text(actionError).font(.nbBody(12)).foregroundStyle(NB.rose) }
                        }.padding(20)
                    }.background(NB.void).navigationTitle("Helper task").navigationBarTitleDisplayMode(.inline)
                        .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { selected = nil } } }
                }
            }
        }
    }
    private func loadTranscript(_ child: CompanionTask) {
        guard !child.failed else { transcript = child.lastTool; return }
        working = true
        Task {
            do { transcript = try await store.companionTranscript(child.id) }
            catch { actionError = error.localizedDescription }
            working = false
        }
    }
    private func control(_ child: CompanionTask, action: String) {
        working = true; actionError = nil
        Task {
            do { try await store.controlCompanion(child.id, action: action, text: steer); if action == "steer" { steer = "" } }
            catch { actionError = error.localizedDescription }
            working = false
        }
    }
}
