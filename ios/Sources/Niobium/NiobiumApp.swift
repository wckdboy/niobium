import SwiftUI

@main
struct NiobiumApp: App {
    @StateObject private var store = AppStore()
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(store)
                .onAppear {
                    UIApplication.shared.isIdleTimerDisabled = ProcessInfo.processInfo.arguments.contains("--no-splash")
                }
        }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active { Task { await store.resumeConnection() } }
        }
    }
}
