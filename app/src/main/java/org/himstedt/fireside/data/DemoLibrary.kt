package org.himstedt.fireside.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import org.himstedt.fireside.slideshow.Rng

/**
 * Locally generated artwork so the app (and the theme previews) can be enjoyed
 * without an Immich server. Each image is a stylised low-poly landscape, so the
 * slideshow effects have real edges and depth to work with.
 */
object DemoLibrary {

    const val ALBUM_ID = "demo-album"

    val photos: List<Photo> = listOf(
        Photo("demo-0", "aurora.jpg", "2024-01-14T18:20:00.000Z", 1600, 900, "Tromsø"),
        Photo("demo-1", "ridge.jpg", "2024-02-02T08:05:00.000Z", 1200, 1500, "Chamonix"),
        Photo("demo-2", "dunes.jpg", "2024-03-09T16:40:00.000Z", 1500, 1000, "Merzouga"),
        Photo("demo-3", "harbour.jpg", "2024-03-28T19:12:00.000Z", 1080, 1350, "Bergen"),
        Photo("demo-4", "pines.jpg", "2024-04-17T07:55:00.000Z", 1600, 1600, "Bavaria"),
        Photo("demo-5", "saltflat.jpg", "2024-05-05T12:30:00.000Z", 1800, 1200, "Uyuni"),
        Photo("demo-6", "glacier.jpg", "2024-06-21T10:15:00.000Z", 1000, 1500, "Jostedal"),
        Photo("demo-7", "canyon.jpg", "2024-07-08T17:48:00.000Z", 1500, 1000, "Sedona"),
        Photo("demo-8", "islands.jpg", "2024-08-19T06:25:00.000Z", 1600, 900, "Lofoten"),
        Photo("demo-9", "storm.jpg", "2024-09-11T15:02:00.000Z", 1200, 1600, "Skye"),
        Photo("demo-10", "lagoon.jpg", "2024-10-03T20:10:00.000Z", 1800, 771, "Bora Bora"),
        Photo("demo-11", "tundra.jpg", "2024-11-22T13:37:00.000Z", 1500, 1200, "Abisko"),
        Photo("demo-12", "cliffs.jpg", "2024-12-06T09:44:00.000Z", 1200, 1500, "Etretat"),
        Photo("demo-13", "midnight.jpg", "2024-12-31T23:05:00.000Z", 1600, 900, "Reykjavík"),
    )

    val album: Album = Album(
        id = ALBUM_ID,
        name = "Demo · Northern Light",
        description = "Fourteen locally generated landscapes, no server required.",
        assetCount = photos.size,
        coverAssetId = photos.first().id,
        startDate = photos.first().takenAt,
        endDate = photos.last().takenAt,
    )

    fun indexOf(photoId: String): Int =
        photos.indexOfFirst { it.id == photoId }.coerceAtLeast(0)

    fun bitmap(photoId: String, width: Int, height: Int): ImageBitmap =
        render(indexOf(photoId), width, height).asImageBitmap()

    fun bitmap(index: Int, width: Int, height: Int): ImageBitmap =
        render(index, width, height).asImageBitmap()

    // ---------------------------------------------------------------- painting

    private data class Palette(
        val skyTop: Int,
        val skyBottom: Int,
        val glow: Int,
        val far: Int,
        val mid: Int,
        val near: Int,
        val water: Int,
        val night: Boolean = false,
    )

    private val palettes = listOf(
        // Dawn over snow
        Palette(0xFF152A4A.toInt(), 0xFFF6B27A.toInt(), 0xFFFFE6BE.toInt(), 0xFF4A6180.toInt(), 0xFF2E4059.toInt(), 0xFF16233A.toInt(), 0xFF0C1626.toInt()),
        // Golden dunes
        Palette(0xFF2A1B3D.toInt(), 0xFFE8874A.toInt(), 0xFFFFD9A0.toInt(), 0xFF8A4E32.toInt(), 0xFF5E3423.toInt(), 0xFF31190F.toInt(), 0xFF1A0C08.toInt()),
        // Blue hour
        Palette(0xFF0B1A33.toInt(), 0xFF3E6B9E.toInt(), 0xFFBFE3FF.toInt(), 0xFF2C4A70.toInt(), 0xFF1A2E47.toInt(), 0xFF0D1A28.toInt(), 0xFF060D16.toInt()),
        // Ember canyon
        Palette(0xFF30121B.toInt(), 0xFFC4562F.toInt(), 0xFFFFB07A.toInt(), 0xFF7A2A22.toInt(), 0xFF4E1A16.toInt(), 0xFF2A0D0B.toInt(), 0xFF140605.toInt()),
        // Forest mist
        Palette(0xFF1E2B22.toInt(), 0xFF9BB98B.toInt(), 0xFFE7F2D8.toInt(), 0xFF5C7A5E.toInt(), 0xFF3A4F3C.toInt(), 0xFF1F2E22.toInt(), 0xFF101A12.toInt()),
        // Aurora night
        Palette(0xFF04070F.toInt(), 0xFF0F2B3D.toInt(), 0xFF7FE3C0.toInt(), 0xFF123047.toInt(), 0xFF0A1C2A.toInt(), 0xFF050D14.toInt(), 0xFF020509.toInt(), night = true),
        // Rose salt flat
        Palette(0xFF3A2233.toInt(), 0xFFE3A0A8.toInt(), 0xFFFFE3E0.toInt(), 0xFF8C5566.toInt(), 0xFF5E3543.toInt(), 0xFF341C24.toInt(), 0xFF1C0E13.toInt()),
        // Steel storm
        Palette(0xFF20242B.toInt(), 0xFF7C8898.toInt(), 0xFFDCE6F0.toInt(), 0xFF4E5866.toInt(), 0xFF323A45.toInt(), 0xFF1B2027.toInt(), 0xFF0C0F13.toInt()),
        // Alpine teal
        Palette(0xFF0E2B33.toInt(), 0xFF8FD6D0.toInt(), 0xFFEAFBF7.toInt(), 0xFF3F7C7C.toInt(), 0xFF2A5A5C.toInt(), 0xFF153A3C.toInt(), 0xFF0A2325.toInt()),
        // Lavender dusk
        Palette(0xFF2A2140.toInt(), 0xFFB79AD6.toInt(), 0xFFF1E4FF.toInt(), 0xFF6B5A8A.toInt(), 0xFF4A3D63.toInt(), 0xFF2A2238.toInt(), 0xFF150F1E.toInt()),
        // Mint morning
        Palette(0xFF1B3A2A.toInt(), 0xFF9FD8A6.toInt(), 0xFFEFFBE8.toInt(), 0xFF54845F.toInt(), 0xFF3A6146.toInt(), 0xFF22412E.toInt(), 0xFF12251A.toInt()),
        // Plum storm
        Palette(0xFF1D1622.toInt(), 0xFF8B6A7A.toInt(), 0xFFE7D3DA.toInt(), 0xFF5A4351.toInt(), 0xFF3E2E39.toInt(), 0xFF261C24.toInt(), 0xFF130E12.toInt()),
        // Sandstone noon
        Palette(0xFF2E3E52.toInt(), 0xFFD9C08A.toInt(), 0xFFFFF3D2.toInt(), 0xFF8C7A57.toInt(), 0xFF63533B.toInt(), 0xFF3B3022.toInt(), 0xFF1F1810.toInt()),
        // Copper night
        Palette(0xFF070A12.toInt(), 0xFF3A2118.toInt(), 0xFFFFB27A.toInt(), 0xFF2A1A16.toInt(), 0xFF1A100D.toInt(), 0xFF0D0806.toInt(), 0xFF050303.toInt(), night = true),
    )

    private fun render(index: Int, width: Int, height: Int): Bitmap {
        val w = width.coerceAtLeast(16).toFloat()
        val h = height.coerceAtLeast(16).toFloat()
        val seed = index * 7919L + 17L
        val p = palettes[index.mod(palettes.size)]
        val bmp = Bitmap.createBitmap(w.toInt(), h.toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)

        val horizon = h * Rng.range(seed, 10, 0.54f, 0.72f)
        val overcast = Rng.unit(seed, 11) < 0.16f
        val sunX = w * Rng.range(seed, 1, 0.14f, 0.86f)
        val sunY = h * Rng.range(seed, 2, 0.14f, 0.40f)
        val sunR = h * Rng.range(seed, 3, 0.03f, 0.085f)

        // Sky
        paint.shader = LinearGradient(0f, 0f, 0f, horizon, p.skyTop, p.skyBottom, Shader.TileMode.CLAMP)
        // Paint the full height: the water rect only starts a little below the
        // horizon, and any unpainted strip would show up as bare black canvas.
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null

        // Stars
        if (p.night) {
            paint.color = Color.WHITE
            repeat(90) { i ->
                val x = Rng.unit(seed, 900 + i) * w
                val y = Rng.unit(seed, 1_000 + i) * horizon * 0.85f
                val radius = 0.6f + Rng.unit(seed, 1_100 + i) * 1.6f
                paint.alpha = (60 + (Rng.unit(seed, 1_200 + i) * 170)).toInt()
                canvas.drawCircle(x, y, radius, paint)
            }
            paint.alpha = 255
        }

        if (overcast) {
            // No sun disc: a wide bright band just above the horizon instead.
            paint.shader = LinearGradient(
                0f, horizon - h * 0.30f, 0f, horizon,
                intArrayOf(withAlpha(p.glow, 0), withAlpha(p.glow, 60), withAlpha(p.glow, 120)),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP,
            )
            canvas.drawRect(0f, horizon - h * 0.30f, w, horizon, paint)
            paint.shader = null
        } else {
            paint.shader = RadialGradient(
                sunX, sunY, sunR * 7f,
                intArrayOf(withAlpha(p.glow, 130), withAlpha(p.glow, 40), withAlpha(p.glow, 0)),
                floatArrayOf(0f, 0.35f, 1f),
                Shader.TileMode.CLAMP,
            )
            canvas.drawCircle(sunX, sunY, sunR * 7f, paint)
            paint.shader = null
            paint.color = p.glow
            canvas.drawCircle(sunX, sunY, sunR, paint)
        }

        // Clouds: soft horizontal strokes
        repeat(if (overcast) 10 else 5) { i ->
            val cy = h * Rng.range(seed, 200 + i, 0.10f, 0.5f)
            val cw = w * Rng.range(seed, 300 + i, 0.18f, 0.55f)
            val cx = Rng.range(seed, 400 + i, -0.1f, 0.9f) * w
            val alpha = if (overcast) (26 + Rng.unit(seed, 500 + i) * 34).toInt()
            else (18 + Rng.unit(seed, 500 + i) * 30).toInt()
            paint.color = withAlpha(p.glow, alpha)
            canvas.drawOval(cx, cy, cx + cw, cy + h * 0.03f, paint)
        }

        // A small flock, on some of the brighter scenes
        if (!p.night && !overcast && Rng.unit(seed, 12) > 0.5f) {
            val flockX = w * Rng.range(seed, 13, 0.1f, 0.8f)
            val flockY = h * Rng.range(seed, 14, 0.1f, 0.3f)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = Math.max(1f, h * 0.002f)
            paint.color = withAlpha(p.near, 110)
            repeat(5) { i ->
                val bx = flockX + w * 0.020f * i + Rng.range(seed, 1_400 + i, -5f, 5f)
                val by = flockY + h * Rng.range(seed, 1_500 + i, -0.02f, 0.02f)
                val s = w * 0.005f * Rng.range(seed, 1_600 + i, 0.7f, 1.2f)
                canvas.drawLine(bx - s, by, bx, by - s * 0.7f, paint)
                canvas.drawLine(bx, by - s * 0.7f, bx + s, by, paint)
            }
            paint.style = Paint.Style.FILL
        }

        // Landforms: three silhouettes, far to near, in one of three styles.
        when (index.mod(3)) {
            0 -> {
                ridge(canvas, seed + 1, w, h, horizon, h * 0.16f, p.far, 11)
                ridge(canvas, seed + 2, w, h, horizon + h * 0.04f, h * 0.13f, p.mid, 9)
                ridge(canvas, seed + 3, w, h, horizon + h * 0.07f, h * 0.10f, p.near, 7)
            }

            1 -> {
                hills(canvas, seed + 1, w, h, horizon, h * 0.14f, p.far, 4)
                hills(canvas, seed + 2, w, h, horizon + h * 0.04f, h * 0.11f, p.mid, 3)
                hills(canvas, seed + 3, w, h, horizon + h * 0.07f, h * 0.08f, p.near, 2)
            }

            else -> {
                islands(canvas, seed + 1, w, h, horizon, p.far, 6, 0.05f, 0.15f)
                islands(canvas, seed + 2, w, h, horizon + h * 0.03f, p.mid, 4, 0.07f, 0.19f)
            }
        }

        // Water
        val waterTop = horizon + h * 0.015f
        paint.shader = LinearGradient(
            0f, waterTop, 0f, h,
            intArrayOf(
                withAlpha(blend(p.water, p.glow, 0.30f), 235),
                withAlpha(blend(p.water, p.glow, 0.10f), 240),
                p.water,
            ),
            floatArrayOf(0f, 0.35f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, waterTop, w, h, paint)
        paint.shader = null

        // Reflection under the sun: soft slices that widen toward the viewer so
        // the water never reads as a hard-edged rectangle.
        if (!overcast) {
            val streakW = sunR * Rng.range(seed, 4, 1.4f, 2.6f)
            val slices = 56
            for (i in 0 until slices) {
                val t = i / slices.toFloat()
                val y0 = waterTop + (h - waterTop) * t
                val y1 = waterTop + (h - waterTop) * ((i + 1f) / slices)
                val half = streakW * (0.3f + 1.15f * t) * Rng.range(seed, 2_500 + i, 0.94f, 1.06f)
                val alpha = (86 * (1f - t) * (1f - t) + 5).toInt()
                paint.shader = LinearGradient(
                    sunX - half, 0f, sunX + half, 0f,
                    intArrayOf(withAlpha(p.glow, 0), withAlpha(p.glow, alpha), withAlpha(p.glow, 0)),
                    floatArrayOf(0f, 0.5f, 1f),
                    Shader.TileMode.CLAMP,
                )
                canvas.drawRect(sunX - half, y0, sunX + half, y1 + 1f, paint)
            }
            paint.shader = null
        }

        // Ripple lines, faint across the whole surface
        paint.strokeWidth = Math.max(1f, h * 0.0022f)
        repeat(14) { i ->
            val y = waterTop + (h - waterTop) * (0.04f + 0.066f * i) + Rng.range(seed, 600 + i, -3f, 3f)
            val x0 = w * Rng.range(seed, 700 + i, -0.05f, 0.35f)
            val x1 = w * Rng.range(seed, 800 + i, 0.65f, 1.05f)
            paint.color = withAlpha(p.glow, (26 - i).coerceAtLeast(5))
            canvas.drawLine(x0, y, x1, y, paint)
        }

        // Foreground silhouettes give each scene its own frame.
        when (index.mod(4)) {
            0 -> pines(canvas, seed + 5, w, h, p.near, 7)
            1 -> rocks(canvas, seed + 6, w, h, p.near)
            2 -> reeds(canvas, seed + 7, w, h, p.near)
            else -> Unit
        }

        // Film grain, so flat gradients do not band on a TV panel
        paint.style = Paint.Style.FILL
        repeat(700) { i ->
            val x = Rng.unit(seed, 3_000 + i) * w
            val y = Rng.unit(seed, 4_000 + i) * h
            paint.color = if (Rng.unit(seed, 5_000 + i) > 0.5f) 0x0DFFFFFF else 0x0D000000
            canvas.drawCircle(x, y, 1.1f, paint)
        }

        // Vignette
        paint.shader = RadialGradient(
            w * 0.5f, h * 0.5f, Math.max(w, h) * 0.78f,
            intArrayOf(0x00000000, 0x00000000, 0x66000000),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null

        return bmp
    }

    private fun ridge(canvas: Canvas, seed: Long, w: Float, h: Float, baseY: Float, amplitude: Float, color: Int, steps: Int) {
        val path = Path()
        path.moveTo(-w * 0.03f, h)
        path.lineTo(-w * 0.03f, baseY)
        for (i in 0..steps) {
            val x = w * (i.toFloat() / steps) - w * 0.03f
            val peak = if (i % 2 == 0) Rng.range(seed, i, 0.55f, 1f) else Rng.range(seed, i, 0.05f, 0.4f)
            path.lineTo(x, baseY - amplitude * peak)
        }
        path.lineTo(w * 1.03f, baseY)
        path.lineTo(w * 1.03f, h)
        path.close()
        canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
    }

    /** Soft rolling hills, for the gentler palettes. */
    private fun hills(canvas: Canvas, seed: Long, w: Float, h: Float, baseY: Float, amplitude: Float, color: Int, steps: Int) {
        val path = Path()
        val startX = -w * 0.05f
        val span = w * 1.1f
        val dx = span / steps
        path.moveTo(startX, h)
        path.lineTo(startX, baseY - amplitude * Rng.range(seed, 0, 0.2f, 0.6f))
        var x = startX
        var y = baseY
        repeat(steps) { i ->
            val nx = x + dx
            val ny = baseY - amplitude * Rng.range(seed, i + 1, 0.25f, 1f)
            path.cubicTo(x + dx * 0.45f, y - amplitude * 0.2f, nx - dx * 0.45f, ny - amplitude * 0.1f, nx, ny)
            x = nx
            y = ny
        }
        path.lineTo(w * 1.05f, h)
        path.close()
        canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
    }

    /** Island silhouettes sitting in the water, with their bases submerged. */
    private fun islands(
        canvas: Canvas,
        seed: Long,
        w: Float,
        h: Float,
        horizon: Float,
        color: Int,
        count: Int,
        minHeight: Float,
        maxHeight: Float,
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        repeat(count) { i ->
            val cx = w * Rng.range(seed, i * 3, -0.02f, 1.02f)
            val half = w * Rng.range(seed, i * 3 + 1, 0.05f, 0.16f)
            val peak = h * Rng.range(seed, i * 3 + 2, minHeight, maxHeight)
            val base = horizon + h * 0.09f
            val path = Path()
            path.moveTo(cx - half, base)
            path.quadTo(cx - half * 0.5f, horizon - peak, cx - half * 0.05f, horizon - peak * 0.85f)
            path.quadTo(cx + half * 0.45f, horizon - peak * 0.35f, cx + half, base)
            path.close()
            canvas.drawPath(path, paint)
        }
    }

    /** Pine silhouettes along the bottom edge. */
    private fun pines(canvas: Canvas, seed: Long, w: Float, h: Float, color: Int, count: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        repeat(count) { i ->
            val cx = w * ((i + Rng.range(seed, i, 0.1f, 0.9f)) / count)
            val tall = h * Rng.range(seed, 100 + i, 0.22f, 0.40f)
            val half = w * Rng.range(seed, 200 + i, 0.022f, 0.042f)
            val path = Path()
            for (tier in 0..2) {
                val top = h - tall + tall * 0.26f * tier
                val bottom = h - tall * 0.42f + tall * 0.30f * tier
                val spread = half * (0.9f + 0.5f * tier)
                path.moveTo(cx, top)
                path.lineTo(cx + spread, bottom)
                path.lineTo(cx - spread, bottom)
                path.close()
            }
            path.addRect(cx - half * 0.18f, h - tall * 0.5f, cx + half * 0.18f, h, Path.Direction.CW)
            canvas.drawPath(path, paint)
        }
    }

    /** Boulders in the near foreground, cropped by the frame. */
    private fun rocks(canvas: Canvas, seed: Long, w: Float, h: Float, color: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        val count = 4
        repeat(count) { i ->
            val cx = w * Rng.range(seed, i * 2, -0.02f, 1.02f)
            val rw = w * Rng.range(seed, i * 2 + 1, 0.10f, 0.26f)
            val rh = h * Rng.range(seed, 300 + i, 0.05f, 0.11f)
            canvas.drawOval(cx - rw, h - rh * 2.1f, cx + rw, h + rh, paint)
        }
    }

    /** Reeds and grasses, thin enough to read as texture. */
    private fun reeds(canvas: Canvas, seed: Long, w: Float, h: Float, color: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = Math.max(1.5f, h * 0.004f)
        }
        repeat(26) { i ->
            val bx = w * Rng.range(seed, 500 + i, 0.02f, 0.98f)
            val tall = h * Rng.range(seed, 600 + i, 0.08f, 0.20f)
            val lean = w * Rng.range(seed, 700 + i, -0.02f, 0.02f)
            val path = Path()
            path.moveTo(bx, h + 2f)
            path.quadTo(bx + lean * 0.4f, h - tall * 0.6f, bx + lean, h - tall)
            canvas.drawPath(path, paint)
        }
    }

    /** Linear blend of two ARGB colours, [t] = 0 keeps [a]. */
    private fun blend(a: Int, b: Int, t: Float): Int {
        val f = t.coerceIn(0f, 1f)
        val ar = (a shr 16) and 0xFF
        val ag = (a shr 8) and 0xFF
        val ab = a and 0xFF
        val br = (b shr 16) and 0xFF
        val bg = (b shr 8) and 0xFF
        val bb = b and 0xFF
        val r = (ar + (br - ar) * f).toInt().coerceIn(0, 255)
        val g = (ag + (bg - ag) * f).toInt().coerceIn(0, 255)
        val bl = (ab + (bb - ab) * f).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        (color and 0x00FFFFFF) or ((alpha.coerceIn(0, 255)) shl 24)
}
