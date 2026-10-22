import SwiftUI
import UIKit

enum NB {
    private static func dynamic(_ dark: UInt32, _ light: UInt32) -> Color {
        Color(uiColor: UIColor { trait in
            UIColor(hex: trait.userInterfaceStyle == .light ? light : dark)
        })
    }

    private static func dynamicAlpha(dark: (UInt32, Double), light: (UInt32, Double)) -> Color {
        Color(uiColor: UIColor { trait in
            let value = trait.userInterfaceStyle == .light ? light : dark
            return UIColor(hex: value.0, alpha: value.1)
        })
    }

    static let void = dynamic(0x07050C, 0xF8F7FF)
    static let ink = dynamic(0x0B0812, 0xFFFFFF)
    static let surface = dynamic(0x120E1B, 0xFFFFFF)
    static let raised = dynamic(0x191324, 0xF2ECFA)
    static let hover = dynamic(0x22192F, 0xEDE6F8)
    static let line = dynamicAlpha(dark: (0xFFFFFF, 0.07), light: (0x8B6FE0, 0.16))
    static let lineStrong = dynamicAlpha(dark: (0xFFFFFF, 0.13), light: (0x8B6FE0, 0.24))
    static let fill = dynamicAlpha(dark: (0xFFFFFF, 0.055), light: (0x1B1326, 0.045))
    static let fillSoft = dynamicAlpha(dark: (0xFFFFFF, 0.028), light: (0x1B1326, 0.025))
    static let fillStrong = dynamicAlpha(dark: (0xFFFFFF, 0.10), light: (0x1B1326, 0.075))
    static let shade = dynamicAlpha(dark: (0x000000, 0.32), light: (0x2A1745, 0.12))
    static let code = dynamicAlpha(dark: (0x000000, 0.26), light: (0x1B1326, 0.045))
    static let text = dynamic(0xF6F2FF, 0x1B1326)
    static let secondary = dynamic(0xADA3BD, 0x625671)
    static let tertiary = dynamic(0x736A82, 0x74698D)
    static let ion = dynamic(0xA78BFA, 0x7C3AED)
    static let ionDeep = dynamic(0x7C3AED, 0x6D28D9)
    static let violet = dynamic(0xE9D5FF, 0x5B21B6)
    static let mint = dynamic(0x5EEAD4, 0x0F9F8A)
    static let amber = dynamic(0xFBBF24, 0xB77900)
    static let rose = dynamic(0xFB7185, 0xD9485F)
    static let onIon = Color.white
    static let orbPalette: [Color] = [ion, dynamic(0xF0ABFC, 0xC026D3), mint, amber, dynamic(0xFDA4AF, 0xE11D48), dynamic(0xC4B5FD, 0x8B5CF6)]

    static func color(for name: String?) -> Color {
        guard let name, !name.isEmpty else { return ion }
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.hasPrefix("#"), let value = UInt32(trimmed.dropFirst(), radix: 16) {
            return Color(hex: value)
        }
        switch trimmed.lowercased() {
        case "athena": return ion
        case "forge": return dynamic(0xF5C97A, 0xB77900)
        case "scout": return mint
        case "hermes": return dynamic(0xC4B5FD, 0x8B5CF6)
        default: break
        }
        var hash: UInt64 = 41
        for scalar in trimmed.unicodeScalars { hash = hash &* 31 &+ UInt64(scalar.value) }
        return orbPalette[Int(hash % UInt64(orbPalette.count))]
    }
}

enum NBAppearance: String, CaseIterable, Identifiable {
    case system, light, dark
    var id: String { rawValue }
    var title: String {
        switch self {
        case .system: return "System"
        case .light: return "Light"
        case .dark: return "Dark"
        }
    }
    var icon: String {
        switch self {
        case .system: return "circle.lefthalf.filled"
        case .light: return "sun.max"
        case .dark: return "moon.stars"
        }
    }
    var colorScheme: ColorScheme? {
        switch self {
        case .system: return nil
        case .light: return .light
        case .dark: return .dark
        }
    }
}

@MainActor
enum Haptics {
    static var enabled: Bool {
        UserDefaults.standard.object(forKey: "niobium.haptics") as? Bool ?? true
    }
    static func tap(_ style: UIImpactFeedbackGenerator.FeedbackStyle = .light) {
        guard enabled else { return }
        UIImpactFeedbackGenerator(style: style).impactOccurred()
    }
    static func warning() {
        guard enabled else { return }
        UINotificationFeedbackGenerator().notificationOccurred(.warning)
    }
}

extension UIColor {
    convenience init(hex: UInt32, alpha: CGFloat = 1) {
        self.init(red: CGFloat((hex >> 16) & 0xFF) / 255,
                  green: CGFloat((hex >> 8) & 0xFF) / 255,
                  blue: CGFloat(hex & 0xFF) / 255,
                  alpha: alpha)
    }
}

extension Color {
    init(hex: UInt32, alpha: Double = 1) {
        self.init(.sRGB,
                  red: Double((hex >> 16) & 0xFF) / 255,
                  green: Double((hex >> 8) & 0xFF) / 255,
                  blue: Double(hex & 0xFF) / 255,
                  opacity: alpha)
    }
}

extension Font {
    static func nbDisplay(_ size: CGFloat) -> Font { .system(size: size, weight: .semibold, design: .default) }
    static func nbTitle(_ size: CGFloat) -> Font { .system(size: size, weight: .semibold, design: .default) }
    static func nbBody(_ size: CGFloat, weight: Font.Weight = .regular) -> Font { .system(size: size, weight: weight, design: .default) }
    static func nbMono(_ size: CGFloat, weight: Font.Weight = .regular) -> Font { .system(size: size, weight: weight, design: .monospaced) }
    static func nbLabel(_ size: CGFloat = 10) -> Font { .system(size: size, weight: .semibold, design: .default) }
}

struct NBMeshBackground: View {
    var intensity: Double = 1
    var body: some View {
        ZStack {
            NB.void
            GeometryReader { proxy in
                let w = max(proxy.size.width, 1)
                let h = max(proxy.size.height, 1)
                ZStack {
                    RadialGradient(colors: [NB.ion.opacity(0.16 * intensity), .clear],
                                   center: UnitPoint(x: 0.78, y: -0.04), startRadius: 0, endRadius: w * 0.95)
                    RadialGradient(colors: [NB.ionDeep.opacity(0.10 * intensity), .clear],
                                   center: UnitPoint(x: 0.02, y: 0.48), startRadius: 0, endRadius: h * 0.55)
                    RadialGradient(colors: [NB.violet.opacity(0.10 * intensity), .clear],
                                   center: UnitPoint(x: 0.6, y: 1.02), startRadius: 0, endRadius: w * 0.8)
                }
            }
            LinearGradient(colors: [NB.fillSoft.opacity(0.6), .clear, NB.shade.opacity(0.35)],
                           startPoint: .top, endPoint: .bottom)
        }
        .ignoresSafeArea()
    }
}

struct NBCard<Content: View>: View {
    var padding: CGFloat = 18
    var radius: CGFloat = 22
    var tint: Color = NB.surface
    @ViewBuilder var content: Content

    var body: some View {
        content
            .padding(padding)
            .background(
                RoundedRectangle(cornerRadius: radius, style: .continuous)
                    .fill(tint)
                    .overlay(
                        RoundedRectangle(cornerRadius: radius, style: .continuous)
                            .strokeBorder(
                                LinearGradient(colors: [NB.fillStrong, NB.fillSoft],
                                               startPoint: .topLeading, endPoint: .bottomTrailing),
                                lineWidth: 1)
                    )
            )
    }
}

struct NBPrimaryButtonStyle: ButtonStyle {
    var tint: Color = NB.ion
    var compact = false
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.nbBody(compact ? 14 : 15, weight: .semibold))
            .foregroundStyle(.white)
            .padding(.horizontal, compact ? 16 : 22)
            .frame(height: compact ? 38 : 52)
            .background(
                PixelButtonShape()
                    .fill(LinearGradient(colors: [tint, NB.ionDeep],
                                         startPoint: .topLeading, endPoint: .bottomTrailing))
                    .overlay(PixelButtonShape().stroke(NB.onIon.opacity(0.22), lineWidth: 1))
                    .shadow(color: tint.opacity(configuration.isPressed ? 0.1 : 0.32), radius: 8, y: 3)
            )
            .opacity(configuration.isPressed ? 0.82 : 1)
            .scaleEffect(configuration.isPressed ? 0.975 : 1)
            .animation(.spring(response: 0.28, dampingFraction: 0.8), value: configuration.isPressed)
    }
}

struct NBQuietButtonStyle: ButtonStyle {
    var compact = false
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.nbBody(compact ? 14 : 15, weight: .medium))
            .foregroundStyle(NB.text)
            .padding(.horizontal, compact ? 14 : 20)
            .frame(height: compact ? 38 : 50)
            .background(PixelButtonShape().fill(configuration.isPressed ? NB.fillStrong : NB.fill))
            .overlay(PixelButtonShape().stroke(NB.line, lineWidth: 1))
            .opacity(configuration.isPressed ? 0.75 : 1)
    }
}

struct NBIconButton: View {
    let symbol: String
    var size: CGFloat = 38
    var tint: Color = NB.secondary
    var action: () -> Void
    var body: some View {
        Button(action: action) {
            Image(systemName: symbol)
                .font(.system(size: size * 0.38, weight: .semibold))
                .foregroundStyle(tint)
                .frame(width: size, height: size)
                .background(Circle().fill(NB.fill))
                .overlay(Circle().strokeBorder(NB.line, lineWidth: 1))
        }
        .buttonStyle(.plain)
    }
}

struct NBPill: View {
    var text: String
    var tint: Color = NB.ion
    var filled = false
    var body: some View {
        Text(text)
            .font(.nbBody(10.5, weight: .medium))
            .foregroundStyle(filled ? NB.onIon : tint)
            .padding(.horizontal, 8)
            .padding(.vertical, 4)
            .background(
                Capsule().fill(filled ? tint : tint.opacity(0.10))
            )
            .overlay(Capsule().strokeBorder(filled ? .clear : tint.opacity(0.26), lineWidth: 1))
    }
}

struct NBDivider: View {
    var body: some View { Rectangle().fill(NB.line).frame(height: 1) }
}

struct NBSectionHeader: View {
    var title: String
    var detail: String? = nil
    var action: (() -> Void)? = nil
    var actionLabel: String = "See all"
    var body: some View {
        HStack(alignment: .firstTextBaseline) {
            Text(title)
                .font(.nbBody(13, weight: .semibold))
                .foregroundStyle(NB.text.opacity(0.86))
            Spacer()
            if let detail { Text(detail).font(.nbMono(9)).foregroundStyle(NB.tertiary) }
            if let action {
                Button(actionLabel, action: action)
                    .font(.nbBody(12, weight: .medium))
                    .foregroundStyle(NB.ion)
            }
        }
    }
}

struct NBEyebrow: View {
    var text: String
    var color: Color = NB.tertiary
    var body: some View {
        Text(text.uppercased())
            .font(.nbLabel(9.5))
            .tracking(2.2)
            .foregroundStyle(color)
    }
}

struct StatusDot: View {
    var color: Color = NB.mint
    var pulse = false
    @State private var animate = false
    var body: some View {
        ZStack {
            if pulse {
                Circle().fill(color.opacity(0.25)).frame(width: 18, height: 18)
                    .scaleEffect(animate ? 1.6 : 1).opacity(animate ? 0 : 1)
                    .animation(.easeOut(duration: 1.6).repeatForever(autoreverses: false), value: animate)
            }
            Circle().fill(color).frame(width: 7, height: 7)
                .overlay(Circle().strokeBorder(NB.shade, lineWidth: 0.5))
        }
        .onAppear { animate = true }
    }
}

struct NBScreenHeader: View {
    var eyebrow: String
    var title: String
    var trailing: AnyView? = nil
    var body: some View {
        HStack(alignment: .bottom) {
            VStack(alignment: .leading, spacing: 7) {
                NBEyebrow(text: eyebrow)
                Text(title)
                    .font(.nbDisplay(36))
                    .tracking(-1.2)
                    .foregroundStyle(NB.text)
            }
            Spacer()
            trailing
        }
        .padding(.horizontal, 20)
        .padding(.top, 8)
    }
}

extension View {
    func nbHaptic(_ style: UIImpactFeedbackGenerator.FeedbackStyle = .light) -> some View {
        self.onTapGesture { UIImpactFeedbackGenerator(style: style).impactOccurred() }
    }
    func hideKeyboard() {
        UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
    }
}

struct ShimmerText: View {
    var text: String
    @State private var phase: CGFloat = -1
    var body: some View {
        Text(text)
            .overlay(
                LinearGradient(colors: [.clear, NB.fillStrong, .clear], startPoint: .leading, endPoint: .trailing)
                    .frame(width: 80)
                    .offset(x: phase * 280)
                    .mask(Text(text))
            )
            .onAppear {
                withAnimation(.linear(duration: 1.8).repeatForever(autoreverses: false)) { phase = 1 }
            }
    }
}

struct NBSkeleton: View {
    var width: CGFloat? = nil
    var height: CGFloat = 12
    @State private var shimmer = false
    var body: some View {
        RoundedRectangle(cornerRadius: height / 2)
            .fill(NB.fill)
            .frame(width: width, height: height)
            .overlay(
                LinearGradient(colors: [.clear, NB.fill, .clear], startPoint: .leading, endPoint: .trailing)
                    .offset(x: shimmer ? 180 : -180)
            )
            .clipped()
            .onAppear { withAnimation(.linear(duration: 1.6).repeatForever(autoreverses: false)) { shimmer = true } }
    }
}
