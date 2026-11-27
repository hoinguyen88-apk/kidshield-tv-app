package com.example

import android.app.Application
import com.example.data.local.AppDatabase
import com.example.data.local.GuardPreferences
import com.example.data.repository.WatchGuardRepository

class KidShieldApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
    val preferences: GuardPreferences by lazy { GuardPreferences(this) }
    val repository: WatchGuardRepository by lazy {
        WatchGuardRepository(
            context = this,
            prefs = preferences,
            watchDao = database.watchDao()
        )
    }
}
