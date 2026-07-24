package com.example.mallar

import android.app.Application
import com.example.mallar.data.StartupCoordinator

/**
 * Custom Application class to trigger early initialization of critical components.
 */
class MallARApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Trigger initialization as soon as the process starts
        StartupCoordinator.initialize(this)
    }
}
