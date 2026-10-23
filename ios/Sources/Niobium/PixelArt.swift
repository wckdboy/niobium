import Foundation

/// Portable, bounded indexed pixels. No remote URL or executable image content.
struct PixelArt: Codable, Equatable, Hashable {
    var version = 1
    let size: Int
    let palette: [UInt32]
    let pixels: String

    var indices: [UInt8]? {
        guard version == 1, [16, 48].contains(size), (1...32).contains(palette.count),
              palette.allSatisfy({ $0 <= 0xFFFFFF }), pixels.utf8.count <= 3072,
              let data = Data(base64Encoded: pixels), data.count == size * size else { return nil }
        let values = Array(data)
        guard values.allSatisfy({ $0 == 255 || Int($0) < palette.count }), values.contains(where: { $0 != 255 }),
              (0..<size).allSatisfy({ values[$0] == 255 && values[(size-1)*size+$0] == 255 && values[$0*size] == 255 && values[$0*size+size-1] == 255 }) else { return nil }
        return values
    }
    var isValid: Bool { indices != nil }
    var rgba: Data? {
        guard let values = indices else { return nil }
        return Data(values.flatMap { index -> [UInt8] in
            guard index != 255 else { return [0, 0, 0, 0] }
            let color = palette[Int(index)]
            return [UInt8(color >> 16 & 255), UInt8(color >> 8 & 255), UInt8(color & 255), 255]
        })
    }
}
