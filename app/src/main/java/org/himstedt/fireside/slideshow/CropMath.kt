package org.himstedt.fireside.slideshow

import kotlin.math.max
import kotlin.math.min

/**
 * The source rectangle a crop takes from a photo.
 *
 * Kept apart from the drawing so the one property that matters can be tested: a
 * crop is **centred** on the photo unless a focus is asked for. Cropping to fill a
 * panel is unavoidable — a portrait in a landscape slot has to lose something —
 * but it must lose the same amount from both sides, or the subject drifts out of
 * the panel and a lion becomes a strip of ground.
 */
object CropMath {

    /**
     * Source rect for filling a slot of [dstAspect] from a [imgW] × [imgH] photo.
     *
     * [focusX] and [focusY] are 0..1 across the photo, 0.5 being its middle. The
     * rect is the largest one of the slot's shape that fits, divided by [zoom],
     * and it is placed *symmetrically* about the focus point — clamped only at the
     * photo's edges, which is where the photo runs out rather than a choice.
     */
    fun sourceRect(
        imgW: Float,
        imgH: Float,
        dstAspect: Float,
        zoom: Float = 1f,
        focusX: Float = 0.5f,
        focusY: Float = 0.5f,
    ): Rect4 {
        if (imgW <= 0f || imgH <= 0f || dstAspect <= 0f) return Rect4(0f, 0f, 0f, 0f)
        val z = max(1f, zoom)
        val imgAspect = imgW / imgH
        var srcW: Float
        var srcH: Float
        if (imgAspect > dstAspect) {
            srcH = imgH / z
            srcW = srcH * dstAspect
        } else {
            srcW = imgW / z
            srcH = srcW / dstAspect
        }
        srcW = min(srcW, imgW)
        srcH = min(srcH, imgH)

        val cx = focusX.coerceIn(0f, 1f) * imgW
        val cy = focusY.coerceIn(0f, 1f) * imgH
        val left = (cx - srcW / 2f).coerceIn(0f, max(0f, imgW - srcW))
        val top = (cy - srcH / 2f).coerceIn(0f, max(0f, imgH - srcH))
        return Rect4(left, top, srcW, srcH)
    }

    /** A source rectangle: where it starts and how much it takes. */
    data class Rect4(val left: Float, val top: Float, val width: Float, val height: Float) {
        val right: Float get() = left + width
        val bottom: Float get() = top + height
        val centerX: Float get() = left + width / 2f
        val centerY: Float get() = top + height / 2f
    }
}
