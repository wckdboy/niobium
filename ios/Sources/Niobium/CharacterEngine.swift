import SwiftUI
import UIKit

// A small immutable layer atlas is decoded once; rendered 48×48 sprites are cached.
@MainActor
final class CharacterEngine {
    static let shared = CharacterEngine()
    private struct Atlas: Decodable {
        var size: Int
        var palettes: [String: [String: String]]
        var fixed: [String: String]
        var bodies: [String: [String]]
        var hair: [String: Hair]
        var faces: [String: [String]]
        var outfits: [String: [String]]
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
        member.recipe == CharacterRecipe.cast[member.id] &&
        CastMember.all.first(where: { $0.id == member.id })?.companion == member.companion
    }
    func authoredImage(_ member: CastMember) -> UIImage? {
        guard usesAuthored(member), let index = CastMember.all.firstIndex(where: { $0.id == member.id }) else { return nil }
        let key = ("authored-" + member.id) as NSString
        if let image = cache.object(forKey: key) { return image }
        guard let sheet = UIImage(named: "PixelCastAtlas")?.cgImage else { return nil }
        let width = CGFloat(sheet.width) / 4, height = CGFloat(sheet.height) / 2
        // Generated source cells include a small overscan. Trim it on every edge
        // so neighbouring portraits never leak into the cached sprite.
        let inset = ceil(min(width, height) * 0.06)
        guard let crop = sheet.cropping(to: CGRect(x: CGFloat(index % 4) * width, y: CGFloat(index / 4) * height, width: width, height: height).insetBy(dx: inset, dy: inset).integral) else { return nil }
        let format = UIGraphicsImageRendererFormat(); format.scale = 1; format.opaque = false
        let image = UIGraphicsImageRenderer(size: CGSize(width: 48, height: 48), format: format).image { context in
            context.cgContext.interpolationQuality = .high
            UIImage(cgImage: crop).draw(in: CGRect(x: 0, y: 0, width: 48, height: 48))
        }
        cache.setObject(image, forKey: key)
        return image
    }
    func image(for recipe: CharacterRecipe) -> UIImage? {
        guard recipe.isValid, let atlas, atlas.size == 48 else { return nil }
        let key = "\(recipe.body)|\(recipe.palette)|\(recipe.hair)|\(recipe.face)|\(recipe.outfit)|\(recipe.accessory)" as NSString
        if let image = cache.object(forKey: key) { return image }
        let palette = atlas.fixed.merging(atlas.palettes[recipe.palette] ?? [:]) { _, value in value }
        let layers = [atlas.hair[recipe.hair]?.back, atlas.bodies[recipe.body], atlas.outfits[recipe.outfit],
                      atlas.faces[recipe.face], atlas.hair[recipe.hair]?.front, atlas.accessories[recipe.accessory]]
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
    var body: some View {
        Group {
            if let sprite = CharacterEngine.shared.authoredImage(member) {
                Image(uiImage: sprite).resizable().interpolation(.none).scaledToFit()
            } else if let recipe = member.recipe, let sprite = CharacterEngine.shared.image(for: recipe) {
                Image(uiImage: sprite).resizable().interpolation(.none).scaledToFit()
            } else {
                Image("Portrait_\(member.portrait)").resizable().interpolation(.none).scaledToFit()
            }
        }
        .accessibilityLabel("\(member.name), adult pixel character")
    }
}
