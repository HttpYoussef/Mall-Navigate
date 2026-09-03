package com.example.mallar

import android.app.Application
import com.example.mallar.data.LegacyLanguageMigration
import com.example.mallar.data.StartupCoordinator

/**
 * Custom Application class to trigger early initialization of critical components.
 */
class MallARApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Run one-time legacy language migration before any components initialize
        LegacyLanguageMigration.runOnce(this)
        // Trigger initialization as soon as the process starts
        StartupCoordinator.initialize(this)
    }
}
