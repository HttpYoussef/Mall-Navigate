package com.example.mallar.ml

import android.graphics.Bitmap
import android.util.Log
import com.example.mallar.data.GraphNode
import com.example.mallar.data.MallGraph
import com.example.mallar.data.Place
import io.mockk.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class LocalizationEngineTest {

    private val mockBitmap = mockk<Bitmap>()
    private val mockDetector = mockk<LogoDetector>()
    
    private val mockGraph = MallGraph(
        nodes = listOf(
            GraphNode(id = 1, x = 10.0, y = 10.0, floor = 2, shopId = 101, shopName = "Zara", logo = "zara.png"),
            GraphNode(id = 2, x = 20.0, y = 20.0, floor = 2, shopId = 102, shopName = "Nike", logo = "nike.png"),
            GraphNode(id = 3, x = 15.0, y = 15.0, floor = 2, shopId = null, shopName = null, logo = null)
        ),
        edges = emptyList()
    )
    
    private val mockPlaces = listOf(
        Place(id = 101, brand = "Zara", x = 10, y = 10, logo = "zara.png"),
        Place(id = 102, brand = "Nike", x = 20, y = 20, logo = "nike.png")
    )

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0
        
        mockkObject(PnPSolver)
        
        every { mockBitmap.width } returns 1080
        every { mockBitmap.height } returns 1920
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `estimatePose - path No Detection - returns landmarkCount 0`() {
        every { mockDetector.detectTopNWithLocation(any(), any(), any()) } returns emptyList()
        
        val result = LocalizationEngine.estimatePose(mockBitmap, mockDetector, mockGraph, mockPlaces)
        
        assertEquals(0, result.landmarkCount)
    }

    @Test
    fun `estimatePose - path Single Landmark - returns landmarkCount 1`() {
        every { mockDetector.detectTopNWithLocation(any(), any(), any()) } returns listOf(
            DetectionResult("Zara", 0.9f, 0.5f, 0.5f)
        )
        
        val result = LocalizationEngine.estimatePose(mockBitmap, mockDetector, mockGraph, mockPlaces)
        
        assertEquals(1, result.landmarkCount)
    }

    @Test
    fun `estimatePose - path PnP Success - returns correct landmarkCount`() {
        every { mockDetector.detectTopNWithLocation(any(), any(), any()) } returns listOf(
            DetectionResult("Zara", 0.9f, 0.4f, 0.6f),
            DetectionResult("Nike", 0.85f, 0.6f, 0.4f)
        )
        every { PnPSolver.solve(any(), any(), any(), any(), any()) } returns PnPSolver.PoseResult(15.0, 15.0, 90f, 1.0f, 2)
        
        val result = LocalizationEngine.estimatePose(mockBitmap, mockDetector, mockGraph, mockPlaces)
        
        assertEquals(2, result.landmarkCount)
    }

    @Test
    fun `estimatePose - path Centroid Fallback - returns correct landmarkCount`() {
        every { mockDetector.detectTopNWithLocation(any(), any(), any()) } returns listOf(
            DetectionResult("Zara", 0.9f, 0.4f, 0.6f),
            DetectionResult("Nike", 0.85f, 0.6f, 0.4f)
        )
        every { PnPSolver.solve(any(), any(), any(), any(), any()) } returns null
        
        val result = LocalizationEngine.estimatePose(mockBitmap, mockDetector, mockGraph, mockPlaces)
        
        assertEquals(2, result.landmarkCount)
    }
}
