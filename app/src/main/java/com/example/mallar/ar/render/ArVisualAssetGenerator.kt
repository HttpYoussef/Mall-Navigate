package com.example.mallar.ar.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * ArVisualAssetGenerator
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * Generates high-resolution, anti-aliased Google Maps Live View-style floor
 * navigation chevron decals and arrival beacons in memory.
 *
 * Designed with high-contrast borders and self-luminous colors so AR arrows
 * are clearly visible on any indoor mall flooring without being dimmed or
 * shaded black by ambient scene lighting.
 */
object ArVisualAssetGenerator {
    private const val BITMAP_SIZE = 512

    private var standardChevronBitmap: Bitmap? = null
    private var turnChevronBitmap: Bitmap? = null
    private var arrivalBeaconBitmap: Bitmap? = null

    @Synchronized
    fun getStandardChevron(): Bitmap {
        return standardChevronBitmap ?: generateChevronBitmap(
            outerBorderColor = 0xFFFFFFFF.toInt(),
            bodyColor = 0xFF1A73E8.toInt(),       // Google Blue
            coreColor = 0xFF00E5FF.toInt()        // Electric Cyan Highlight
        ).also { standardChevronBitmap = it }
    }

    @Synchronized
    fun getTurnChevron(): Bitmap {
        return turnChevronBitmap ?: generateChevronBitmap(
            outerBorderColor = 0xFFFFFFFF.toInt(),
            bodyColor = 0xFFFF9100.toInt(),       // High-visibility Amber
            coreColor = 0xFFFFD600.toInt()        // Bright Golden Core
        ).also { turnChevronBitmap = it }
    }

    @Synchronized
    fun getArrivalBeacon(): Bitmap {
        return arrivalBeaconBitmap ?: generateArrivalBitmap().also { arrivalBeaconBitmap = it }
    }

    private fun generateChevronBitmap(
        outerBorderColor: Int,
        bodyColor: Int,
        coreColor: Int
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(BITMAP_SIZE, BITMAP_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
            style = Paint.Style.FILL
        }

        // Soft drop shadow for floor depth
        paint.color = 0x55000000
        val shadowPath = createChevronPath(offsetY = 12f, inset = 0f)
        canvas.drawPath(shadowPath, paint)

        // Outer high-contrast white border
        paint.color = outerBorderColor
        val outerPath = createChevronPath(offsetY = 0f, inset = 0f)
        canvas.drawPath(outerPath, paint)

        // Main navigation color body
        paint.color = bodyColor
        val bodyPath = createChevronPath(offsetY = 0f, inset = 24f)
        canvas.drawPath(bodyPath, paint)

        // Inner glowing core
        paint.color = coreColor
        val corePath = createChevronPath(offsetY = 0f, inset = 52f)
        canvas.drawPath(corePath, paint)

        return bitmap
    }

    private fun createChevronPath(offsetY: Float, inset: Float): Path {
        val path = Path()
        val cx = BITMAP_SIZE / 2f

        val tipY = 64f + inset * 1.3f + offsetY
        val rightOuterX = 436f - inset * 0.8f
        val rightOuterY = 376f - inset * 0.4f + offsetY
        val rightInnerX = 366f - inset * 0.6f
        val rightInnerY = 426f - inset * 0.6f + offsetY
        val notchY = 270f + inset * 0.7f + offsetY
        val leftInnerX = 146f + inset * 0.6f
        val leftInnerY = 426f - inset * 0.6f + offsetY
        val leftOuterX = 76f + inset * 0.8f
        val leftOuterY = 376f - inset * 0.4f + offsetY

        path.moveTo(cx, tipY)
        path.lineTo(rightOuterX, rightOuterY)
        path.lineTo(rightInnerX, rightInnerY)
        path.lineTo(cx, notchY)
        path.lineTo(leftInnerX, leftInnerY)
        path.lineTo(leftOuterX, leftOuterY)
        path.close()

        return path
    }

    private fun generateArrivalBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(BITMAP_SIZE, BITMAP_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val cx = BITMAP_SIZE / 2f
        val cy = BITMAP_SIZE / 2f

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
            style = Paint.Style.FILL
        }

        // Soft drop shadow
        paint.color = 0x55000000
        canvas.drawCircle(cx, cy + 8f, 210f, paint)

        // Outer white ring
        paint.color = 0xFFFFFFFF.toInt()
        canvas.drawCircle(cx, cy, 210f, paint)

        // Outer emerald ring
        paint.color = 0xFF00E676.toInt()
        canvas.drawCircle(cx, cy, 195f, paint)

        // Middle white ring
        paint.color = 0xFFFFFFFF.toInt()
        canvas.drawCircle(cx, cy, 145f, paint)

        // Inner emerald circle
        paint.color = 0xFF00C853.toInt()
        canvas.drawCircle(cx, cy, 130f, paint)

        // Center white ring
        paint.color = 0xFFFFFFFF.toInt()
        canvas.drawCircle(cx, cy, 60f, paint)

        // Center emerald bullseye
        paint.color = 0xFF00E676.toInt()
        canvas.drawCircle(cx, cy, 45f, paint)

        return bitmap
    }

    fun dispose() {
        standardChevronBitmap?.recycle()
        standardChevronBitmap = null
        turnChevronBitmap?.recycle()
        turnChevronBitmap = null
        arrivalBeaconBitmap?.recycle()
        arrivalBeaconBitmap = null
    }
}
