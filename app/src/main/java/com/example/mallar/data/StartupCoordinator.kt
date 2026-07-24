package com.example.mallar.data

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class StartupState {
    Idle,
    Loading,
    Success,
    Error
}

/**
 * Manages the application's critical startup sequence with fault tolerance and state tracking.
 */
object StartupCoordinator {

    private val _state = MutableStateFlow(StartupState.Idle)
    val state: StateFlow<StartupState> = _state.asStateFlow()

    private var initJob: Job? = null

    /**
     * Triggers the initialization sequence. Uses applicationContext to prevent Activity leaks.
     */
    fun initialize(context: Context) {
        if (_state.value == StartupState.Loading || _state.value == StartupState.Success) return

        val appContext = context.applicationContext
        initJob?.cancel()
        initJob = CoroutineScope(Dispatchers.IO).launch {
            _state.value = StartupState.Loading
            try {
                // Parallel initialization of preferences and managers
                val prefsTask = launch { AppPreferences.init(appContext) }
                val favTask = launch { FavoritesManager.init(appContext) }
                val parkingTask = launch { ParkingManager.init(appContext) }
                
                joinAll(prefsTask, favTask, parkingTask)

                // Critical data loading with 5-second timeout
                withTimeout(5000) {
                    PlaceRepository.load(appContext)
                    MallGraphRepository.load(appContext)
                }

                _state.value = StartupState.Success
            } catch (e: Exception) {
                android.util.Log.e("StartupCoordinator", "Initialization failed", e)
                _state.value = StartupState.Error
            }
        }
    }

    /**
     * Resets the coordinator to Idle state. Useful for logout or testing.
     */
    fun reset() {
        initJob?.cancel()
        _state.value = StartupState.Idle
    }

    fun retry(context: Context) {
        _state.value = StartupState.Idle
        initialize(context)
    }
}
