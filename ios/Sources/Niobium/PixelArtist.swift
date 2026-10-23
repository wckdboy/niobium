import SwiftUI
import ImageIO

@MainActor
protocol ArtistKeyVault {
    func read() -> String?
    func save(_ value: String) -> Bool
    func remove()
}

struct KeychainArtistKeys: ArtistKeyVault {
    func read() -> String? { Keychain.get(service: PixelArtist.keyService, account: "openai") }
    func save(_ value: String) -> Bool { Keychain.set(value, service: PixelArtist.keyService, account: "openai") }
    func remove() { Keychain.delete(service: PixelArtist.keyService, account: "openai") }
}

struct PixelArtJob: Codable {
    var target: String
    var phase: String
    var responseID: String?
    var candidate: PixelArt?
    var message: String?
}

/// The paid POST is issued once. Only saved response IDs are polled on resume.
@MainActor
final class PixelArtist: ObservableObject {
    static let shared = PixelArtist()
    static let keyService = "ai.wckd.niobium.image-provider"
    @Published private(set) var job: PixelArtJob?
    @Published private(set) var hasKey = false
    private static let networkSession = URLSession(configuration: .ephemeral, delegate: ArtistRedirectPolicy(), delegateQueue: nil)
    private let session: URLSession
    private let defaults: UserDefaults
    private let keys: any ArtistKeyVault
    private let pollInterval: Duration
    private var task: Task<Void, Never>?
    private var generation = UUID()
    private var observers: [NSObjectProtocol] = []
    var busy: Bool { ["submitting", "queued", "in_progress"].contains(job?.phase ?? "") }

    init(session: URLSession? = nil, defaults: UserDefaults = .standard, keys: (any ArtistKeyVault)? = nil, pollInterval: Duration = .seconds(3)) {
        self.session = session ?? Self.networkSession; self.defaults = defaults; self.keys = keys ?? KeychainArtistKeys(); self.pollInterval = pollInterval
        job = defaults.data(forKey: "niobium.artist.job").flatMap { try? JSONDecoder().decode(PixelArtJob.self, from: $0) }
        hasKey = !(self.keys.read() ?? "").isEmpty
        if job?.phase == "submitting", job?.responseID == nil {
            job?.phase = "unknown"
            job?.message = "The request was interrupted before its receipt arrived. Check provider usage before starting another design."
            persist()
        }
        observers.append(NotificationCenter.default.addObserver(forName: UIApplication.willResignActiveNotification, object: nil, queue: .main) { [weak self] _ in
            Task { @MainActor in self?.pausePolling() }
        })
        observers.append(NotificationCenter.default.addObserver(forName: UIApplication.didBecomeActiveNotification, object: nil, queue: .main) { [weak self] _ in
            Task { @MainActor in self?.resume() }
        })
    }
    func saveKey(_ key: String) -> Bool {
        let clean = key.trimmingCharacters(in: .whitespacesAndNewlines)
        guard (20...512).contains(clean.utf8.count), !clean.contains(where: { $0.isWhitespace }),
              keys.save(clean) else { return false }
        hasKey = true; resume(); return true
    }
    func removeKey() {
        guard !busy else { return }
        keys.remove(); hasKey = false
    }
    private func persist() {
        if let job, let raw = try? JSONEncoder().encode(job) { defaults.set(raw, forKey: "niobium.artist.job") }
    }
    nonisolated static func body(prompt: String, target: String, referencePNG: Data?) -> JSONObject {
        let subject = target == "companion"
            ? "Exactly one freaky adorable fantasy/cyborg companion creature, not a human; expressive eyes, distinct silhouette, no other character. Design for a 16 by 16 logical-pixel sprite."
            : "Exactly one unambiguously adult person age 28–45, mature facial structure and adult proportions, waist-up. Attractive fantasy/cyborg design; bold skimpy opaque clothing, stylish cutouts, straps or cropped armor. All intimate areas fully covered; non-explicit, no nudity, no sexual activity, no youthful or school styling. Design for a 48 by 48 logical-pixel portrait."
        let instructions = "Generate production game sprite art. " + subject + " Match the supplied reference identity when present; change only the requested clothing/style/features. Transparent background, fully contained with generous empty margins, centered; no labels, text, watermark, frame, backdrop, ground shadow, extra creature or interface. Crisp intentional square pixel clusters, limited 24–32 color palette, clear eyes and selective high-contrast shading, premium 16-bit RPG art; avoid smooth painting or blur. Owner’s design brief: " + prompt
        var content: [JSONObject] = [["type": "input_text", "text": instructions]]
        if let referencePNG { content.append(["type": "input_image", "image_url": "data:image/png;base64," + referencePNG.base64EncodedString()]) }
        return ["model": "gpt-6-astra", "background": true, "store": true,
                "input": [["role": "user", "content": content]],
                "tools": [["type": "image_generation", "model": "gpt-image-2.5-sunburst",
                           "background": "transparent", "output_format": "png", "quality": "medium", "size": "1024x1024"]]]
    }
    func start(prompt: String, target: String, referencePNG: Data?) {
        guard !busy, hasKey, ["pixel", "companion"].contains(target), (1...2000).contains(prompt.trimmingCharacters(in: .whitespacesAndNewlines).count) else { return }
        task?.cancel(); let epoch = UUID(); generation = epoch
        job = .init(target: target, phase: "submitting"); persist()
        // Keep the receipt request alive if the scene backgrounds during POST.
        let background = UIApplication.shared.beginBackgroundTask(withName: "Pixel design receipt")
        task = Task {
            defer { UIApplication.shared.endBackgroundTask(background); if generation == epoch { task = nil; if busy, job?.responseID != nil, UIApplication.shared.applicationState == .active { resume() } } }
            do {
                let value = try await request(method: "POST", suffix: "", body: Self.body(prompt: prompt, target: target, referencePNG: referencePNG))
                try await consume(value)
            } catch is CancellationError { }
            catch {
                guard generation == epoch else { return }
                let http = (error as? ArtistHTTPError)?.code ?? 0
                let known = job?.responseID != nil || ((400..<500).contains(http) && http != 408)
                job?.phase = known ? "failed" : "unknown"
                job?.message = known ? error.localizedDescription : "The receipt did not arrive. Check provider usage before starting another design; it may still be running."
                persist()
            }
        }
    }
    func pausePolling() {
        guard job?.responseID != nil, busy else { return }
        generation = UUID(); task?.cancel(); task = nil
    }
    func resume() {
        guard task == nil, busy, let id = job?.responseID, Self.validID(id), hasKey else { return }
        let epoch = UUID(); generation = epoch
        task = Task {
            defer { if generation == epoch { task = nil } }
            while !Task.isCancelled, busy {
                do {
                    try await Task.sleep(for: pollInterval)
                    try await consume(request(method: "GET", suffix: "/" + id))
                } catch is CancellationError { return }
                catch let error as ArtistHTTPError {
                    if error.code < 500 && error.code != 429 { job?.phase = "failed" }
                    job?.message = error.localizedDescription; persist()
                    if !busy { return }
                } catch {
                    if Task.isCancelled { return }
                    job?.message = "Waiting for a connection. Your saved design receipt is safe."; persist()
                }
            }
        }
    }
    func cancel() {
        guard let id = job?.responseID, busy, Self.validID(id) else { return }
        pausePolling(); let epoch = UUID(); generation = epoch
        task = Task {
            defer { if generation == epoch { task = nil } }
            do { try await consume(request(method: "POST", suffix: "/" + id + "/cancel", body: [:])) }
            catch is CancellationError { }
            catch { job?.message = "Cancellation wasn’t confirmed. Resume to check the design’s status."; persist() }
        }
    }
    nonisolated static func validID(_ id: String) -> Bool { id.range(of: "^resp_[A-Za-z0-9_-]{1,160}$", options: .regularExpression) != nil }
    private func request(method: String, suffix: String, body: JSONObject? = nil) async throws -> JSONObject {
        guard let key = keys.read(), !key.isEmpty else { throw ArtistHTTPError(code: 401) }
        var request = URLRequest(url: URL(string: "https://api.openai.com/v1/responses" + suffix)!)
        request.httpMethod = method; request.timeoutInterval = 60; request.cachePolicy = .reloadIgnoringLocalCacheData
        request.setValue("no-store", forHTTPHeaderField: "Cache-Control")
        request.setValue("Bearer " + key, forHTTPHeaderField: "Authorization")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        if let body { request.httpBody = try JSONSerialization.data(withJSONObject: body) }
        let (raw, response) = try await session.data(for: request)
        guard let http = response as? HTTPURLResponse else { throw ArtistHTTPError(code: 0) }
        guard (200...299).contains(http.statusCode) else { throw ArtistHTTPError(code: http.statusCode) }
        guard raw.count <= 32 * 1024 * 1024, let value = try JSONSerialization.jsonObject(with: raw) as? JSONObject else { throw ArtistHTTPError(code: 0) }
        return value
    }
    private func consume(_ value: JSONObject) async throws {
        try Task.checkCancellation()
        guard let id = value.string("id"), Self.validID(id), let phase = value.string("status"),
              ["queued", "in_progress", "completed", "failed", "incomplete", "cancelled"].contains(phase) else { throw ArtistHTTPError(code: 0) }
        if let previous = job?.responseID, previous != id { throw ArtistHTTPError(code: 0) }
        job?.responseID = id; job?.phase = phase == "completed" ? "in_progress" : phase; job?.message = nil; persist()
        if phase == "completed" {
            guard let encoded = value.objects("output").first(where: { $0.string("type") == "image_generation_call" })?.string("result"),
                  encoded.utf8.count <= 28 * 1024 * 1024 else { throw ArtistHTTPError(code: 0) }
            let size = job?.target == "companion" ? 16 : 48
            let art = try await Task.detached(priority: .userInitiated) {
                guard let raw = Data(base64Encoded: encoded) else { throw ArtistHTTPError(code: 0) }
                return try PixelArtBaker.bake(raw, size: size)
            }.value
            try Task.checkCancellation()
            job?.candidate = art; job?.phase = "completed"; persist()
        } else if ["failed", "incomplete"].contains(phase) { job?.message = "The provider couldn’t finish this design. Your current art is unchanged."; persist() }
    }
}

private final class ArtistRedirectPolicy: NSObject, URLSessionTaskDelegate, @unchecked Sendable {
    func urlSession(_ session: URLSession, task: URLSessionTask, willPerformHTTPRedirection response: HTTPURLResponse,
                    newRequest request: URLRequest, completionHandler: @escaping (URLRequest?) -> Void) {
        completionHandler(nil)
    }
}

struct ArtistHTTPError: LocalizedError {
    let code: Int
    var errorDescription: String? {
        switch code {
        case 401, 403: return "Check your OpenAI image key and model access."
        case 404: return "The saved design is unavailable or expired. Your current art is unchanged."
        case 429: return "The image provider reached its usage limit. Try checking the saved design later."
        case 0: return "The provider returned an unusable image design. Your current art is unchanged."
        default: return "The image provider declined the request (HTTP \(code)). Check model access and usage."
        }
    }
}

/// Offline conversion runs off the main thread; each result is at most 32 colors.
enum PixelArtBaker {
    nonisolated static func bake(_ data: Data, size: Int) throws -> PixelArt {
        guard [16,48].contains(size), data.count <= 21 * 1024 * 1024,
              let source = CGImageSourceCreateWithData(data as CFData, nil),
              CGImageSourceGetType(source) as String? == "public.png",
              let properties = CGImageSourceCopyPropertiesAtIndex(source, 0, nil) as? [CFString: Any],
              let width = properties[kCGImagePropertyPixelWidth] as? Int, let height = properties[kCGImagePropertyPixelHeight] as? Int,
              (size...2048).contains(width), (size...2048).contains(height),
              let image = CGImageSourceCreateImageAtIndex(source, 0, nil) else { throw ArtistHTTPError(code: 0) }
        var bytes = [UInt8](repeating: 0, count: size*size*4)
        bytes.withUnsafeMutableBytes { buffer in
            let context = CGContext(data: buffer.baseAddress, width: size, height: size, bitsPerComponent: 8, bytesPerRow: size*4,
                space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
            context.interpolationQuality = .high
            let scale = min(Double(size-2)/Double(width), Double(size-2)/Double(height))
            let w = Double(width)*scale, h = Double(height)*scale
            context.draw(image, in: CGRect(x: (Double(size)-w)/2, y: (Double(size)-h)/2, width: w, height: h))
        }
        let corners = [size+1,size+size-2,(size-2)*size+1,(size-2)*size+size-2]
        guard corners.filter({ bytes[$0*4+3] < 128 }).count >= 2 else { throw ArtistHTTPError(code: 0) }
        var colors: [UInt32?] = [], histogram: [UInt32:Int] = [:]
        for i in 0..<(size*size) {
            let alpha = Int(bytes[i*4+3])
            if alpha < 128 || i%size == 0 || i%size == size-1 || i/size == 0 || i/size == size-1 { colors.append(nil); continue }
            let r = UInt32(min(255,Int(bytes[i*4])*255/alpha)), g = UInt32(min(255,Int(bytes[i*4+1])*255/alpha)), b = UInt32(min(255,Int(bytes[i*4+2])*255/alpha))
            let color = r << 16 | g << 8 | b
            colors.append(color); histogram[color, default: 0] += 1
        }
        guard histogram.values.reduce(0,+) >= size*size/12 else { throw ArtistHTTPError(code: 0) }
        let samples = histogram.keys.sorted()
        func distance(_ a: UInt32, _ b: UInt32) -> Int {
            [16,8,0].reduce(0) { sum, shift in let d = Int(a >> shift & 255) - Int(b >> shift & 255); return sum+d*d }
        }
        var palette = [samples.max { histogram[$0]! < histogram[$1]! }!]
        func nearest(_ color: UInt32) -> Int { palette.indices.min { distance(color,palette[$0]) < distance(color,palette[$1]) }! }
        while palette.count < min(32,samples.count) {
            let next = samples.max { Double(distance($0,palette[nearest($0)])) * sqrt(Double(histogram[$0]!)) < Double(distance($1,palette[nearest($1)])) * sqrt(Double(histogram[$1]!)) }!
            if palette.contains(next) { break }; palette.append(next)
        }
        for _ in 0..<4 {
            var sums = Array(repeating: [0,0,0,0], count: palette.count)
            for c in samples { let i = nearest(c), n = histogram[c]!; sums[i][0] += Int(c >> 16 & 255)*n; sums[i][1] += Int(c >> 8 & 255)*n; sums[i][2] += Int(c & 255)*n; sums[i][3] += n }
            for i in palette.indices where sums[i][3] > 0 { let s = sums[i]; palette[i] = UInt32(s[0]/s[3]) << 16 | UInt32(s[1]/s[3]) << 8 | UInt32(s[2]/s[3]) }
        }
        let result = PixelArt(size: size, palette: palette, pixels: Data(colors.map { $0.map { UInt8(nearest($0)) } ?? 255 }).base64EncodedString())
        guard result.isValid else { throw ArtistHTTPError(code: 0) }; return result
    }
}

struct PixelArtistButton: View {
    var target: String
    var reference: PixelArt? = nil
    var referenceImage: UIImage? = nil
    var onApply: (PixelArt) -> Void
    @State private var open = false
    var body: some View {
        Button { open = true } label: { PixelActionLabel("Design with AI", icon: .spark) }
            .buttonStyle(NBQuietButtonStyle()).accessibilityIdentifier("artist.open." + target)
            .sheet(isPresented: $open) { PixelArtistView(target: target, referenceImage: reference.flatMap { CharacterEngine.shared.image(for: $0) } ?? referenceImage, onApply: onApply) }
    }
}

private struct PixelArtistView: View {
    let target: String
    var referenceImage: UIImage?
    var onApply: (PixelArt) -> Void
    @ObservedObject private var artist = PixelArtist.shared
    @Environment(\.dismiss) private var dismiss
    @State private var prompt = ""
    @State private var key = ""
    @State private var configuring = false
    @State private var keyError = false
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    Text(target == "companion" ? "A little strange. All yours." : "A look of their own.").font(.nbDisplay(25)).foregroundStyle(NB.text)
                    if !artist.hasKey || configuring {
                        SecureField("OpenAI API key", text: $key).textInputAutocapitalization(.never).autocorrectionDisabled().font(.nbMono(13)).padding(14).pixelPanel()
                        Button { keyError = !artist.saveKey(key); if !keyError { key = ""; configuring = false } } label: { PixelActionLabel("Save key", icon: .shield) }.buttonStyle(NBQuietButtonStyle()).disabled(key.isEmpty)
                        if keyError { Text("Couldn’t save that key securely.").font(.nbBody(12)).foregroundStyle(NB.rose) }
                    }
                    TextField(target == "companion" ? "A translucent jelly fox with four eyes…" : "A moonlit corset, silver buckles, violet cloak…", text: $prompt, axis: .vertical)
                        .lineLimit(3...6).font(.nbBody(14)).padding(14).pixelPanel().accessibilityIdentifier("artist.prompt")
                    Text("Uses your OpenAI key. The brief and reference art go to the provider; usage charges apply. Designs are stored by the provider so you can return later.")
                        .font(.nbBody(11)).foregroundStyle(NB.secondary)
                    if artist.busy {
                        HStack(spacing: 12) { ProgressView().tint(NB.ion); Text(artist.job?.phase == "submitting" ? "Starting design…" : "Designing… You can close this.").font(.nbBody(13)).foregroundStyle(NB.text) }
                        if artist.job?.responseID != nil {
                            HStack { Button("Check status") { artist.resume() }.foregroundStyle(NB.secondary); Spacer(); Button("Cancel design") { artist.cancel() }.foregroundStyle(NB.rose) }.font(.nbBody(13))
                        }
                    } else {
                        Button { artist.start(prompt: prompt, target: target, referencePNG: referenceImage?.pngData()) } label: { PixelActionLabel("Generate design", icon: .spark) }
                            .buttonStyle(NBPrimaryButtonStyle()).disabled(!artist.hasKey || prompt.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || prompt.count > 2000)
                            .accessibilityIdentifier("artist.generate")
                    }
                    if let job = artist.job, let art = job.candidate, job.target == target, let image = CharacterEngine.shared.image(for: art) {
                        Image(uiImage: image).resizable().interpolation(.none).scaledToFit().frame(maxWidth: .infinity).frame(height: 192).background(PixelButtonShape().fill(NB.fillSoft))
                        Button { onApply(art); dismiss() } label: { PixelActionLabel("Use this design", icon: .pixels) }.buttonStyle(NBPrimaryButtonStyle()).accessibilityIdentifier("artist.apply")
                        Text("Your persona and access stay as reviewed.").font(.nbBody(11)).foregroundStyle(NB.secondary)
                    }
                    if let message = artist.job?.message { Text(message).font(.nbBody(12)).foregroundStyle(NB.secondary) }
                    if artist.hasKey, !artist.busy {
                        HStack { Button("Replace key") { configuring.toggle() }; Spacer(); Button("Remove key") { artist.removeKey() } }.font(.nbBody(12)).foregroundStyle(NB.secondary)
                    }
                }.padding(20)
            }.background(NB.void).navigationTitle("AI atelier").navigationBarTitleDisplayMode(.inline)
                .toolbar { ToolbarItem(placement: .topBarTrailing) { Button("Done") { dismiss() } } }
                .task { artist.resume() }
        }
    }
}

struct CompanionArtEditor: View {
    let kind: String
    @Binding var art: PixelArt?
    @State private var history: [PixelArt?] = []
    var body: some View {
        VStack(spacing: 8) {
            PixelArtistButton(target: "companion", reference: art, referenceImage: UIImage(named: "Comp_" + kind)) { value in
                history = Array((history + [art]).suffix(12)); art = value
            }
            if !history.isEmpty {
                Button { art = history.removeLast() } label: { PixelActionLabel("Undo creature design", icon: .back) }.buttonStyle(NBQuietButtonStyle(compact: true))
            }
        }.onChange(of: kind) { _, _ in history = [] }
    }
}
