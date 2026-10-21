import SwiftUI

struct RoomsView: View {
    @EnvironmentObject private var store: AppStore
    @Binding var newGroup: Bool
    @State private var appeared = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                HStack {
                    Wordmark()
                    Spacer()
                    NBIconButton(symbol: "plus", size: 38, tint: NB.ion) { newGroup = true }
                }
                .padding(.horizontal, 20)
                .padding(.top, 6)

                VStack(alignment: .leading, spacing: 7) {
                    Text("Rooms")
                        .font(.nbDisplay(40))
                        .tracking(-1.5)
                        .foregroundStyle(NB.text)
                    Text("A few pixels, thinking together.")
                        .font(.nbBody(14))
                        .foregroundStyle(NB.secondary)
                }
                .padding(.horizontal, 20)
                .padding(.top, 28)

                if store.groups.isEmpty {
                    EmptyStateView(icon: "person.2",
                                   title: "No rooms yet",
                                   detail: "Put two to six pixels in a room and let them work a problem together.",
                                   actionTitle: "Create a room") { newGroup = true }
                        .padding(.horizontal, 20)
                        .padding(.top, 10)
                } else {
                    LazyVStack(spacing: 7) {
                        ForEach(Array(store.groups.enumerated()), id: \.element.id) { index, room in
                            GroupRow(room: room) { store.openGroup(room) }
                                .opacity(appeared ? 1 : 0)
                                .offset(y: appeared ? 0 : 8)
                                .animation(.easeOut(duration: 0.34).delay(Double(index) * 0.04), value: appeared)
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.top, 24)
                }

                Button {
                    newGroup = true
                } label: {
                    HStack(spacing: 10) {
                        Image(systemName: "plus").font(.system(size: 13, weight: .semibold)).foregroundStyle(NB.ion)
                        Text("New room").font(.nbBody(14, weight: .medium)).foregroundStyle(NB.text)
                        Spacer()
                        Text("2–6 pixels").font(.nbBody(11)).foregroundStyle(NB.tertiary)
                    }
                    .padding(15)
                    .background(RoundedRectangle(cornerRadius: 18).fill(Color.white.opacity(0.028)))
                    .overlay(RoundedRectangle(cornerRadius: 18).strokeBorder(NB.ion.opacity(0.16), lineWidth: 1))
                }
                .buttonStyle(.plain)
                .padding(.horizontal, 16)
                .padding(.top, 16)

                Text("Rooms run on your gateway, not the phone. They keep thinking when the app is closed.")
                    .font(.nbBody(11))
                    .foregroundStyle(NB.tertiary)
                    .lineSpacing(2)
                    .padding(.horizontal, 22)
                    .padding(.top, 20)
                    .padding(.bottom, 34)
            }
        }
        .scrollIndicators(.hidden)
        .refreshable { await store.loadGroups() }
        .onAppear {
            withAnimation(.easeOut(duration: 0.45)) { appeared = true }
            Task { await store.loadGroups() }
        }
    }
}

struct GroupChatView: View {
    @EnvironmentObject private var store: AppStore
    let room: GroupRoom
    @State private var draft = ""
    @FocusState private var focused: Bool

    private var members: [String: GroupMember] {
        Dictionary(uniqueKeysWithValues: room.members.map { ($0.profile, $0) })
    }

    var body: some View {
        ZStack {
            NB.void.ignoresSafeArea()
            VStack(spacing: 0) {
                header
                NBDivider()
                transcript
                composer
            }
        }
        .preferredColorScheme(.dark)
        .task {
            if store.activeGroup?.roomID != room.roomID {
                store.openGroup(room)
            }
        }
    }

    private var header: some View {
        HStack(spacing: 11) {
            Button { store.closeGroup() } label: {
                Image(systemName: "chevron.left")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(NB.secondary)
                    .frame(width: 34, height: 34)
                    .background(Circle().fill(.white.opacity(0.05)))
            }
            .buttonStyle(.plain)
            GroupAvatar(members: room.members, size: 38)
            VStack(alignment: .leading, spacing: 2) {
                Text(room.name).font(.nbTitle(16)).foregroundStyle(NB.text)
                Text("\(room.members.count) pixels · room")
                    .font(.nbBody(10.5)).foregroundStyle(NB.tertiary)
            }
            Spacer()
            Button { Task { await store.refreshGroup() } } label: {
                Image(systemName: "arrow.clockwise")
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(NB.secondary)
                    .frame(width: 34, height: 34)
                    .background(Circle().fill(.white.opacity(0.05)))
            }
            .buttonStyle(.plain)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 10)
        .background(NB.ink.opacity(0.92).ignoresSafeArea(edges: .top))
    }

    private var transcript: some View {
        ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(spacing: 14) {
                    VStack(spacing: 9) {
                        GroupAvatar(members: room.members, size: 56)
                        Text(room.name).font(.nbTitle(19)).foregroundStyle(NB.text)
                        Text(room.members.map(\.name).joined(separator: " · "))
                            .font(.nbBody(10.5))
                            .foregroundStyle(NB.tertiary)
                    }
                    .padding(.top, 8)
                    .padding(.bottom, 12)

                    ForEach(store.roomEvents) { event in
                        if event.isMessage {
                            eventRow(event)
                        } else if event.isNotice {
                            HStack(spacing: 8) {
                                Rectangle().fill(NB.line).frame(height: 1)
                                Text(event.reason ?? event.kind.replacingOccurrences(of: "_", with: " "))
                                    .font(.nbBody(10)).foregroundStyle(NB.tertiary)
                                Rectangle().fill(NB.line).frame(height: 1)
                            }
                        }
                    }
                    Color.clear.frame(height: 2).id("room-bottom")
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 18)
            }
            .scrollIndicators(.hidden)
            .scrollDismissesKeyboard(.interactively)
            .onChange(of: store.roomEvents.count) { _, _ in
                withAnimation(.easeOut(duration: 0.24)) { proxy.scrollTo("room-bottom", anchor: .bottom) }
            }
            .onAppear { proxy.scrollTo("room-bottom", anchor: .bottom) }
        }
    }

    private func eventRow(_ event: RoomEvent) -> some View {
        let isUser = event.actorKind == "user" || event.actorID == "user"
        let member = members[event.actorID]
        return HStack(alignment: .top, spacing: 9) {
            if isUser { Spacer(minLength: 52) }
            if !isUser {
                PixelTile(accent: NB.color(for: event.actorID), size: 26)
            }
            VStack(alignment: isUser ? .trailing : .leading, spacing: 4) {
                HStack(spacing: 6) {
                    Text(isUser ? "You" : (member?.name ?? event.displayName))
                        .font(.nbBody(10.5, weight: .medium))
                        .foregroundStyle(isUser ? NB.secondary : NB.color(for: event.actorID))
                    if let ts = event.timestamp {
                        Text(ts.clockTime).font(.nbBody(9)).foregroundStyle(NB.tertiary)
                    }
                }
                MarkdownText(text: event.text, tint: isUser ? Color(hex: 0x0A0712) : NB.text)
                    .padding(.horizontal, 13)
                    .padding(.vertical, 10)
                    .background(
                        RoundedRectangle(cornerRadius: 16, style: .continuous)
                            .fill(isUser
                                  ? AnyShapeStyle(LinearGradient(colors: [NB.ion, NB.ionDeep],
                                                                 startPoint: .topLeading, endPoint: .bottomTrailing))
                                  : AnyShapeStyle(Color.white.opacity(0.035)))
                    )
                    .overlay(
                        RoundedRectangle(cornerRadius: 16, style: .continuous)
                            .strokeBorder(isUser ? .clear : NB.line, lineWidth: 1)
                    )
            }
            if !isUser { Spacer(minLength: 36) }
        }
    }

    private var composer: some View {
        HStack(spacing: 9) {
            TextField("Message the room…", text: $draft, axis: .vertical)
                .lineLimit(1...5)
                .font(.nbBody(15))
                .foregroundStyle(NB.text)
                .focused($focused)
                .padding(.vertical, 8)
            Button {
                let text = draft
                draft = ""
                Task { await store.sendGroup(text) }
            } label: {
                ZStack {
                    Circle()
                        .fill(draft.trimmingCharacters(in: .whitespaces).isEmpty ? Color.white.opacity(0.06) : NB.ion)
                        .frame(width: 34, height: 34)
                    Image(systemName: "arrow.up")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundStyle(draft.isEmpty ? NB.tertiary : .white)
                }
            }
            .buttonStyle(.plain)
            .disabled(draft.trimmingCharacters(in: .whitespaces).isEmpty)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .background(
            RoundedRectangle(cornerRadius: 22, style: .continuous)
                .fill(NB.raised.opacity(0.96))
                .overlay(RoundedRectangle(cornerRadius: 22).strokeBorder(focused ? NB.ion.opacity(0.35) : NB.line, lineWidth: 1))
        )
        .padding(.horizontal, 12)
        .padding(.bottom, 8)
    }
}

struct NewGroupView: View {
    @EnvironmentObject private var store: AppStore
    @Environment(\.dismiss) private var dismiss
    @State private var name = ""
    @State private var selected: Set<String> = []
    @State private var saving = false
    @State private var error: String?

    var body: some View {
        NavigationStack {
            ZStack {
                NBMeshBackground(intensity: 0.55)
                ScrollView {
                    VStack(alignment: .leading, spacing: 18) {
                        VStack(alignment: .leading, spacing: 8) {
                            Text("Room name")
                                .font(.nbBody(11, weight: .medium)).foregroundStyle(NB.tertiary)
                            TextField("Launch Room", text: $name)
                                .font(.nbTitle(18))
                                .foregroundStyle(NB.text)
                                .padding(13)
                                .background(RoundedRectangle(cornerRadius: 13).fill(Color.white.opacity(0.035)))
                                .overlay(RoundedRectangle(cornerRadius: 13).strokeBorder(NB.line, lineWidth: 1))
                        }

                        HStack {
                            Text("Pixels")
                                .font(.nbBody(13, weight: .semibold)).foregroundStyle(NB.text)
                            Spacer()
                            Text("\(selected.count) / 6")
                                .font(.nbBody(11)).foregroundStyle(selected.count >= 2 ? NB.mint : NB.tertiary)
                        }

                        ForEach(store.bots) { pixel in
                            Button {
                                if selected.contains(pixel.name) {
                                    selected.remove(pixel.name)
                                } else if selected.count < 6 {
                                    selected.insert(pixel.name)
                                }
                            } label: {
                                HStack(spacing: 12) {
                                    BotAvatar(bot: pixel, size: 40, showStatus: false)
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(pixel.title).font(.nbBody(14, weight: .semibold)).foregroundStyle(NB.text)
                                        Text(pixel.subtitle).font(.nbBody(11)).foregroundStyle(NB.secondary).lineLimit(1)
                                    }
                                    Spacer()
                                    Image(systemName: selected.contains(pixel.name) ? "checkmark.circle.fill" : "circle")
                                        .foregroundStyle(selected.contains(pixel.name) ? NB.ion : NB.tertiary)
                                }
                                .padding(12)
                                .background(RoundedRectangle(cornerRadius: 14).fill(selected.contains(pixel.name) ? NB.ion.opacity(0.09) : Color.white.opacity(0.025)))
                                .overlay(RoundedRectangle(cornerRadius: 14).strokeBorder(selected.contains(pixel.name) ? NB.ion.opacity(0.25) : NB.line, lineWidth: 1))
                            }
                            .buttonStyle(.plain)
                        }

                        if store.bots.count < 2 {
                            Text("Create at least two pixels before opening a room.")
                                .font(.nbBody(12)).foregroundStyle(NB.amber)
                        }
                        if let error { Text(error).font(.nbBody(11)).foregroundStyle(NB.rose) }

                        Button {
                            saving = true; error = nil
                            Task {
                                do {
                                    try await store.createGroup(name: name, members: store.bots.filter { selected.contains($0.name) })
                                    dismiss()
                                } catch {
                                    self.error = error.localizedDescription
                                    saving = false
                                }
                            }
                        } label: {
                            HStack {
                                if saving { ProgressView().tint(.white).scaleEffect(0.8) }
                                Text(saving ? "Creating room…" : "Create room").frame(maxWidth: .infinity)
                            }
                        }
                        .buttonStyle(NBPrimaryButtonStyle(tint: NB.ion))
                        .disabled(name.trimmingCharacters(in: .whitespaces).isEmpty || selected.count < 2 || saving)
                        .padding(.top, 4)
                    }
                    .padding(20)
                }
            }
            .navigationTitle("New room")
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
        .preferredColorScheme(.dark)
    }
}
