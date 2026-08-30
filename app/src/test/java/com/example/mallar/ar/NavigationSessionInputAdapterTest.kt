package com.example.mallar.ar

import android.util.Log
import com.example.mallar.data.AStarPath
import com.example.mallar.data.Place
import com.example.mallar.ui.localization.NavigationState
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class NavigationSessionInputAdapterTest {

    @Before
    fun setup() {
        NavigationState.reset()
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `takeSnapshot - with valid NavigationState - returns correct snapshot`() {
        // Arrange
        val startPlace = Place(id = 1, brand = "Start", x = 0, y = 0, logo = "logo.png")
        val selectedPlace = Place(id = 2, brand = "Target Store", x = 10, y = 10, logo = "logo.png")
        val path = AStarPath(nodeIds = listOf(1, 10, 2), totalDistancePx = 20.0, steps = emptyList())
        
        NavigationState.startPlace = startPlace
        NavigationState.selectedPlace = selectedPlace
        NavigationState.aStarPath = path

        // Act
        val snapshot = NavigationSessionInputAdapter.takeSnapshot()

        // Assert
        assertNotNull(snapshot)
        assertEquals("Target Store", snapshot?.destinationName)
        assertEquals(1, snapshot?.startNodeId)
        assertEquals(listOf(1, 10, 2), snapshot?.pathNodeIds)
    }

    @Test
    fun `takeSnapshot - verifies snapshot immutability`() {
        // Arrange
        val startPlace = Place(id = 1, brand = "Start", x = 0, y = 0, logo = "logo.png")
        val selectedPlace = Place(id = 2, brand = "Original End", x = 10, y = 10, logo = "logo.png")
        val path = AStarPath(nodeIds = listOf(1, 2), totalDistancePx = 10.0, steps = emptyList())
        
        NavigationState.startPlace = startPlace
        NavigationState.selectedPlace = selectedPlace
        NavigationState.aStarPath = path

        // Act
        val snapshot = NavigationSessionInputAdapter.takeSnapshot()
        
        // Mutate original global state
        NavigationState.selectedPlace = Place(id = 3, brand = "Mutated End", x = 20, y = 20, logo = "logo.png")
        
        // Assert
        assertNotNull(snapshot)
        assertEquals("Original End", snapshot?.destinationName)
    }

    @Test
    fun `takeSnapshot - verifies single read guarantee`() {
        // Arrange
        mockkObject(NavigationState)
        val path = AStarPath(nodeIds = listOf(1, 2), totalDistancePx = 10.0, steps = emptyList())
        val start = Place(id = 1, brand = "Start", x = 0, y = 0, logo = "")
        val end = Place(id = 2, brand = "End", x = 10, y = 10, logo = "")
        
        every { NavigationState.startPlace } returns start
        every { NavigationState.selectedPlace } returns end
        every { NavigationState.aStarPath } returns path
        every { NavigationState.estimatedHeadingDeg } returns null
        every { NavigationState.startWithAr } returns false

        // Act
        NavigationSessionInputAdapter.takeSnapshot()

        // Assert
        // We verify that the adapter reads each relevant field exactly once.
        verify(exactly = 1) { NavigationState.startPlace }
        verify(exactly = 1) { NavigationState.selectedPlace }
        verify(exactly = 1) { NavigationState.aStarPath }
        
        unmockkObject(NavigationState)
    }

    @Test
    fun `takeSnapshot - with incomplete NavigationState - returns null`() {
        // Arrange: Missing path
        NavigationState.startPlace = Place(id = 1, brand = "Start", x = 0, y = 0, logo = "logo.png")
        NavigationState.selectedPlace = Place(id = 2, brand = "End", x = 10, y = 10, logo = "logo.png")
        NavigationState.aStarPath = null

        // Act
        val snapshot = NavigationSessionInputAdapter.takeSnapshot()

        // Assert
        assertNull(snapshot)
    }
}
