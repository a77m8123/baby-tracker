package com.babytracker.data

import com.babytracker.model.AppData
import kotlinx.serialization.json.Json

object AppDataCodec {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    fun encode(data: AppData): String = json.encodeToString(AppData.serializer(), data)

    /** Битые или чужие данные не роняют приложение — возвращаем null. */
    fun decodeOrNull(raw: String?): AppData? {
        if (raw.isNullOrBlank()) return null
        val parsed = runCatching { json.decodeFromString(AppData.serializer(), raw) }.getOrNull() ?: return null
        return normalize(parsed)
    }

    fun decode(raw: String?): AppData = decodeOrNull(raw) ?: AppData()

    private fun normalize(data: AppData): AppData {
        val s = data.settings
        return data.copy(
            settings = s.copy(
                name = s.name.trim().ifEmpty { "Малыш" }.take(30),
                feedIntervalMinutes = s.feedIntervalMinutes.coerceIn(30, 8 * 60),
                defaultBottleMl = s.defaultBottleMl.coerceIn(10, 400),
                sleepGoalMinutes = s.sleepGoalMinutes.coerceIn(0, 20 * 60),
                soundTimerMinutes = s.soundTimerMinutes.coerceIn(0, 12 * 60),
                volume = s.volume.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0.7f,
            ),
            // незакрытым может быть только один сон — самый поздний
            sleeps = data.sleeps
                .filter { it.end == null || it.end >= it.start }
                .let { list ->
                    val open = list.filter { it.end == null }.maxByOrNull { it.start }
                    list.filter { it.end != null || it == open }
                },
            growth = data.growth.filter { g ->
                (g.weightG ?: 1) > 0 && (g.heightCm?.isFinite() ?: true) && (g.headCm?.isFinite() ?: true)
            },
        )
    }
}
