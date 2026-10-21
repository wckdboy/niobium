import SwiftUI
import PhotosUI

struct ChatView: View {
    @EnvironmentObject private var store: AppStore
    let bot: BotProfile

    @State private var draft = ""
    @State private var photoItem: PhotosPickerItem?
    @State private var attachment: UIImage?
    @State private var showTools = false
    @State private var showRoutines = false
    @State private var showInfo = false
    @State private var showClarify = false
    @State private var appeared = false
    @FocusState private var inputFocused: Bool
    @StateObject private var speech = SpeechTranscriber()
    @AppStorage("niobium.showReasoning") private var showReasoning = true
    @Namespace private var bottomAnchor

    var body: some View {
        ZStack {
            NB.void.ignoresSafeArea()
            VStack(spacing: 0) {
                header
                Divider().overlay(NB.line)
                messageScroll
                composer
            }
            if store.isLoadingChat {
                loadingOverlay
            }
        }
        .preferredColorScheme(.dark)
        .task {
            if store.activeBot?.name != bot.name || store.chatSessionID == nil {
                store.openBot(bot)
            }
            withAnimation(.easeOut(duration: 0.45)) { appeared = true }
        }
        .onDisappear { speech.stop() }
        .sheet(isPresented: $showTools) {
            ChatToolsSheet(bot: bot).environmentObject(store)
        }
        .sheet(isPresented: $showRoutines) {
            RoutinesView(bot: bot).environmentObject(store)
        }
        .sheet(isPresented: $showInfo) {
            BotInfoSheet(bot: bot).environmentObject(store)
        }
        .sheet(isPresented: $showClarify) {
            if let request = store.clarifyRequest {
                ClarifySheet(request: request) { answers in
                    store.resolveClarify(answers)
                    showClarify = false
                } onCancel: {
                    store.resolveClarify([:])
                    showClarify = false
                }
            }
        }
        .onChange(of: store.clarifyRequest) { _, new in
            if new != nil { showClarify = true }
        }
        .onChange(of: photoItem) { _, item in
            guard let item else { return }
            Task {
                if let data = try? await item.loadTransferable(type: Data.self),
                   let image = UIImage(data: data) {
                    await MainActor.run { attachment = image }
                }
            }
        }
        .onChange(of: speech.transcript) { _, value in
            if !value.isEmpty {
                draft = value
            }
        }
    }

    // MARK: Header

    private var header: some View {
        HStack(spacing: 12) {
            Button {
                store.closeBot()
            } label: {
                Image(systemName: "chevron.left")
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(NB.secondary)
                    .frame(width: 34, height: 34)
                    .background(Circle().fill(.white.opacity(0.05)))
            }
            .buttonStyle(.plain)

            BotAvatar(bot: bot, size: 36, showStatus: false)
            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 6) {
                    Text(bot.title).font(.nbTitle(16)).foregroundStyle(NB.text)
                    if store.isDemo { NBPill(text: "demo", tint: NB.violet) }
                }
                HStack(spacing: 5) {
                    Circle().fill(store.isTurnActive ? NB.mint : NB.tertiary).frame(width: 4.5, height: 4.5)
                    Text(store.isTurnActive ? "Working" : (bot.model.isEmpty ? "Ready" : bot.model))
                        .font(.nbMono(8.5))
                        .foregroundStyle(NB.tertiary)
                        .lineLimit(1)
                }
            }
            Spacer()
            Menu {
                Button { showTools = true } label: { Label("Tools & capabilities", systemImage: "hammer") }
                Button { showRoutines = true } label: { Label("Routines", systemImage: "clock.arrow.circlepath") }
                Button { showInfo = true } label: { Label("Pixel details", systemImage: "info.circle") }
                Divider()
                Button {
                    UIPasteboard.general.string = "\(bot.title) · \(bot.name)\n\(bot.role)"
                } label: { Label("Copy pixel details", systemImage: "doc.on.doc") }
                if !store.isDemo {
                    Button {
                        Task { await store.refreshAll() }
                    } label: { Label("Refresh from gateway", systemImage: "arrow.clockwise") }
                }
            } label: {
                Image(systemName: "ellipsis")
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(NB.secondary)
                    .frame(width: 34, height: 34)
                    .background(Circle().fill(.white.opacity(0.05)))
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 10)
        .background(
            ZStack {
                Rectangle().fill(NB.ink.opacity(0.9))
                LinearGradient(colors: [bot.accent.opacity(0.07), .clear], startPoint: .top, endPoint: .bottom)
            }
            .ignoresSafeArea(edges: .top)
        )
    }

    // MARK: Messages

    private var messageScroll: some View {
        ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(spacing: 16) {
                    if store.messages.isEmpty && !store.isLoadingChat {
                        chatPreamble
                    }
                    ForEach(store.messages) { message in
                        messageRow(message)
                            .id(message.id)
                    }
                    if store.isTurnActive {
                        HStack(spacing: 8) {
                            Circle().fill(bot.accent).frame(width: 5, height: 5)
                            Text(store.turnStatus.isEmpty ? "\(bot.title) is thinking" : store.turnStatus)
                                .font(.nbMono(9))
                                .foregroundStyle(NB.tertiary)
                            TypingIndicator()
                            Spacer()
                        }
                        .padding(.leading, 4)
                        .id("typing")
                    }
                    if store.messages.isEmpty && !store.isLoadingChat {
                        starterPrompts
                    }
                    Color.clear.frame(height: 2).id("bottom")
                }
                .padding(.horizontal, 16)
                .padding(.top, 18)
                .padding(.bottom, 24)
            }
            .scrollDismissesKeyboard(.interactively)
            .scrollIndicators(.hidden)
            .onChange(of: store.messages.count) { _, _ in scroll(proxy, animated: true) }
            .onChange(of: store.messages.last?.text) { _, _ in scroll(proxy, animated: false) }
            .onChange(of: store.isTurnActive) { _, active in
                if active { scroll(proxy, animated: true) }
            }
            .onChange(of: inputFocused) { _, focused in
                if focused { scroll(proxy, animated: true) }
            }
            .onAppear { scroll(proxy, animated: false) }
        }
    }

    private func scroll(_ proxy: ScrollViewProxy, animated: Bool) {
        let action = { proxy.scrollTo("bottom", anchor: .bottom) }
        if animated { withAnimation(.easeOut(duration: 0.25)) { action() } } else { action() }
    }

    private var chatPreamble: some View {
        VStack(spacing: 10) {
            BotAvatar(bot: bot, size: 60, showStatus: false)
            Text(bot.title).font(.nbTitle(20)).foregroundStyle(NB.text)
            Text(bot.role.isEmpty ? "A Hermes pixel" : bot.role)
                .font(.nbBody(12.5))
                .foregroundStyle(NB.secondary)
                .multilineTextAlignment(.center)
                .frame(maxWidth: 300)
        }
        .frame(maxWidth: .infinity)
        .padding(.top, 14)
        .padding(.bottom, 14)
    }

    @ViewBuilder
    private func messageRow(_ message: ChatMessage) -> some View {
        switch message.role {
        case .user:
            HStack {
                Spacer(minLength: 48)
                Text(message.text)
                    .font(.nbBody(15))
                    .foregroundStyle(.white)
                    .lineSpacing(3)
                    .textSelection(.enabled)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 11)
                    .background(
                        RoundedRectangle(cornerRadius: 18, style: .continuous)
                            .fill(
                                LinearGradient(colors: [NB.ion, NB.ionDeep],
                                               startPoint: .topLeading, endPoint: .bottomTrailing)
                            )
                    )
            }
        case .assistant:
            VStack(alignment: .leading, spacing: 9) {
                if showReasoning, let reasoning = message.reasoning, !reasoning.isEmpty {
                    ThinkingBlock(text: reasoning)
                }
                if message.text.isEmpty && message.isStreaming {
                    TypingIndicator()
                        .padding(.vertical, 6)
                } else {
                    MarkdownText(text: message.text)
                    if message.isStreaming {
                        Rectangle()
                            .fill(bot.accent)
                            .frame(width: 7, height: 15)
                            .opacity(0.8)
                            .padding(.top, -2)
                    }
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.trailing, 20)
        case .tool:
            ToolCallCard(message: message)
                .padding(.leading, 2)
        case .system:
            HStack(spacing: 8) {
                Rectangle().fill(NB.line).frame(height: 1)
                Text(message.text)
                    .font(.nbMono(8.5))
                    .foregroundStyle(NB.tertiary)
                    .lineLimit(2)
                    .fixedSize(horizontal: false, vertical: true)
                Rectangle().fill(NB.line).frame(height: 1)
            }
        }
    }

    private var starterPrompts: some View {
        VStack(alignment: .leading, spacing: 12) {
            NBEyebrow(text: "Start a thread")
            ForEach(starterList, id: \.self) { prompt in
                Button {
                    Task { await store.send(prompt) }
                } label: {
                    HStack {
                        Text(prompt)
                            .font(.nbBody(13.5))
                            .foregroundStyle(NB.secondary)
                            .multilineTextAlignment(.leading)
                        Spacer()
                        Image(systemName: "arrow.up.right")
                            .font(.system(size: 10, weight: .semibold))
                            .foregroundStyle(NB.ion.opacity(0.8))
                    }
                    .padding(.vertical, 11)
                    .padding(.horizontal, 13)
                    .background(RoundedRectangle(cornerRadius: 13).fill(Color.white.opacity(0.025)))
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.top, 2)
    }

    private var starterList: [String] {
        switch bot.name {
        case "athena": return ["What needs me today?", "Research the competitive landscape", "Turn this into a decision memo"]
        case "forge": return ["Run a clean build and report blockers", "Review the working tree", "Ship the release checklist"]
        case "scout": return ["Anything I should know from the overnight sweep?", "Watch our competitors", "Summarize the web"]
        default: return ["What can you do with your current tools?", "Give me a status update", "Start a research thread"]
        }
    }

    // MARK: Composer

    private var composer: some View {
        VStack(spacing: 0) {
            if let approval = store.approvals.first {
                ApprovalCard(approval: approval) { choice in
                    store.resolveApproval(approval, choice: choice)
                }
                .padding(.horizontal, 12)
                .padding(.top, 10)
                .transition(.move(edge: .bottom).combined(with: .opacity))
            }

            if let attachment {
                HStack(spacing: 10) {
                    Image(uiImage: attachment)
                        .resizable().scaledToFill()
                        .frame(width: 48, height: 48)
                        .clipShape(RoundedRectangle(cornerRadius: 9))
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Image attached").font(.nbBody(11.5, weight: .medium)).foregroundStyle(NB.text)
                        Text("Sent with your next message").font(.nbMono(8.5)).foregroundStyle(NB.tertiary)
                    }
                    Spacer()
                    Button { self.attachment = nil } label: {
                        Image(systemName: "xmark.circle.fill").foregroundStyle(NB.tertiary)
                    }
                }
                .padding(10)
                .background(RoundedRectangle(cornerRadius: 13).fill(NB.surface))
                .padding(.horizontal, 12)
                .padding(.top, 8)
            }

            VStack(spacing: 9) {
                HStack(spacing: 10) {
                    Menu {
                        PhotosPicker(selection: $photoItem, matching: .images) {
                            Label("Photo library", systemImage: "photo")
                        }
                        Button {
                            UIPasteboard.general.string = nil
                            toastPaste()
                        } label: { Label("Paste from clipboard", systemImage: "doc.on.clipboard") }
                    } label: {
                        Image(systemName: "plus")
                            .font(.system(size: 16, weight: .medium))
                            .foregroundStyle(NB.secondary)
                            .frame(width: 34, height: 34)
                            .background(Circle().fill(.white.opacity(0.055)))
                            .overlay(Circle().strokeBorder(NB.line, lineWidth: 1))
                    }

                    TextField("Message \(bot.title)…", text: $draft, axis: .vertical)
                        .lineLimit(1...5)
                        .font(.nbBody(15))
                        .foregroundStyle(NB.text)
                        .focused($inputFocused)
                        .padding(.vertical, 8)

                    if speech.isRecording {
                        HStack(spacing: 3) {
                            ForEach(0..<4) { i in
                                Capsule().fill(NB.rose.opacity(0.7))
                                    .frame(width: 2, height: CGFloat(6 + (i % 2) * 7))
                            }
                        }
                        .transition(.opacity)
                    }

                    Button {
                        if speech.isRecording {
                            speech.stop()
                        } else {
                            speech.start()
                        }
                    } label: {
                        Image(systemName: speech.isRecording ? "waveform" : "mic")
                            .font(.system(size: 14, weight: .medium))
                            .foregroundStyle(speech.isRecording ? NB.rose : NB.tertiary)
                            .frame(width: 30, height: 34)
                    }
                    .buttonStyle(.plain)

                    if store.isTurnActive {
                        Button {
                            store.stopTurn()
                        } label: {
                            ZStack {
                                Circle().fill(NB.text).frame(width: 33, height: 33)
                                RoundedRectangle(cornerRadius: 2.5).fill(NB.void).frame(width: 10, height: 10)
                            }
                        }
                        .buttonStyle(.plain)
                    } else {
                        Button {
                            let text = draft
                            let image = attachment
                            draft = ""
                            attachment = nil
                            photoItem = nil
                            speech.stop()
                            Task { await store.send(text, attachment: image) }
                        } label: {
                            ZStack {
                                Circle()
                                    .fill(canSend ? NB.ion : Color.white.opacity(0.07))
                                    .frame(width: 33, height: 33)
                                Image(systemName: "arrow.up")
                                    .font(.system(size: 14, weight: .bold))
                                    .foregroundStyle(canSend ? Color(hex: 0x090C13) : NB.tertiary)
                            }
                        }
                        .buttonStyle(.plain)
                        .disabled(!canSend)
                    }
                }
            }
            .padding(.horizontal, 10)
            .padding(.top, 8)
            .padding(.bottom, 9)
            .background(
                RoundedRectangle(cornerRadius: 22, style: .continuous)
                    .fill(NB.raised.opacity(0.94))
                    .overlay(
                        RoundedRectangle(cornerRadius: 22, style: .continuous)
                            .strokeBorder(inputFocused ? NB.ion.opacity(0.35) : NB.line, lineWidth: 1)
                    )
                    .shadow(color: .black.opacity(0.3), radius: 18, y: 8)
            )
            .padding(.horizontal, 12)
            .padding(.bottom, 8)
            .padding(.top, 8)
        }
        .background(
            LinearGradient(colors: [.clear, NB.void.opacity(0.92)], startPoint: .top, endPoint: .bottom)
                .ignoresSafeArea(edges: .bottom)
        )
        .animation(.spring(response: 0.35, dampingFraction: 0.86), value: store.approvals.count)
        .animation(.spring(response: 0.3, dampingFraction: 0.9), value: attachment)
    }

    private var canSend: Bool {
        !draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || attachment != nil
    }

    private func toastPaste() {
        if let image = UIPasteboard.general.image {
            attachment = image
        } else {
            store.toastMessage("No image on the clipboard")
        }
    }

    private var loadingOverlay: some View {
        ZStack {
            NB.void.opacity(0.72).ignoresSafeArea()
            VStack(spacing: 12) {
                AtomicMark(size: 52, animated: true)
                Text("Attaching to \(bot.title)…")
                    .font(.nbMono(10))
                    .foregroundStyle(NB.secondary)
            }
        }
        .transition(.opacity)
    }
}

struct ApprovalCard: View {
    var approval: ApprovalRequest
    var resolve: (String) -> Void
    @State private var showDetails = false

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 7) {
                Image(systemName: "exclamationmark.shield.fill")
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundStyle(NB.amber)
                Text("APPROVAL REQUIRED")
                    .font(.nbMono(8, weight: .semibold)).tracking(1.4)
                    .foregroundStyle(NB.amber)
                Spacer()
                Text(approval.toolName)
                    .font(.nbMono(8))
                    .foregroundStyle(NB.tertiary)
            }
            Text(approval.command.isEmpty ? approval.description : approval.command)
                .font(.nbMono(11.5))
                .foregroundStyle(NB.text)
                .lineLimit(showDetails ? 8 : 3)
                .padding(10)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(RoundedRectangle(cornerRadius: 9).fill(.black.opacity(0.3)))
                .onTapGesture { withAnimation { showDetails.toggle() } }
            if !approval.description.isEmpty && approval.description != approval.command {
                Text(approval.description)
                    .font(.nbBody(11))
                    .foregroundStyle(NB.secondary)
                    .lineLimit(2)
            }
            HStack(spacing: 7) {
                if approval.choices.contains("once") {
                    Button("Allow once") { resolve("once") }
                        .buttonStyle(NBPrimaryButtonStyle(tint: NB.amber, compact: true))
                }
                if approval.choices.contains("session") {
                    Button("Session") { resolve("session") }
                        .buttonStyle(NBQuietButtonStyle(compact: true))
                }
                if approval.choices.contains("always") {
                    Menu {
                        Button("Always allow") { resolve("always") }
                    } label: {
                        Text("Always")
                            .font(.nbBody(13, weight: .medium))
                            .foregroundStyle(NB.text)
                            .padding(.horizontal, 13)
                            .frame(height: 38)
                            .background(Capsule().fill(.white.opacity(0.055)))
                            .overlay(Capsule().strokeBorder(NB.line, lineWidth: 1))
                    }
                }
                Spacer()
                Button("Deny") { resolve("deny") }
                    .font(.nbBody(13, weight: .medium))
                    .foregroundStyle(NB.rose)
                    .padding(.horizontal, 8)
            }
        }
        .padding(14)
        .background(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .fill(NB.raised)
                .overlay(
                    RoundedRectangle(cornerRadius: 18, style: .continuous)
                        .strokeBorder(NB.amber.opacity(0.3), lineWidth: 1)
                )
                .shadow(color: .black.opacity(0.35), radius: 20, y: 8)
        )
    }
}

struct ClarifySheet: View {
    let request: ClarifyRequest
    var onSubmit: ([String: String]) -> Void
    var onCancel: () -> Void
    @State private var answers: [String: String] = [:]
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ZStack {
                NBMeshBackground(intensity: 0.5)
                ScrollView {
                    VStack(alignment: .leading, spacing: 20) {
                        ForEach(request.questions) { question in
                            VStack(alignment: .leading, spacing: 10) {
                                Text(question.question)
                                    .font(.nbBody(15, weight: .medium))
                                    .foregroundStyle(NB.text)
                                if question.choices.isEmpty {
                                    TextField("Your answer", text: Binding(
                                        get: { answers[question.id] ?? "" },
                                        set: { answers[question.id] = $0 }
                                    ), axis: .vertical)
                                    .font(.nbBody(14))
                                    .padding(12)
                                    .background(RoundedRectangle(cornerRadius: 12).fill(NB.surface))
                                } else {
                                    ForEach(question.choices, id: \.self) { choice in
                                        Button {
                                            answers[question.id] = choice
                                        } label: {
                                            HStack {
                                                Text(choice).font(.nbBody(13.5)).foregroundStyle(NB.text)
                                                Spacer()
                                                Image(systemName: answers[question.id] == choice ? "checkmark.circle.fill" : "circle")
                                                    .foregroundStyle(answers[question.id] == choice ? NB.ion : NB.tertiary)
                                            }
                                            .padding(12)
                                            .background(RoundedRectangle(cornerRadius: 12).fill(NB.surface))
                                            .overlay(RoundedRectangle(cornerRadius: 12).strokeBorder(
                                                answers[question.id] == choice ? NB.ion.opacity(0.4) : NB.line, lineWidth: 1))
                                        }
                                        .buttonStyle(.plain)
                                    }
                                }
                            }
                        }
                    }
                    .padding(20)
                }
            }
            .navigationTitle("A question")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { onCancel(); dismiss() }
                        .foregroundStyle(NB.secondary)
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Send") { onSubmit(answers); dismiss() }
                        .foregroundStyle(NB.ion)
                        .disabled(answers.count < request.questions.count)
                }
            }
            .toolbarBackground(NB.ink, for: .navigationBar)
        }
        .presentationDetents([.medium, .large])
        .presentationBackground(NB.ink)
        .preferredColorScheme(.dark)
    }
}
