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
