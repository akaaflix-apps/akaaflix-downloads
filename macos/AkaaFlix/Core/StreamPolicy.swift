import Foundation

/// Live stream container/protocol formats Akaa Flix can request from an Xtream server.
enum LiveStreamFormat: String, Codable, CaseIterable, Hashable {
    case mpegTS = "ts"
    case hls = "m3u8"
    case dash = "mpd"
    case rtmp = "rtmp"
    case rtsp = "rtsp"
    case http = "mp4"

    var displayName: String {
        switch self {
        case .mpegTS: return "MPEG-TS"
        case .hls: return "HLS"
        case .dash: return "DASH"
        case .rtmp: return "RTMP"
        case .rtsp: return "RTSP"
        case .http: return "HTTP"
        }
    }

    var fileHint: String {
        switch self {
        case .mpegTS: return "(.ts)"
        case .hls: return "(.m3u8)"
        case .dash: return "(.mpd)"
        case .rtmp: return "(.rtmp)"
        case .rtsp: return "(.rtsp)"
        case .http: return "(.mp4)"
        }
    }
}

/// The live-stream priority is a product rule, not a preference:
///   1. MPEG-TS (.ts)   2. HLS (.m3u8)
/// It cannot be reordered by settings, by the playback engine, or by the CPU architecture.
/// Optional compatibility formats (DASH/RTMP/RTSP/HTTP) may only be appended *after* these two.
enum LiveStreamPolicy {

    static let mandatoryOrder: [LiveStreamFormat] = [.mpegTS, .hls]

    static let optionalCompatibilityFormats: [LiveStreamFormat] = [.dash, .rtmp, .rtsp, .http]

    /// Ordered list of formats to attempt for a live channel.
    static func attemptOrder(enabledCompatibilityFormats: Set<LiveStreamFormat>, rtmpPort: String?) -> [LiveStreamFormat] {
        var order = mandatoryOrder
        for format in optionalCompatibilityFormats where enabledCompatibilityFormats.contains(format) {
            if format == .rtmp, !isUsablePort(rtmpPort) { continue }
            order.append(format)
        }
        return order
    }

    static func isUsablePort(_ port: String?) -> Bool {
        guard let p = port.flatMap({ Int($0.trimmingCharacters(in: .whitespaces)) }) else { return false }
        return (1...65535).contains(p)
    }
}

/// A resolved, playable URL. `description` never prints the URL because it contains credentials.
struct StreamCandidate: Equatable, CustomStringConvertible {
    var url: URL
    var format: LiveStreamFormat?
    /// True for adaptive (HLS/DASH) sources where a preferred resolution can be requested.
    var isAdaptive: Bool

    var description: String { "StreamCandidate(format: \(format?.displayName ?? "file"), adaptive: \(isAdaptive))" }
}

/// Builds Xtream Codes stream URLs:
///   live:    {base}live/{user}/{pass}/{id}.{ext}
///   movie:   {base}movie/{user}/{pass}/{id}.{ext}
///   episode: {base}series/{user}/{pass}/{id}.{ext}
///   rtmp:    rtmp://{host}:{rtmpPort}/live/{user}/{pass}/{id}
enum StreamURLBuilder {

    static func live(_ creds: XtreamCredentials, streamID: Int, format: LiveStreamFormat, rtmpPort: String?) -> StreamCandidate? {
        let user = encode(creds.username), pass = encode(creds.password)
        let urlString: String
        switch format {
        case .rtmp:
            guard LiveStreamPolicy.isUsablePort(rtmpPort) else { return nil }
            urlString = "rtmp://\(hostForURL(creds.baseURL)):\(rtmpPort!.trimmingCharacters(in: .whitespaces))/live/\(user)/\(pass)/\(streamID)"
        case .rtsp:
            urlString = "rtsp://\(hostForURL(creds.baseURL))/live/\(user)/\(pass)/\(streamID)"
        default:
            urlString = "\(creds.base)live/\(user)/\(pass)/\(streamID).\(format.rawValue)"
        }
        guard let url = URL(string: urlString) else { return nil }
        return StreamCandidate(url: url, format: format, isAdaptive: format == .hls || format == .dash)
    }

    /// Ordered live candidates following `LiveStreamPolicy`.
    static func liveCandidates(_ creds: XtreamCredentials, streamID: Int, enabledCompatibilityFormats: Set<LiveStreamFormat>, rtmpPort: String?) -> [StreamCandidate] {
        LiveStreamPolicy.attemptOrder(enabledCompatibilityFormats: enabledCompatibilityFormats, rtmpPort: rtmpPort)
            .compactMap { live(creds, streamID: streamID, format: $0, rtmpPort: rtmpPort) }
    }

    static func movie(_ creds: XtreamCredentials, streamID: Int, containerExtension: String?) -> StreamCandidate? {
        let ext = sanitizeExtension(containerExtension) ?? "mp4"
        guard let url = URL(string: "\(creds.base)movie/\(encode(creds.username))/\(encode(creds.password))/\(streamID).\(ext)") else { return nil }
        return StreamCandidate(url: url, format: nil, isAdaptive: ext == "m3u8" || ext == "mpd")
    }

    static func episode(_ creds: XtreamCredentials, episodeID: String, containerExtension: String?) -> StreamCandidate? {
        let ext = sanitizeExtension(containerExtension) ?? "mp4"
        guard let url = URL(string: "\(creds.base)series/\(encode(creds.username))/\(encode(creds.password))/\(encode(episodeID)).\(ext)") else { return nil }
        return StreamCandidate(url: url, format: nil, isAdaptive: ext == "m3u8" || ext == "mpd")
    }

    static func sanitizeExtension(_ ext: String?) -> String? {
        guard var e = ext?.trimmingCharacters(in: .whitespaces).lowercased(), !e.isEmpty else { return nil }
        while e.hasPrefix(".") { e.removeFirst() }
        guard (2...5).contains(e.count), e.allSatisfy({ ($0.isLetter || $0.isNumber) && $0.isASCII }) else { return nil }
        return e
    }

    private static func hostForURL(_ baseURL: String) -> String {
        let host = ServerURL.host(baseURL)
        return host.contains(":") && !host.hasPrefix("[") ? "[\(host)]" : host
    }

    /// Path-segment encoding for credentials containing special characters.
    static func encode(_ value: String) -> String {
        var out = ""
        let safe = Set("-._~!$&'()*+,;=:@")
        for byte in value.utf8 {
            let c = Character(Unicode.Scalar(byte))
            if byte < 128 && (c.isLetter || c.isNumber || safe.contains(c)) {
                out.append(c)
            } else {
                out += String(format: "%%%02X", byte)
            }
        }
        return out
    }

    /// Removes credentials from a stream URL so it can be logged or shown in diagnostics.
    static func redact(_ url: URL, creds: XtreamCredentials) -> String {
        var s = url.absoluteString
        for secret in [encode(creds.password), encode(creds.username)] where secret.count >= 2 {
            s = s.replacingOccurrences(of: "/\(secret)/", with: "/•••/")
        }
        return s
    }
}

/// Engine-agnostic failover planner for one playback session.
///
/// * Before a candidate is confirmed playing, a failure moves to the next candidate in the fixed
///   order (MPEG-TS → HLS → optional compatibility formats).
/// * After a candidate has played, a failure (dropped stream) reconnects starting again from the
///   top of the priority list, up to `maxReconnects` times with exponential back-off.
final class FailoverPlanner {

    enum Decision: Equatable {
        case play(StreamCandidate, delay: TimeInterval)
        case giveUp
    }

    let candidates: [StreamCandidate]
    let maxReconnects: Int
    private(set) var index = 0
    private(set) var reconnects = 0
    private(set) var confirmed = false
    private(set) var attempts: [(LiveStreamFormat?, Bool)] = []

    init(candidates: [StreamCandidate], maxReconnects: Int = 5) {
        self.candidates = candidates
        self.maxReconnects = maxReconnects
    }

    var current: StreamCandidate? { candidates.indices.contains(index) ? candidates[index] : nil }

    func start() -> Decision {
        index = 0
        confirmed = false
        guard let first = current else { return .giveUp }
        return .play(first, delay: 0)
    }

    /// Call when the current candidate is confirmed to render media.
    func didConfirmPlayback() {
        if !confirmed { attempts.append((current?.format, true)) }
        confirmed = true
        reconnects = 0
    }

    func didFail() -> Decision {
        if confirmed {
            // Stream dropped after playing: reconnect from the top of the fixed priority list.
            confirmed = false
            reconnects += 1
            guard reconnects <= maxReconnects, !candidates.isEmpty else { return .giveUp }
            index = 0
            return .play(candidates[0], delay: min(16, pow(2, Double(reconnects - 1))))
        }
        attempts.append((current?.format, false))
        index += 1
        if let next = current { return .play(next, delay: 0) }
        return .giveUp
    }

    /// Human-readable trail such as "MPEG-TS ✕ → HLS ✓" for the playback info overlay.
    var trail: String {
        attempts.map { "\($0.0?.displayName ?? "File") \($0.1 ? "✓" : "✕")" }.joined(separator: " → ")
    }
}
