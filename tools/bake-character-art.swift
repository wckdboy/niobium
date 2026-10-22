import Foundation
import CoreGraphics
import ImageIO
import UniformTypeIdentifiers

// Compile once with swiftc, then pass the versioned imagegen source and output.
// This packaging step gives both clients identical 48px, hard-alpha sprites.
let args = CommandLine.arguments
if args.count != 3 { fatalError("Usage: bake-character-art SOURCE.png OUTPUT.png") }
guard let source = CGImageSourceCreateWithURL(URL(fileURLWithPath: args[1]) as CFURL, nil),
      let image = CGImageSourceCreateImageAtIndex(source, 0, nil),
      image.width >= 192, image.height >= 96 else { fatalError("Invalid cast source") }
let size = 48, columns = 4, rows = 2
let space = CGColorSpaceCreateDeviceRGB()
var sheet = [UInt8](repeating: 0, count: 192 * 96 * 4)
struct RGB: Hashable {
    var r: Int; var g: Int; var b: Int
    func distance(_ other: RGB) -> Int {
        (r-other.r)*(r-other.r) + (g-other.g)*(g-other.g) + (b-other.b)*(b-other.b)
    }
}
func nearest(_ color: RGB, _ palette: [RGB]) -> Int {
    palette.indices.min { color.distance(palette[$0]) < color.distance(palette[$1]) }!
}
for index in 0..<(columns*rows) {
    let x = index % columns * image.width / columns, y = index / columns * image.height / rows
    guard let cell = image.cropping(to: CGRect(x: x, y: y, width: image.width/columns, height: image.height/rows)) else { fatalError("Missing cell") }
    var rgba = [UInt8](repeating: 0, count: size * size * 4)
    rgba.withUnsafeMutableBytes { bytes in
        guard let context = CGContext(data: bytes.baseAddress, width: size, height: size,
            bitsPerComponent: 8, bytesPerRow: size*4, space: space,
            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue) else { fatalError("No bitmap context") }
        context.interpolationQuality = .high
        context.draw(cell, in: CGRect(x: 1, y: 1, width: 46, height: 46))
    }
    var colors: [RGB?] = [], histogram: [RGB: Int] = [:]
    for pixel in 0..<(size*size) {
        let offset = pixel*4, alpha = Int(rgba[offset+3])
        // One transparent logical pixel on every edge prevents cell leakage.
        if alpha < 128 || pixel%size == 0 || pixel%size == 47 || pixel/size == 0 || pixel/size == 47 {
            colors.append(nil); continue
        }
        let color = RGB(r: min(255,Int(rgba[offset])*255/alpha),
                        g: min(255,Int(rgba[offset+1])*255/alpha), b: min(255,Int(rgba[offset+2])*255/alpha))
        colors.append(color); histogram[color, default: 0] += 1
    }
    precondition(colors.compactMap { $0 }.count > 400, "Empty cast cell")
    let samples = histogram.keys.sorted { a,b in
        a.r == b.r ? (a.g == b.g ? a.b < b.b : a.g < b.g) : a.r < b.r
    }
    var palette = [samples.max { histogram[$0]! < histogram[$1]! }!]
    while palette.count < min(32,samples.count) {
        let next = samples.max { a,b in
            let sa = Double(a.distance(palette[nearest(a,palette)])) * sqrt(Double(histogram[a]!))
            let sb = Double(b.distance(palette[nearest(b,palette)])) * sqrt(Double(histogram[b]!))
            return sa < sb
        }!
        if palette.contains(next) { break }
        palette.append(next)
    }
    for _ in 0..<6 {
        var accum = Array(repeating: (r:0,g:0,b:0,n:0),count:palette.count)
        for color in samples {
            let bucket = nearest(color,palette), n = histogram[color]!
            accum[bucket].r += color.r*n; accum[bucket].g += color.g*n
            accum[bucket].b += color.b*n; accum[bucket].n += n
        }
        for bucket in palette.indices where accum[bucket].n > 0 {
            let v=accum[bucket];palette[bucket]=RGB(r:v.r/v.n,g:v.g/v.n,b:v.b/v.n)
        }
    }
    for pixel in colors.indices {
        guard let color=colors[pixel] else { continue }
        let chosen=palette[nearest(color,palette)]
        let output=((index/columns*size+pixel/size)*192 + index%columns*size+pixel%size)*4
        sheet[output]=UInt8(chosen.r);sheet[output+1]=UInt8(chosen.g);sheet[output+2]=UInt8(chosen.b);sheet[output+3]=255
    }
}
guard let provider = CGDataProvider(data: Data(sheet) as CFData),
      let output = CGImage(width:192,height:96,bitsPerComponent:8,bitsPerPixel:32,bytesPerRow:192*4,
        space:space,bitmapInfo:CGBitmapInfo(rawValue:CGImageAlphaInfo.last.rawValue),provider:provider,decode:nil,shouldInterpolate:false,intent:.defaultIntent),
      let destination = CGImageDestinationCreateWithURL(URL(fileURLWithPath:args[2]) as CFURL,UTType.png.identifier as CFString,1,nil)
else { fatalError("Cannot write atlas") }
CGImageDestinationAddImage(destination,output,nil)
precondition(CGImageDestinationFinalize(destination))
print("Baked eight 48px portraits, 32 colors per cell, hard alpha, independent familiars")
