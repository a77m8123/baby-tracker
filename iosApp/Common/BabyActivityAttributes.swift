import ActivityKit
import Foundation

/// Live Activity: идущий сон или кормление на экране блокировки и в Dynamic Island.
/// Файл входит и в приложение, и в расширение виджета.
@available(iOS 16.1, *)
struct BabyActivityAttributes: ActivityAttributes {
    public struct ContentState: Codable, Hashable {
        /// "sleep" или "feed"
        var kind: String
        var since: Date
        var side: String?
    }

    var name: String
}
