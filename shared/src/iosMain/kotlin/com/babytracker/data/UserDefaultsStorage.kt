package com.babytracker.data

import platform.Foundation.NSUserDefaults

class UserDefaultsStorage : KeyValueStorage {
    private val defaults = NSUserDefaults.standardUserDefaults

    override fun read(key: String): String? = defaults.stringForKey(key)

    override fun write(key: String, value: String) {
        defaults.setObject(value, forKey = key)
    }
}
