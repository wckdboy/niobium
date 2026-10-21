import Foundation
import SwiftUI

typealias JSONObject = [String: Any]

extension Dictionary where Key == String, Value == Any {
    func string(_ key: String) -> String? {
        if let value = self[key] as? String { return value }
        if let value = self[key] as? NSNumber { return value.stringValue }
        return nil
    }
    func int(_ key: String) -> Int? {
        if let value = self[key] as? Int { return value }
        if let value = self[key] as? NSNumber { return value.intValue }
        if let value = self[key] as? String { return Int(value) }
        return nil
    }
    func double(_ key: String) -> Double? {
        if let value = self[key] as? Double { return value }
        if let value = self[key] as? NSNumber { return value.doubleValue }
        if let value = self[key] as? String { return Double(value) }
        return nil
    }
    func bool(_ key: String) -> Bool? {
        if let value = self[key] as? Bool { return value }
        if let value = self[key] as? NSNumber { return value.boolValue }
        return nil
    }
    func dict(_ key: String) -> JSONObject? { self[key] as? JSONObject }
    func array(_ key: String) -> [Any]? { self[key] as? [Any] }
    func objects(_ key: String) -> [JSONObject] { (self[key] as? [JSONObject]) ?? [] }
}

func parseJSON(_ data: Data) -> JSONObject? {
    (try? JSONSerialization.jsonObject(with: data)) as? JSONObject
}

func jsonString(_ value: Any?) -> String {
    guard let value else { return "" }
    if let string = value as? String { return string }
    if let dict = value as? JSONObject {
        if let text = dict.string("text") { return text }
        if let summary = dict.string("summary") { return summary }
    }
    if JSONSerialization.isValidJSONObject(value),
       let data = try? JSONSerialization.data(withJSONObject: value, options: [.prettyPrinted, .sortedKeys]),
       let string = String(data: data, encoding: .utf8) {
        return string
    }
    return String(describing: value)
}

func epochDate(_ value: Double?) -> Date? {
    guard let value, value > 0 else { return nil }
    // Hermes uses epoch seconds; tolerate milliseconds from older stores.
    return Date(timeIntervalSince1970: value > 10_000_000_000 ? value / 1000 : value)
}

struct BotProfile: Identifiable, Equatable {
    var name: String
    var title: String
    var role: String
    var provider: String
    var model: String
    var skillCount: Int
    var canonicalSessionID: String?
    var canonicalResolvedID: String?
    var lastSessionID: String?
    var preview: String
    var activityAt: Date?
    var workerActive: Bool
    var isDefault: Bool
    var hidden: Bool
    var accentHex: String?
    var hasAvatar: Bool
    var enabledToolsets: [String] = []

    var id: String { name }
    var accent: Color { NB.color(for: accentHex) }
    var initials: String {
        let parts = title.split(separator: " ").prefix(2)
        if parts.isEmpty { return String(name.prefix(2)).uppercased() }
        return parts.map { String($0.prefix(1)) }.joined().uppercased()
    }
    var isLive: Bool {
        guard workerActive, let activityAt else { return false }
        return Date().timeIntervalSince(activityAt) < 120
    }
    var subtitle: String { role.isEmpty ? "Hermes agent" : role }
    var statusLine: String {
        if isLive { return "Working now" }
        if let activityAt { return activityAt.relativeShort }
        return "Ready"
    }

    static func parse(_ json: JSONObject) -> BotProfile? {
        guard let name = json.string("name"), !name.isEmpty else { return nil }
        let meta = json.dict("ui_meta")?.dict("hermes-bots") ?? [:]
        let canonical = json.dict("canonical_session")
        let last = json.dict("last_session")
        let worker = json.dict("worker_session")
        let display = meta.string("title")?.trimmingCharacters(in: .whitespacesAndNewlines)
        let fallbackTitle = name == "default" ? "Hermes" : name.capitalized
        let title = (display?.isEmpty == false ? display! : (json.string("display_name").flatMap { $0.isEmpty ? nil : $0 } ?? fallbackTitle))
        let role = (meta.string("description").flatMap { $0.isEmpty ? nil : $0 })
            ?? (json.string("description") ?? "")
        let activity = epochDate(canonical?.double("last_active"))
            ?? epochDate(last?.double("last_active"))
            ?? epochDate(worker?.double("last_active"))
        let workerAt = epochDate(worker?.double("last_active"))
        let preview = canonical?.string("preview") ?? last?.string("preview") ?? ""
        return BotProfile(
            name: name,
            title: title,
            role: role,
            provider: json.string("provider") ?? "",
            model: json.string("model") ?? "",
            skillCount: json.int("skill_count") ?? 0,
            canonicalSessionID: canonical?.string("id"),
            canonicalResolvedID: canonical?.string("resolved_id"),
            lastSessionID: last?.string("id"),
            preview: preview,
            activityAt: activity,
            workerActive: workerAt.map { Date().timeIntervalSince($0) < 120 } ?? false,
            isDefault: json.bool("is_default") ?? false,
            hidden: meta.bool("hidden") ?? false,
            accentHex: meta.string("color"),
            hasAvatar: json.bool("has_avatar") ?? false
        )
    }
}

struct GroupMember: Identifiable, Equatable, Hashable {
    var memberID: String
    var profile: String
    var handle: String
    var displayName: String
    var id: String { memberID.isEmpty ? profile : memberID }
    var name: String { displayName.isEmpty ? handle.capitalized : displayName }
    var accent: Color { NB.color(for: profile) }

    static func parse(_ json: JSONObject) -> GroupMember? {
        let profile = json.string("profile") ?? json.string("member_id") ?? ""
        guard !profile.isEmpty else { return nil }
        return GroupMember(memberID: json.string("member_id") ?? profile,
                           profile: profile,
                           handle: json.string("handle") ?? profile,
                           displayName: json.string("display_name") ?? "")
    }
}

struct GroupRoom: Identifiable, Equatable {
    var roomID: String
    var name: String
    var members: [GroupMember]
    var updatedAt: Date?
    var latestSeq: Int
    var disbanded: Bool
    var preview: String = ""
    var id: String { roomID }

    static func parse(_ json: JSONObject) -> GroupRoom? {
        guard let roomID = json.string("room_id"), !roomID.isEmpty else { return nil }
        return GroupRoom(roomID: roomID,
                         name: json.string("name") ?? "Group Chat",
                         members: json.objects("members").compactMap(GroupMember.parse),
                         updatedAt: epochDate(json.double("updated_at")),
                         latestSeq: json.int("latest_seq") ?? 0,
                         disbanded: (json.double("disbanded_at") ?? 0) > 0)
    }
}

struct RoomEvent: Identifiable, Equatable {
    var seq: Int
    var roomID: String
    var eventID: String
    var kind: String
    var actorKind: String
    var actorID: String
    var actorName: String
    var text: String
    var timestamp: Date?
    var status: String?
    var reason: String?
    var id: String { eventID.isEmpty ? "\(roomID)-\(seq)" : eventID }

    var isMessage: Bool { kind == "message.user" || kind == "message.member" || kind == "message" }
    var isNotice: Bool { kind.hasPrefix("turn.") || kind.hasPrefix("member.") || kind == "room.stop_requested" || kind == "room.disbanded" }
    var displayName: String {
        if !actorName.isEmpty { return actorName }
        if actorID == "user" || actorKind == "user" { return "You" }
        return actorID.replacingOccurrences(of: "_", with: " ").capitalized
    }

    static func parse(_ json: JSONObject) -> RoomEvent? {
        guard let seq = json.int("seq") else { return nil }
        let payload = json.dict("payload") ?? [:]
        let actor = json.dict("actor") ?? [:]
        return RoomEvent(seq: seq,
                         roomID: json.string("room_id") ?? "",
                         eventID: json.string("event_id") ?? "",
                         kind: json.string("kind") ?? "",
                         actorKind: actor.string("kind") ?? "",
                         actorID: actor.string("id") ?? "",
                         actorName: actor.string("display_name") ?? "",
                         text: payload.string("text") ?? payload.string("content") ?? "",
                         timestamp: epochDate(json.double("created_at")),
                         status: payload.string("status"),
                         reason: payload.string("reason") ?? payload.string("reason_code") ?? payload.string("error"))
    }
}

enum ToolStatus: String, Equatable {
    case running, completed, failed
}

struct ChatMessage: Identifiable, Equatable {
    enum Role: String { case user, assistant, tool, system }

    var id: String
    var role: Role
    var text: String
    var timestamp: Date
    var displayKind: String?
    var reasoning: String?
    var toolName: String?
    var toolArgs: String?
    var toolResult: String?
    var toolStatus: ToolStatus
    var toolPreview: String?
    var toolDuration: Double?
    var rowID: Int?
    var isStreaming: Bool = false

    var isTool: Bool { role == .tool || toolName != nil }
    var toolSummary: String {
        if let toolPreview, !toolPreview.isEmpty { return toolPreview }
        if let toolArgs, let data = toolArgs.data(using: .utf8), let dict = parseJSON(data) {
            for key in ["command", "path", "query", "url", "description", "prompt", "name", "task"] {
                if let value = dict.string(key), !value.isEmpty { return value }
            }
        }
        return toolName ?? "Tool call"
    }
}

struct ApprovalRequest: Identifiable, Equatable {
    var rpcID: JSONValueID
    var requestID: String
    var sessionID: String
    var command: String
    var description: String
    var toolName: String
    var choices: [String]
    var id: String { requestID.isEmpty ? "\(rpcID)" : requestID }
}

/// JSON-RPC ids can be strings or integers. Preserve whichever the server sent.
enum JSONValueID: Hashable {
    case string(String)
    case number(Int)
    var wire: Any {
        switch self {
        case .string(let s): return s
        case .number(let n): return n
        }
    }
    var description: String {
        switch self {
        case .string(let s): return s
        case .number(let n): return String(n)
        }
    }
    static func parse(_ value: Any?) -> JSONValueID? {
        if let string = value as? String { return .string(string) }
        if let number = value as? Int { return .number(number) }
        if let number = value as? NSNumber { return .number(number.intValue) }
        return nil
    }
}

struct ClarifyQuestion: Identifiable, Equatable {
    var id: String
    var question: String
    var choices: [String]
    var multiSelect: Bool
}

struct ClarifyRequest: Identifiable, Equatable {
    var rpcID: JSONValueID
    var sessionID: String
    var questions: [ClarifyQuestion]
    var id: String { "\(rpcID)" }
}

struct Toolset: Identifiable, Equatable {
    var name: String
    var description: String
    var tools: [String]
    var enabled: Bool
    var id: String { name }
    var icon: String {
        switch name {
        case "terminal": return "terminal"
        case "file", "coding": return "folder"
        case "web": return "globe"
        case "browser": return "safari"
        case "vision": return "eye"
        case "memory": return "brain"
        case "delegation": return "point.3.connected.trianglepath.dotted"
        case "cronjob": return "clock.arrow.circlepath"
        case "image_gen": return "wand.and.stars"
        case "code_execution": return "curlybraces"
        case "clarify": return "questionmark.bubble"
        case "skills": return "sparkles"
        case "todo": return "checklist"
        case "bot_room": return "person.3"
        case "a2a": return "antenna.radiowaves.left.and.right"
        case "discord": return "bubble.left.and.bubble.right"
        default: return "square.grid.2x2"
        }
    }

    static func parse(_ json: JSONObject) -> Toolset? {
        guard let name = json.string("name") else { return nil }
        return Toolset(name: name,
                       description: json.string("description") ?? "",
                       tools: (json.array("tools") as? [String]) ?? [],
                       enabled: json.bool("enabled") ?? false)
    }
}

struct ActivityItem: Identifiable, Equatable {
    enum Kind: String { case tool, message, approval, bot, system }
    var id: String
    var kind: Kind
    var title: String
    var detail: String
    var date: Date
    var botName: String
    var success: Bool
}

struct Routine: Identifiable, Equatable {
    var id: String
    var name: String
    var schedule: String
    var prompt: String
    var enabled: Bool
    var nextRun: String
    var bot: String

    static func parse(_ json: JSONObject, bot: String) -> Routine? {
        let name = json.string("name") ?? json.string("job_id") ?? json.string("id")
        guard let name else { return nil }
        return Routine(id: name,
                       name: name,
                       schedule: json.string("schedule") ?? json.string("schedule_display") ?? "",
                       prompt: json.string("prompt") ?? "",
                       enabled: json.bool("enabled") ?? !(json.bool("paused") ?? false),
                       nextRun: json.string("next_run") ?? json.string("next_run_display") ?? "",
                       bot: bot)
    }
}

extension Date {
    var relativeShort: String {
        let seconds = -timeIntervalSinceNow
        if seconds < 60 { return "now" }
        if seconds < 3600 { return "\(Int(seconds / 60))m" }
        if seconds < 86_400 { return "\(Int(seconds / 3600))h" }
        if seconds < 604_800 { return "\(Int(seconds / 86_400))d" }
        return formatted(.dateTime.month(.abbreviated).day())
    }

    var clockTime: String { formatted(.dateTime.hour().minute()) }
}

struct GatewayConnection: Codable, Equatable, Identifiable {
    var id: UUID
    var name: String
    var baseURL: String
    var savedAt: Date
    var isDemo: Bool

    static var empty: GatewayConnection {
        GatewayConnection(id: UUID(), name: "My Hermes", baseURL: "http://127.0.0.1:9119", savedAt: .distantPast, isDemo: false)
    }
}

enum GatewayState: Equatable {
    case disconnected
    case connecting
    case connected(version: String)
    case failed(String)

    var isConnected: Bool { if case .connected = self { return true }; return false }
    var label: String {
        switch self {
        case .disconnected: return "Offline"
        case .connecting: return "Connecting"
        case .connected: return "Connected"
        case .failed: return "Error"
        }
    }
    var color: Color {
        switch self {
        case .disconnected: return NB.tertiary
        case .connecting: return NB.amber
        case .connected: return NB.mint
        case .failed: return NB.rose
        }
    }
}

struct ModelChoice: Identifiable, Equatable {
    var provider: String
    var providerName: String
    var model: String
    var current: Bool
    var reasoning: Bool
    var id: String { "\(provider)/\(model)" }
    var label: String {
        model.split(separator: "/").last.map(String.init) ?? model
    }
}
