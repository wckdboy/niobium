import SwiftUI

struct CredentialRequest: Identifiable, Equatable {
    var rpcID: JSONValueID
    var sessionID: String
    var method: String
    var title: String
    var detail: String
    var login = false
    var connectionID: UUID? = nil
    var id: String { (connectionID?.uuidString ?? "none") + ":" + rpcID.description }
}
struct CredentialPromptView: View {
    @EnvironmentObject private var store: AppStore
    let request: CredentialRequest
    @State private var value = ""
    @State private var identifier = ""
    @State private var resolved = false

    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 18) {
                Image(systemName: "key.horizontal").font(.system(size: 28)).foregroundStyle(NB.ion)
                Text(request.title).font(.nbTitle(24)).foregroundStyle(NB.text)
                Text(request.detail).font(.nbBody(13)).foregroundStyle(NB.secondary).textSelection(.enabled)
                if request.login {
                    TextField("Login identifier", text: $identifier).textInputAutocapitalization(.never).autocorrectionDisabled()
                        .font(.nbBody(15)).padding(14).background(RoundedRectangle(cornerRadius: 12).fill(NB.surface))
                }
                SecureField(request.login ? "Password" : "Value", text: $value)
                    .textInputAutocapitalization(.never).autocorrectionDisabled().privacySensitive()
                    .font(.nbBody(15)).padding(14).background(RoundedRectangle(cornerRadius: 12).fill(NB.surface))
                Text("Sent to your connected Hermes host for this request. Niobium does not save this value or add it to chat.")
                    .font(.nbBody(11)).foregroundStyle(NB.tertiary)
                Button {
                    var answer = value
                    if request.login, let data = try? JSONSerialization.data(withJSONObject: ["identifier": identifier, "password": value]),
                       let json = String(data: data, encoding: .utf8) { answer = json }
                    resolved = true
                    store.resolveCredential(request, value: answer)
                    value = ""; identifier = ""
                } label: { Text("Send to host").frame(maxWidth: .infinity) }
                    .buttonStyle(NBPrimaryButtonStyle()).disabled(value.isEmpty || (request.login && identifier.isEmpty))
                Spacer()
            }.padding(24).background(NB.void)
                .navigationTitle("Hermes request").navigationBarTitleDisplayMode(.inline)
                .toolbar { ToolbarItem(placement: .cancellationAction) {
                    Button("Skip") { resolved = true; value = ""; identifier = ""; store.resolveCredential(request, value: "") }
                } }
        }
        .onDisappear { value = ""; identifier = ""; if !resolved { store.resolveCredential(request, value: "") } }
    }
}
