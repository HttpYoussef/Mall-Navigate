package com.example.mallar.ar

import android.content.Context
import android.util.Log
import com.google.ar.core.Session
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState
import io.mockk.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ArCoreSessionManagerTest {

    private val mockContext = mockk<Context>(relaxed = true)
    private lateinit var sessionManager: ArCoreSessionManager

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
        
        sessionManager = ArCoreSessionManager(mockContext)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `Lifecycle State - initial state is PAUSED`() {
        assertEquals(ArCoreSessionManager.LifecycleState.PAUSED, sessionManager.lifecycleState.value)
    }

    @Test
    fun `updateTrackingState - updates flows correctly`() {
        sessionManager.updateTrackingState(TrackingState.TRACKING, TrackingFailureReason.NONE)
        
        assertEquals(TrackingState.TRACKING, sessionManager.trackingState.value)
        assertEquals(TrackingFailureReason.NONE, sessionManager.failureReason.value)
    }

    @Test
    fun `updateTrackingState - logs change only on actual difference`() {
        sessionManager.updateTrackingState(TrackingState.TRACKING, TrackingFailureReason.NONE)
        
        // Repeat same state - should NOT log again
        sessionManager.updateTrackingState(TrackingState.TRACKING, TrackingFailureReason.NONE)
        
        // We verify that Log.d was called exactly once (the first time)
        verify(exactly = 1) { Log.d(any(), any()) }
    }
}
