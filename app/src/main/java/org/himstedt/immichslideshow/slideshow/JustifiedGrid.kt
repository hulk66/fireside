package org.himstedt.immichslideshow.slideshow

/** Plain rectangle, so the layout maths stays free of Compose types. */
data class GridRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val area: Float get() = width * height
}

/**
 * Flickr-style justified layout.
 *
 * Photos keep their own aspect ratio and every row is filled edge to edge, so
 * widths vary from tile to tile instead of every tile being the same 16:9 crop.
 * Photos in a row share one height, and the rows are chosen to be as even as the
 * aspect ratios allow — that evenness is what makes the wall read as a layout
 * rather than a pile of strips.
 */
object JustifiedGrid {

    /** Aspect ratios outside this range would produce slivers; clamp them. */
    private const val MIN_ASPECT = 0.45f
    private const val MAX_ASPECT = 2.6f

    private const val FALLBACK_ASPECT = 1.5f

    /** More rows than this on a TV would be unreadable slivers. */
    private const val MAX_ROWS = 6

    private data class Row(val height: Float, val widths: List<Float>)

    /**
     * Lays [aspects] into a [width] × [height] box. Returns one rect per aspect,
     * in the same order, or an empty list when there is nothing to place.
     */
    fun layout(aspects: List<Float>, width: Float, height: Float, gap: Float): List<GridRect> {
        if (aspects.isEmpty() || width <= 1f || height <= 1f) return emptyList()
        val safe = aspects.map { sanitize(it) }
        val rows = bestRows(safe, width, height, gap)
        return build(rows, width, height, gap)
    }

    private fun sanitize(aspect: Float): Float =
        if (!aspect.isFinite() || aspect <= 0f) FALLBACK_ASPECT else aspect.coerceIn(MIN_ASPECT, MAX_ASPECT)

    /**
     * Tries every plausible row count and keeps the one whose rows sit closest to
     * the height they would have if the wall were perfectly even. A greedy fill
     * would happily put eleven photos in the first row and four in the last; this
     * does not.
     */
    private fun bestRows(aspects: List<Float>, width: Float, height: Float, gap: Float): List<Row> {
        val n = aspects.size
        var bestRows: List<Row> = emptyList()
        var bestCost = Float.MAX_VALUE

        for (rowCount in 1..minOf(n, MAX_ROWS)) {
            val target = (height - gap * (rowCount + 1)) / rowCount
            if (target <= 1f) continue
            val cuts = partition(aspects, width, target, rowCount, gap) ?: continue
            // Even rows first, then how close the whole stack comes to the frame:
            // a split that leaves a wide strip of background is worse than one
            // whose rows are a little less even.
            // How much of the wall has to be cropped to meet the frame: a
            // split whose rows add up to the frame height needs none.
            val stack = cuts.rows.sumOf { it.height.toDouble() }.toFloat() + gap * (rowCount + 1)
            val crop = kotlin.math.abs(stack - height) / maxOf(stack, height)
            val cost = crop * 4f + cuts.cost
            if (cost < bestCost) {
                bestCost = cost
                bestRows = cuts.rows
            }
        }
        if (bestRows.isEmpty()) {
            // Degenerate box: one row, however many photos there are.
            bestRows = listOf(rowOf(aspects, aspects.sum(), width, gap))
        }
        return bestRows
    }

    private data class Partition(val cost: Float, val rows: List<Row>)

    /**
     * Splits [aspects] into exactly [rowCount] contiguous rows, minimising the
     * squared deviation of each row height from [target] (dynamic programming:
     * the best split of a prefix is the best split of a shorter prefix plus one
     * row, so the optimum is found rather than guessed).
     */
    private fun partition(
        aspects: List<Float>,
        width: Float,
        target: Float,
        rowCount: Int,
        gap: Float,
    ): Partition? {
        val n = aspects.size
        if (rowCount > n) return null

        // prefix[i] = sum of the first i aspects.
        val prefix = FloatArray(n + 1)
        for (i in 0 until n) prefix[i + 1] = prefix[i] + aspects[i]

        fun heightOf(from: Int, to: Int): Float {
            val sum = prefix[to] - prefix[from]
            if (sum <= 0f) return target
            return (width - gap * (to - from + 1)) / sum
        }

        fun costOf(from: Int, to: Int): Float {
            val deviation = (heightOf(from, to) - target) / target
            return deviation * deviation
        }

        val dp = Array(rowCount + 1) { FloatArray(n + 1) { Float.MAX_VALUE } }
        val back = Array(rowCount + 1) { IntArray(n + 1) { -1 } }
        dp[0][0] = 0f

        for (row in 1..rowCount) {
            for (end in row..n) {
                // Leave at least one photo for every row still to come.
                for (start in (row - 1) until end) {
                    if (dp[row - 1][start] == Float.MAX_VALUE) continue
                    val candidate = dp[row - 1][start] + costOf(start, end)
                    if (candidate < dp[row][end]) {
                        dp[row][end] = candidate
                        back[row][end] = start
                    }
                }
            }
        }

        if (dp[rowCount][n] == Float.MAX_VALUE) return null

        val bounds = ArrayList<Int>(rowCount + 1)
        var end = n
        var row = rowCount
        while (row > 0) {
            bounds.add(0, end)
            end = back[row][end]
            row--
        }
        bounds.add(0, 0)

        val rows = ArrayList<Row>(rowCount)
        for (r in 0 until rowCount) {
            val from = bounds[r]
            val to = bounds[r + 1]
            val slice = aspects.subList(from, to)
            rows.add(rowOf(slice, prefix[to] - prefix[from], width, gap))
        }
        return Partition(dp[rowCount][n], rows)
    }

    private fun rowOf(aspects: List<Float>, sum: Float, width: Float, gap: Float): Row {
        val height = ((width - gap * (aspects.size + 1)) / sum).coerceAtLeast(1f)
        return Row(height, aspects.map { it * height })
    }

    /**
     * Places the rows top to bottom without ever touching a cell's shape.
     *
     * A row's height is fixed by its photos' aspects — the row has to fill the
     * width — so the stack height cannot be dialled in, and for most albums no
     * split adds up to the frame exactly. Rather than squeeze the rows (which is
     * what used to bend the photos), the finished mosaic is scaled uniformly to
     * cover the frame and centred, so the surplus is cropped at the edges. Every
     * cell keeps its photo's shape, and the wall is never narrower than the TV.
     */
    private fun build(rows: List<Row>, width: Float, height: Float, gap: Float): List<GridRect> {
        if (rows.isEmpty()) return emptyList()
        val verticalGaps = rows.size + 1
        val natural = rows.sumOf { it.height.toDouble() }.toFloat()
        val stack = natural + gap * verticalGaps
        // Cover, never shrink: a short stack grows until it meets both edges.
        val scale = (height / stack).coerceAtLeast(1f)
        val hGap = gap * scale
        val vGap = gap * scale
        // Centre by the outer margins, not by the mosaic box: the gaps are part
        // of the mosaic, and counting them twice used to leave a band at the top.
        val topMargin = (height - (natural * scale + (rows.size - 1) * vGap)) / 2f
        // A justified row spans the frame minus one gap at each side.
        val rowSpan = (width - 2f * gap) * scale
        val leftMargin = (width - rowSpan) / 2f

        val out = ArrayList<GridRect>(rows.sumOf { it.widths.size })
        var y = topMargin
        for (row in rows) {
            val rowHeight = row.height * scale
            var x = leftMargin
            for (tileWidth in row.widths) {
                val w = tileWidth * scale
                out.add(GridRect(x, y, x + w, y + rowHeight))
                x += w + hGap
            }
            y += rowHeight + vGap
        }
        return out
    }
}
