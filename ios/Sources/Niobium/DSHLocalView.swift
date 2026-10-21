#if NIOBIUM_LOCAL_DSH
import SwiftUI

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
                .navigationTitle("DeepSeek Harness")
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
        .preferredColorScheme(.dark)
    }
}

#endif
