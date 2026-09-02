package com.example.mallar.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory, per-launch record of which [Mall] the user picked on the mall
 * selection screen.  Not persisted — a new process always starts with null.
 *
 * Observable via [selected] so that composables (e.g. the Home subtitle)
 * recompose automatically when a mall is chosen.
 *
 * There is intentionally NO reset().  Nothing clears [selected] — not logout,
 * not back-navigation.  The lifecycle gate in MallARNavGraph handles the case
 * where [selected] is null after a process death.
 */
object MallSession {
    private val _selected = MutableStateFlow<Mall?>(null)
    val selected: StateFlow<Mall?> = _selected.asStateFlow()

    fun select(mall: Mall) {
        _selected.value = mall
    }
}
