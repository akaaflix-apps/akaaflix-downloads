import Foundation

/// Text helpers for search normalisation, quality badges, years and lists.
enum ContentText {

    /// Lower-cased, accent/diacritic-free form used for case-insensitive search.
    static func normalize(_ input: String) -> String {
        var s = input.decomposedStringWithCanonicalMapping
        s = String(String.UnicodeScalarView(s.unicodeScalars.filter { scalar in
            // Combining marks, Arabic harakat (064B–065F), superscript alef (0670) and tatweel (0640).
            if scalar.properties.generalCategory == .nonspacingMark { return false }
            let v = scalar.value
            if (0x064B...0x065F).contains(v) || v == 0x0670 || v == 0x0640 { return false }
            return true
        }))
        s = s.replacingOccurrences(of: "أ", with: "ا")
            .replacingOccurrences(of: "إ", with: "ا")
            .replacingOccurrences(of: "آ", with: "ا")
            .replacingOccurrences(of: "ة", with: "ه")
            .replacingOccurrences(of: "ى", with: "ي")
        s = s.lowercased()
        let parts = s.split(whereSeparator: { $0.isWhitespace })
        return parts.joined(separator: " ")
    }

    /// Escapes a user query for a SQL LIKE pattern with `ESCAPE '\'`.
    static func likePattern(_ query: String) -> String {
        let n = normalize(query)
            .replacingOccurrences(of: "\\", with: "\\\\")
            .replacingOccurrences(of: "%", with: "\\%")
            .replacingOccurrences(of: "_", with: "\\_")
        return "%\(n)%"
    }

    private static let qualityPatterns: [(String, NSRegularExpression)] = [
        ("4K", try! NSRegularExpression(pattern: "\\b(4k|uhd|2160p?)\\b", options: .caseInsensitive)),
        ("FHD", try! NSRegularExpression(pattern: "\\b(fhd|1080p?|full ?hd)\\b", options: .caseInsensitive)),
        ("HD", try! NSRegularExpression(pattern: "\\b(hd|720p?)\\b", options: .caseInsensitive)),
        ("SD", try! NSRegularExpression(pattern: "\\b(sd|480p?|576p?)\\b", options: .caseInsensitive)),
    ]
    private static let hdrPattern = try! NSRegularExpression(pattern: "\\b(hdr10\\+?|hdr|dolby ?vision|dv)\\b", options: .caseInsensitive)
    private static let yearPattern = try! NSRegularExpression(pattern: "(?<!\\d)(19[2-9]\\d|20[0-4]\\d)(?!\\d)")

    private static func matches(_ re: NSRegularExpression, _ s: String) -> Bool {
        re.firstMatch(in: s, range: NSRange(s.startIndex..., in: s)) != nil
    }

    /// Quality badge parsed from a provider title ("4K", "FHD", "HD", "SD"), or nil when unknown.
    static func qualityOf(_ title: String) -> String? {
        qualityPatterns.first(where: { matches($0.1, title) })?.0
    }

    static func isHDR(_ title: String) -> Bool { matches(hdrPattern, title) }

    /// Extracts a plausible release year from titles or date strings.
    static func yearOf(_ sources: String?...) -> Int? {
        for source in sources {
            guard let source = source, !source.isEmpty else { continue }
            if let m = yearPattern.firstMatch(in: source, range: NSRange(source.startIndex..., in: source)),
               let r = Range(m.range, in: source) {
                return Int(source[r])
            }
        }
        return nil
    }

    /// Splits "Action, Drama / History" style lists.
    static func splitList(_ raw: String?) -> [String] {
        guard let raw = raw else { return [] }
        var seen = Set<String>()
        return raw.components(separatedBy: CharacterSet(charactersIn: ",/|،;"))
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty && seen.insert($0.lowercased()).inserted }
    }

    /// Provider titles often look like "AR | Title (2024) 4K" or "Arabic - English".
    /// Returns a cleaned primary title and an optional secondary line.
    static func displayTitles(_ raw: String) -> (primary: String, secondary: String?) {
        var title = raw.trimmingCharacters(in: .whitespaces)
        // Strip a leading country/language prefix such as "AR |", "[FR]", "EN:".
        if let re = try? NSRegularExpression(pattern: "^\\s*[\\[(]?[A-Za-z]{2,3}[\\])]?\\s*[|:\\-]\\s+"),
           let m = re.firstMatch(in: title, range: NSRange(title.startIndex..., in: title)),
           let r = Range(m.range, in: title) {
            title.removeSubrange(r)
        }
        for sep in [" | ", " - ", " – "] {
            let parts = title.components(separatedBy: sep)
            if parts.count == 2 {
                let a = parts[0].trimmingCharacters(in: .whitespaces)
                let b = parts[1].trimmingCharacters(in: .whitespaces)
                if !a.isEmpty && !b.isEmpty {
                    // Arabic part first when present, matching the approved cards.
                    if containsArabic(b) && !containsArabic(a) { return (b, a) }
                    return (a, b)
                }
            }
        }
        return (title, nil)
    }

    static func containsArabic(_ s: String) -> Bool {
        s.unicodeScalars.contains { (0x0600...0x06FF).contains($0.value) || (0x0750...0x077F).contains($0.value) }
    }
}

/// Parses provider age ratings (MPAA, TV guidelines, "16+", "FSK 12", …) into a minimum age.
enum AgeRating {
    private static let numeric = try! NSRegularExpression(pattern: "(\\d{1,2})\\s*\\+|\\+\\s*(\\d{1,2})|(?:FSK|PEGI|AGE|AB)\\s*(\\d{1,2})", options: .caseInsensitive)

    static func minimumAge(_ raw: String?) -> Int? {
        guard let raw = raw?.trimmingCharacters(in: .whitespaces), !raw.isEmpty else { return nil }
        let r = raw.uppercased()
        switch r {
        case "G", "TV-Y", "TV-G", "U", "ALL", "0", "0+": return 0
        case "TV-Y7", "TV-Y7-FV": return 7
        case "PG", "TV-PG": return 10
        case "PG-13", "TV-14", "12A": return 13
        case "R", "TV-MA", "M", "MA15+": return 17
        case "NC-17", "X", "XXX", "R18", "R18+": return 18
        default: break
        }
        if let m = numeric.firstMatch(in: r, range: NSRange(r.startIndex..., in: r)) {
            for i in 1..<m.numberOfRanges {
                if let range = Range(m.range(at: i), in: r), let v = Int(r[range]), (0...21).contains(v) { return v }
            }
        }
        if let v = Int(r), (0...21).contains(v) { return v }
        return nil
    }

    /// True when content exceeds the configured maximum age (0 = no limit).
    static func isRestricted(_ raw: String?, maxAllowedAge: Int) -> Bool {
        guard maxAllowedAge > 0, let age = minimumAge(raw) else { return false }
        return age > maxAllowedAge
    }
}

enum TimeFormat {
    /// "01:23:45" / "23:45" for player clocks.
    static func clock(_ seconds: Double) -> String {
        guard seconds.isFinite, seconds >= 0 else { return "00:00" }
        let s = Int(seconds.rounded(.down))
        let h = s / 3600, m = (s % 3600) / 60, sec = s % 60
        return h > 0 ? String(format: "%02d:%02d:%02d", h, m, sec) : String(format: "%02d:%02d", m, sec)
    }

    /// Always "HH:MM:SS" (used for totals on the movie screen).
    static func longClock(_ seconds: Double) -> String {
        guard seconds.isFinite, seconds >= 0 else { return "00:00:00" }
        let s = Int(seconds.rounded(.down))
        return String(format: "%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
    }

    /// Parses "01:52:10", "52:10", "112 min", "1h 52m", "6720" into seconds.
    static func parseDuration(_ raw: String?) -> Int? {
        guard let raw = raw?.trimmingCharacters(in: .whitespaces), !raw.isEmpty else { return nil }
        let parts = raw.split(separator: ":").map { String($0) }
        if parts.count >= 2, parts.allSatisfy({ Int($0) != nil }) {
            let nums = parts.compactMap { Int($0) }
            let total = nums.reduce(0) { $0 * 60 + $1 }
            return total > 0 ? total : nil
        }
        let lower = raw.lowercased()
        var total = 0
        if let re = try? NSRegularExpression(pattern: "(\\d+)\\s*(h|hr|hrs|hour|hours|ساعة)"),
           let m = re.firstMatch(in: lower, range: NSRange(lower.startIndex..., in: lower)),
           let r = Range(m.range(at: 1), in: lower), let h = Int(lower[r]) { total += h * 3600 }
        if let re = try? NSRegularExpression(pattern: "(\\d+)\\s*(m|min|mins|minute|minutes|دقيقة|د)\\b"),
           let m = re.firstMatch(in: lower, range: NSRange(lower.startIndex..., in: lower)),
           let r = Range(m.range(at: 1), in: lower), let mm = Int(lower[r]) { total += mm * 60 }
        if total > 0 { return total }
        if let n = Int(lower) { return n > 0 ? (n < 400 ? n * 60 : n) : nil }
        return nil
    }

    static let dayMonthYear: DateFormatter = {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US_POSIX")
        f.dateFormat = "dd-MM-yyyy"
        return f
    }()

    static let hourMinute: DateFormatter = {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US_POSIX")
        f.dateFormat = "HH:mm"
        return f
    }()
}

enum Base64Text {
    /// Xtream EPG titles/descriptions are base64 encoded; falls back to the raw value.
    static func decodeIfBase64(_ value: String?) -> String? {
        guard let value = value?.trimmingCharacters(in: .whitespacesAndNewlines), !value.isEmpty else { return nil }
        let allowed = CharacterSet(charactersIn: "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=\n\r")
        guard value.count % 4 == 0, value.unicodeScalars.allSatisfy({ allowed.contains($0) }),
              let data = Data(base64Encoded: value, options: .ignoreUnknownCharacters),
              let decoded = String(data: data, encoding: .utf8), !decoded.isEmpty else {
            return value
        }
        let printable = decoded.unicodeScalars.filter { !CharacterSet.controlCharacters.contains($0) || $0 == "\n" }
        return printable.count >= decoded.unicodeScalars.count - 1 ? decoded : value
    }
}
