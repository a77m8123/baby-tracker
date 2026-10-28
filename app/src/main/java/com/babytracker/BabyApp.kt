package com.babytracker

import android.app.Application
import androidx.room.Room
import com.babytracker.data.BabyDao
import com.babytracker.data.BabyDatabase
import com.babytracker.data.SettingsStore

class BabyApp : Application() {

    lateinit var database: BabyDatabase
        private set
    lateinit var settings: SettingsStore
        private set

    override fun onCreate() {
        super.onCreate()
        database = Room.databaseBuilder(this, BabyDatabase::class.java, "baby.db")
            .fallbackToDestructiveMigration()
            .build()
        settings = SettingsStore(this)
    }
}

val Application.dao: BabyDao
    get() = (this as BabyApp).database.babyDao()

val Application.settingsStore: SettingsStore
    get() = (this as BabyApp).settings
