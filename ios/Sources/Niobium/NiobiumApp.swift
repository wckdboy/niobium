import SwiftUI

@main
struct NiobiumApp: App {
    @StateObject private var hub = EngineHub()
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            EngineWorkspaceView(hub: hub)
                .onAppear {
                    UIApplication.shared.isIdleTimerDisabled = ProcessInfo.processInfo.arguments.contains("--no-splash")
                }
        }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active { Task { await hub.resumeConnections() } }
        }
    }
}
