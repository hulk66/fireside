package org.himstedt.immichslideshow.slideshow

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Geometry of the Vintage Prints theme: where the front print sits, where the
 * scatter behind it lies, how the two cross-fade, and how the whole composition
 * drifts.
 *
 * Pure arithmetic like [ReflectionsWall] and [OrigamiFold], so the parts that
 * would otherwise only be checkable by eye — that the front print always fits the
 * frame, that its border is even on all four sides, that the scatter actually
 * covers the stage — are unit tested.
 */
object VintagePrints {

    /**
     * Prints scattered behind the front one. One per cell of the jittered grid
     * below, so the grid has no hole in it: a missing cell leaves a bare patch of
     * stage showing, and the pile is the whole background.
     */
    /** Corner prints, laid under the grid so the frame is always backed by paper. */
    private const val UNDER = 4

    /** Every print in the pile: the corner layer first, then the grid. */
    const val BACKDROP = UNDER + 10

    /** Border around the photo, as a share of the print's width. */
    const val MARGIN = 0.014f

    /** How far a print may be tilted, in degrees. */
    const val FRONT_TILT = 3.5f
    const val BACK_TILT = 14f

    /**
     * Share of the transition at which the incoming print is fully in. The
     * incoming print leads — in the reference it is already opaque in places
     * while the outgoing one is still a bright ghost.
     */
    private const val LEAD = 0.72f

    /**
     * Share of the transition at which the outgoing print starts to clear. Before
     * this it keeps its full strength and simply bleaches, which is what makes it
     * read as a photograph going white rather than as a ghost fading into the dark.
     */
    private const val TAIL = 0.30f

    /** How white the outgoing print goes at its most washed. */
    private const val WASH_MAX = 0.60f

    /** Air between the front print's rotated corners and the frame edge. */
    private const val AIR = 0.045f

    /**
     * How much of the frame the front print must span, in at least one direction.
     *
     * Measured as a span rather than as an area because the area a print can cover
     * depends on its shape: a 1:3 portrait print on a 16:9 screen is narrow
     * however tall it is, and demanding a share of the *area* would force it to be
     * enormous. Spanning most of the frame in one direction is what makes it the
     * subject, and it works for every shape.
     */
    private const val HERO_SPAN = 0.75f

    /** Largest the print may get, as a share of the largest that would fit. */
    private const val MAX_OVERFLOW = 1.20f

    private const val ATTEMPTS = 8

    /** How far off centre the front print may sit, as a share of the frame. */
    private const val CENTRE_LO = 0.38f
    private const val CENTRE_HI = 0.62f

    /**
     * How far the composition turns from one end of a hold to the other, and how
     * far it creeps sideways.
     *
     * The drift is a *ramp*, not an oscillation. A sine wave reverses every minute
     * or so, which reads as the picture pumping in and out; the reference camera
     * simply keeps going one way for as long as a photo is up.
     */
    private const val DRIFT_TILT = 1.25f
    private const val DRIFT_PAN = 0.0055f
    private const val DRIFT_ZOOM = 0.030f

    /** The scatter is laid on a jittered grid, so it covers rather than clumps. */
    private const val COLS = 5
    private const val ROWS = 2

    private const val DEG = (PI / 180.0).toFloat()
    private const val SALT_TILT = 1
    private const val SALT_SIZE = 2
    private const val SALT_X = 3
    private const val SALT_Y = 4
    private const val SALT_BACK = 20

    /** One print on the stage, border included. */
    data class Print(
        val photoIndex: Int,
        val centerX: Float,
        val centerY: Float,
        /** Outer size, border included. */
        val width: Float,
        val height: Float,
        val angleDeg: Float,
        val margin: Float,
    ) {
        val photoLeft: Float get() = centerX - width / 2f + margin
        val photoTop: Float get() = centerY - height / 2f + margin
        val photoWidth: Float get() = width - margin * 2f
        val photoHeight: Float get() = height - margin * 2f

        /** Half-extents of the print once it is tilted. */
        val halfWidth: Float
            get() = (abs(width * cos(angleDeg * DEG)) + abs(height * sin(angleDeg * DEG))) / 2f
        val halfHeight: Float
            get() = (abs(width * sin(angleDeg * DEG)) + abs(height * cos(angleDeg * DEG))) / 2f
    }

    /** How the two prints cross-fade on the way to the next one. */
    data class Blend(
        /** Alpha of the print that is leaving. */
        val outgoing: Float,
        /** Alpha of the print that is arriving; it leads [outgoing]. */
        val incoming: Float,
        /** How far the outgoing print has washed out to a bright haze. */
        val wash: Float,
    )

    /**
     * The front print for one photo.
     *
     * The print takes the photo's own shape, so the border is even on all four
     * sides and the whole photo shows — no crop, no bars. Size, tilt and position
     * are randomised per photo, which is what keeps a wall of one print from
     * looking like a fixed frame with pictures swapped inside it.
     */
    fun front(photoIndex: Int, aspect: Float, frameW: Float, frameH: Float, seed: Long): Print {
        val a = aspect.coerceIn(0.25f, 4f)
        val angle = Rng.range(seed, SALT_TILT, -FRONT_TILT, FRONT_TILT)
        val cosA = abs(cos(angle * DEG))
        val sinA = abs(sin(angle * DEG))

        // Border is a share of the width, so the outer size follows from the
        // photo's shape: width per unit of photo height, and height likewise.
        val perWidth = a / (1f - 2f * MARGIN)
        val perHeight = 1f + 2f * MARGIN * perWidth

        // Largest photo height whose tilted print still clears the frame.
        val availW = frameW * (1f - 2f * AIR)
        val availH = frameH * (1f - 2f * AIR)
        val byWidth = availW / (perWidth * cosA + perHeight * sinA)
        val byHeight = availH / (perWidth * sinA + perHeight * cosA)
        val fitting = minOf(byWidth, byHeight)

        // The print is allowed past the edge of the frame, the way the reference
        // lets a print run off the bottom of the screen — that is what makes it
        // read as a physical print larger than the window rather than as a picture
        // fitted into one. It still has to stay the subject, so an arrangement
        // that leaves most of the frame uncovered is re-rolled.
        var best: Print? = null
        var bestCoverage = -1f
        for (attempt in 0 until ATTEMPTS) {
            val photoHeight = fitting * Rng.range(seed, SALT_SIZE + attempt, 0.80f, MAX_OVERFLOW)
            val width = perWidth * photoHeight
            val height = perHeight * photoHeight
            val candidate = Print(
                photoIndex = photoIndex,
                // Near the middle of the screen, not anywhere on it: a print
                // pinned to a corner reads as a layout, while one sitting a little
                // off centre reads as a print put down by hand. The size carries
                // the variation, and the print is free to run off the edges.
                centerX = frameW * Rng.range(seed, SALT_X + attempt, CENTRE_LO, CENTRE_HI),
                centerY = frameH * Rng.range(seed, SALT_Y + attempt, CENTRE_LO, CENTRE_HI),
                width = width,
                height = height,
                angleDeg = angle,
                margin = MARGIN * width,
            )
            val span = maxOf(candidate.width / frameW, candidate.height / frameH)
            if (span > bestCoverage) {
                best = candidate
                bestCoverage = span
            }
            if (span >= HERO_SPAN) return candidate
        }
        // Never reached in practice, but a print at the edge of the frame is a
        // better answer than no print at all.
        return best ?: Print(
            photoIndex = photoIndex,
            centerX = frameW / 2f,
            centerY = frameH / 2f,
            width = perWidth * fitting,
            height = perHeight * fitting,
            angleDeg = angle,
            margin = MARGIN * perWidth * fitting,
        )
    }

    /** Share of the frame that [print] covers, sampled on a grid. */
    fun coverage(print: Print, frameW: Float, frameH: Float): Float {
        val cols = 12
        val rows = 7
        var hit = 0
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val x = (c + 0.5f) / cols * frameW
                val y = (r + 0.5f) / rows * frameH
                if (contains(print, x, y)) hit++
            }
        }
        return hit.toFloat() / (cols * rows)
    }

    /** Is (x, y) inside this tilted print, border included? */
    fun contains(print: Print, x: Float, y: Float): Boolean {
        val rad = print.angleDeg * DEG
        val dx = x - print.centerX
        val dy = y - print.centerY
        val localX = dx * cos(rad) + dy * sin(rad)
        val localY = -dx * sin(rad) + dy * cos(rad)
        return abs(localX) <= print.width / 2f && abs(localY) <= print.height / 2f
    }

    /**
     * One print of the scatter behind the front one.
     *
     * Laid on a jittered grid rather than at free random positions: free positions
     * clump, and clumping leaves a bare stage behind the front print, which is the
     * one thing this theme cannot have. Prints are allowed to hang off the frame —
     * that is what makes it read as a pile rather than a layout.
     */
    fun backdrop(
        photoIndex: Int,
        aspect: Float,
        frameW: Float,
        frameH: Float,
        seed: Long,
        slot: Int,
    ): Print = if (slot < UNDER) {
        // One print centred just outside each corner, large enough that it cannot
        // help but cover it whatever its shape. The grid above leaves narrow gaps
        // between neighbouring prints, and the drift turns the whole composition
        // far enough to bring a gap at the edge into view - which showed bare
        // stage in the corner until this layer existed.
        val corner = slot % UNDER
        val out = 0.02f
        Print(
            photoIndex = photoIndex,
            centerX = frameW * ((if (corner == 0 || corner == 2) -out else 1f + out) +
                Rng.range(seed, SALT_BACK + 100 + slot, -0.04f, 0.04f)),
            centerY = frameH * ((if (corner < 2) -out else 1f + out) +
                Rng.range(seed, SALT_BACK + 120 + slot, -0.04f, 0.04f)),
            width = frameH * Rng.range(seed, SALT_BACK + 140 + slot, 1.05f, 1.45f) *
                aspect.coerceIn(0.35f, 2.6f) / (1f - 2f * MARGIN),
            height = frameH * Rng.range(seed, SALT_BACK + 140 + slot, 1.05f, 1.45f) *
                (1f + 2f * MARGIN),
            angleDeg = Rng.range(seed, SALT_BACK + 160 + slot, -BACK_TILT, BACK_TILT),
            margin = 0f,
        ).let { it.copy(margin = MARGIN * it.width) }
    } else {
        gridPrint(photoIndex, aspect, frameW, frameH, seed, slot - UNDER)
    }

    private fun gridPrint(
        photoIndex: Int,
        aspect: Float,
        frameW: Float,
        frameH: Float,
        seed: Long,
        slot: Int,
    ): Print {
        val col = slot % COLS
        val row = (slot / COLS) % ROWS
        val a = aspect.coerceIn(0.35f, 2.6f)
        val photoHeight = frameH * Rng.range(seed, SALT_BACK + slot, 0.55f, 1.05f)
        val width = photoHeight * a / (1f - 2f * MARGIN)
        val margin = MARGIN * width
        val height = photoHeight + 2f * margin
        return Print(
            photoIndex = photoIndex,
            // Tight jitter: a print that wanders far from its cell leaves a gap
            // between itself and its neighbour, and the drift turns the whole
            // composition far enough to bring that gap into view.
            centerX = frameW * ((col + 0.5f) / COLS + Rng.range(seed, SALT_BACK + 40 + slot, -0.09f, 0.09f)),
            centerY = frameH * ((row + 0.5f) / ROWS + Rng.range(seed, SALT_BACK + 60 + slot, -0.14f, 0.14f)),
            width = width,
            height = height,
            angleDeg = Rng.range(seed, SALT_BACK + 80 + slot, -BACK_TILT, BACK_TILT),
            margin = margin,
        )
    }

    /** The cross-fade between two prints. */
    fun blend(transition: Float): Blend {
        val m = Easing.easeInOutCubic(transition.coerceIn(0f, 1f))
        return Blend(
            // Holds its strength, then clears at the very end: a print that fades
            // as fast as it washes reads as a grey ghost of itself rather than as
            // paper going white.
            outgoing = Easing.smoothstep(((1f - m) / TAIL).coerceIn(0f, 1f)),
            incoming = Easing.smoothstep((m / LEAD).coerceIn(0f, 1f)),
            wash = WASH_MAX * m,
        )
    }

    /** How much the composition must be enlarged to stay covered when turned [deg]. */
    fun overscanFor(deg: Float, frameW: Float, frameH: Float): Float {
        val rad = abs(deg) * DEG
        return maxOf(
            cos(rad) + (frameH / frameW) * sin(rad),
            cos(rad) + (frameW / frameH) * sin(rad),
        )
    }

    /** A slow camera drift over the whole composition. */
    data class Drift(
        /** Scale about the frame centre; never less than the turn needs. */
        val scale: Float,
        val angleDeg: Float,
        val dx: Float,
        val dy: Float,
    ) {
        /** Is this drift guaranteed to leave the frame covered? */
        fun covers(frameW: Float, frameH: Float): Boolean =
            scale >= overscanFor(angleDeg, frameW, frameH)
    }

    /**
     * The composition creeps very slowly, the way the reference does — a push that
     * takes minutes to complete a cycle. The periods are long on purpose: a drift
     * whose period approaches a slide's length is a bob, and a bob reads as a
     * rendering fault rather than as breathing.
     */
    /**
     * Where a photo's camera is on its one-way run, 0..1.
     *
     * A photo creeps from one end of its travel to the other across its *hold* and
     * then stops, holding that framing while it fades out. It never comes back:
     * the photo arriving is a different print with a camera of its own, starting
     * at the beginning of its own run, and the cross-fade bridges the two
     * framings. Nothing reverses at any point, in the hold or in the fade.
     *
     * Coming back instead - returning to the start during the transition - is what
     * made the picture pump: the swing back was visible at the start of the fade,
     * and again as the next hold set off the other way.
     */
    fun phase(hold: Float, transition: Float): Float =
        if (transition > 0f) 1f else hold.coerceIn(0f, 1f)

    fun drift(phase: Float, frameW: Float, frameH: Float): Drift {
        val p = phase.coerceIn(0f, 1f)
        // One-way travel: -1 at the start of the hold, +1 at its end.
        val travel = 2f * p - 1f
        val angle = DRIFT_TILT * travel
        val dx = DRIFT_PAN * travel
        val dy = DRIFT_PAN * 0.75f * travel
        // Turning the composition uncovers its corners, so the zoom and the turn
        // are one motion, not two: the scale is never allowed below what the
        // angle needs. That is also why the pile has to be drawn larger than the
        // frame - the corners of a turned sheet of photos would otherwise show
        // bare stage.
        // Overscan for the steepest turn this drift ever reaches, not for the
        // angle right now. Paying only for the current angle would make the scale
        // dip as the turn passed through zero and climb again - a small pump in
        // the zoom channel, which is the very thing the ramp is here to avoid.
        val cover = overscanFor(DRIFT_TILT, frameW, frameH)
        // Creeping sideways uncovers an edge just as turning uncovers a corner, so
        // the pan is paid for out of the same scale rather than being free.
        val pan = 1f + 2f * maxOf(abs(dx), abs(dy))
        // The push runs the same way as the turn: in over the hold, out again as
        // the next photo arrives.
        val breathe = 1f + DRIFT_ZOOM * p
        return Drift(
            scale = cover * pan * breathe,
            angleDeg = angle,
            dx = dx,
            dy = dy,
        )
    }
}
