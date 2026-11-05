package com.babytracker.data

import android.content.Context

class SharedPrefsStorage(context: Context) : KeyValueStorage {
    private val prefs = context.getSharedPreferences("malysh", Context.MODE_PRIVATE)

    override fun read(key: String): String? = prefs.getString(key, null)

    // commit, а не apply: виджет пишет из BroadcastReceiver, процесс может сразу завершиться
    override fun write(key: String, value: String) {
        prefs.edit().putString(key, value).commit()
    }
}
