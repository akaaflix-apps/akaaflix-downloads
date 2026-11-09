import AppKit
import ImageIO
import SwiftUI

/// Memory + disk image cache with downsampling (thousands of posters stay cheap on 8 GB Macs).
final class ImagePipeline {
    static let shared = ImagePipeline()

    private let memory = NSCache<NSString, NSImage>()
    private let session: URLSession
    private let queue = DispatchQueue(label: "akaaflix.images", qos: .userInitiated, attributes: .concurrent)
    private var inflight: [String: [(NSImage?) -> Void]] = [:]
    private let lock = NSLock()

    init(cacheDirectory: URL? = nil) {
        memory.countLimit = 600
        memory.totalCostLimit = 256 * 1024 * 1024
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 20
        config.httpMaximumConnectionsPerHost = 6
        config.requestCachePolicy = .returnCacheDataElseLoad
        let dir = cacheDirectory ?? AppPaths.standard().imageCache
        config.urlCache = URLCache(memoryCapacity: 16 * 1024 * 1024, diskCapacity: 400 * 1024 * 1024, directory: dir)
        config.httpAdditionalHeaders = ["User-Agent": AppInfo.userAgent]
        session = URLSession(configuration: config)
    }

    func cached(_ url: String, maxPixel: CGFloat) -> NSImage? {
        memory.object(forKey: key(url, maxPixel))
    }

    private func key(_ url: String, _ maxPixel: CGFloat) -> NSString { "\(Int(maxPixel))|\(url)" as NSString }

    func load(_ urlString: String, maxPixel: CGFloat, completion: @escaping (NSImage?) -> Void) {
        let k = key(urlString, maxPixel)
        if let img = memory.object(forKey: k) { completion(img); return }
        guard let url = URL(string: urlString) else { completion(nil); return }
        lock.lock()
        if inflight[k as String] != nil {
            inflight[k as String]?.append(completion)
            lock.unlock()
            return
        }
        inflight[k as String] = [completion]
        lock.unlock()

        session.dataTask(with: url) { [weak self] data, response, _ in
            guard let self = self else { return }
            var image: NSImage?
            if let data = data, let code = (response as? HTTPURLResponse)?.statusCode, (200..<300).contains(code) {
                image = ImagePipeline.downsample(data, maxPixel: maxPixel)
            }
            if let img = image {
                let cost = Int(img.size.width * img.size.height * 4)
                self.memory.setObject(img, forKey: k, cost: cost)
            }
            self.lock.lock()
            let callbacks = self.inflight.removeValue(forKey: k as String) ?? []
            self.lock.unlock()
            DispatchQueue.main.async { callbacks.forEach { $0(image) } }
        }.resume()
    }

    static func downsample(_ data: Data, maxPixel: CGFloat) -> NSImage? {
        let options: [CFString: Any] = [kCGImageSourceShouldCache: false]
        guard let source = CGImageSourceCreateWithData(data as CFData, options as CFDictionary) else { return nil }
        let thumbOptions: [CFString: Any] = [
            kCGImageSourceCreateThumbnailFromImageAlways: true,
            kCGImageSourceShouldCacheImmediately: true,
            kCGImageSourceCreateThumbnailWithTransform: true,
            kCGImageSourceThumbnailMaxPixelSize: max(64, maxPixel),
        ]
        guard let cg = CGImageSourceCreateThumbnailAtIndex(source, 0, thumbOptions as CFDictionary) else { return nil }
        return NSImage(cgImage: cg, size: NSSize(width: cg.width, height: cg.height))
    }

    func clear() {
        memory.removeAllObjects()
        session.configuration.urlCache?.removeAllCachedResponses()
    }
}

/// Observable loader for one image view.
final class RemoteImageLoader: ObservableObject {
    @Published var image: NSImage?
    @Published var failed = false
    private var current: String?

    func load(_ url: String?, maxPixel: CGFloat) {
        guard let url = url, !url.isEmpty else { image = nil; failed = true; current = nil; return }
        guard url != current else { return }
        current = url
        failed = false
        if let cached = ImagePipeline.shared.cached(url, maxPixel: maxPixel) { image = cached; return }
        image = nil
        ImagePipeline.shared.load(url, maxPixel: maxPixel) { [weak self] img in
            guard let self = self, self.current == url else { return }
            self.image = img
            self.failed = img == nil
        }
    }
}

enum ImagePlaceholder { case poster, backdrop, channel, person, episode }

/// Network image with branded placeholder (AsyncImage is macOS 12+, so this keeps macOS 11 support).
struct RemoteImage: View {
    let url: String?
    var contentMode: ContentMode = .fill
    var placeholder: ImagePlaceholder = .poster
    var maxPixel: CGFloat = 600
    var title: String?
    @StateObject private var loader = RemoteImageLoader()

    var body: some View {
        ZStack {
            if let img = loader.image {
                Image(nsImage: img)
                    .resizable()
                    .interpolation(.high)
                    .aspectRatio(contentMode: contentMode)
            } else {
                PlaceholderArt(kind: placeholder, title: title, loading: !loader.failed && url != nil)
            }
        }
        .onAppear { loader.load(url, maxPixel: maxPixel) }
        .onChange(of: url) { newValue in loader.load(newValue, maxPixel: maxPixel) }
    }
}

struct PlaceholderArt: View {
    let kind: ImagePlaceholder
    var title: String?
    var loading = false

    private var icon: String {
        switch kind {
        case .poster: return "film"
        case .backdrop: return "sparkles.tv"
        case .channel: return "tv"
        case .person: return "person.fill"
        case .episode: return "play.rectangle"
        }
    }

    var body: some View {
        GeometryReader { geo in
            ZStack {
                linear([Color(hex: 0x1A1B2C), Color(hex: 0x0B0B14)])
                radial([Theme.red.opacity(0.35), .clear], center: UnitPoint(x: 0.2, y: 0.9), end: max(geo.size.width, geo.size.height) * 0.7)
                radial([Theme.blue.opacity(0.3), .clear], center: UnitPoint(x: 0.9, y: 0.1), end: max(geo.size.width, geo.size.height) * 0.6)
                VStack(spacing: 6) {
                    if kind == .channel, let t = title, !t.isEmpty {
                        Text(String(t.prefix(3)).uppercased())
                            .font(.system(size: min(geo.size.width, geo.size.height) * 0.32, weight: .heavy))
                            .foregroundColor(.white.opacity(0.85))
                    } else {
                        Image(systemName: icon)
                            .font(.system(size: min(geo.size.width, geo.size.height) * 0.26, weight: .light))
                            .foregroundColor(.white.opacity(loading ? 0.25 : 0.45))
                    }
                }
            }
        }
    }
}
