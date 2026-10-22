import SwiftUI
import UIKit

// A small immutable layer atlas is decoded once; rendered 48×48 sprites are cached.
@MainActor
final class CharacterEngine {
    static let shared = CharacterEngine()
    private struct Atlas: Decodable {
        var version: Int
        var size: Int
        var palettes: [String: [String: String]]
        var fixed: [String: String]
        var skin_tones: [String: [String: String]]
        var hair_tones: [String: [String: String]]
        var details: [String: [String]]
        var bodies: [String: [String]]
        var hair: [String: Hair]
        var faces: [String: [String]]
        var blink_faces: [String: [String]]
        var outfits: [String: [String: [String]]]
        var accessories: [String: [String]]
        struct Hair: Decodable { var back: [String]; var front: [String] }
    }
    private let atlas: Atlas?
    private let cache = NSCache<NSString, UIImage>()
    private init() {
        atlas = Bundle.main.url(forResource: "character_engine", withExtension: "json")
            .flatMap { try? Data(contentsOf: $0) }.flatMap { try? JSONDecoder().decode(Atlas.self, from: $0) }
        cache.countLimit = 192
    }
    func usesAuthored(_ member: CastMember) -> Bool {
        member.recipe == CharacterRecipe.cast[member.id]
    }
    func authoredImage(_ member: CastMember) -> UIImage? {
        guard usesAuthored(member), let index = CastMember.all.firstIndex(where: { $0.id == member.id }) else { return nil }
        let key = ("authored-" + member.id) as NSString
        if let image = cache.object(forKey: key) { return image }
        guard let sheet = UIImage(named: "PixelCastAtlas")?.cgImage else { return nil }
        guard sheet.width == 192, sheet.height == 96,
              let crop = sheet.cropping(to: CGRect(x: index % 4 * 48, y: index / 4 * 48, width: 48, height: 48)) else { return nil }
        let image = UIImage(cgImage: crop)
        cache.setObject(image, forKey: key)
        return image
    }
    func image(for recipe: CharacterRecipe, blink: Bool = false) -> UIImage? {
        guard recipe.isValid, let atlas, atlas.version == 3, atlas.size == 48 else { return nil }
        let key = (CharacterRecipe.fieldNames.map { recipe.value(for: $0) }.joined(separator: "|") + (blink ? "|blink" : "|open")) as NSString
        if let image = cache.object(forKey: key) { return image }
        var palette = atlas.fixed.merging(atlas.palettes[recipe.palette] ?? [:]) { _, value in value }
        palette.merge(atlas.skin_tones[recipe.skin] ?? [:]) { _, value in value }
        palette.merge(atlas.hair_tones[recipe.hairTone] ?? [:]) { _, value in value }
        let layers = [atlas.hair[recipe.hair]?.back, atlas.bodies[recipe.body], atlas.outfits[recipe.outfit]?[recipe.body],
                      (blink ? atlas.blink_faces : atlas.faces)[recipe.face], atlas.details[recipe.detail],
                      atlas.hair[recipe.hair]?.front, atlas.accessories[recipe.accessory]]
        var pixels = [UInt8](repeating: 0, count: 48 * 48 * 4)
        for layer in layers.compactMap({ $0 }) {
            for (y, row) in layer.prefix(48).enumerated() {
                for (x, symbol) in row.prefix(48).enumerated() {
                    guard let hex = palette[String(symbol)], let value = UInt32(hex.dropFirst(), radix: 16) else { continue }
                    let offset = (y * 48 + x) * 4
                    pixels[offset] = UInt8((value >> 16) & 255)
                    pixels[offset + 1] = UInt8((value >> 8) & 255)
                    pixels[offset + 2] = UInt8(value & 255)
                    pixels[offset + 3] = 255
                }
            }
        }
        guard let provider = CGDataProvider(data: Data(pixels) as CFData),
              let cg = CGImage(width: 48, height: 48, bitsPerComponent: 8, bitsPerPixel: 32, bytesPerRow: 192,
                               space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGBitmapInfo(rawValue: CGImageAlphaInfo.last.rawValue),
                               provider: provider, decode: nil, shouldInterpolate: false, intent: .defaultIntent) else { return nil }
        let image = UIImage(cgImage: cg)
        cache.setObject(image, forKey: key)
        return image
    }
}

struct CharacterSprite: View {
    let member: CastMember
    var animate = false
    @Environment(\.displayScale) private var displayScale
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.scenePhase) private var scenePhase
    @State private var blink = false
    private struct BlinkIdentity: Equatable { let recipe: CharacterRecipe?; let enabled: Bool }
    private var motion: BlinkIdentity {
        .init(recipe: member.recipe, enabled: animate && !reduceMotion && scenePhase == .active && !CharacterEngine.shared.usesAuthored(member))
    }
    var body: some View {
        GeometryReader { proxy in
            let side = max(1, floor(min(proxy.size.width, proxy.size.height) * displayScale / 48)) * 48 / displayScale
            Group {
                if let sprite = CharacterEngine.shared.authoredImage(member) {
                    Image(uiImage: sprite).resizable().interpolation(.none)
                } else if let recipe = member.recipe, let sprite = CharacterEngine.shared.image(for: recipe, blink: blink && motion.enabled) {
                    Image(uiImage: sprite).resizable().interpolation(.none)
                } else {
                    Image("Portrait_\(member.portrait)").resizable().interpolation(.none)
                }
            }
            .frame(width: side, height: side)
            .offset(x: floor((proxy.size.width-side)*displayScale/2)/displayScale,
                    y: floor((proxy.size.height-side)*displayScale/2)/displayScale)
        }
        .accessibilityLabel("\(member.name), adult pixel character")
        .task(id: motion) {
            blink = false
            guard motion.enabled, let recipe = member.recipe else { return }
            let offset = CharacterRecipe.fieldNames.map { recipe.value(for: $0) }.joined().utf8.reduce(0) { ($0 + Int($1)) % 1400 }
            do {
                while !Task.isCancelled {
                    try await Task.sleep(for: .milliseconds(4200 + offset))
                    blink = true
                    try await Task.sleep(for: .milliseconds(140))
                    blink = false
                }
            } catch { blink = false }
        }
    }
}

struct FamiliarSprite: View {
    let kind: String
    var size: CGFloat = 32
    @Environment(\.displayScale) private var displayScale
    var body: some View {
        let side = max(1, floor(size * displayScale / 16)) * 16 / displayScale
        Image("Comp_\(kind)").resizable().interpolation(.none)
            .frame(width: side, height: side)
            .offset(x: floor((size-side)*displayScale/2)/displayScale,
                    y: floor((size-side)*displayScale/2)/displayScale)
            .frame(width: size, height: size, alignment: .topLeading)
            .accessibilityHidden(true)
    }
}
