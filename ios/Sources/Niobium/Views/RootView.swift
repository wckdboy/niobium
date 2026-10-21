import SwiftUI

struct RootView: View {
    @EnvironmentObject private var store: AppStore
    @State private var splash = !ProcessInfo.processInfo.arguments.contains("--no-splash")

    var body: some View {
        ZStack {
            Group {
                if store.needsConnection {
                    OnboardingView()
                        .transition(.opacity)
                } else {
                    MainShell()
                        .transition(.opacity)
                }
            }
            .animation(.easeInOut(duration: 0.3), value: store.needsConnection)

            if splash {
                SplashView()
                    .transition(.opacity)
                    .zIndex(20)
            }

            VStack {
                Spacer()
                if let error = store.errorBanner {
                    NBToast(text: error, isError: true)
                        .padding(.bottom, 8)
                        .transition(.move(edge: .bottom).combined(with: .opacity))
                } else if let toast = store.toast {
                    NBToast(text: toast)
                        .padding(.bottom, 8)
                        .transition(.move(edge: .bottom).combined(with: .opacity))
                }
            }
            .padding(.bottom, 76)
            .animation(.spring(response: 0.45, dampingFraction: 0.86), value: store.toast)
            .animation(.spring(response: 0.45, dampingFraction: 0.86), value: store.errorBanner)
            .zIndex(30)
        }
        .preferredColorScheme(.dark)
        .fullScreenCover(item: $store.activeBot, onDismiss: { store.closeBot() }) { bot in
            ChatView(bot: bot)
                .environmentObject(store)
        }
        .fullScreenCover(item: $store.activeGroup, onDismiss: { store.closeGroup() }) { room in
            GroupChatView(room: room)
                .environmentObject(store)
        }
        .onAppear {
            Task { @MainActor in
                try? await Task.sleep(for: .milliseconds(ProcessInfo.processInfo.arguments.contains("--no-splash") ? 50 : 820))
                withAnimation(.easeOut(duration: 0.45)) { splash = false }
            }
        }
    }
}

struct SplashView: View {
    @State private var bloom = false
    @State private var lift = false
    var body: some View {
        ZStack {
            NB.void
            RadialGradient(colors: [NB.ion.opacity(bloom ? 0.23 : 0.08), .clear],
                           center: .center, startRadius: 0, endRadius: 360)
            VStack(spacing: 20) {
                AtomicMark(size: 91, animated: true)
                    .scaleEffect(bloom ? 1 : 0.88)
                    .opacity(bloom ? 1 : 0)
                VStack(spacing: 6) {
                    Text("NIOBIUM")
                        .font(.system(size: 19, weight: .bold))
                        .tracking(8)
                        .foregroundStyle(NB.text)
                    Text("Nb · 41 · HERMES")
                        .font(.nbMono(8.5))
                        .tracking(2.4)
                        .foregroundStyle(NB.tertiary)
                }
                .offset(y: lift ? 0 : 8)
                .opacity(bloom ? 1 : 0)
            }
        }
        .onAppear {
            withAnimation(.easeOut(duration: 0.7)) { bloom = true }
            withAnimation(.easeOut(duration: 0.8).delay(0.15)) { lift = true }
        }
    }
}

enum AppTab: String, CaseIterable {
    case pixels, rooms, activity, settings
    var title: String {
        switch self {
        case .pixels: return "Pixels"
        case .rooms: return "Rooms"
        case .activity: return "Activity"
        case .settings: return "Settings"
        }
    }
    var icon: String {
        switch self {
        case .pixels: return "square.grid.2x2"
        case .rooms: return "person.2"
        case .activity: return "bolt.horizontal"
        case .settings: return "slider.horizontal.3"
        }
    }
}

struct MainShell: View {
    @EnvironmentObject private var store: AppStore
    @State private var tab: AppTab = MainShell.initialTab
    @State private var newBot = false
    @State private var newGroup = false
    @State private var showToolbox = ProcessInfo.processInfo.arguments.contains("--toolbox")
#if NIOBIUM_LOCAL_DSH
    @State private var showDSH = ProcessInfo.processInfo.arguments.contains("--dsh")
#endif

    static var initialTab: AppTab {
        let args = ProcessInfo.processInfo.arguments
        guard let index = args.firstIndex(of: "--tab"), index + 1 < args.count else { return .pixels }
        let raw = args[index + 1].lowercased()
        return AppTab(rawValue: raw == "bots" ? "pixels" : (raw == "pulse" ? "activity" : raw)) ?? .pixels
    }

    var body: some View {
        ZStack {
            NBMeshBackground(intensity: 0.7)
            Group {
                switch tab {
                case .pixels:
                    BotsView(newBot: $newBot)
                case .rooms:
                    RoomsView(newGroup: $newGroup)
                case .activity:
                    PulseView()
                case .settings:
                    SettingsView(onOpenTools: { showToolbox = true })
                }
            }
            .transition(.opacity)
        }
        .safeAreaInset(edge: .bottom, spacing: 0) {
            TabBar(selection: $tab)
        }
        .sheet(isPresented: $newBot) {
            NewBotView().environmentObject(store)
        }
        .sheet(isPresented: $newGroup) {
            NewGroupView().environmentObject(store)
        }
        .fullScreenCover(isPresented: $showToolbox) {
            ToolsView(onClose: { showToolbox = false })
                .environmentObject(store)
        }
#if NIOBIUM_LOCAL_DSH
        .sheet(isPresented: $showDSH) {
            DSHLocalScreen()
        }
#endif
    }
}

struct TabBar: View {
    @Binding var selection: AppTab
    var body: some View {
        HStack(spacing: 0) {
            ForEach(AppTab.allCases, id: \.self) { tab in
                Button {
                    Haptics.tap()
                    withAnimation(.spring(response: 0.32, dampingFraction: 0.84)) { selection = tab }
                } label: {
                    VStack(spacing: 5) {
                        ZStack {
                            if selection == tab {
                                RoundedRectangle(cornerRadius: 11, style: .continuous)
                                    .fill(NB.ion.opacity(0.13))
                                    .frame(width: 45, height: 29)
                                    .matchedGeometryEffect(id: "tabselection", in: namespace)
                                    .overlay(
                                        RoundedRectangle(cornerRadius: 11, style: .continuous)
                                            .strokeBorder(NB.ion.opacity(0.22), lineWidth: 1)
                                    )
                            }
                            Image(systemName: tab.icon)
                                .font(.system(size: 16, weight: selection == tab ? .semibold : .regular))
                                .foregroundStyle(selection == tab ? NB.ion : NB.tertiary)
                        }
                        .frame(height: 30)
                        Text(tab.title)
                            .font(.system(size: 9.5, weight: selection == tab ? .semibold : .medium))
                            .foregroundStyle(selection == tab ? NB.text : NB.tertiary)
                    }
                    .frame(maxWidth: .infinity)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.top, 9)
        .padding(.bottom, 4)
        .padding(.horizontal, 8)
        .background(
            ZStack {
                Rectangle().fill(.ultraThinMaterial)
                Rectangle().fill(NB.ink.opacity(0.82))
                VStack { Rectangle().fill(NB.line).frame(height: 1); Spacer() }
            }
            .ignoresSafeArea(edges: .bottom)
        )
    }

    @Namespace private var namespace
}
