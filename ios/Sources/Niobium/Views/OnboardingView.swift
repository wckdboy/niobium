import SwiftUI

struct OnboardingView: View {
    @EnvironmentObject private var store: AppStore
    @State private var gateway = "http://127.0.0.1:9119"
    @State private var token = ""
    @State private var showToken = false
    @State private var appeared = false
    @FocusState private var focused: Field?
    enum Field { case url, token }

    var body: some View {
        ZStack {
            NBMeshBackground(intensity: 1.15)
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    HStack {
                        AtomicMark(size: 46, animated: true)
                        Spacer()
                        Text("Nb · 41")
                            .font(.nbBody(10))
                            .foregroundStyle(NB.tertiary)
                    }
                    .padding(.top, 10)

                    Text("Your pixels.\nIn your pocket.")
                        .font(.nbDisplay(48))
                        .tracking(-2.2)
                        .foregroundStyle(NB.text)
                        .lineSpacing(-3)
                        .padding(.top, 42)

                    Text("A quiet command deck for Hermes. Every pixel, every tool, every approval — right here.")
                        .font(.nbBody(15))
                        .foregroundStyle(NB.secondary)
                        .lineSpacing(3)
                        .padding(.top, 14)
                        .frame(maxWidth: 350, alignment: .leading)

                    VStack(alignment: .leading, spacing: 14) {
                        HStack {
                            Text("Connect")
                                .font(.nbBody(13, weight: .semibold))
                                .foregroundStyle(NB.text)
                            Spacer()
                            HStack(spacing: 6) {
                                Circle()
                                    .fill(store.isConnecting ? NB.amber : NB.tertiary)
                                    .frame(width: 5, height: 5)
                                Text(store.isConnecting ? "connecting" : "gateway")
                                    .font(.nbBody(10))
                                    .foregroundStyle(NB.tertiary)
                            }
                        }

                        field("Gateway URL", icon: "link") {
                            TextField("http://192.168.1.20:9119", text: $gateway)
                                .textInputAutocapitalization(.never)
                                .keyboardType(.URL)
                                .autocorrectionDisabled()
                                .focused($focused, equals: .url)
                                .font(.nbBody(14))
                                .foregroundStyle(NB.text)
                        }

                        field("Access token", icon: "key") {
                            HStack {
                                Group {
                                    if showToken {
                                        TextField("Hermes session token", text: $token)
                                    } else {
                                        SecureField("Hermes session token", text: $token)
                                    }
                                }
                                .textInputAutocapitalization(.never)
                                .autocorrectionDisabled()
                                .focused($focused, equals: .token)
                                .font(.nbBody(13))
                                .foregroundStyle(NB.text)
                                Button { showToken.toggle() } label: {
                                    Image(systemName: showToken ? "eye.slash" : "eye")
                                        .font(.system(size: 12))
                                        .foregroundStyle(NB.tertiary)
                                }
                            }
                        }

                        if let error = store.connectError {
                            Text(error)
                                .font(.nbBody(12))
                                .foregroundStyle(NB.rose)
                                .padding(11)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .background(RoundedRectangle(cornerRadius: 11).fill(NB.rose.opacity(0.08)))
                        }

                        Button {
                            focused = nil
                            Task { await store.connect(url: gateway, token: token, name: "My Hermes") }
                        } label: {
                            HStack(spacing: 8) {
                                if store.isConnecting {
                                    ProgressView().tint(.white).scaleEffect(0.8)
                                }
                                Text(store.isConnecting ? "Connecting…" : "Continue")
                                Spacer()
                                Image(systemName: "arrow.right")
                                    .font(.system(size: 13, weight: .bold))
                            }
                        }
                        .buttonStyle(NBPrimaryButtonStyle())
                        .disabled(store.isConnecting)

                        Button {
                            store.enterDemo()
                        } label: {
                            Text("or explore the demo")
                                .font(.nbBody(13))
                                .foregroundStyle(NB.secondary)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 6)
                        }
                        .buttonStyle(.plain)
                    }
                    .padding(17)
                    .background(
                        RoundedRectangle(cornerRadius: 22, style: .continuous)
                            .fill(NB.ink.opacity(0.72))
                            .overlay(
                                RoundedRectangle(cornerRadius: 22, style: .continuous)
                                    .strokeBorder(NB.line, lineWidth: 1)
                            )
                    )
                    .padding(.top, 32)
                    .offset(y: appeared ? 0 : 12)
                    .opacity(appeared ? 1 : 0)

                    Text("Token stays in the Keychain. Niobium has no account, no cloud, no analytics.")
                        .font(.nbBody(10.5))
                        .foregroundStyle(NB.tertiary)
                        .padding(.top, 16)
                        .padding(.horizontal, 4)

                    Spacer(minLength: 40)
                }
                .padding(.horizontal, 22)
                .frame(maxWidth: 560)
                .frame(maxWidth: .infinity)
            }
            .scrollDismissesKeyboard(.interactively)
        }
        .onAppear {
            withAnimation(.easeOut(duration: 0.6)) { appeared = true }
        }
    }

    private func field<Content: View>(_ label: String, icon: String, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 7) {
            HStack(spacing: 5) {
                Image(systemName: icon).font(.system(size: 8, weight: .semibold))
                Text(label).font(.nbBody(10.5, weight: .medium))
            }
            .foregroundStyle(NB.tertiary)
            content()
                .padding(.horizontal, 12)
                .frame(height: 46)
                .background(RoundedRectangle(cornerRadius: 12, style: .continuous).fill(NB.fillSoft))
                .overlay(
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .strokeBorder(NB.line, lineWidth: 1)
                )
        }
    }
}
