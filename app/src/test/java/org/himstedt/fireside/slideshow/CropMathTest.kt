package org.himstedt.fireside.slideshow

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The crop is centred, and that is the whole point: cropping to fill a panel is
 * unavoidable, but losing the same amount from both sides is what keeps the
 * subject inside the panel instead of drifting out of it.
 */
class CropMathTest {

    @Test
    fun `a crop is centred on the photo`() {
        // Portrait photo, landscape slot: the crop takes a band from the middle.
        val crop = CropMath.sourceRect(imgW = 900f, imgH = 1200f, dstAspect = 2f)
        assertEquals("the band should be centred across", 450f, crop.centerX, 0.5f)
        assertEquals("the band should be centred down", 600f, crop.centerY, 0.5f)
        assertEquals("the crop should be as wide as the photo", 900f, crop.width, 0.5f)
        assertEquals("the crop should be half as tall", 450f, crop.height, 0.5f)
    }

    @Test
    fun `a landscape photo in a portrait slot is cropped from both sides`() {
        val crop = CropMath.sourceRect(imgW = 1600f, imgH = 900f, dstAspect = 0.6f)
        assertEquals(800f, crop.centerX, 0.5f)
        assertEquals(450f, crop.centerY, 0.5f)
        assertEquals(900f, crop.height, 0.5f)
        assertEquals(540f, crop.width, 0.5f)
        // Equal amounts lost each side.
        assertEquals("uneven crop", crop.left, 1600f - crop.right, 0.5f)
    }

    @Test
    fun `cropping never leaves the photo`() {
        for ((iw, ih) in listOf(900f to 1200f, 1600f to 900f, 1000f to 1000f, 400f to 1600f)) {
            for (dst in listOf(0.4f, 0.66f, 1f, 1.5f, 2.5f)) {
                for (zoom in listOf(1f, 1.4f, 2.5f)) {
                    val crop = CropMath.sourceRect(iw, ih, dst, zoom)
                    assertTrue("crop starts before the photo", crop.left >= -0.001f)
                    assertTrue("crop starts after the photo", crop.top >= -0.001f)
                    assertTrue("crop runs past the photo", crop.right <= iw + 0.001f)
                    assertTrue("crop runs past the photo", crop.bottom <= ih + 0.001f)
                    assertTrue("crop is empty", crop.width > 0f && crop.height > 0f)
                    // And it always has the slot's shape, or the photo would be
                    // stretched when it is drawn into the slot.
                    assertEquals("wrong crop shape", dst, crop.width / crop.height, 0.01f)
                }
            }
        }
    }

    @Test
    fun `zoom takes a smaller band from the middle`() {
        val plain = CropMath.sourceRect(1000f, 1000f, 1f)
        val zoomed = CropMath.sourceRect(1000f, 1000f, 1f, zoom = 2f)
        assertEquals("the zoom should halve the band", plain.width / 2f, zoomed.width, 0.5f)
        assertEquals("the zoom should stay centred", plain.centerX, zoomed.centerX, 0.5f)
        assertEquals("the zoom should stay centred", plain.centerY, zoomed.centerY, 0.5f)
    }

    @Test
    fun `a focus point moves the band, and the edges clamp it`() {
        // A focus near an edge takes what it can and stops at the photo, rather
        // than running past it and drawing blank space.
        val left = CropMath.sourceRect(1000f, 1000f, 1f, focusX = 0f)
        assertEquals("the band should sit at the edge", 0f, left.left, 0.5f)
        val right = CropMath.sourceRect(1000f, 1000f, 1f, focusX = 1f)
        assertEquals("the band should end at the edge", 1000f, right.right, 0.5f)
        // Out-of-range focus is clamped, not extrapolated.
        val past = CropMath.sourceRect(1000f, 1000f, 1f, focusX = 5f)
        assertEquals(right.left, past.left, 0.5f)
    }

    @Test
    fun `a square photo in a square slot is not cropped at all`() {
        val crop = CropMath.sourceRect(800f, 800f, 1f)
        assertEquals(0f, crop.left, 0.001f)
        assertEquals(0f, crop.top, 0.001f)
        assertEquals(800f, crop.width, 0.001f)
        assertEquals(800f, crop.height, 0.001f)
    }

    @Test
    fun `a degenerate photo does not divide by zero`() {
        val crop = CropMath.sourceRect(0f, 0f, 1.5f)
        assertEquals(0f, crop.width, 0f)
        assertEquals(0f, crop.height, 0f)
        val noSlot = CropMath.sourceRect(800f, 600f, 0f)
        assertEquals(0f, noSlot.width, 0f)
    }

    @Test
    fun `every slot in a justified wall is cropped from the middle`() {
        // The wall's cells have mixed shapes, so this is where an off-centre crop
        // would actually be visible: a portrait photo in a wide cell losing its top
        // or its bottom instead of both.
        val cells = listOf(
            GridRect(0f, 0f, 640f, 360f),
            GridRect(0f, 360f, 320f, 720f),
            GridRect(320f, 360f, 960f, 720f),
        )
        val aspects = listOf(0.75f, 1.5f, 0.6f)
        for ((i, cell) in cells.withIndex()) {
            val imgW = 900f
            val imgH = imgW / aspects[i]
            val crop = CropMath.sourceRect(imgW, imgH, cell.width / cell.height)
            assertEquals("cell $i crop is off-centre across", imgW / 2f, crop.centerX, 0.5f)
            assertEquals("cell $i crop is off-centre down", imgH / 2f, crop.centerY, 0.5f)
            assertTrue("cell $i crop is off-centre", abs(crop.left - (imgW - crop.right)) < 0.5f)
        }
    }
}
