package org.himstedt.fireside.slideshow

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * The fold itself: which edge a piece of paper turns about, how far it has
 * turned, and the projective mapping that puts it on screen at that angle.
 *
 * Deliberately free of Compose types, so the effect can be unit tested as
 * arithmetic rather than as pixels. The properties that matter are geometric:
 * the hinge must not move, the free edge must shrink towards it, and the far
 * edge must come out shorter than the hinge edge — that trapezoid is the whole
 * difference between paper and a squash.
 */
object OrigamiFold {

    /** The edge the paper is hinged on. The opposite edge lifts away. */
    enum class Edge { TOP, BOTTOM, LEFT, RIGHT }

    /** One page in this many turns as a single sheet, the rest cell by cell. */
    private const val SHEET_EVERY = 4L

    /** Steepest the paper gets. Past this it is edge-on, and gone. */
    const val MAX_ANGLE_DEG = 84f

    /** Which way the paper turns on this page. */
    fun edgeFor(generation: Long, seed: Long): Edge {
        val n = Edge.entries.size
        return Edge.entries[Math.floorMod(generation * 3L + seed, n.toLong()).toInt()]
    }

    /**
     * True when the whole wall turns as one sheet rather than cell by cell. The
     * first page never does: there is nothing behind it to reveal.
     */
    fun sheetFor(generation: Long, seed: Long): Boolean =
        generation > 0L && Math.floorMod(generation * 7L + seed, SHEET_EVERY) == 0L

    /**
     * Homography for a sheet turned [turned] of the way over, as nine floats in
     * row-major order mapping `(x, y, 1)` to `(X, Y, W)`; the point lands at
     * `(X/W, Y/W)`.
     *
     * The free edge turns away from the viewer, so it is divided down by the
     * perspective term: the nearer the viewer ([camera], in pixels), the more the
     * far edge shrinks. Everything at depth also converges towards
     * ([centerX], [centerY]) — the viewer's line of sight, which for a screen is
     * its middle. Returns the identity when nothing has turned.
     */
    fun homography(
        cell: GridRect,
        edge: Edge,
        turned: Float,
        camera: Float,
        centerX: Float,
        centerY: Float,
    ): FloatArray {
        val e = turned.coerceIn(0f, 1f)
        val radians = e * MAX_ANGLE_DEG * (PI.toFloat() / 180f)
        val c = cos(radians)
        val s = sin(radians)
        val f = camera.coerceAtLeast(1f)
        // Depth per pixel of distance from the hinge.
        val k = s / f
        return when (edge) {
            Edge.BOTTOM -> floatArrayOf(
                1f, -centerX * k, centerX * cell.bottom * k,
                0f, c - centerY * k, cell.bottom * (1f - c) + centerY * cell.bottom * k,
                0f, -k, 1f + cell.bottom * k,
            )
            Edge.TOP -> floatArrayOf(
                1f, centerX * k, -centerX * cell.top * k,
                0f, c + centerY * k, cell.top * (1f - c - centerY * k),
                0f, k, 1f - cell.top * k,
            )
            Edge.RIGHT -> floatArrayOf(
                c - centerX * k, 0f, cell.right * (1f - c + centerX * k),
                -centerY * k, 1f, centerY * cell.right * k,
                -k, 0f, 1f + cell.right * k,
            )
            Edge.LEFT -> floatArrayOf(
                c + centerX * k, 0f, cell.left * (1f - c - centerX * k),
                centerY * k, 1f, -centerY * cell.left * k,
                k, 0f, 1f - cell.left * k,
            )
        }
    }

    /** Where `(x, y)` lands under [homography], with the perspective divide. */
    fun map(homography: FloatArray, x: Float, y: Float): Pair<Float, Float> {
        val w = homography[6] * x + homography[7] * y + homography[8]
        if (abs(w) < 1e-6f) return x to y
        return (homography[0] * x + homography[1] * y + homography[2]) / w to
            (homography[3] * x + homography[4] * y + homography[5]) / w
    }

    /**
     * How dark the paper is once it has turned this far: it tips away from the
     * light, so it goes to shadow as it goes over. 0 when flat.
     */
    fun shadeStrength(turned: Float): Float {
        val radians = turned.coerceIn(0f, 1f) * MAX_ANGLE_DEG * (PI.toFloat() / 180f)
        return 0.62f * sin(radians)
    }
}
