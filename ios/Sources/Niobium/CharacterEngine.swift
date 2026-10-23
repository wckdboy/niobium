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
    private struct LookAtlas: Decodable {
        let version: Int
        let size: Int
        let looks: [String: LookArt]
        let accents: [String: [UInt32]]
        struct LookArt: Decodable {
            let colors: [UInt32]
            let pixels: [Int]
            let materials: [Int]
            let shades: [Int]
        }
    }
    private lazy var atlas: Atlas? = Bundle.main.url(forResource: "character_engine", withExtension: "json")
        .flatMap { try? Data(contentsOf: $0) }.flatMap { try? JSONDecoder().decode(Atlas.self, from: $0) }
    private lazy var looks: LookAtlas? = (NSDataAsset(name: "PortraitLookCatalog")?.data)
        .flatMap { try? JSONDecoder().decode(LookAtlas.self, from: $0) }
    private let cache = NSCache<NSString, UIImage>()
    private init() {
        cache.countLimit = 192
    }
    func image(for art: PixelArt) -> UIImage? {
        guard let raw = art.rgba else { return nil }
        let colors = art.palette.map { String($0) }.joined(separator: ",")
        let key = "custom|\(art.size)|\(colors)|\(art.pixels)" as NSString
        if let cached = cache.object(forKey: key) { return cached }
        guard let provider = CGDataProvider(data: raw as CFData),
              let cg = CGImage(width: art.size, height: art.size, bitsPerComponent: 8, bitsPerPixel: 32, bytesPerRow: art.size * 4,
                space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGBitmapInfo(rawValue: CGImageAlphaInfo.last.rawValue),
                provider: provider, decode: nil, shouldInterpolate: false, intent: .defaultIntent) else { return nil }
        let image = UIImage(cgImage: cg); cache.setObject(image, forKey: key); return image
    }
    func image(for look: PixelLook) -> UIImage? {
        if look.isValid, let art = look.customArt { return image(for: art) }
        guard look.isValid, let data = looks, data.version == 1, data.size == 48,
              let art = data.looks[look.portrait], art.pixels.count == 2304,
              art.materials.count == 2304, art.shades.count == 2304 else { return nil }
        let key = ("look|" + [look.portrait, look.accent].joined(separator: "|")) as NSString
        if let cached = cache.object(forKey: key) { return cached }
        let pigments: [[UInt32]?] = [nil, data.accents[look.accent]]
        var pixels = [UInt8](repeating: 0, count: 2304 * 4)
        for i in art.pixels.indices {
            let index = art.pixels[i]
            guard index >= 0, art.colors.indices.contains(index) else { continue }
            let material = art.materials[i], shade = art.shades[i]
            let dye = pigments.indices.contains(material) ? pigments[material] : nil
            let color = dye.flatMap { $0.indices.contains(shade) ? $0[shade] : nil } ?? art.colors[index]
            pixels[i * 4] = UInt8((color >> 16) & 255)
            pixels[i * 4 + 1] = UInt8((color >> 8) & 255)
            pixels[i * 4 + 2] = UInt8(color & 255)
            pixels[i * 4 + 3] = 255
        }
        guard let provider = CGDataProvider(data: Data(pixels) as CFData),
              let cg = CGImage(width: 48, height: 48, bitsPerComponent: 8, bitsPerPixel: 32, bytesPerRow: 192,
                               space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGBitmapInfo(rawValue: CGImageAlphaInfo.last.rawValue),
                               provider: provider, decode: nil, shouldInterpolate: false, intent: .defaultIntent) else { return nil }
        let image = UIImage(cgImage: cg)
        cache.setObject(image, forKey: key)
        return image
    }
    func usesAuthored(_ member: CastMember) -> Bool {
        member.recipe == CharacterRecipe.cast[member.id]
    }
    func authoredImage(_ member: CastMember) -> UIImage? {
        guard usesAuthored(member) else { return nil }
        // Stock and garment variants share one canonical cached RGBA path.
        return image(for: PixelLook(portrait: member.id))
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
        .init(recipe: member.recipe, enabled: member.look == nil && animate && !reduceMotion && scenePhase == .active && !CharacterEngine.shared.usesAuthored(member))
    }
    var body: some View {
        GeometryReader { proxy in
            let side = max(1, floor(min(proxy.size.width, proxy.size.height) * displayScale / 48)) * 48 / displayScale
            Group {
                if let look = member.look, let sprite = CharacterEngine.shared.image(for: look) {
                    Image(uiImage: sprite).resizable().interpolation(.none)
                } else if let sprite = CharacterEngine.shared.authoredImage(member) {
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
    var art: PixelArt? = nil
    @Environment(\.displayScale) private var displayScale
    var body: some View {
        let side = max(1, floor(size * displayScale / 16)) * 16 / displayScale
        (art.flatMap { CharacterEngine.shared.image(for: $0) }.map { Image(uiImage: $0) } ?? Image("Comp_\(kind)")).resizable().interpolation(.none)
            .frame(width: side, height: side)
            .offset(x: floor((size-side)*displayScale/2)/displayScale,
                    y: floor((size-side)*displayScale/2)/displayScale)
            .frame(width: size, height: size, alignment: .topLeading)
            .accessibilityHidden(true)
    }
}
