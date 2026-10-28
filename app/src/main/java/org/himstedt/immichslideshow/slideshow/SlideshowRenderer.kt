package org.himstedt.immichslideshow.slideshow

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Paints one slideshow frame with plain [DrawScope] calls.
 *
 * Everything is derived from [SlideshowFrame] only, which keeps the renderer
 * stateless and lets the theme picker reuse it for animated thumbnails.
 */
object SlideshowRenderer {

    fun DrawScope.renderSlideshow(frame: SlideshowFrame, textMeasurer: TextMeasurer? = null) {
        drawRect(Color.Black)
        if (!frame.hasPhotos) {
            drawEmptyState()
            return
        }
        when (frame.effect) {
            SlideshowEffect.CLASSIC -> drawClassic(frame)
            SlideshowEffect.KEN_BURNS -> drawKenBurns(frame)
            SlideshowEffect.SLIDE -> drawSlide(frame)
            SlideshowEffect.FLIP -> drawFlip(frame)
            SlideshowEffect.FOLD -> drawFold(frame)
            SlideshowEffect.SHIFTING_TILES -> drawShiftingTiles(frame)
            SlideshowEffect.REFLECTIONS -> drawReflections(frame, textMeasurer)
            SlideshowEffect.SCRAPBOOK -> drawScrapbook(frame, textMeasurer)
            SlideshowEffect.ORIGAMI -> drawOrigami(frame)
            SlideshowEffect.PHOTO_GRID -> drawPhotoGrid(frame)
        }
        // A vignette darkens the corners: wrong on the two themes with a light
        // stage, where it reads as grime rather than as depth.
        val lightStage = frame.effect == SlideshowEffect.SCRAPBOOK ||
            frame.effect == SlideshowEffect.REFLECTIONS
        if (frame.prefs.vignette && !lightStage) drawVignette(0.5f)
        // The two walls fill the frame on purpose; a caption would sit on a photo.
        if (frame.effect != SlideshowEffect.SCRAPBOOK &&
            frame.effect != SlideshowEffect.PHOTO_GRID &&
            frame.effect != SlideshowEffect.ORIGAMI
        ) {
            drawCaption(
                frame = frame,
                textMeasurer = textMeasurer,
                alpha = captionAlpha(frame),
                dark = frame.effect == SlideshowEffect.REFLECTIONS,
                fontScale = if (frame.effect == SlideshowEffect.REFLECTIONS) 0.036f else 0.052f,
            )
        }
    }

    /** Captions fade in when a slide settles, then get out of the way. */
    private fun captionAlpha(frame: SlideshowFrame): Float {
        if (!frame.prefs.showCaption || frame.transition > 0f) return 0f
        val hold = frame.hold
        // Stay up for most of the hold and clear out just before the cut, so a
        // caption is not missing for half of every slide.
        return when {
            hold < 0.06f -> hold / 0.06f
            hold < 0.80f -> 1f
            hold < 0.94f -> 1f - (hold - 0.80f) / 0.14f
            else -> 0f
        }.coerceIn(0f, 1f)
    }

    /**
     * The date and place line. [dark] is for the light stage, which needs dark
     * type and no black scrim under it.
     */
    private fun DrawScope.drawCaption(
        frame: SlideshowFrame,
        textMeasurer: TextMeasurer?,
        alpha: Float,
        dark: Boolean = false,
        fontScale: Float = 0.052f,
    ) {
        if (alpha <= 0.01f || textMeasurer == null) return
        val text = frame.currentPhoto?.caption?.takeIf { it.isNotBlank() } ?: return

        if (!dark) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f * alpha)),
                    startY = size.height * 0.72f,
                    endY = size.height,
                ),
            )
        }

        // Sized from the canvas so the same renderer reads correctly on a TV
        // and inside a small theme-preview tile.
        val captionPx = (size.height * fontScale).coerceAtLeast(10f)
        val style = TextStyle(
            color = if (dark) Color(0xFF2B2B2B).copy(alpha = alpha) else Color.White.copy(alpha = 0.94f * alpha),
            fontSize = captionPx.toSp(),
            fontWeight = FontWeight.Light,
            letterSpacing = (captionPx * 0.02f).toSp(),
            shadow = if (dark) {
                null
            } else {
                Shadow(
                    color = Color.Black.copy(alpha = 0.65f * alpha),
                    offset = Offset(0f, captionPx * 0.05f),
                    blurRadius = captionPx * 0.20f,
                )
            },
        )
        val layout = textMeasurer.measure(text = text, style = style, maxLines = 1)
        drawText(
            textLayoutResult = layout,
            topLeft = Offset(
                size.width * 0.062f,
                // At the foot of the reflection, clear of the chrome's hint row.
                size.height * (if (dark) 0.935f else 0.885f) - layout.size.height,
            ),
        )
    }

    // ------------------------------------------------------------- themes

    /** Slow cross-dissolve with a gentle push-in. */
    private fun DrawScope.drawClassic(frame: SlideshowFrame) {
        val fit = frame.prefs.fitMode.isFit
        val full = Rect(Offset.Zero, size)
        val p = Easing.easeInOutCubic(frame.transition)
        val cur = frame.bitmaps.full(frame.currentIndex)

        if (cur != null) {
            val vp = pushInViewport(frame.seed, frame.progress, frame.prefs.panIntensity)
            drawCropped(cur, full, vp.zoom * (1f + 0.04f * p), vp.focusX, vp.focusY, fit = fit)
        } else {
            drawPlaceholder()
        }

        if (frame.transition > 0f) {
            val next = frame.bitmaps.full(frame.nextIndex)
            if (next != null) {
                val nvp = pushInViewport(frame.nextSeed, 0f, frame.prefs.panIntensity)
                drawCropped(next, full, nvp.zoom * (1.06f - 0.06f * p), nvp.focusX, nvp.focusY, alpha = p, fit = fit)
            }
        }
    }

    /** Roving pan and zoom that keeps moving for the whole slide. */
    private fun DrawScope.drawKenBurns(frame: SlideshowFrame) {
        val fit = frame.prefs.fitMode.isFit
        val full = Rect(Offset.Zero, size)
        val p = Easing.easeInOutCubic(frame.transition)
        val cur = frame.bitmaps.full(frame.currentIndex)

        if (cur != null) {
            val vp = kenBurnsViewport(frame.seed, frame.progress, frame.prefs.panIntensity)
            drawCropped(cur, full, vp.zoom, vp.focusX, vp.focusY, fit = fit)
        } else {
            drawPlaceholder()
        }

        if (frame.transition > 0f) {
            val next = frame.bitmaps.full(frame.nextIndex)
            if (next != null) {
                val vp = kenBurnsViewport(frame.nextSeed, 0f, frame.prefs.panIntensity)
                drawCropped(next, full, vp.zoom * 1.04f, vp.focusX, vp.focusY, alpha = p, fit = fit)
            }
        }
    }

    /** Frames glide past each other, the incoming one casting a soft edge shadow. */
    private fun DrawScope.drawSlide(frame: SlideshowFrame) {
        val fit = frame.prefs.fitMode.isFit
        val full = Rect(Offset.Zero, size)
        val w = size.width
        val p = frame.transition
        val e = Easing.easeInOutCubic(p)
        val cur = frame.bitmaps.full(frame.currentIndex)

        if (cur != null) {
            withTransform({ translate(-w * 0.26f * e, 0f) }) {
                drawCropped(cur, full, 1f + 0.03f * e, 0.5f, 0.5f, fit = fit)
            }
        } else {
            drawPlaceholder()
        }

        if (p > 0f) {
            drawRect(Color.Black.copy(alpha = 0.5f * e))

            val next = frame.bitmaps.full(frame.nextIndex)
            val x = w * (1f - e)
            // Soft shadow travelling with the incoming frame
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent),
                    startX = x - w * 0.10f,
                    endX = x,
                ),
                topLeft = Offset(x - w * 0.10f, 0f),
                size = Size(w * 0.10f, size.height),
            )
            if (next != null) {
                withTransform({ translate(x, 0f) }) {
                    drawCropped(next, full, 1.03f - 0.03f * e, 0.5f, 0.5f, fit = fit)
                }
            }
        }
    }

    /** A card turning over on a shared vertical axis. */
    private fun DrawScope.drawFlip(frame: SlideshowFrame) {
        val fit = frame.prefs.fitMode.isFit
        val p = frame.transition
        val inset = Size(size.width * 0.055f, size.height * 0.075f)
        val card = Rect(inset.width, inset.height, size.width - inset.width, size.height - inset.height)
        val radius = CornerRadius(16.dp.toPx())
        val cardPath = Path().apply { addRoundRect(RoundRect(card, radius)) }

        // Stage glow behind the card
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF1B2130), Color(0xFF07080C)),
                center = card.center,
                radius = max(size.width, size.height) * 0.7f,
            ),
        )

        if (p <= 0.5f) {
            val k = (p / 0.5f).coerceIn(0f, 1f)
            val sx = cos(k * HALF_PI)
            if (sx > 0.01f) {
                withTransform({ scale(sx, 1f - 0.05f * k, card.center) }) {
                    clipPath(cardPath) {
                        val bmp = frame.bitmaps.full(frame.currentIndex)
                        if (bmp != null) drawCropped(bmp, card, 1f, 0.5f, 0.5f, fit = fit) else drawPlaceholder(card)
                        drawRect(Color.Black.copy(alpha = 0.5f * k), topLeft = card.topLeft, size = card.size)
                    }
                }
            }
        } else {
            val k = ((p - 0.5f) / 0.5f).coerceIn(0f, 1f)
            val sx = sin(k * HALF_PI)
            if (sx > 0.01f) {
                withTransform({ scale(sx, 1f - 0.05f * (1f - k), card.center) }) {
                    clipPath(cardPath) {
                        val bmp = frame.bitmaps.full(frame.nextIndex)
                        if (bmp != null) drawCropped(bmp, card, 1f, 0.5f, 0.5f, fit = fit) else drawPlaceholder(card)
                        drawRect(Color.Black.copy(alpha = 0.5f * (1f - k)), topLeft = card.topLeft, size = card.size)
                    }
                }
            }
        }

        // Card edge highlight
        drawRoundRect(
            color = Color.White.copy(alpha = 0.10f),
            topLeft = card.topLeft,
            size = card.size,
            cornerRadius = radius,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f),
        )
    }

    /** Vertical panels fold away like an accordion. */
    private fun DrawScope.drawFold(frame: SlideshowFrame) {
        val fit = frame.prefs.fitMode.isFit
        val full = Rect(Offset.Zero, size)
        val panels = 10
        val panelW = size.width / panels
        val cur = frame.bitmaps.full(frame.currentIndex)
        val next = frame.bitmaps.full(frame.nextIndex)

        // The incoming photo sits underneath
        val under = next ?: cur
        if (under != null) {
            val vp = pushInViewport(frame.nextSeed, 0f, frame.prefs.panIntensity)
            drawCropped(under, full, vp.zoom * 1.02f, vp.focusX, vp.focusY, fit = fit)
        } else {
            drawPlaceholder()
        }
        if (cur == null) return

        for (i in 0 until panels) {
            val local = stagger(frame.transition, i, panels, spread = 0.55f)
            val e = Easing.easeInOutCubic(local)
            val sx = 1f - e
            if (sx <= 0.01f) continue

            val left = i * panelW
            val pivotX = if (i % 2 == 0) left else left + panelW
            clipRect(left, 0f, left + panelW, size.height) {
                withTransform({ scale(sx, 1f, Offset(pivotX, 0f)) }) {
                    drawCropped(cur, full, 1f, 0.5f, 0.5f, fit = fit)
                }
            }
            // Shading that makes the fold read as a physical crease
            val visibleWidth = panelW * sx
            val shadeLeft = if (i % 2 == 0) left else left + panelW - visibleWidth
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Black.copy(alpha = 0.55f * e), Color.Black.copy(alpha = 0.15f * e)),
                    startX = shadeLeft,
                    endX = shadeLeft + visibleWidth,
                ),
                topLeft = Offset(shadeLeft, 0f),
                size = Size(visibleWidth, size.height),
            )
            drawLine(
                color = Color.White.copy(alpha = 0.10f * e),
                start = Offset(shadeLeft, 0f),
                end = Offset(shadeLeft, size.height),
                strokeWidth = 1.5f,
            )
        }
    }

    /** A grid of tiles shuffles off toward the frame edges. */
    private fun DrawScope.drawShiftingTiles(frame: SlideshowFrame) {
        val fit = frame.prefs.fitMode.isFit
        val full = Rect(Offset.Zero, size)
        val cols = 5
        val rows = 3
        val tileW = size.width / cols
        val tileH = size.height / rows
        val cur = frame.bitmaps.full(frame.currentIndex)
        val next = frame.bitmaps.full(frame.nextIndex)
        val under = next ?: cur

        if (under != null) {
            val vp = pushInViewport(frame.nextSeed, 0f, frame.prefs.panIntensity)
            drawCropped(under, full, vp.zoom * 1.03f, vp.focusX, vp.focusY, fit = fit)
        } else {
            drawPlaceholder()
        }
        if (cur == null) return

        val total = cols * rows
        for (i in 0 until total) {
            val local = stagger(frame.transition, i, total, spread = 0.6f)
            val e = Easing.easeInCubic(local)
            if (e >= 0.999f) continue

            val col = i % cols
            val row = i / cols
            val tile = Rect(col * tileW, row * tileH, (col + 1) * tileW, (row + 1) * tileH)
            val dir = tileExitDirection(frame.seed, i, cols)
            val dx = dir.x * size.width * 0.5f * e
            val dy = dir.y * size.height * 0.55f * e
            val rotation = 16f * dir.x * e

            withTransform({
                translate(dx, dy)
                rotate(rotation, tile.center)
            }) {
                clipRect(tile.left, tile.top, tile.right, tile.bottom) {
                    drawCropped(cur, full, 1f, 0.5f, 0.5f, alpha = 1f - e, fit = fit)
                }
            }
        }

        // No seam lines: every tile is cropped from the same full-frame mapping,
        // so at rest the tiles join into one continuous picture. The splits only
        // appear while a tile is actually moving.
    }

    /**
     * Photos on a bright stage, with a soft smear of themselves underneath.
     *
     * Most steps show a collage of three; every few steps a single photo takes
     * the whole stage. The stage is white on purpose: a reflection only reads as
     * a reflection when there is something for it to sit on, and a dark smear of
     * a dark photo on a dark background disappears.
     */
    private fun DrawScope.drawReflections(frame: SlideshowFrame, textMeasurer: TextMeasurer?) {
        val fit = frame.prefs.fitMode.isFit
        val w = size.width
        val h = size.height
        drawRect(Color(0xFFF7F5F1))

        val p = Easing.easeInOutCubic(frame.transition)
        val gap = 6.dp.toPx()
        // As much of the frame as the photos can have: the caption below needs
        // one line, not a band.
        val box = GridRect(w * 0.03f, h * 0.035f, w * 0.97f, h * 0.735f)
        val baseline = box.bottom

        val index = frame.currentIndex
        if (ReflectionsWall.visible(index, frame.count).isEmpty()) {
            drawPlaceholder(Rect(box.left, box.top, box.right, baseline))
            return
        }
        val mix = (frame.progress / 0.22f).coerceIn(0f, 1f)
        val modeChanged = ReflectionsWall.modeChangesAt(index)

        // The stage crops the wall, so a wall scaled up to fill the width loses
        // the top of its tallest column instead of running over the chrome.
        clipRect(box.left, 0f, box.right, baseline) {
            if (modeChanged && mix < 1f) {
                // One photo becoming three (or back) re-flows the whole wall, so
                // the two arrangements cross-fade as a whole.
                drawWall(frame, index - 1, fit, box, gap, alpha = 1f - mix, newestMix = 1f)
            }
            drawWall(
                frame = frame,
                index = index,
                fit = fit,
                box = box,
                gap = gap,
                alpha = if (modeChanged) mix else 1f,
                newestMix = if (modeChanged) 1f else mix,
            )
        }

        // One short smear under the whole wall, mirrored and faded to the stage.
        // A real reflection, not a hint: tall enough to reach down to the
        // caption line, so the caption sits at the foot of the glass.
        val stripTop = baseline + h * 0.006f
        val stripHeight = min(h * 0.18f, h - stripTop)
        if (stripHeight <= 1f) return
        val strip = Rect(box.left, stripTop, box.right, stripTop + stripHeight)
        val cells = ReflectionsWall.visible(index, frame.count)
        val aspects = cells.map { frame.photos.getOrNull(it)?.aspectRatio ?: 1.5f }
        val rects = ReflectionsWall.layout(aspects, box, gap)

        // No wash and no hairline under the wall. A uniform grey band with four
        // hard edges is not glass, it is a grey rectangle sitting on the photo
        // stage - and the stage is the same colour as the fade, so the smear can
        // simply dissolve into it.
        // Stop the clip a hair above the strip's own bottom: on the boundary row
        // the fade's edge is only half covered, which let a few percent of the
        // smear through as a one pixel line across the frame.
        clipRect(strip.left, strip.top, strip.right, strip.bottom - 1.5f) {
            // Foreshortened, not squashed: 0.7 reads as a reflection on a table
            // top, while 0.42 read as the photo smeared out of shape.
            withTransform({ scale(1f, -0.70f, Offset((strip.left + strip.right) / 2f, stripTop)) }) {
                cells.forEachIndexed { cell, photoIndex ->
                    val place = rects.getOrNull(cell) ?: return@forEachIndexed
                    val bitmap = frame.bitmaps.full(photoIndex) ?: return@forEachIndexed
                    drawCropped(
                        img = bitmap,
                        dst = Rect(place.left, place.top, place.right, place.bottom),
                        alpha = 0.50f,
                        fit = fit,
                        backdrop = Color.Transparent,
                    )
                }
            }
            // Fade to the stage and finish before the strip does. Reaching full
            // opacity only at the very bottom row still leaves a couple of
            // percent of a dark photo showing, and that reads as a faint line
            // where the reflection stops.
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFF7F5F1).copy(alpha = 0.05f),
                        Color(0xFFF7F5F1).copy(alpha = 0.30f),
                        Color(0xFFF7F5F1).copy(alpha = 0.72f),
                        Color(0xFFF7F5F1),
                    ),
                    startY = strip.top,
                    endY = strip.top + stripHeight * 0.78f,
                ),
                topLeft = strip.topLeft,
                size = strip.size,
            )
        }
    }

    /** Paints one arrangement of the wall; [newestMix] cross-fades the cell that changed. */
    private fun DrawScope.drawWall(
        frame: SlideshowFrame,
        index: Int,
        fit: Boolean,
        box: GridRect,
        gap: Float,
        alpha: Float,
        newestMix: Float,
    ) {
        val cells = ReflectionsWall.visible(index, frame.count)
        if (cells.isEmpty() || alpha <= 0.01f) return
        val aspects = cells.map { frame.photos.getOrNull(it)?.aspectRatio ?: 1.5f }
        val rects = ReflectionsWall.layout(aspects, box, gap)
        val newest = ReflectionsWall.newestCell(index, frame.count)

        cells.forEachIndexed { cell, photoIndex ->
            val place = rects.getOrNull(cell) ?: return@forEachIndexed
            val rect = Rect(place.left, place.top, place.right, place.bottom)
            val current = frame.bitmaps.full(photoIndex)
            if (cell == newest && newestMix < 1f) {
                // The new photo cross-fades in over the one this cell held.
                val previous = ReflectionsWall.photoIndex(index - 1, cell, frame.count)
                frame.bitmaps.full(previous)?.let {
                    drawFloatingPhoto(it, rect, alpha = (1f - newestMix) * alpha, fit = fit, shadow = 0f)
                }
                if (current != null) {
                    drawFloatingPhoto(current, rect, alpha = newestMix * alpha, fit = fit, shadow = 0f)
                }
            } else if (current != null) {
                // No shadow on this theme. A drop shadow and a reflection are two
                // answers to the same question - where the photo sits in space -
                // and together they read as a grey halo around every edge.
                drawFloatingPhoto(current, rect, alpha = alpha, fit = fit, shadow = 0f)
            } else if (alpha > 0.5f) {
                drawPlaceholder(rect)
            }
        }
    }

    /** Polaroids settling onto a paper desk. */
    private fun DrawScope.drawScrapbook(frame: SlideshowFrame, textMeasurer: TextMeasurer?) {
        val fit = frame.prefs.fitMode.isFit
        val w = size.width
        val h = size.height
        drawRect(Color(0xFFE9E1D2))
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x33FFFFFF), Color(0x00000000), Color(0x22000000)),
                center = Offset(w * 0.35f, h * 0.25f),
                radius = max(w, h) * 0.9f,
            ),
        )

        // Paper speckles, fixed in space so they don't crawl between frames
        repeat(110) { i ->
            val x = Rng.unit(90_001L, i) * w
            val y = Rng.unit(90_002L, i) * h
            val r = 0.6f + Rng.unit(90_003L, i) * 1.4f
            drawCircle(Color.Black.copy(alpha = 0.045f), radius = r, center = Offset(x, y))
        }

        val visible = ScrapbookDesk.visible(frame.currentIndex, frame.count)
        if (visible.isEmpty()) {
            drawPlaceholder()
            return
        }
        // The new card falls onto the pile over the first slice of the cycle;
        // keying this off the cycle (not the transition) keeps it settled through
        // the hold.
        val fall = (frame.progress / 0.22f).coerceIn(0f, 1f)
        val settle = Easing.dropIn(fall)
        val fadeIn = (fall / 0.35f).coerceIn(0f, 1f)

        // The card that no longer fits fades out underneath the pile.
        ScrapbookDesk.leaving(frame.currentIndex, frame.count)?.let { going ->
            if (fadeIn < 1f) {
                drawDeskCard(
                    frame = frame,
                    photoIndex = going,
                    textMeasurer = textMeasurer,
                    fit = fit,
                    alpha = 1f - fadeIn,
                    settle = 1f,
                    arriving = false,
                    caption = false,
                )
            }
        }
        // Oldest first, so the newest card lands on top of the pile.
        for (rank in visible.indices.reversed()) {
            val newest = rank == 0
            drawDeskCard(
                frame = frame,
                photoIndex = visible[rank],
                textMeasurer = textMeasurer,
                fit = fit,
                alpha = if (newest) fadeIn else 1f,
                settle = if (newest) settle else 1f,
                arriving = newest,
                caption = rank < ScrapbookDesk.CAPTIONED,
            )
        }
    }

    /**
     * Paints one polaroid of the desk. [settle] drives the arrival of a new card:
     * 0 leaves it above the frame, 1 is settled in place.
     */
    private fun DrawScope.drawDeskCard(
        frame: SlideshowFrame,
        photoIndex: Int,
        textMeasurer: TextMeasurer?,
        fit: Boolean,
        alpha: Float,
        settle: Float,
        arriving: Boolean,
        caption: Boolean,
    ) {
        val photo = frame.photos.getOrNull(photoIndex) ?: return
        val alpha = alpha.coerceIn(0f, 1f)
        if (alpha <= 0.01f) return

        val w = size.width
        val h = size.height
        val aspect = (photo.aspectRatio ?: 1.4f).coerceIn(0.7f, 2.0f)
        val place = ScrapbookDesk.placement(photo.id, aspect, w / h)

        var cardW = w * place.width
        var cardH = cardW * (1.034f / aspect + 0.10f)
        val maxH = h * 0.62f
        if (cardH > maxH) {
            cardW *= maxH / cardH
            cardH = cardW * (1.034f / aspect + 0.10f)
        }

        // The card falls in from above the top edge and settles onto the pile;
        // once it has landed it simply stays put.
        val fall = if (arriving) (1f - settle).coerceIn(0f, 1f) else 0f
        val scale = 1f + 0.05f * fall
        val halfW = cardW * scale / 2f
        val halfH = cardH * scale / 2f
        // A card may hang over an edge, but never by more than about a quarter
        // of itself, so nothing is ever half off the screen. Only the resting
        // place is clamped — the fall itself starts well above the frame.
        val cx = (w * place.cx).coerceIn(halfW * 0.5f, w - halfW * 0.5f)
        val cy = (h * place.cy).coerceIn(halfH * 0.5f, h - halfH * 0.5f) - fall * h * 0.75f

        // The card tilts a little more while falling and straightens as it lands.
        val rotation = place.rotation + fall * if (place.rotation >= 0f) 7f else -7f
        val cardRect = Rect(cx - halfW, cy - halfH, cx + halfW, cy + halfH)
        // Tiles are plenty for a card this size and keep the full-frame cache free.
        val bitmap = frame.bitmaps.full(photoIndex) ?: frame.bitmaps.tile(photoIndex)
        drawPolaroid(
            img = bitmap,
            rect = cardRect,
            rotation = rotation,
            caption = if (caption) photo.caption else null,
            textMeasurer = textMeasurer,
            alpha = alpha,
            fit = fit,
        )

        // A little washi tape on every third card, so the pile reads as paper.
        // Tape is stuck on only after the card has landed.
        if (photo.id.hashCode().mod(3) == 0 || arriving) {
            val tapeAlpha = if (arriving) ((settle - 0.85f) / 0.15f).coerceIn(0f, 1f) else alpha
            if (tapeAlpha > 0.02f) drawTape(cardRect, rotation, tapeAlpha)
        }
    }

    /**
     * Origami: a justified wall of photos whose cells turn over like paper.
     *
     * The wall is cut once, from the album's own shapes, and keeps that shape all
     * session. That is what makes the fold work: a cell can only reveal the next
     * photo in the hole the old one leaves if it is the same hole. Re-cutting the
     * wall every page leaves flaps folding away from cells that no longer exist,
     * which opens black wedges onto the stage instead of onto a photograph.
     *
     * Most pages turn cell by cell, fanned across the wall; every fourth turns the
     * whole wall as one sheet. The fold is a real perspective projection rather
     * than a squash — the free edge shrinks towards the hinge and its corners
     * converge on the viewer's line of sight, which is what reads as paper.
     */
    private fun DrawScope.drawOrigami(frame: SlideshowFrame) {
        val w = size.width
        val h = size.height
        // What shows in the seams between sheets, and under a sheet that is
        // edge-on: shadow, not a photograph.
        drawRect(Color(0xFF07080B))

        val slots = GridPlan.slotsForEffect(frame.effect, frame.gridSlots)
        val page = GridPlan.pageAt(frame.elapsedMs, frame.count, slots)
        val gap = 3.dp.toPx()
        val cells = justifiedCells(frame, List(slots) { it }, w, h, gap)
        if (cells.isEmpty()) {
            drawPlaceholder()
            return
        }

        // The arriving wall is simply there, always: paper does not fade in, and
        // until a cell turns it is covered by the photo it is replacing.
        page.tiles.forEach { tile ->
            drawGridTile(frame.bitmaps.tile(tile.toIndex), cells.getOrNull(tile.slot), 1f)
        }
        if (page.generation <= 0L) return

        val seed = frame.photos.getOrNull(page.indices.firstOrNull() ?: 0)?.id?.hashCode()?.toLong() ?: 0L
        val edge = OrigamiFold.edgeFor(page.generation, seed)
        val camera = maxOf(w, h) * 2.2f
        val sheet = OrigamiFold.sheetFor(page.generation, seed)
        val local = (frame.elapsedMs - page.generation * GridPlan.PAGE_MS).toFloat() / GridPlan.PAGE_MS
        val sheetRect = GridRect(0f, 0f, w, h)
        val sheetTurn = if (sheet) Easing.smoothstep((local / SHEET_SPAN).coerceIn(0f, 1f)) else 0f
        val sheetFold = OrigamiFold.homography(sheetRect, edge, sheetTurn, camera, w / 2f, h / 2f)

        page.tiles.forEach { tile ->
            val cell = cells.getOrNull(tile.slot) ?: return@forEach
            // One sheet turns all at once; separate cells take their turn in
            // sequence, which is what fans them across the wall.
            val turn = if (sheet) {
                sheetTurn
            } else {
                val start = FAN_SPREAD * tile.slot / slots
                Easing.easeInOutCubic(((local - start) / FAN_SPAN).coerceIn(0f, 1f))
            }
            if (turn >= 0.999f) return@forEach
            val bitmap = frame.bitmaps.tile(tile.fromIndex) ?: return@forEach
            val rect = Rect(cell.left, cell.top, cell.right, cell.bottom)
            val fold = if (sheet) sheetFold else OrigamiFold.homography(cell, edge, turn, camera, w / 2f, h / 2f)
            val shade = foldShade(edge, cell, turn)
            if (sheet) {
                // The whole wall is one sheet, so its cells are not clipped to
                // their own holes: the sheet has to be free to leave them.
                withPerspective(fold) {
                    drawCropped(bitmap, rect, 1f, 0.5f, 0.5f, fit = false)
                    drawRect(shade, rect.topLeft, rect.size)
                }
            } else {
                clipPath(Path().apply { addRect(rect) }) {
                    withPerspective(fold) {
                        drawCropped(bitmap, rect, 1f, 0.5f, 0.5f, fit = false)
                        drawRect(shade, rect.topLeft, rect.size)
                    }
                }
            }
        }
    }

    /** Shadow across a turning cell, darkest at its free edge. */
    private fun foldShade(edge: OrigamiFold.Edge, cell: GridRect, turned: Float): Brush {
        val strength = OrigamiFold.shadeStrength(turned)
        val clear = Color.Transparent
        val dark = Color.Black.copy(alpha = strength)
        return when (edge) {
            // The hinge is the lit end; the free edge is the one in shadow.
            OrigamiFold.Edge.BOTTOM -> Brush.verticalGradient(listOf(dark, clear), cell.top, cell.bottom)
            OrigamiFold.Edge.TOP -> Brush.verticalGradient(listOf(clear, dark), cell.top, cell.bottom)
            OrigamiFold.Edge.LEFT -> Brush.horizontalGradient(listOf(clear, dark), cell.left, cell.right)
            OrigamiFold.Edge.RIGHT -> Brush.horizontalGradient(listOf(dark, clear), cell.left, cell.right)
        }
    }

    /**
     * Draws [block] through a projective homography — nine floats, row-major.
     *
     * Compose's own draw transforms are affine, and the whole point of the fold
     * is that it is not: the free edge has to come out shorter than the hinge
     * edge. So the homography goes onto the underlying canvas, which applies
     * perspective to everything drawn inside it.
     */
    private inline fun DrawScope.withPerspective(homography: FloatArray, block: DrawScope.() -> Unit) {
        val canvas = drawContext.canvas.nativeCanvas
        val matrix = android.graphics.Matrix()
        matrix.setValues(homography)
        val save = canvas.save()
        canvas.concat(matrix)
        block()
        canvas.restoreToCount(save)
    }

    /** A living mosaic of the whole album. */
    private fun DrawScope.drawPhotoGrid(frame: SlideshowFrame) {
        val w = size.width
        val h = size.height
        drawRect(Color(0xFF07080B))

        val page = GridPlan.pageAt(frame.elapsedMs, frame.count, frame.gridSlots)
        if (page.tiles.isEmpty()) {
            drawPlaceholder()
            return
        }

        val gap = 6.dp.toPx()
        val currentCells = justifiedCells(frame, page.indices, w, h, gap)
        if (currentCells.isEmpty()) {
            drawPlaceholder()
            return
        }
        val previousCells = if (page.generation > 0L) {
            justifiedCells(frame, GridPlan.indicesFor(page.generation - 1, frame.count, frame.gridSlots), w, h, gap)
        } else {
            currentCells
        }

        // The grid is a mosaic of crops: letterboxing every tile would fill the
        // wall with bars, so it always fills regardless of the fit setting.
        for (draw in GridLayers.plan(page, currentCells, previousCells)) {
            drawGridTile(frame.bitmaps.tile(draw.index), draw.cell, draw.alpha)
        }
    }

    private fun justifiedCells(
        frame: SlideshowFrame,
        indices: List<Int>,
        width: Float,
        height: Float,
        gap: Float,
    ): List<GridRect> {
        val aspects = indices.map { index ->
            frame.photos.getOrNull(index)?.aspectRatio ?: 1.5f
        }
        return JustifiedGrid.layout(aspects, width, height, gap)
    }

    private fun DrawScope.drawGridTile(bitmap: ImageBitmap?, cell: GridRect?, alpha: Float) {
        if (cell == null || alpha <= 0.001f) return
        val rect = Rect(cell.left, cell.top, cell.right, cell.bottom)
        if (bitmap == null) {
            if (alpha >= 0.999f) drawRect(Color(0xFF141821), rect.topLeft, rect.size)
            return
        }
        val clip = Path().apply { addRoundRect(RoundRect(rect, CornerRadius(3.dp.toPx()))) }
        clipPath(clip) {
            drawCropped(bitmap, rect, 1f, 0.5f, 0.5f, alpha = alpha, fit = false)
        }
    }

    // ------------------------------------------------------------ painting helpers

    private fun DrawScope.drawFloatingPhoto(
        img: ImageBitmap,
        dst: Rect,
        alpha: Float = 1f,
        fit: Boolean = false,
        shadow: Float = 1f,
    ) {
        val radius = CornerRadius(10.dp.toPx())
        val path = Path().apply { addRoundRect(RoundRect(dst, radius)) }

        // Layered shadow to fake a soft drop shadow. It is dialled down on the
        // light stages, where three black layers read as grey boxes around every
        // photo instead of as depth.
        if (shadow > 0.01f) {
            for (i in 3 downTo 1) {
                val grow = i * 5f
                drawRoundRect(
                    color = Color.Black.copy(alpha = 0.10f * alpha * shadow),
                    topLeft = Offset(dst.left - grow, dst.top + grow * 1.6f),
                    size = Size(dst.width + grow * 2f, dst.height + grow * 2f),
                    cornerRadius = CornerRadius(radius.x + grow),
                )
            }
        }
        clipPath(path) { drawCropped(img, dst, 1f, 0.5f, 0.5f, alpha, fit = fit) }
        drawRoundRect(
            color = Color.White.copy(alpha = 0.08f * alpha),
            topLeft = dst.topLeft,
            size = dst.size,
            cornerRadius = radius,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f),
        )
    }

    private fun DrawScope.drawPolaroid(
        img: ImageBitmap?,
        rect: Rect,
        rotation: Float,
        caption: String?,
        textMeasurer: TextMeasurer?,
        alpha: Float,
        fit: Boolean = false,
    ) {
        val corner = CornerRadius(4.dp.toPx())
        withTransform({ rotate(rotation, rect.center) }) {
            // Shadow
            drawRoundRect(
                color = Color.Black.copy(alpha = 0.28f * alpha),
                topLeft = Offset(rect.left + 7f, rect.top + 12f),
                size = rect.size,
                cornerRadius = corner,
            )
            // Card
            drawRoundRect(
                color = Color(0xFFFBF8F1).copy(alpha = alpha),
                topLeft = rect.topLeft,
                size = rect.size,
                cornerRadius = corner,
            )

            val pad = rect.width * 0.05f
            val photoRect = Rect(
                rect.left + pad,
                rect.top + pad,
                rect.right - pad,
                rect.bottom - pad - rect.height * 0.13f,
            )
            if (img != null) {
                clipRect(photoRect.left, photoRect.top, photoRect.right, photoRect.bottom) {
                    // The mat around a fitted photo is the paper, not black.
                    drawCropped(img, photoRect, 1f, 0.5f, 0.5f, alpha, fit = fit, backdrop = Color(0xFFFBF8F1))
                }
            } else {
                drawRect(Color(0xFF2A2D35).copy(alpha = alpha), photoRect.topLeft, photoRect.size)
            }

            if (caption != null && textMeasurer != null) {
                val style = TextStyle(
                    color = Color(0xFF4A4438).copy(alpha = alpha),
                    fontSize = (rect.width * 0.045f).toSp(),
                    fontWeight = FontWeight.Normal,
                    fontFamily = FontFamily.Serif,
                    letterSpacing = (rect.width * 0.001f).toSp(),
                )
                var layout = textMeasurer.measure(caption, style, maxLines = 1)
                // Shrink to fit the white strip instead of running off the card.
                if (layout.size.width > photoRect.width && layout.size.width > 0) {
                    val scale = photoRect.width / layout.size.width
                    layout = textMeasurer.measure(
                        caption,
                        style.copy(fontSize = (rect.width * 0.045f * scale).toSp()),
                        maxLines = 1,
                    )
                }
                drawText(
                    textLayoutResult = layout,
                    topLeft = Offset(
                        photoRect.left + (photoRect.width - layout.size.width) / 2f,
                        photoRect.bottom + (rect.bottom - photoRect.bottom - layout.size.height) / 2f,
                    ),
                )
            }
        }
    }

    private fun DrawScope.drawTape(rect: Rect, rotation: Float, alpha: Float) {
        withTransform({ rotate(rotation, rect.center) }) {
            val tapeW = rect.width * 0.20f
            val tapeH = rect.height * 0.058f
            val top = rect.top - tapeH * 0.5f
            // Two washi strips, tilted the other way from each other.
            withTransform({
                rotate(-7f, Offset(rect.left + rect.width * 0.16f, top + tapeH * 0.5f))
            }) {
                drawRect(
                    color = Color(0xA6E4D8BE).copy(alpha = alpha),
                    topLeft = Offset(rect.left + rect.width * 0.06f, top),
                    size = Size(tapeW, tapeH),
                )
                drawRect(
                    color = Color.White.copy(alpha = 0.22f * alpha),
                    topLeft = Offset(rect.left + rect.width * 0.06f, top),
                    size = Size(tapeW, tapeH * 0.35f),
                )
            }
            withTransform({
                rotate(6f, Offset(rect.right - rect.width * 0.16f, top + tapeH * 0.5f))
            }) {
                drawRect(
                    color = Color(0xA6E4D8BE).copy(alpha = alpha),
                    topLeft = Offset(rect.right - rect.width * 0.06f - tapeW, top),
                    size = Size(tapeW, tapeH),
                )
                drawRect(
                    color = Color.White.copy(alpha = 0.22f * alpha),
                    topLeft = Offset(rect.right - rect.width * 0.06f - tapeW, top),
                    size = Size(tapeW, tapeH * 0.35f),
                )
            }
        }
    }

    /**
     * Paints [img] into [dst]. In [fit] mode the whole picture is shown at its
     * original aspect ratio on a [backdrop]; otherwise it is cropped to fill.
     */
    private fun DrawScope.drawCropped(
        img: ImageBitmap,
        dst: Rect,
        zoom: Float = 1f,
        focusX: Float = 0.5f,
        focusY: Float = 0.5f,
        alpha: Float = 1f,
        fit: Boolean = false,
        backdrop: Color = Color(0xFF07080B),
    ) {
        if (dst.width <= 1f || dst.height <= 1f || alpha <= 0.001f) return
        val imgW = img.width.toFloat()
        val imgH = img.height.toFloat()
        if (imgW <= 0f || imgH <= 0f) return

        if (fit) {
            val a = alpha.coerceIn(0f, 1f)
            if (backdrop.alpha > 0.001f) {
                drawRect(
                    color = backdrop.copy(alpha = backdrop.alpha * a),
                    topLeft = dst.topLeft,
                    size = dst.size,
                )
            }
            val imgAspect = imgW / imgH
            val dstAspect = dst.width / dst.height
            val drawW: Float
            val drawH: Float
            if (imgAspect > dstAspect) {
                drawW = dst.width
                drawH = dst.width / imgAspect
            } else {
                drawH = dst.height
                drawW = dst.height * imgAspect
            }
            drawImage(
                image = img,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(img.width, img.height),
                dstOffset = IntOffset(
                    (dst.left + (dst.width - drawW) / 2f).roundToInt(),
                    (dst.top + (dst.height - drawH) / 2f).roundToInt(),
                ),
                dstSize = IntSize(
                    drawW.roundToInt().coerceAtLeast(1),
                    drawH.roundToInt().coerceAtLeast(1),
                ),
                alpha = a,
                filterQuality = FilterQuality.Medium,
            )
            return
        }

        val z = zoom.coerceAtLeast(1f)
        val dstAspect = dst.width / dst.height
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
        srcW = srcW.coerceAtMost(imgW)
        srcH = srcH.coerceAtMost(imgH)

        val cx = focusX.coerceIn(0f, 1f) * imgW
        val cy = focusY.coerceIn(0f, 1f) * imgH
        val left = (cx - srcW / 2f).coerceIn(0f, (imgW - srcW).coerceAtLeast(0f))
        val top = (cy - srcH / 2f).coerceIn(0f, (imgH - srcH).coerceAtLeast(0f))

        drawImage(
            image = img,
            srcOffset = IntOffset(left.roundToInt(), top.roundToInt()),
            srcSize = IntSize(srcW.roundToInt().coerceAtLeast(1), srcH.roundToInt().coerceAtLeast(1)),
            dstOffset = IntOffset(dst.left.roundToInt(), dst.top.roundToInt()),
            dstSize = IntSize(dst.width.roundToInt().coerceAtLeast(1), dst.height.roundToInt().coerceAtLeast(1)),
            alpha = alpha.coerceIn(0f, 1f),
            filterQuality = FilterQuality.Medium,
        )
    }

    private fun DrawScope.drawVignette(strength: Float) {
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.Transparent,
                    Color.Black.copy(alpha = strength),
                ),
                center = center,
                radius = max(size.width, size.height) * 0.78f,
            ),
        )
    }

    private fun DrawScope.drawPlaceholder(rect: Rect = Rect(Offset.Zero, size)) {
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFF161A24), Color(0xFF0A0C11)),
                start = rect.topLeft,
                end = rect.bottomRight,
            ),
            topLeft = rect.topLeft,
            size = rect.size,
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.05f),
            radius = min(rect.width, rect.height) * 0.05f,
            center = rect.center,
        )
    }

    private fun DrawScope.drawEmptyState() {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF12151D), Color(0xFF05060A)),
                startY = 0f,
                endY = size.height,
            ),
        )
    }

    private fun fitRect(img: ImageBitmap, box: Rect): Rect {
        if (img.width <= 0 || img.height <= 0) return box
        val aspect = img.width.toFloat() / img.height.toFloat()
        var w = box.width
        var h = w / aspect
        if (h > box.height) {
            h = box.height
            w = h * aspect
        }
        return Rect(
            box.center.x - w / 2f,
            box.center.y - h / 2f,
            box.center.x + w / 2f,
            box.center.y + h / 2f,
        )
    }

    /** Share of a page over which a whole-sheet fold turns over. */
    private const val SHEET_SPAN = 0.24f

    /** Share of a page one cell takes to turn, and how far the cells fan out. */
    private const val FAN_SPAN = 0.10f
    private const val FAN_SPREAD = 0.10f

    private const val HALF_PI = (Math.PI / 2).toFloat()
}

/** DrawScope entry point, so call sites don't need the renderer receiver. */
fun DrawScope.drawSlideshow(frame: SlideshowFrame, textMeasurer: TextMeasurer? = null) {
    with(SlideshowRenderer) { renderSlideshow(frame, textMeasurer) }
}
