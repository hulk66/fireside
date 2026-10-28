package com.firetv.immichslideshow.slideshow

/**
 * The Reflections wall: a few photos side by side on dark glass.
 *
 * Cells are a ring, the same trick the scrapbook desk uses — the newest photo
 * always lands in the next cell, so exactly one photo changes per step and the
 * rest of the wall stays put. Every cell keeps its photo's own aspect ratio, and
 * all cells share one baseline, which is what lets a single reflection strip run
 * under the whole wall.
 */
object ReflectionsWall {

    /** Cells on the wall; three gives one tall column and two stacked ones. */
    const val CELLS = 3

    private const val MIN_ASPECT = 0.45f
    private const val MAX_ASPECT = 2.6f
    private const val FALLBACK_ASPECT = 1.5f

    /** Photo shown in [cell] while photo [index] is current. */
    fun photoIndex(index: Int, cell: Int, count: Int): Int {
        if (count <= 0) return 0
        val cells = minOf(count, CELLS)
        val stepsBack = Math.floorMod(index - cell, cells)
        return Math.floorMod(index - stepsBack, count)
    }

    /** The cell that received the newest photo. */
    fun newestCell(index: Int, count: Int): Int =
        if (count <= 0) 0 else Math.floorMod(index, minOf(count, CELLS))

    /** Photos on the wall, indexed by cell. */
    fun visible(index: Int, count: Int): List<Int> {
        if (count <= 0) return emptyList()
        return (0 until minOf(count, CELLS)).map { photoIndex(index, it, count) }
    }

    /**
     * Lays the cells out in [box], bottom aligned.
     *
     * With three photos the most portrait one becomes the tall left column and
     * the other two stack beside it; their width is solved so the stack is
     * exactly as tall as the column. Two photos get equal-height columns, and one
     * photo simply fills the box at its own shape.
     */
    fun layout(aspects: List<Float>, box: GridRect, gap: Float): List<GridRect> {
        if (aspects.isEmpty() || box.width <= 1f || box.height <= 1f) return emptyList()
        val safe = aspects.map { sanitize(it) }
        return when (safe.size) {
            1 -> listOf(fitIn(safe[0], box))
            2 -> twoColumns(safe, box, gap)
            else -> threeCells(safe.take(3), box, gap)
        }
    }

    private fun sanitize(aspect: Float): Float =
        if (!aspect.isFinite() || aspect <= 0f) FALLBACK_ASPECT else aspect.coerceIn(MIN_ASPECT, MAX_ASPECT)

    /** The largest rect of [aspect] that fits [box], centred. */
    private fun fitIn(aspect: Float, box: GridRect): GridRect {
        var w = box.width
        var h = w / aspect
        if (h > box.height) {
            h = box.height
            w = h * aspect
        }
        val left = box.left + (box.width - w) / 2f
        val top = box.top + (box.height - h) / 2f
        return GridRect(left, top, left + w, top + h)
    }

    private fun twoColumns(aspects: List<Float>, box: GridRect, gap: Float): List<GridRect> {
        val inner = box.width - gap
        val (a, b) = aspects
        // Both columns fill the height; if they do not fit side by side, shrink
        // both by the same factor so the pair stays centred and keeps its shape.
        var wa = box.height * a
        var wb = box.height * b
        val total = wa + wb
        if (total > inner) {
            val k = inner / total
            wa *= k
            wb *= k
        }
        val startX = box.left + (box.width - (wa + wb + gap)) / 2f
        val height = box.height
        return listOf(
            GridRect(startX, box.bottom - height, startX + wa, box.bottom),
            GridRect(startX + wa + gap, box.bottom - height, startX + wa + gap + wb, box.bottom),
        )
    }

    private fun threeCells(aspects: List<Float>, box: GridRect, gap: Float): List<GridRect> {
        // The most portrait photo takes the single column; the other two stack.
        val tallCell = aspects.indices.minByOrNull { aspects[it] } ?: 0
        val stacked = aspects.indices.filter { it != tallCell }

        // Width the stacked column needs so its two photos add up to the height.
        val sumReciprocal = stacked.sumOf { (1f / aspects[it]).toDouble() }.toFloat()
        val stackWidth = if (sumReciprocal > 0f) {
            ((box.height - gap) / sumReciprocal).coerceAtMost(box.width * 0.72f)
        } else {
            box.width * 0.5f
        }

        // Tall column: fills the height at its own shape, unless that would leave
        // the stack no room.
        var tallW = box.height * aspects[tallCell]
        val roomForTall = (box.width - gap - stackWidth).coerceAtLeast(1f)
        if (tallW > roomForTall) tallW = roomForTall
        val tallH = tallW / aspects[tallCell]

        // Centre the wall as one block: the two columns sit one gap apart, with
        // the leftover split evenly at the sides rather than pooled in between.
        val wallWidth = tallW + gap + stackWidth
        val left = box.left + (box.width - wallWidth) / 2f

        val out = arrayOfNulls<GridRect>(aspects.size)
        out[tallCell] = GridRect(left, box.bottom - tallH, left + tallW, box.bottom)

        // Stacked column: bottom aligned, laid upwards from the baseline.
        var y = box.bottom
        val stackLeft = left + tallW + gap
        for (cell in stacked.sortedByDescending { aspects[it] }) {
            val cellHeight = stackWidth / aspects[cell]
            out[cell] = GridRect(stackLeft, y - cellHeight, stackLeft + stackWidth, y)
            y -= cellHeight + gap
        }
        return out.filterNotNull()
    }
}
