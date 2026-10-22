import SwiftUI

struct PixelPanel: ViewModifier {
    var radius: CGFloat = 18
    var accented = false
    func body(content: Content) -> some View {
        content
            .background(RoundedRectangle(cornerRadius: radius, style: .continuous).fill(NB.surface.opacity(0.94)))
            .overlay(RoundedRectangle(cornerRadius: radius, style: .continuous).strokeBorder(accented ? NB.ion.opacity(0.24) : NB.lineStrong.opacity(0.72), lineWidth: 1))
            .overlay(alignment: .topLeading) {
                Canvas { context, _ in
                    for rect in [CGRect(x: 10, y: 15, width: 3, height: 7), CGRect(x: 13, y: 12, width: 7, height: 3), CGRect(x: 21, y: 9, width: 3, height: 3)] {
                        context.fill(Path(rect), with: .color(NB.ion.opacity(0.28)))
                    }
                }.frame(width: 30, height: 30).allowsHitTesting(false).accessibilityHidden(true)
            }
            .shadow(color: NB.ion.opacity(0.045), radius: 12, y: 4)
    }
}
extension View {
    func pixelPanel(radius: CGFloat = 18, accented: Bool = false) -> some View {
        modifier(PixelPanel(radius: radius, accented: accented))
    }
}

enum PixelGlyph: String {
    case home, pixels, terminal, activity, settings, play, stop, back, link, spark, shield
    var rectangles: [[Int]] {
        switch self {
        case .home: return [[7,1,2,2],[5,3,6,2],[3,5,10,2],[1,7,14,2],[3,9,4,6],[9,9,4,6],[7,9,2,2]]
        case .pixels: return [[1,1,6,6],[9,1,6,6],[1,9,6,6],[9,9,6,6]]
        case .terminal: return [[1,2,14,2],[1,12,14,2],[1,4,2,8],[13,4,2,8],[4,5,2,2],[6,7,2,2],[4,9,2,2],[9,9,2,2]]
        case .activity: return [[1,9,3,2],[4,5,2,6],[6,2,2,7],[8,7,2,7],[10,9,2,2],[12,6,2,5],[14,8,2,2]]
        case .settings: return [[6,1,4,3],[6,12,4,3],[1,6,3,4],[12,6,3,4],[3,3,3,3],[10,3,3,3],[3,10,3,3],[10,10,3,3],[4,4,8,2],[4,10,8,2],[4,6,2,4],[10,6,2,4]]
        case .play: return (0...5).map { [3+$0*2,2+$0,2,12-$0*2] }
        case .stop: return [[3,3,10,10]]
        case .back: return [[7,2,2,2],[5,4,2,2],[3,6,2,4],[5,10,2,2],[7,12,2,2],[5,7,10,2]]
        case .link: return [[2,2,7,2],[2,4,2,5],[4,7,3,2],[7,7,2,2],[9,7,3,2],[12,7,2,5],[7,12,7,2],[7,4,2,3]]
        case .spark: return [[7,1,2,4],[5,5,6,2],[1,7,14,2],[5,9,6,2],[7,11,2,4]]
        case .shield: return [[6,1,4,2],[2,3,12,2],[2,5,2,5],[12,5,2,5],[4,10,2,2],[10,10,2,2],[6,12,4,2]]
        }
    }
}
struct PixelIcon: View {
    var glyph: PixelGlyph
    var size: CGFloat = 24
    var tint: Color = NB.ion
    @Environment(\.displayScale) private var scale
    init(_ glyph: PixelGlyph, size: CGFloat = 24, tint: Color = NB.ion) {
        self.glyph = glyph; self.size = size; self.tint = tint
    }
    var body: some View {
        Canvas { context, bounds in
            let cell = max(1, floor(min(bounds.width, bounds.height) * scale / 16)) / scale
            let x = floor((bounds.width - cell * 16) * scale / 2) / scale
            let y = floor((bounds.height - cell * 16) * scale / 2) / scale
            for r in glyph.rectangles {
                context.fill(Path(CGRect(x: x + CGFloat(r[0])*cell, y: y + CGFloat(r[1])*cell,
                                         width: CGFloat(r[2])*cell, height: CGFloat(r[3])*cell)), with: .color(tint))
            }
        }.frame(width: size, height: size).accessibilityHidden(true)
    }
}
struct PixelActionLabel: View {
    let title: String
    var icon: PixelGlyph
    init(_ title: String, icon: PixelGlyph) { self.title = title; self.icon = icon }
    var body: some View {
        HStack(spacing: 10) { PixelIcon(icon, size: 20, tint: .primary); Text(title) }.frame(maxWidth: .infinity)
    }
}

struct LocalToolsView: View {
    @EnvironmentObject private var store: AppStore
    var onClose: (() -> Void)?
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                if let onClose {
                    Button(action: onClose) { PixelActionLabel("Back to Pixel", icon: .back) }.buttonStyle(NBQuietButtonStyle())
                }
                PixelIcon(.shield, size: 30)
                Text("Local tool access").font(.nbTitle(28)).foregroundStyle(NB.text)
                Text("Your Pixel’s reviewed preset decides which tools it can use. Create a new Pixel to choose a different set.")
                    .font(.nbBody(13)).foregroundStyle(NB.secondary).lineSpacing(4)
                if let bot = store.activeBot {
                    VStack(alignment: .leading, spacing: 12) {
                        Text(bot.title).font(.nbTitle(16)).foregroundStyle(NB.text)
                        if bot.enabledToolsets.isEmpty { Text("No optional toolsets selected.").font(.nbBody(12)).foregroundStyle(NB.tertiary) }
                        ForEach(bot.enabledToolsets, id: \.self) { tool in
                            HStack(spacing: 10) { PixelIcon(.spark, size: 16); Text(tool.capitalized).font(.nbBody(13)).foregroundStyle(NB.secondary) }
                        }
                    }.frame(maxWidth: .infinity, alignment: .leading).padding(18).pixelPanel()
                }
                Text("Skills share the local workspace. Model keys and advanced DSH configuration are under Engines. Tool selection controls the preset’s catalog; local Pixels share app storage.")
                    .font(.nbBody(12)).foregroundStyle(NB.tertiary).lineSpacing(4)
            }.padding(20)
        }.background(NB.void)
    }
}
struct PixelButtonShape: Shape {
    func path(in r: CGRect) -> Path {
        let s: CGFloat = 4
        var p = Path()
        p.move(to: CGPoint(x: s, y: 0)); p.addLine(to: CGPoint(x: r.width-s, y: 0))
        p.addLine(to: CGPoint(x: r.width-s, y: s)); p.addLine(to: CGPoint(x: r.width, y: s))
        p.addLine(to: CGPoint(x: r.width, y: r.height-s)); p.addLine(to: CGPoint(x: r.width-s, y: r.height-s))
        p.addLine(to: CGPoint(x: r.width-s, y: r.height)); p.addLine(to: CGPoint(x: s, y: r.height))
        p.addLine(to: CGPoint(x: s, y: r.height-s)); p.addLine(to: CGPoint(x: 0, y: r.height-s))
        p.addLine(to: CGPoint(x: 0, y: s)); p.addLine(to: CGPoint(x: s, y: s)); p.closeSubpath()
        return p
    }
}
