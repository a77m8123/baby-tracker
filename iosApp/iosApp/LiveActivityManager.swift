import ActivityKit
import Foundation

/// Запускает, обновляет и закрывает Live Activity по текущему состоянию из Kotlin.
@available(iOS 16.1, *)
enum LiveActivityManager {
    static func apply(_ status: BabyStatus) {
        let wanted = desiredState(status)
        let current = Activity<BabyActivityAttributes>.activities

        guard let state = wanted else {
            for activity in current {
                Task { await activity.end(using: nil, dismissalPolicy: .immediate) }
            }
            return
        }

        if let activity = current.first {
            if activity.contentState != state {
                Task { await activity.update(using: state) }
            }
            for extra in current.dropFirst() {
                Task { await extra.end(using: nil, dismissalPolicy: .immediate) }
            }
            return
        }

        guard ActivityAuthorizationInfo().areActivitiesEnabled else { return }
        _ = try? Activity<BabyActivityAttributes>.request(
            attributes: BabyActivityAttributes(name: status.name),
            contentState: state,
            pushType: nil
        )
    }

    private static func desiredState(_ status: BabyStatus) -> BabyActivityAttributes.ContentState? {
        guard status.liveActivity else { return nil }
        if status.feeding, let since = date(ms: status.feedStartedMs) {
            return .init(kind: "feed", since: since, side: status.feedSide)
        }
        if status.sleeping, let since = date(ms: status.sinceMs) {
            return .init(kind: "sleep", since: since, side: nil)
        }
        return nil
    }
}
