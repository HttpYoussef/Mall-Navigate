package com.example.mallar.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.graphics.ColorUtils
import kotlin.math.*

// ─────────────────────────────────────────────────────────────────────────────
/**
 * CameraOverlayView
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * A lightweight custom View that renders the pseudo-AR navigation overlay
 * directly via Android Canvas. Placed as a transparent layer on top of the
 * CameraX preview.
 *
 * NO AR ANCHORS — NO GLB MODELS — NO OPENGL REQUIRED.
 *
 * Renders:
 *  • Glowing path dots (projected path waypoints)
 *  • Connecting path lines between waypoints
 *  • Distance-scaled destination beacon with pulsing ring
 *  • Turn direction indicator (arrow chevron) at the top-centre
 *  • User position indicator (glowing blue dot) at screen bottom-centre
 *  • Optional floating distance label
 *
 * All drawing is done per-frame on the main thread via invalidate().
 * The projection data is prepared by [OverlayProjectionEngine] and supplied
 * by [CameraNavigationScreen] after every sensor update.
 */
class CameraOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    // ── State ─────────────────────────────────────────────────────────────────
    private var projectedPoints: List<ProjectedPoint> = emptyList()
    private var turnInfo: TurnInfo? = null
    private var distanceToDestM: Float = 0f
    private var destinationName: String = ""
    private var isNavigating: Boolean = false
    private var showDirectionOverlay: Boolean = true

    // ── Animation ─────────────────────────────────────────────────────────────
    /** Pulse phase [0, 2π] — drives beacon ring animation. */
    private var pulsePhase: Float = 0f
    /** Flow phase [0, 1] — drives dot-flow animation along path. */
    private var flowPhase: Float = 0f

    private val pulseAnimator = ValueAnimator.ofFloat(0f, (2f * PI).toFloat()).apply {
        duration = 2000L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { pulsePhase = it.animatedValue as Float; invalidate() }
    }

    private val flowAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1800L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { flowPhase = it.animatedValue as Float }
    }

    // ── Paints ────────────────────────────────────────────────────────────────

    // Path dot — main fill
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color  = COLOR_PATH_DOT
        style  = Paint.Style.FILL
    }

    // Path dot — glow ring
    private val dotGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color  = ColorUtils.setAlphaComponent(COLOR_PATH_DOT, 120)
        style  = Paint.Style.FILL
    }

    // Path connecting line
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color       = ColorUtils.setAlphaComponent(COLOR_PATH_DOT, 160)
        style       = Paint.Style.STROKE
        strokeWidth = 4f
        strokeCap   = Paint.Cap.ROUND
        strokeJoin  = Paint.Join.ROUND
        pathEffect  = DashPathEffect(floatArrayOf(12f, 10f), 0f)
    }

    // Destination beacon — filled circle
    private val beaconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color  = COLOR_BEACON
        style  = Paint.Style.FILL
    }

    // Destination beacon — pulsing ring
    private val beaconRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color  = COLOR_BEACON
        style  = Paint.Style.STROKE
        strokeWidth = 3f
    }

    // Turn direction arrow background
    private val arrowBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color  = Color.argb(200, 37, 135, 153)
        style  = Paint.Style.FILL
    }

    // Turn direction arrow icon
    private val arrowIconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color       = Color.WHITE
        style       = Paint.Style.FILL_AND_STROKE
        strokeWidth = 2f
        strokeJoin  = Paint.Join.ROUND
        strokeCap   = Paint.Cap.ROUND
    }

    // Text labels
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color     = Color.WHITE
        textSize  = 42f
        textAlign = Paint.Align.CENTER
        typeface  = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        setShadowLayer(6f, 0f, 2f, Color.argb(180, 0, 0, 0))
    }

    private val distLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color     = Color.WHITE
        textSize  = 34f
        textAlign = Paint.Align.CENTER
        typeface  = Typeface.DEFAULT
        setShadowLayer(4f, 0f, 1f, Color.argb(160, 0, 0, 0))
    }

    // User location dot
    private val userDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color  = COLOR_USER_DOT
        style  = Paint.Style.FILL
    }

    private val userDotRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color       = Color.WHITE
        style       = Paint.Style.STROKE
        strokeWidth = 3f
    }

    // ── Reusable scratch objects (avoid per-frame allocation) ──────────────────
    private val scratchPath  = Path()
    private val scratchRectF = RectF()

    // ─────────────────────────────────────────────────────────────────────────
    // Public API — called by the Screen on every sensor / navigation update
    // ─────────────────────────────────────────────────────────────────────────

    fun updateNavigation(
        points: List<ProjectedPoint>,
        turn: TurnInfo?,
        distM: Float,
        destName: String,
        navigating: Boolean
    ) {
        projectedPoints   = points
        turnInfo          = turn
        distanceToDestM   = distM
        destinationName   = destName
        isNavigating      = navigating
        invalidate()
    }

    fun setShowDirectionOverlay(show: Boolean) {
        showDirectionOverlay = show
        invalidate()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        pulseAnimator.start()
        flowAnimator.start()
    }

    override fun onDetachedFromWindow() {
        pulseAnimator.cancel()
        flowAnimator.cancel()
        super.onDetachedFromWindow()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Drawing
    // ─────────────────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!isNavigating || projectedPoints.isEmpty()) {
            drawIdleState(canvas)
            return
        }

        drawPathLines(canvas)
        drawPathDots(canvas)
        drawDestinationBeacon(canvas)
        drawUserDot(canvas)

        if (showDirectionOverlay) {
            drawTurnDirectionCard(canvas)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Draw: idle state (not navigating or no projection data)
    // ─────────────────────────────────────────────────────────────────────────

    private fun drawIdleState(canvas: Canvas) {
        // Draw a faint scanning ring to show the camera is active
        val cx = width / 2f
        val cy = height * 0.6f
        val r  = 40f + 10f * sin(pulsePhase)
        canvas.drawCircle(cx, cy, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(60, 0, 180, 255)
            style = Paint.Style.STROKE
            strokeWidth = 2f
        })
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Draw: connecting path lines between projected waypoints
    // ─────────────────────────────────────────────────────────────────────────

    private fun drawPathLines(canvas: Canvas) {
        val ahead = projectedPoints.filter { it.isAhead }
        if (ahead.size < 2) return

        scratchPath.reset()
        scratchPath.moveTo(ahead[0].screenX, ahead[0].screenY)
        for (i in 1 until ahead.size) {
            val p = ahead[i]
            scratchPath.lineTo(p.screenX, p.screenY)
        }

        // Animate dashOffset for flowing effect
        (linePaint.pathEffect as? DashPathEffect)?.let { /* reuse */ }
        linePaint.pathEffect = DashPathEffect(floatArrayOf(12f, 10f), flowPhase * 22f)
        linePaint.alpha = 160
        canvas.drawPath(scratchPath, linePaint)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Draw: glowing path waypoint dots
    // ─────────────────────────────────────────────────────────────────────────

    private fun drawPathDots(canvas: Canvas) {
        for ((idx, point) in projectedPoints.withIndex()) {
            if (point.isFinalNode) continue   // drawn separately as beacon

            val baseRadius = DOT_BASE_RADIUS * point.perspectiveScale
            val glowRadius = baseRadius * 2.2f
            val alpha      = (point.alpha * 255).toInt().coerceIn(0, 255)

            // Flow brightness — dots brighten as the flow wave passes through
            val flowBrightness = 1f + 0.3f * sin((flowPhase - idx * 0.1f) * 2 * PI.toFloat())
            val dotAlpha       = (alpha * flowBrightness.coerceIn(0.7f, 1.3f)).toInt().coerceIn(0, 255)

            // Glow ring
            dotGlowPaint.alpha = (dotAlpha * 0.4f).toInt()
            canvas.drawCircle(point.screenX, point.screenY, glowRadius, dotGlowPaint)

            // Core dot
            dotPaint.alpha = dotAlpha
            canvas.drawCircle(point.screenX, point.screenY, baseRadius, dotPaint)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Draw: destination beacon with animated pulse ring
    // ─────────────────────────────────────────────────────────────────────────

    private fun drawDestinationBeacon(canvas: Canvas) {
        val dest = projectedPoints.firstOrNull { it.isFinalNode } ?: return

        val baseR   = DOT_BASE_RADIUS * dest.perspectiveScale * 1.8f
        val alpha   = (dest.alpha * 255).toInt().coerceIn(0, 255)

        // Outer pulse ring — expands and fades on pulse
        val pulseR = baseR + (15f + 8f * sin(pulsePhase)) * dest.perspectiveScale
        beaconRingPaint.alpha = (alpha * (0.4f + 0.3f * sin(pulsePhase + PI.toFloat() * 0.5f))).toInt().coerceIn(0, 255)
        canvas.drawCircle(dest.screenX, dest.screenY, pulseR, beaconRingPaint)

        // Second pulse ring (phase-offset for dual-ring effect)
        val pulseR2 = baseR + (8f + 5f * sin(pulsePhase + PI.toFloat())) * dest.perspectiveScale
        beaconRingPaint.alpha = (alpha * 0.25f).toInt()
        canvas.drawCircle(dest.screenX, dest.screenY, pulseR2, beaconRingPaint)

        // Beacon fill
        beaconPaint.alpha = alpha
        canvas.drawCircle(dest.screenX, dest.screenY, baseR, beaconPaint)

        // Inner white dot
        canvas.drawCircle(dest.screenX, dest.screenY, baseR * 0.4f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
            this.alpha = alpha
        })

        // Distance label below beacon
        if (distanceToDestM > 0f && dest.isAhead) {
            val distText = formatDistance(distanceToDestM)
            distLabelPaint.alpha = alpha
            canvas.drawText(distText, dest.screenX, dest.screenY + baseR + 36f, distLabelPaint)

            if (destinationName.isNotBlank()) {
                labelPaint.textSize = 36f
                labelPaint.alpha    = (alpha * 0.9f).toInt()
                canvas.drawText(destinationName, dest.screenX, dest.screenY + baseR + 76f, labelPaint)
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Draw: user position dot (always bottom-centre of screen)
    // ─────────────────────────────────────────────────────────────────────────

    private fun drawUserDot(canvas: Canvas) {
        val cx = width / 2f
        val cy = height * 0.80f   // lower portion — represents "feet" area

        // Outer glow
        canvas.drawCircle(cx, cy, USER_DOT_RADIUS * 2.2f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color  = Color.argb(60, 37, 135, 153)
            style  = Paint.Style.FILL
        })
        // White ring
        canvas.drawCircle(cx, cy, USER_DOT_RADIUS + 4f, userDotRingPaint)
        // Fill
        canvas.drawCircle(cx, cy, USER_DOT_RADIUS, userDotPaint)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Draw: turn direction card at top-centre
    // ─────────────────────────────────────────────────────────────────────────

    private fun drawTurnDirectionCard(canvas: Canvas) {
        val turn = turnInfo ?: return
        val cx   = width / 2f
        val cy   = 160f   // fixed top position

        val cardW = 220f
        val cardH = 120f

        // Card background
        scratchRectF.set(cx - cardW / 2f, cy - cardH / 2f, cx + cardW / 2f, cy + cardH / 2f)
        canvas.drawRoundRect(scratchRectF, 24f, 24f, arrowBgPaint)

        // Arrow chevron icon
        drawTurnChevron(canvas, cx - 30f, cy, turn.direction)

        // Turn label text
        val label = when (turn.direction) {
            OverlayTurnDirection.STRAIGHT -> "Go Straight"
            OverlayTurnDirection.LEFT     -> "Turn Left"
            OverlayTurnDirection.RIGHT    -> "Turn Right"
            OverlayTurnDirection.U_TURN   -> "Turn Around"
        }
        labelPaint.textSize = 34f
        labelPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(label, cx - 10f, cy + 12f, labelPaint)
        labelPaint.textAlign = Paint.Align.CENTER   // restore
    }

    /** Draw a simple chevron arrow for turn direction. */
    private fun drawTurnChevron(canvas: Canvas, cx: Float, cy: Float, direction: OverlayTurnDirection) {
        scratchPath.reset()
        val size = 28f

        when (direction) {
            OverlayTurnDirection.STRAIGHT -> {
                // Up-arrow chevron
                scratchPath.moveTo(cx, cy - size)
                scratchPath.lineTo(cx - size * 0.7f, cy + size * 0.3f)
                scratchPath.moveTo(cx, cy - size)
                scratchPath.lineTo(cx + size * 0.7f, cy + size * 0.3f)
            }
            OverlayTurnDirection.LEFT -> {
                // Left arrow
                scratchPath.moveTo(cx + size * 0.5f, cy - size)
                scratchPath.lineTo(cx - size * 0.5f, cy)
                scratchPath.lineTo(cx + size * 0.5f, cy + size)
            }
            OverlayTurnDirection.RIGHT -> {
                // Right arrow
                scratchPath.moveTo(cx - size * 0.5f, cy - size)
                scratchPath.lineTo(cx + size * 0.5f, cy)
                scratchPath.lineTo(cx - size * 0.5f, cy + size)
            }
            OverlayTurnDirection.U_TURN -> {
                // U-turn arc
                scratchRectF.set(cx - size, cy - size * 0.5f, cx + size, cy + size * 0.5f)
                scratchPath.addArc(scratchRectF, 0f, 180f)
                scratchPath.lineTo(cx - size, cy - size * 0.5f)
            }
        }

        arrowIconPaint.style  = Paint.Style.STROKE
        arrowIconPaint.strokeWidth = 5f
        canvas.drawPath(scratchPath, arrowIconPaint)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun formatDistance(metres: Float): String = when {
        metres < 10f   -> "< 10 m"
        metres < 1000f -> "${metres.toInt()} m"
        else           -> "${"%.1f".format(metres / 1000f)} km"
    }

    // ─────────────────────────────────────────────────────────────────────────
    companion object {
        private val COLOR_PATH_DOT = Color.rgb(37, 135, 153)   // vivid blue
        private val COLOR_BEACON   = Color.rgb(50, 205, 100)    // vivid greenprivate val COLOR_USER_DOT = Color.rgb(37, 135, 153)
        private val COLOR_USER_DOT = Color.rgb(37, 135, 153)   // user blue
// user blue

        private const val DOT_BASE_RADIUS  = 16f    // base radius for path dot (px)
        private const val USER_DOT_RADIUS  = 14f    // radius of user indicator dot (px)
    }
}
