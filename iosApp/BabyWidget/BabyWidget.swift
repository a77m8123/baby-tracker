import ActivityKit
import SwiftUI
import WidgetKit

@main
struct BabyWidgetBundle: WidgetBundle {
    var body: some Widget {
        BabyStatusWidget()
        BabyLiveActivity()
    }
}

// MARK: Цвета — как в приложении (shared/.../ui/Theme.kt)

private enum Palette {
    static let sleep = Color(red: 0xA9 / 255, green: 0x9C / 255, blue: 0xFF / 255)
    static let feed = Color(red: 0xFF / 255, green: 0xA6 / 255, blue: 0x7C / 255)
    static let bg = Color(red: 0x1B / 255, green: 0x1B / 255, blue: 0x28 / 255)
    static let text = Color(red: 0xED / 255, green: 0xEB / 255, blue: 0xF5 / 255)
    static let muted = Color(red: 0x9C / 255, green: 0x99 / 255, blue: 0xB0 / 255)
}

// MARK: Виджет на экран «Домой» и экран блокировки

struct StatusEntry: TimelineEntry {
    let date: Date
    let status: BabyStatus?
}

struct StatusProvider: TimelineProvider {
    func placeholder(in context: Context) -> StatusEntry {
        StatusEntry(date: Date(), status: BabyStatus(name: "Малыш", sleeping: true, sinceMs: Date().addingTimeInterval(-2400).timeIntervalSince1970 * 1000))
    }

    func getSnapshot(in context: Context, completion: @escaping (StatusEntry) -> Void) {
        completion(StatusEntry(date: Date(), status: BabyStatus.load() ?? placeholder(in: context).status))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<StatusEntry>) -> Void) {
        let entry = StatusEntry(date: Date(), status: BabyStatus.load())
        // счётчики идут сами (Text с .timer); перерисовка раз в 15 минут — для «пора кормить»
        completion(Timeline(entries: [entry], policy: .after(Date().addingTimeInterval(15 * 60))))
    }
}

struct BabyStatusWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "BabyStatus", provider: StatusProvider()) { entry in
            StatusView(entry: entry)
        }
        .configurationDisplayName("Малыш")
        .description("Сон и кормление одним взглядом")
        .supportedFamilies([.systemSmall, .systemMedium, .accessoryRectangular])
    }
}

struct StatusView: View {
    @Environment(\.widgetFamily) private var family
    let entry: StatusEntry

    var body: some View {
        content
            .widgetBackground(Palette.bg)
    }

    @ViewBuilder
    private var content: some View {
        if let s = entry.status {
            if family == .accessoryRectangular {
                VStack(alignment: .leading, spacing: 2) {
                    Text(s.sleeping ? "Спит" : "Не спит").font(.headline)
                    if let since = date(ms: s.sinceMs) {
                        Text(since, style: .timer).font(.body.monospacedDigit())
                    }
                    feedLine(s).font(.caption)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            } else {
                VStack(alignment: .leading, spacing: 4) {
                    Text(s.name).font(.caption.bold()).foregroundColor(Palette.sleep)
                    HStack(spacing: 6) {
                        Image(systemName: s.sleeping ? "moon.fill" : "sun.max.fill")
                            .foregroundColor(s.sleeping ? Palette.sleep : Palette.feed)
                        Text(s.sleeping ? "Спит" : "Не спит").font(.title3.bold()).foregroundColor(Palette.text)
                    }
                    if let since = date(ms: s.sinceMs) {
                        Text(since, style: .timer)
                            .font(.system(size: family == .systemSmall ? 26 : 30, weight: .bold).monospacedDigit())
                            .foregroundColor(Palette.text)
                    }
                    Spacer(minLength: 0)
                    feedLine(s).font(.caption).foregroundColor(Palette.muted).lineLimit(2)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            }
        } else {
            Text("Откройте «Малыш», чтобы начать")
                .font(.caption)
                .foregroundColor(Palette.muted)
        }
    }

    @ViewBuilder
    private func feedLine(_ s: BabyStatus) -> some View {
        if s.feeding, let since = date(ms: s.feedStartedMs) {
            (Text("Кормление ") + Text(since, style: .timer))
        } else if let next = date(ms: s.nextFeedMs), next > Date() {
            Text("Кормление в ") + Text(next, style: .time)
        } else if date(ms: s.lastFeedMs) != nil {
            Text("Пора кормить")
        } else {
            Text("Кормлений пока нет")
        }
    }
}

private extension View {
    /// На iOS 17 фон виджета задаётся через containerBackground, раньше — обычным фоном.
    @ViewBuilder
    func widgetBackground(_ color: Color) -> some View {
        if #available(iOS 17.0, *) {
            containerBackground(color, for: .widget)
        } else {
            padding().background(color)
        }
    }
}

// MARK: Live Activity

struct BabyLiveActivity: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: BabyActivityAttributes.self) { context in
            LockScreenActivity(name: context.attributes.name, state: context.state)
                .activityBackgroundTint(Palette.bg)
                .activitySystemActionForegroundColor(Palette.text)
        } dynamicIsland: { context in
            let state = context.state
            let color = state.kind == "sleep" ? Palette.sleep : Palette.feed
            return DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    Label(title(state), systemImage: icon(state))
                        .foregroundColor(color)
                        .font(.headline)
                }
                DynamicIslandExpandedRegion(.trailing) {
                    Text(state.since, style: .timer)
                        .font(.title2.bold().monospacedDigit())
                        .multilineTextAlignment(.trailing)
                        .frame(maxWidth: 110)
                }
                DynamicIslandExpandedRegion(.bottom) {
                    Text(subtitle(context.attributes.name, state))
                        .font(.caption)
                        .foregroundColor(Palette.muted)
                }
            } compactLeading: {
                Image(systemName: icon(state)).foregroundColor(color)
            } compactTrailing: {
                Text(state.since, style: .timer)
                    .monospacedDigit()
                    .frame(maxWidth: 56)
                    .foregroundColor(color)
            } minimal: {
                Image(systemName: icon(state)).foregroundColor(color)
            }
        }
    }
}

private func icon(_ state: BabyActivityAttributes.ContentState) -> String {
    state.kind == "sleep" ? "moon.fill" : "drop.fill"
}

private func title(_ state: BabyActivityAttributes.ContentState) -> String {
    state.kind == "sleep" ? "Спит" : "Кормление"
}

private func subtitle(_ name: String, _ state: BabyActivityAttributes.ContentState) -> String {
    if state.kind == "sleep" { return "\(name) уснул(а) в \(state.since.formatted(date: .omitted, time: .shortened))" }
    if let side = state.side { return "\(name) · сейчас \(side) грудь" }
    return name
}

struct LockScreenActivity: View {
    let name: String
    let state: BabyActivityAttributes.ContentState

    var body: some View {
        let color = state.kind == "sleep" ? Palette.sleep : Palette.feed
        HStack(spacing: 14) {
            Image(systemName: icon(state))
                .font(.title2)
                .foregroundColor(color)
                .frame(width: 44, height: 44)
                .background(color.opacity(0.18))
                .clipShape(Circle())
            VStack(alignment: .leading, spacing: 2) {
                Text(title(state)).font(.headline).foregroundColor(Palette.text)
                Text(subtitle(name, state)).font(.caption).foregroundColor(Palette.muted)
            }
            Spacer()
            Text(state.since, style: .timer)
                .font(.title.bold().monospacedDigit())
                .foregroundColor(Palette.text)
                .multilineTextAlignment(.trailing)
                .frame(maxWidth: 120)
        }
        .padding(16)
    }
}
