import SwiftUI
import WebKit

struct RemoteHarnessView: View {
    @Environment(\.dismiss) private var dismiss
    @AppStorage("niobium.harnessURL") private var savedURL = ""
    @State private var draft = ""
    @State private var activeURL: URL?
    @State private var error: String?

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                if let activeURL {
                    HarnessWebView(url: activeURL, error: $error)
                        .ignoresSafeArea(edges: .bottom)
                } else {
                    VStack(alignment: .leading, spacing: 16) {
                        VStack(alignment: .leading, spacing: 6) {
                            Text("Remote DeepSeek Harness")
                                .font(.nbTitle(22)).foregroundStyle(NB.text)
                            Text("Run DSH on your computer or VPS, then open its web UI here. This works on iPhone, iPad and Android; the embedded Alpine guest remains iOS-only.")
                                .font(.nbBody(12)).foregroundStyle(NB.secondary).lineSpacing(2)
                        }
                        TextField("http://vps.local:3080", text: $draft)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                            .keyboardType(.URL)
                            .font(.nbBody(14)).foregroundStyle(NB.text)
                            .padding(12)
                            .background(RoundedRectangle(cornerRadius: 12).fill(NB.surface))
                            .overlay(RoundedRectangle(cornerRadius: 12).strokeBorder(NB.line, lineWidth: 1))
                        Button {
                            open()
                        } label: {
                            Text("Open harness").frame(maxWidth: .infinity)
                        }
                        .buttonStyle(NBPrimaryButtonStyle())
                        .disabled(draft.trimmingCharacters(in: .whitespaces).isEmpty)
                        if let error {
                            Text(error).font(.nbBody(11)).foregroundStyle(NB.rose)
                        }
                        Spacer()
                    }
                    .padding(22)
                    .background(NBMeshBackground(intensity: 0.4))
                }
            }
            .navigationTitle("Harness")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Done") { dismiss() }.foregroundStyle(NB.ion)
                }
                if activeURL != nil {
                    ToolbarItem(placement: .topBarTrailing) {
                        Button("Change") { activeURL = nil; error = nil }
                            .foregroundStyle(NB.secondary)
                    }
                }
            }
            .toolbarBackground(NB.ink, for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar)
        }
        .preferredColorScheme(nil)
        .onAppear {
            draft = savedURL
            if !savedURL.isEmpty, let url = URL(string: savedURL) { activeURL = url }
        }
    }

    private func open() {
        var raw = draft.trimmingCharacters(in: .whitespacesAndNewlines)
        if !raw.contains("://") { raw = "http://\(raw)" }
        guard let url = URL(string: raw), let scheme = url.scheme,
              ["http", "https"].contains(scheme), url.host != nil else {
            error = "Enter a full http:// or https:// URL."
            return
        }
        savedURL = raw
        error = nil
        activeURL = url
    }
}

private struct HarnessWebView: UIViewRepresentable {
    let url: URL
    @Binding var error: String?

    func makeUIView(context: Context) -> WKWebView {
        let config = WKWebViewConfiguration()
        config.websiteDataStore = .default()
        let web = WKWebView(frame: .zero, configuration: config)
        web.navigationDelegate = context.coordinator
        web.allowsBackForwardNavigationGestures = true
        web.load(URLRequest(url: url))
        return web
    }

    func updateUIView(_ webView: WKWebView, context: Context) {}
    func makeCoordinator() -> Coordinator { Coordinator(error: $error) }

    final class Coordinator: NSObject, WKNavigationDelegate {
        @Binding var error: String?
        init(error: Binding<String?>) { _error = error }
        func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
            self.error = "Could not load \(webView.url?.absoluteString ?? "harness"): \(error.localizedDescription)"
        }
    }
}
