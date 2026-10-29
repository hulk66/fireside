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
     * How far the whole composition turns, and how fast. Gentle on purpose: a
     * few degrees over half a minute reads as a camera creeping round the table,
     * while a few degrees over a slide reads as a wobble.
     */
    private const val DRIFT_TILT = 1.2f

    /** Periods in seconds - the drift is driven by a clock in seconds, not ms. */
    private const val DRIFT_TILT_S = 46f
    private const val DRIFT_ZOOM_S = 58f
    private const val DRIFT_PAN_S = 70f

    /** The scatter is laid on a jittered grid, so it covers rather than clumps. */
    private const val COLS = 5
    private const val ROWS = 2

    private const val DEG = (PI / 180.0).toFloat()
    private const val TAU = (2.0 * PI).toFloat()

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
        // Deliberately short of the largest print that would fit: a print that
        // fills the frame has nowhere to move, so every step lands on top of the
        // last one and the dissolve reads as a translucent overlay instead of as
        // a new print placed on the pile.
        val photoHeight = minOf(byWidth, byHeight) * Rng.range(seed, SALT_SIZE, 0.74f, 0.92f)

        val width = perWidth * photoHeight
        val height = perHeight * photoHeight
        val margin = MARGIN * width

        val halfW = (width * cosA + height * sinA) / 2f
        val halfH = (width * sinA + height * cosA) / 2f
        val loX = frameW * AIR + halfW
        val hiX = frameW * (1f - AIR) - halfW
        val loY = frameH * AIR + halfH
        val hiY = frameH * (1f - AIR) - halfH
        return Print(
            photoIndex = photoIndex,
            centerX = loX + (hiX - loX).coerceAtLeast(0f) * Rng.unit(seed, SALT_X),
            centerY = loY + (hiY - loY).coerceAtLeast(0f) * Rng.unit(seed, SALT_Y),
            width = width,
            height = height,
            angleDeg = angle,
            margin = margin,
        )
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

    /** A slow camera drift over the whole composition. */
    data class Drift(
        /** Scale about the frame centre; never less than the turn needs. */
        val scale: Float,
        val angleDeg: Float,
        val dx: Float,
        val dy: Float,
    ) {
        /** Is this drift guaranteed to leave the frame covered? */
        fun covers(frameW: Float, frameH: Float): Boolean {
            val rad = abs(angleDeg) * DEG
            val need = maxOf(
                cos(rad) + (frameH / frameW) * sin(rad),
                cos(rad) + (frameW / frameH) * sin(rad),
            )
            return scale >= need
        }
    }

    /**
     * The composition creeps very slowly, the way the reference does — a push that
     * takes minutes to complete a cycle. The periods are long on purpose: a drift
     * whose period approaches a slide's length is a bob, and a bob reads as a
     * rendering fault rather than as breathing.
     */
    fun drift(elapsedMs: Long, frameW: Float, frameH: Float): Drift {
        val t = elapsedMs / 1000f
        val angle = DRIFT_TILT * sin(t * TAU / DRIFT_TILT_S)
        // Turning the composition uncovers its corners, so the zoom and the turn
        // are one motion, not two: the scale is never allowed below what the
        // angle needs. That is also why the pile has to be drawn larger than the
        // frame - the corners of a turned sheet of photos would otherwise show
        // bare stage.
        val rad = abs(angle) * DEG
        val cover = maxOf(
            cos(rad) + (frameH / frameW) * sin(rad),
            cos(rad) + (frameW / frameH) * sin(rad),
        )
        val dx = 0.0045f * sin(t * TAU / DRIFT_PAN_S + 0.7f)
        val dy = 0.0035f * sin(t * TAU / DRIFT_PAN_S + 2.1f)
        // Creeping sideways uncovers an edge just as turning uncovers a corner, so
        // the pan is paid for out of the same scale rather than being free.
        val pan = 1f + 2f * maxOf(abs(dx), abs(dy))
        val breathe = 1.012f + 0.012f * sin(t * TAU / DRIFT_ZOOM_S + 0.9f)
        return Drift(
            scale = cover * pan * breathe,
            angleDeg = angle,
            dx = dx,
            dy = dy,
        )
    }
}
