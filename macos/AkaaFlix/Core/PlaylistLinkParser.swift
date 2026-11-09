import Foundation

/// Extracts Xtream Codes details from text found in a QR code or pasted by the user:
///  • `http://host:port/get.php?username=U&password=P&type=m3u_plus`
///  • `http://host:port/player_api.php?username=U&password=P`
///  • JSON `{"name":..., "url":..., "username":..., "password":...}`
///  • `url|username|password` or `url username password`
enum PlaylistLinkParser {

    static func parse(_ raw: String) -> PlaylistInput? {
        let text = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return nil }

        if text.hasPrefix("{"), let data = text.data(using: .utf8),
           let obj = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any] {
            let url = LenientJSON.firstString(obj, "url", "server", "host", "server_url")
            let user = LenientJSON.firstString(obj, "username", "user")
            let pass = LenientJSON.firstString(obj, "password", "pass")
            guard let u = url, let us = user, let p = pass else { return nil }
            return PlaylistInput(name: LenientJSON.firstString(obj, "name", "title") ?? defaultName(u), username: us, password: p, url: u)
        }

        if let comps = URLComponents(string: text), let items = comps.queryItems,
           let user = items.first(where: { $0.name.lowercased() == "username" })?.value,
           let pass = items.first(where: { $0.name.lowercased() == "password" })?.value,
           let normalized = ServerURL.normalize(text) {
            return PlaylistInput(name: defaultName(normalized.baseURL), username: user, password: pass, url: normalized.baseURL)
        }

        let parts = text.components(separatedBy: CharacterSet(charactersIn: "|\n ")).map { $0.trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty }
        if parts.count == 3, ServerURL.normalize(parts[0]) != nil {
            return PlaylistInput(name: defaultName(parts[0]), username: parts[1], password: parts[2], url: parts[0])
        }
        return nil
    }

    private static func defaultName(_ url: String) -> String {
        let host = ServerURL.host(ServerURL.normalize(url)?.baseURL ?? url)
        return host.isEmpty ? "Xtream Codes" : host
    }
}
