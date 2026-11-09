import Foundation

/// Normalises the many server URL formats users paste from their provider, e.g.
/// `host:8080`, `http://host:8080/`, `https://host/player_api.php?username=..`,
/// `http://1.2.3.4:25461/get.php?...`, `http://host/c/` or `[::1]:80`.
enum ServerURL {

    struct Normalized: Equatable {
        /// Base URL ending with "/", e.g. `http://host:8080/`.
        var baseURL: String
        /// True when the user did not type a scheme (HTTPS is probed first, then HTTP).
        var schemeWasGuessed: Bool
    }

    static func normalize(_ raw: String, defaultScheme: String = "http") -> Normalized? {
        var input = raw.trimmingCharacters(in: .whitespacesAndNewlines).replacingOccurrences(of: "\\", with: "/")
        if input.isEmpty || input.contains(where: { $0.isWhitespace }) { return nil }
        let hasScheme = input.contains("://")
        if hasScheme {
            let scheme = input.components(separatedBy: "://")[0].lowercased()
            guard scheme == "http" || scheme == "https" else { return nil }
        } else {
            input = "\(defaultScheme)://\(input)"
        }
        guard var comps = URLComponents(string: input), let host = comps.host, !host.isEmpty else { return nil }
        let bareHost = host.trimmingCharacters(in: CharacterSet(charactersIn: "[]"))
        guard isPlausibleHost(bareHost) else { return nil }
        if let port = comps.port, !(1...65535).contains(port) { return nil }
        comps.scheme = comps.scheme?.lowercased()
        comps.query = nil
        comps.fragment = nil
        comps.user = nil
        comps.password = nil
        var segments = comps.path.split(separator: "/").map(String.init).filter { !$0.isEmpty }
        while let last = segments.last, last.lowercased().hasSuffix(".php") { segments.removeLast() }
        comps.path = segments.isEmpty ? "/" : "/" + segments.joined(separator: "/") + "/"
        guard var base = comps.string else { return nil }
        if !base.hasSuffix("/") { base += "/" }
        return Normalized(baseURL: base, schemeWasGuessed: !hasScheme)
    }

    /// Same server reachable with the other scheme (used when the scheme was guessed).
    static func withScheme(_ baseURL: String, scheme: String) -> String? {
        guard var comps = URLComponents(string: baseURL) else { return nil }
        comps.scheme = scheme
        return comps.string
    }

    private static func isPlausibleHost(_ host: String) -> Bool {
        if host.contains(":") { return true } // IPv6
        if host.lowercased() == "localhost" { return true }
        let parts = host.split(separator: ".", omittingEmptySubsequences: false)
        if parts.count == 4, parts.allSatisfy({ !$0.isEmpty && $0.allSatisfy(\.isNumber) }) {
            return parts.allSatisfy { (Int($0) ?? 999) <= 255 }
        }
        return host.contains(".") && parts.allSatisfy { !$0.isEmpty && $0.count <= 63 }
    }

    /// Scheme, host and port only (no credentials, path or query) — safe for UI and logs.
    static func displayHost(_ baseURL: String) -> String {
        guard let comps = URLComponents(string: baseURL), let scheme = comps.scheme, let host = comps.host else { return "" }
        let defaultPort = scheme == "https" ? 443 : 80
        let hostPart = host.contains(":") && !host.hasPrefix("[") ? "[\(host)]" : host
        if let port = comps.port, port != defaultPort { return "\(scheme)://\(hostPart):\(port)" }
        return "\(scheme)://\(hostPart)"
    }

    static func host(_ baseURL: String) -> String {
        URLComponents(string: baseURL)?.host ?? ""
    }
}
