package com.example.mallar.ar

import android.util.Log
import com.example.mallar.ar.model.NavigationSessionSnapshot
import com.example.mallar.data.*
import io.mockk.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RoutePathLayerTest {

    private val testGraph = MallGraph(
        nodes = listOf(
            GraphNode(id = 1, x = 0.0, y = 0.0, floor = 2, shopId = null, shopName = null, logo = null),
            GraphNode(id = 2, x = 10.0, y = 0.0, floor = 2, shopId = null, shopName = null, logo = null),
            GraphNode(id = 3, x = 20.0, y = 0.0, floor = 2, shopId = null, shopName = null, logo = null),
            GraphNode(id = 4, x = 10.0, y = 10.0, floor = 2, shopId = null, shopName = null, logo = null)
        ),
        edges = listOf(
            GraphEdge(1, 2), GraphEdge(2, 3), GraphEdge(2, 4)
        )
    )

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0

        mockkObject(MallGraphRepository)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `RoutePathLayer - initialization - resolves metadata correctly`() {
        // Arrange
        val snapshot = NavigationSessionSnapshot(
            destinationName = "Store",
            startNodeId = 1,
            pathNodeIds = listOf(1, 2, 3),
            instructions = listOf(
                NavInstruction(AStarDirection.STRAIGHT, 10.0, 0),
                NavInstruction(AStarDirection.ARRIVED, 0.0, 2)
            )
        )

        // Act
        val layer = RoutePathLayer(snapshot, testGraph)

        // Assert
        assertEquals("Should hold 3 nodes", 3, layer.getActiveRouteIds().size)
        val metadata = layer.getRouteMetadata()
        assertEquals("First node should be 1", 1, metadata[0].nodeId)
        assertEquals("First node direction should be STRAIGHT", AStarDirection.STRAIGHT, metadata[0].direction)
        assertEquals("Last node should be 3", 3, metadata[2].nodeId)
        assertEquals("Last node direction should be ARRIVED", AStarDirection.ARRIVED, metadata[2].direction)
        assertTrue("Last node must be flagged as destination", metadata[2].isDestination)
    }

    @Test
    fun `recalculate - updates internal state correctly`() {
        // Arrange
        val initialSnapshot = NavigationSessionSnapshot(
            destinationName = "Store",
            startNodeId = 1,
            pathNodeIds = listOf(1, 2, 3),
            instructions = emptyList()
        )
        val layer = RoutePathLayer(initialSnapshot, testGraph)
        
        // Mock a reroute from node 2 to node 3 that goes through node 4 instead
        val newPath = AStarPath(
            nodeIds = listOf(2, 4, 3),
            totalDistancePx = 20.0,
            steps = listOf(
                NavInstruction(AStarDirection.RIGHT, 10.0, 0),
                NavInstruction(AStarDirection.LEFT, 10.0, 1),
                NavInstruction(AStarDirection.ARRIVED, 0.0, 2)
            )
        )
        // Recalculate(2) will look for path from 2 to 3 (last node of current path)
        every { MallGraphRepository.aStarByNodeId(testGraph, 2, 3) } returns newPath

        // Act
        layer.recalculate(2)

        // Assert
        assertEquals("Path should be updated to new IDs", listOf(2, 4, 3), layer.getActiveRouteIds())
        val metadata = layer.getRouteMetadata()
        assertEquals("Metadata count must match path size", 3, metadata.size)
        assertEquals("Node 4 should have direction LEFT", AStarDirection.LEFT, metadata[1].direction)
    }
}
