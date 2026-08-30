package com.example.mallar.ar.render

import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.node.CubeNode
import com.google.ar.core.Anchor

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * StaticTestObject
 * ─────────────────────────────────────────────────────────────────────────────
 * 
 * Trivial Module 7 subset for Phase 4.
 * Renders a fixed world-locked primitive to verify tracking stability.
 */
object StaticTestObject {
    
    /**
     * Adds a visible test cube at the given anchor.
     */
    fun addTestSphere(sceneView: ARSceneView, anchor: Anchor) {
        val anchorNode = AnchorNode(sceneView.engine, anchor)
        
        // Add a visible cube as a child (Finding 1)
        val cubeNode = CubeNode(
            engine = sceneView.engine
        )
        anchorNode.addChildNode(cubeNode)
        
        sceneView.addChildNode(anchorNode)
    }
}
