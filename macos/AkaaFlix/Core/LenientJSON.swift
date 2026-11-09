import Foundation

/// Xtream Codes panels are notoriously inconsistent: numbers arrive as strings, strings as
/// numbers, empty lists as `{}` / `false` / `null`, and objects as empty arrays. These helpers
/// read values from `JSONSerialization` output without ever trapping on an unexpected type.
enum LenientJSON {

    /// Parses data into a Foundation JSON object. Tolerates a UTF-8 BOM and leading whitespace.
    static func parse(_ data: Data) throws -> Any {
        var payload = data
        if payload.starts(with: [0xEF, 0xBB, 0xBF]) { payload = payload.dropFirst(3) }
        if payload.isEmpty { throw AkaaError.invalidResponse }
        do {
            return try JSONSerialization.jsonObject(with: payload, options: [.fragmentsAllowed])
        } catch {
            throw AkaaError.invalidResponse
        }
    }

    static func string(_ value: Any?) -> String? {
        switch value {
        case let s as String:
            let t = s.trimmingCharacters(in: .whitespacesAndNewlines)
            return t.isEmpty || t.lowercased() == "null" ? nil : t
        case let n as NSNumber:
            if CFGetTypeID(n) == CFBooleanGetTypeID() { return n.boolValue ? "1" : "0" }
            if n.doubleValue.rounded() == n.doubleValue, abs(n.doubleValue) < 9.0e15 { return String(n.int64Value) }
            return n.stringValue
        default:
            return nil
        }
    }

    static func int(_ value: Any?) -> Int? {
        switch value {
        case let n as NSNumber:
            if CFGetTypeID(n) == CFBooleanGetTypeID() { return n.boolValue ? 1 : 0 }
            let d = n.doubleValue
            guard d.isFinite, abs(d) < 9.0e15 else { return nil }
            return Int(d)
        case let s as String:
            let t = s.trimmingCharacters(in: .whitespacesAndNewlines)
            if let i = Int(t) { return i }
            if let d = Double(t), d.isFinite, abs(d) < 9.0e15 { return Int(d) }
            return nil
        default:
            return nil
        }
    }

    static func double(_ value: Any?) -> Double? {
        switch value {
        case let n as NSNumber:
            if CFGetTypeID(n) == CFBooleanGetTypeID() { return nil }
            return n.doubleValue.isFinite ? n.doubleValue : nil
        case let s as String:
            let t = s.trimmingCharacters(in: .whitespacesAndNewlines).replacingOccurrences(of: ",", with: ".")
            guard let d = Double(t), d.isFinite else { return nil }
            return d
        default:
            return nil
        }
    }

    /// Accepts `["a","b"]`, `"a"`, or `"a,b"` (only splits on commas when asked).
    static func stringArray(_ value: Any?, splitCommas: Bool = false) -> [String] {
        switch value {
        case let arr as [Any]:
            return arr.compactMap { string($0) }
        case let s as String:
            guard let t = string(s) else { return [] }
            if splitCommas { return t.split(separator: ",").map { $0.trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty } }
            return [t]
        default:
            return []
        }
    }

    static func object(_ value: Any?) -> [String: Any]? { value as? [String: Any] }

    static func array(_ value: Any?) -> [Any] {
        if let a = value as? [Any] { return a }
        return []
    }

    /// First non-empty string among several keys.
    static func firstString(_ object: [String: Any]?, _ keys: String...) -> String? {
        guard let object = object else { return nil }
        for key in keys {
            if let s = string(object[key]) { return s }
        }
        return nil
    }
}
