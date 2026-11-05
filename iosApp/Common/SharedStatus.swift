import Foundation

/// Общая папка приложения и виджета. AltStore переименовывает группу (добавляет Team ID)
/// и кладёт настоящее имя в Info.plist под ключом ALTAppGroups — берём его, если есть.
enum AppGroup {
    static let fallback = "group.com.babytracker.app"

    static var id: String {
        (Bundle.main.object(forInfoDictionaryKey: "ALTAppGroups") as? [String])?.first ?? fallback
    }

    static var defaults: UserDefaults? { UserDefaults(suiteName: id) }

    static let statusKey = "widget-status"
}

/// То же, что WidgetStatus в Kotlin (shared/.../logic/Reminders.kt).
struct BabyStatus: Codable {
    var name: String
    var sleeping: Bool
    var sinceMs: Double?
    var lastFeedMs: Double?
    var nextFeedMs: Double?
    var feeding: Bool = false
    var feedStartedMs: Double?
    var feedSide: String?
    var liveActivity: Bool = true

    static func decode(_ json: String) -> BabyStatus? {
        guard let data = json.data(using: .utf8) else { return nil }
        return try? JSONDecoder().decode(BabyStatus.self, from: data)
    }

    static func load() -> BabyStatus? {
        guard let json = AppGroup.defaults?.string(forKey: AppGroup.statusKey) else { return nil }
        return decode(json)
    }
}

func date(ms: Double?) -> Date? {
    ms.map { Date(timeIntervalSince1970: $0 / 1000) }
}
