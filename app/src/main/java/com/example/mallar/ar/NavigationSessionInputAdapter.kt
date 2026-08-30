package com.example.mallar.ar

import android.util.Log
import com.example.mallar.ar.model.NavigationSessionSnapshot
import com.example.mallar.ui.localization.NavigationState

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * Module 9 — NavigationSessionInputAdapter
 * ─────────────────────────────────────────────────────────────────────────────
 * 
 * Provides the subsystem's only point of contact with the existing application's
 * global navigation state. Per Spec §3, it performs exactly one read at start.
 */
object NavigationSessionInputAdapter {
    private const val TAG = "NavSessionInputAdapter"

    /**
     * Captures an immutable snapshot of the current NavigationState.
     * 
     * Precondition: Assumes mandatory data (path, start/end) is populated upstream.
     * Returns null if mandatory data is missing, with defensive logging.
     */
    fun takeSnapshot(): NavigationSessionSnapshot? {
        val selectedPlace = NavigationState.selectedPlace
        val startPlace = NavigationState.startPlace
        val path = NavigationState.aStarPath

        if (selectedPlace == null || startPlace == null || path == null) {
            Log.w(TAG, "Snapshot aborted: NavigationState incomplete. " +
                "dest=${selectedPlace?.brand}, start=${startPlace?.brand}, pathNodes=${path?.nodeIds?.size}")
            return null
        }

        // Resolving the true start graph node ID:
        // path.nodeIds.first() is the actual starting GraphNode ID on the computed route.
        val resolvedStartNodeId = path.nodeIds.firstOrNull() ?: startPlace.id

        // Create immutable snapshot.
        return NavigationSessionSnapshot(
            destinationName = selectedPlace.brand,
            startNodeId = resolvedStartNodeId,
            pathNodeIds = path.nodeIds.toList(),
            instructions = path.steps.toList(),
            initialHeadingDeg = NavigationState.estimatedHeadingDeg,
            startWithAr = NavigationState.startWithAr
        ).also {
            Log.d(TAG, "Module 9: Snapshot captures destination '${it.destinationName}' with ${it.pathNodeIds.size} nodes, startNodeId=${it.startNodeId}.")
        }
    }
}
