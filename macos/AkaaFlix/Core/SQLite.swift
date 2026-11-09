import Foundation
import SQLite3

/// Values bound to / read from SQLite.
enum SQLValue: Equatable {
    case int(Int64)
    case double(Double)
    case text(String)
    case null

    init(_ v: Int?) { self = v.map { .int(Int64($0)) } ?? .null }
    init(_ v: Int64?) { self = v.map { .int($0) } ?? .null }
    init(_ v: Double?) { self = v.map { .double($0) } ?? .null }
    init(_ v: String?) { self = v.map { .text($0) } ?? .null }
    init(_ v: Bool) { self = .int(v ? 1 : 0) }
    init(_ v: Date?) { self = v.map { .double($0.timeIntervalSince1970) } ?? .null }
}

private let SQLITE_TRANSIENT = unsafeBitCast(-1, to: sqlite3_destructor_type.self)

/// One result row. Columns are addressed by index in SELECT order.
struct SQLRow {
    fileprivate let stmt: OpaquePointer

    func isNull(_ i: Int32) -> Bool { sqlite3_column_type(stmt, i) == SQLITE_NULL }
    func int(_ i: Int32) -> Int { Int(sqlite3_column_int64(stmt, i)) }
    func int64(_ i: Int32) -> Int64 { sqlite3_column_int64(stmt, i) }
    func optInt(_ i: Int32) -> Int? { isNull(i) ? nil : int(i) }
    func double(_ i: Int32) -> Double { sqlite3_column_double(stmt, i) }
    func optDouble(_ i: Int32) -> Double? { isNull(i) ? nil : double(i) }
    func bool(_ i: Int32) -> Bool { sqlite3_column_int64(stmt, i) != 0 }
    func date(_ i: Int32) -> Date? { isNull(i) ? nil : Date(timeIntervalSince1970: double(i)) }
    func string(_ i: Int32) -> String {
        guard let c = sqlite3_column_text(stmt, i) else { return "" }
        return String(cString: c)
    }
    func optString(_ i: Int32) -> String? { isNull(i) ? nil : string(i) }
}

/// A thread-safe SQLite connection (serialised with a recursive lock so transactions can nest calls).
final class SQLiteDatabase {

    private var handle: OpaquePointer?
    private let lock = NSRecursiveLock()
    let path: String

    init(path: String) throws {
        self.path = path
        var db: OpaquePointer?
        let flags = SQLITE_OPEN_READWRITE | SQLITE_OPEN_CREATE | SQLITE_OPEN_FULLMUTEX
        guard sqlite3_open_v2(path, &db, flags, nil) == SQLITE_OK, let opened = db else {
            let message = db.map { String(cString: sqlite3_errmsg($0)) } ?? "open failed"
            sqlite3_close(db)
            throw AkaaError.storage(message)
        }
        handle = opened
        sqlite3_busy_timeout(opened, 5000)
        try execute("PRAGMA journal_mode=WAL; PRAGMA synchronous=NORMAL; PRAGMA foreign_keys=ON; PRAGMA temp_store=MEMORY;")
    }

    deinit { close() }

    func close() {
        lock.lock(); defer { lock.unlock() }
        if let h = handle {
            sqlite3_close_v2(h)
            handle = nil
        }
    }

    private var errorMessage: String {
        guard let h = handle else { return "database closed" }
        return String(cString: sqlite3_errmsg(h))
    }

    func execute(_ sql: String) throws {
        lock.lock(); defer { lock.unlock() }
        guard let h = handle else { throw AkaaError.storage("database closed") }
        var err: UnsafeMutablePointer<CChar>?
        if sqlite3_exec(h, sql, nil, nil, &err) != SQLITE_OK {
            let message = err.map { String(cString: $0) } ?? errorMessage
            sqlite3_free(err)
            throw AkaaError.storage(message)
        }
    }

    private func prepare(_ sql: String) throws -> OpaquePointer {
        guard let h = handle else { throw AkaaError.storage("database closed") }
        var stmt: OpaquePointer?
        guard sqlite3_prepare_v2(h, sql, -1, &stmt, nil) == SQLITE_OK, let s = stmt else {
            throw AkaaError.storage("\(errorMessage) — \(sql.prefix(120))")
        }
        return s
    }

    private func bind(_ stmt: OpaquePointer, _ args: [SQLValue]) {
        sqlite3_reset(stmt)
        sqlite3_clear_bindings(stmt)
        for (i, value) in args.enumerated() {
            let idx = Int32(i + 1)
            switch value {
            case .int(let v): sqlite3_bind_int64(stmt, idx, v)
            case .double(let v): sqlite3_bind_double(stmt, idx, v)
            case .text(let v): sqlite3_bind_text(stmt, idx, v, -1, SQLITE_TRANSIENT)
            case .null: sqlite3_bind_null(stmt, idx)
            }
        }
    }

    /// Executes a statement that returns no rows.
    @discardableResult
    func run(_ sql: String, _ args: [SQLValue] = []) throws -> Int {
        lock.lock(); defer { lock.unlock() }
        let stmt = try prepare(sql)
        defer { sqlite3_finalize(stmt) }
        bind(stmt, args)
        let rc = sqlite3_step(stmt)
        guard rc == SQLITE_DONE || rc == SQLITE_ROW else { throw AkaaError.storage(errorMessage) }
        return Int(sqlite3_changes(handle))
    }

    /// Runs the same statement for many argument lists (fast bulk inserts). Call inside `transaction`.
    func runBatch(_ sql: String, _ rows: [[SQLValue]]) throws {
        lock.lock(); defer { lock.unlock() }
        let stmt = try prepare(sql)
        defer { sqlite3_finalize(stmt) }
        for args in rows {
            bind(stmt, args)
            let rc = sqlite3_step(stmt)
            guard rc == SQLITE_DONE || rc == SQLITE_ROW else { throw AkaaError.storage(errorMessage) }
        }
    }

    var lastInsertRowID: Int64 {
        lock.lock(); defer { lock.unlock() }
        return handle.map { sqlite3_last_insert_rowid($0) } ?? 0
    }

    func query<T>(_ sql: String, _ args: [SQLValue] = [], _ map: (SQLRow) throws -> T) throws -> [T] {
        lock.lock(); defer { lock.unlock() }
        let stmt = try prepare(sql)
        defer { sqlite3_finalize(stmt) }
        bind(stmt, args)
        var out: [T] = []
        while true {
            let rc = sqlite3_step(stmt)
            if rc == SQLITE_ROW {
                out.append(try map(SQLRow(stmt: stmt)))
            } else if rc == SQLITE_DONE {
                break
            } else {
                throw AkaaError.storage(errorMessage)
            }
        }
        return out
    }

    func scalarInt(_ sql: String, _ args: [SQLValue] = []) throws -> Int {
        try query(sql, args) { $0.int(0) }.first ?? 0
    }

    func transaction<T>(_ block: () throws -> T) throws -> T {
        lock.lock(); defer { lock.unlock() }
        try execute("BEGIN IMMEDIATE")
        do {
            let result = try block()
            try execute("COMMIT")
            return result
        } catch {
            try? execute("ROLLBACK")
            throw error
        }
    }

    var userVersion: Int {
        get { (try? scalarInt("PRAGMA user_version")) ?? 0 }
        set { try? execute("PRAGMA user_version = \(newValue)") }
    }
}
