import SwiftUI
import WebKit

struct RemoteHarnessView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var savedURL = ""
    @State private var reloadID = 0
    @State private var draft = ""
    @State private var activeURL: URL?
    @State private var error: String?

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                if activeURL != nil, let error {
                    HStack {
                        Text(error).font(.nbBody(11)).foregroundStyle(NB.rose).lineLimit(3)
                        Spacer()
                        Button("Retry") { self.error = nil; reloadID += 1 }.font(.nbBody(12, weight: .semibold))
                    }.padding(12).background(NB.surface)
                }
                if let activeURL {
                    HarnessWebView(url: activeURL, error: $error).id(reloadID)
                        .ignoresSafeArea(edges: .bottom)
                } else {
                    VStack(alignment: .leading, spacing: 16) {
                        VStack(alignment: .leading, spacing: 6) {
                            Text("Remote DeepSeek Harness")
                                .font(.nbTitle(22)).foregroundStyle(NB.text)
                            Text("Run DSH on your computer or VPS, then open its web UI here. Remote DSH works on iPhone, iPad and Android. For on-device DSH, return to Local.")
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
            savedURL = Keychain.get(service: "io.sovware.niobium.harness-url", account: "default") ?? ""
            if savedURL.isEmpty, let legacy = UserDefaults.standard.string(forKey: "niobium.harnessURL"), !legacy.isEmpty {
                Keychain.set(legacy, service: "io.sovware.niobium.harness-url", account: "default")
                savedURL = legacy
                UserDefaults.standard.removeObject(forKey: "niobium.harnessURL")
            }
            draft = savedURL
            if !savedURL.isEmpty, let url = URL(string: savedURL) { activeURL = url }
        }
    }

    private func open() {
        var raw = draft.trimmingCharacters(in: .whitespacesAndNewlines)
        if !raw.contains("://") { raw = "http://\(raw)" }
        guard let url = URL(string: raw), let scheme = url.scheme,
              ["http", "https"].contains(scheme), url.host != nil, url.user == nil, url.password == nil else {
            error = "Enter a full http:// or https:// URL."
            return
        }
        Keychain.set(raw, service: "io.sovware.niobium.harness-url", account: "default")
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
    static func dismantleUIView(_ webView: WKWebView, coordinator: Coordinator) {
        webView.stopLoading(); webView.navigationDelegate = nil
    }
    func makeCoordinator() -> Coordinator { Coordinator(error: $error) }

    final class Coordinator: NSObject, WKNavigationDelegate {
        @Binding var error: String?
        init(error: Binding<String?>) { _error = error }
        func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) { error = nil }
        func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
            self.error = "Could not load harness: \(error.localizedDescription)"
        }
        func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
            self.error = "Could not load \(webView.url?.host ?? "harness"): \(error.localizedDescription)"
        }
    }
}
