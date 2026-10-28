package com.babytracker.sound

/** Доступные звуки. Всё синтезируется в реальном времени, файлов в APK нет. */
enum class SoundType(val title: String, val subtitle: String) {
    WHITE_NOISE("Белый шум", "Мягкое шипение, приглушает малыша"),
    RAIN("Дождь", "Ровный стук капель по крыше"),
    DROPS("Капли", "Редкие бульканье в тишине"),
    LULLABY("Колыбельная", "Брамс, нежный голос"),
    ;

    companion object {
        fun fromName(name: String?): SoundType? = entries.firstOrNull { it.name == name }
    }
}
