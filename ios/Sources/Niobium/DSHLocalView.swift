#if NIOBIUM_LOCAL_DSH
import SwiftUI

@MainActor
enum LocalDSHProvider {
    static func start(token: String, status: (String, Bool) -> Void) async throws -> URL {
        let harness = DSHHarness.shared()
        let boot = DSHBootCoordinator.shared()
        var environment = harness.extraEnvironment
        let changed = environment["NIOBIUM_AGENT_TOKEN"] != token
        environment["NIOBIUM_AGENT_TOKEN"] = token
        environment["NODE_OPTIONS"] = "--require /usr/local/lib/niobium-agent.cjs"
#if DEBUG
        // Only the separately sandboxed acceptance app can redirect its model
        // to the UI runner's loopback fixture. Release builds contain no hook.
        if Bundle.main.bundleIdentifier == "ai.wckd.niobium.acceptance",
           let raw = ProcessInfo.processInfo.environment["NIOBIUM_DSH_FIXTURE_URL"],
           let url = URL(string: raw), url.scheme == "http", url.host == "127.0.0.1",
           let port = url.port, (1024...65535).contains(port) {
            environment["DEEPSEEK_BASE_URL"] = raw
            environment["DEEPSEEK_API_KEY"] = "niobium-local-fixture"
        }
#endif
        harness.extraEnvironment = environment
        boot.start()
        let deadline = Date().addingTimeInterval(300)
        var started = false
        while Date() < deadline {
            try Task.checkCancellation()
            if boot.phase == .failed { throw RPCError.transport(boot.statusMessage) }
            if boot.phase == .ready {
                if !started {
                    started = true
                    if changed && harness.state == .ready { harness.restart() }
                    else if harness.state == .idle || harness.state == .stopped { harness.start() }
                }
                if harness.state == .failed { throw RPCError.transport(harness.lastError ?? "Local engine could not start.") }
                if harness.state == .ready, let url = harness.baseURL { status("Running", true); return url }
                status("Starting your Pixels…", false)
            } else { status(boot.statusMessage, false) }
            try await Task.sleep(for: .milliseconds(250))
        }
        throw RPCError.transport("Local startup timed out. Open Engines to try again.")
    }
}

/// Full DeepSeek Harness running in the embedded iSH/Alpine guest.
/// This is the stock `DSHRootViewController` from the DSH iOS project:
/// boot progress, the harness web UI, terminal, capabilities, logs and restarts.
struct DSHLocalView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        DSHRootViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct DSHLocalScreen: View {
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            DSHLocalView()
                .ignoresSafeArea(edges: .bottom)
                .navigationTitle("Advanced DSH")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .topBarLeading) {
                        Text("local · iSH")
                            .font(.nbBody(10.5, weight: .medium))
                            .foregroundStyle(NB.tertiary)
                    }
                    ToolbarItem(placement: .confirmationAction) {
                        Button("Done") { dismiss() }
                            .foregroundStyle(NB.ion)
                    }
                }
                .toolbarBackground(NB.ink, for: .navigationBar)
                .toolbarBackground(.visible, for: .navigationBar)
                .onAppear {
                    DSHBootCoordinator.shared().start()
                }
        }
    }
}

#endif
