package org.himstedt.immichslideshow.slideshow

/** A 2D vector, kept free of Android types so motion maths stays unit testable. */
data class Vec2(val x: Float, val y: Float) {
    operator fun plus(other: Vec2) = Vec2(x + other.x, y + other.y)
    operator fun times(factor: Float) = Vec2(x * factor, y * factor)
    companion object {
        val Zero = Vec2(0f, 0f)
    }
}

/**
 * Where the camera is looking at a photo: [zoom] = 1 fills the frame, larger
 * values crop in. [focusX]/[focusY] are normalised image coordinates
 * (0 = left/top edge, 1 = right/bottom edge).
 */
data class Viewport(val zoom: Float, val focusX: Float, val focusY: Float) {
    companion object {
        val Center = Viewport(1f, 0.5f, 0.5f)
    }
}

/** Clamps a viewport so its crop window never leaves the source image. */
fun Viewport.clamped(): Viewport {
    val z = zoom.coerceAtLeast(1f)
    val half = 0.5f / z
    return Viewport(
        zoom = z,
        focusX = focusX.coerceIn(half, 1f - half),
        focusY = focusY.coerceIn(half, 1f - half),
    )
}

/**
 * Classic Ken Burns move: a slow push in while drifting toward one of the
 * image's thirds. Deterministic per [seed], so a photo always performs the same
 * move.
 */
fun kenBurnsViewport(seed: Long, t: Float, intensity: Float = 1f): Viewport {
    val amount = intensity.coerceIn(0f, 2f)
    val zoomStart = 1f + (0.02f + 0.05f * Rng.unit(seed, 11)) * amount
    val zoomEnd = zoomStart + (0.09f + 0.11f * Rng.unit(seed, 12)) * amount

    // Pick start/end anchors from the nine "rule of thirds" intersections.
    val anchors = listOf(
        0.32f to 0.32f, 0.5f to 0.32f, 0.68f to 0.32f,
        0.32f to 0.5f, 0.5f to 0.5f, 0.68f to 0.5f,
        0.32f to 0.68f, 0.5f to 0.68f, 0.68f to 0.68f,
    )
    val start = anchors[Rng.int(seed, 13, anchors.size)]
    var end = anchors[Rng.int(seed, 14, anchors.size)]
    if (end == start) {
        end = anchors[(anchors.indexOf(start) + 4) % anchors.size]
    }

    val e = Easing.easeInOutSine(t)
    return Viewport(
        zoom = Easing.lerp(zoomStart, zoomEnd, e),
        focusX = Easing.lerp(start.first, end.first, e),
        focusY = Easing.lerp(start.second, end.second, e),
    ).clamped()
}

/** Gentle push-in used by the calmer themes. */
fun pushInViewport(seed: Long, t: Float, intensity: Float = 1f): Viewport {
    val amount = intensity.coerceIn(0f, 2f)
    val z0 = 1.0f + 0.01f * Rng.unit(seed, 21) * amount
    val z1 = z0 + 0.05f * amount
    val fx = Easing.lerp(0.5f, 0.5f + 0.05f * Rng.sign(seed, 22), Easing.easeInOutSine(t))
    val fy = Easing.lerp(0.5f, 0.5f + 0.04f * Rng.sign(seed, 23), Easing.easeInOutSine(t))
    return Viewport(Easing.lerp(z0, z1, Easing.easeInOutSine(t)), fx, fy).clamped()
}

/**
 * Staggered local progress for one of [count] items: each item runs its own
 * [spread] slice of the transition, offset evenly across the remaining time.
 */
fun stagger(p: Float, index: Int, count: Int, spread: Float = 0.55f): Float {
    if (count <= 1) return p.coerceIn(0f, 1f)
    val s = spread.coerceIn(0.05f, 1f)
    val start = (1f - s) * index.toFloat() / (count - 1).toFloat()
    return ((p - start) / s).coerceIn(0f, 1f)
}

/** Direction a tile flies off toward, derived from the photo id. */
fun tileExitDirection(seed: Long, index: Int, cols: Int): Vec2 {
    val column = (index % cols).toFloat() / cols.coerceAtLeast(1)
    val base = if (column < 0.5f) -1f else 1f
    val jitter = Rng.range(seed, 100 + index, -0.25f, 0.25f)
    val vertical = Rng.range(seed, 200 + index, -0.55f, 0.55f)
    return Vec2(base + jitter, vertical)
}

/** Deterministic per-photo rotation in degrees, e.g. for scrapbook polaroids. */
fun photoRotation(seed: Long, index: Int, maxDegrees: Float = 6f): Float =
    Rng.range(seed, 300 + index, -maxDegrees, maxDegrees)

/** Deterministic unit offset inside the frame, e.g. scrapbook pile placement. */
fun photoOffset(seed: Long, index: Int, amount: Float = 0.06f): Vec2 =
    Vec2(Rng.range(seed, 400 + index, -amount, amount), Rng.range(seed, 500 + index, -amount, amount))
