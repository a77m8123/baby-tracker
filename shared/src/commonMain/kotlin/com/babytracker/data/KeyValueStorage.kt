package com.babytracker.data

/** На Android — SharedPreferences, на iOS — NSUserDefaults. */
interface KeyValueStorage {
    fun read(key: String): String?
    fun write(key: String, value: String)
}

class InMemoryStorage(initial: Map<String, String> = emptyMap()) : KeyValueStorage {
    private val values = initial.toMutableMap()
    override fun read(key: String): String? = values[key]
    override fun write(key: String, value: String) {
        values[key] = value
    }
}
