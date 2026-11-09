import Foundation

/// Derives browse icons/filters from provider category names. Xtream Codes does not expose
/// countries or channel kinds, so they are inferred from the category titles (Arabic + Latin).
enum CategoryKind: String, Codable, CaseIterable {
    case arabic, international, movies, series, kids, documentary, news, sports, adult, other
}

enum CategoryClassifier {

    private static let adult = ["adult", "xxx", "18+", "+18", "for adults", "porn", "erotic", "للكبار", "كبار فقط", "+١٨"]
    private static let kids = ["kid", "child", "cartoon", "toon", "junior", "disney", "nick", "اطفال", "أطفال", "كرتون", "kinder", "enfant"]
    private static let docs = ["documentar", "docu", "doc ", "nat geo", "discovery", "history", "وثائقي", "وثائقية", "dokument"]
    private static let news = ["news", "akhbar", "اخبار", "أخبار", "info", "nachrichten", "noticias"]
    private static let sports = ["sport", "bein", "رياض", "football", "soccer", "ssc", "league", "f1", "nba", "ufc", "wwe"]
    private static let movies = ["movie", "cinema", "film", "aflam", "افلام", "أفلام", "سينما", "kino", "box office", "vod"]
    private static let series = ["series", "مسلسل", "مسلسلات", "drama", "دراما", "shows"]
    private static let arabic = [
        "arab", "عرب", "عربي", "عربية", "مصر", "egypt", "saudi", "سعود", "ksa", "uae", "emirat", "الامارات", "qatar", "قطر",
        "kuwait", "الكويت", "iraq", "العراق", "syria", "سوري", "leban", "لبنان", "jordan", "الاردن", "morocco", "المغرب",
        "algeri", "الجزائر", "tunis", "تونس", "libya", "ليبيا", "oman", "عمان", "bahrain", "البحرين", "yemen", "اليمن",
        "sudan", "السودان", "palestin", "فلسطين", "osn", "mbc", "rotana", "روتانا", "shahid", "شاهد", "art ", "nile", "النيل",
    ]
    private static let arabicCountryCodes: Set<String> = ["AR", "ARB", "ARAB", "EG", "SA", "KSA", "AE", "UAE", "QA", "KW", "IQ", "SY", "LB", "JO", "MA", "DZ", "TN", "LY", "OM", "BH", "YE", "SD", "PS"]
    private static let notCountries: Set<String> = ["HD", "SD", "FHD", "UHD", "VIP", "TV", "VOD", "PPV", "THE", "NEW", "ALL", "TOP", "LIVE"]
    private static let countryPrefix = try! NSRegularExpression(pattern: "^\\s*[|\\[(]?\\s*([A-Za-z]{2,3})\\s*[|:\\-\\])]")

    private static func normalizedKeywords(_ list: [String]) -> [String] {
        list.map { k in
            let n = ContentText.normalize(k)
            return k.hasSuffix(" ") ? n + " " : n
        }
    }
    private static let adultN = normalizedKeywords(adult)
    private static let kidsN = normalizedKeywords(kids)
    private static let docsN = normalizedKeywords(docs)
    private static let newsN = normalizedKeywords(news)
    private static let sportsN = normalizedKeywords(sports)
    private static let moviesN = normalizedKeywords(movies)
    private static let seriesN = normalizedKeywords(series)
    private static let arabicN = normalizedKeywords(arabic)

    private static func padded(_ name: String) -> String { " " + ContentText.normalize(name) + " " }

    static func classify(_ name: String) -> CategoryKind {
        let n = padded(name)
        if adultN.contains(where: { n.contains($0) }) { return .adult }
        if kidsN.contains(where: { n.contains($0) }) { return .kids }
        if docsN.contains(where: { n.contains($0) }) { return .documentary }
        if newsN.contains(where: { n.contains($0) }) { return .news }
        if sportsN.contains(where: { n.contains($0) }) { return .sports }
        if moviesN.contains(where: { n.contains($0) }) { return .movies }
        if seriesN.contains(where: { n.contains($0) }) { return .series }
        if isArabic(name) { return .arabic }
        if countryCode(name) != nil { return .international }
        return .other
    }

    static func isAdult(_ name: String) -> Bool {
        let n = padded(name)
        return adultN.contains(where: { n.contains($0) })
    }

    static func isArabic(_ name: String) -> Bool {
        let n = padded(name)
        if arabicN.contains(where: { n.contains($0) }) { return true }
        if let code = countryCode(name) { return arabicCountryCodes.contains(code) }
        return false
    }

    /// Country / region code from prefixes such as "AR | MBC", "[FR] Canal", "UK: Sports".
    static func countryCode(_ name: String) -> String? {
        guard let m = countryPrefix.firstMatch(in: name, range: NSRange(name.startIndex..., in: name)),
              let r = Range(m.range(at: 1), in: name) else { return nil }
        let code = name[r].uppercased()
        guard (2...3).contains(code.count), !notCountries.contains(code) else { return nil }
        return code
    }

    /// Two-letter ISO code (when recognisable) for flag rendering; detects Arabic country names too.
    static func flagCountry(_ name: String) -> String? {
        let n = ContentText.normalize(name)
        let named: [(String, String)] = [
            ("مصر", "EG"), ("egypt", "EG"), ("سعود", "SA"), ("saudi", "SA"), ("الامارات", "AE"), ("emirat", "AE"),
            ("قطر", "QA"), ("qatar", "QA"), ("الكويت", "KW"), ("kuwait", "KW"), ("العراق", "IQ"), ("iraq", "IQ"),
            ("سوري", "SY"), ("syria", "SY"), ("لبنان", "LB"), ("leban", "LB"), ("الاردن", "JO"), ("jordan", "JO"),
            ("المغرب", "MA"), ("morocco", "MA"), ("الجزائر", "DZ"), ("algeri", "DZ"), ("تونس", "TN"), ("tunis", "TN"),
            ("ليبيا", "LY"), ("libya", "LY"), ("فلسطين", "PS"), ("palestin", "PS"), ("ترك", "TR"), ("turk", "TR"),
            ("كوري", "KR"), ("korea", "KR"), ("هند", "IN"), ("india", "IN"),
        ]
        for (k, code) in named where n.contains(ContentText.normalize(k)) { return code }
        guard let code = countryCode(name) else { return nil }
        let map = ["UK": "GB", "KSA": "SA", "UAE": "AE", "USA": "US", "AR": nil, "ARB": nil, "ARAB": nil] as [String: String?]
        if let mapped = map[code] { return mapped }
        return code.count == 2 ? code : nil
    }

    /// Regional-indicator emoji flag for a two-letter ISO code.
    static func flagEmoji(_ iso: String) -> String {
        iso.uppercased().unicodeScalars.compactMap { Unicode.Scalar(127397 + $0.value) }.map { String($0) }.joined()
    }
}
