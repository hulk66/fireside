package org.himstedt.immichslideshow.slideshow

/** One cell of the Origami wall: the photo it holds, and the one it turns into. */
data class OrigamiCell(
    val from: Int,
    val to: Int,
) {
    val turns: Boolean get() = from != to
}

/** The wall at one page: which photo each cell holds, and which cells turn. */
data class OrigamiWall(
    val page: Long,
    /** Every cell turns on this page. */
    val full: Boolean,
    val cells: List<OrigamiCell>,
)

/**
 * What the Origami wall shows, page by page.
 *
 * Two pages in three turn the whole wall over: every cell takes the next photo in
 * the album, so the wall is replaced at once. The third turns a *single* cell —
 * one photo leaves, the next arrives in its place, and the rest of the wall simply
 * stays. Changing the whole wall every time is more motion than one photograph's
 * worth of news can justify.
 *
 * The photo for each cell is derived, not accumulated: page `p` sits
 * `fullsBefore(p)` windows into the album, so any page can be computed on its own
 * and the renderer stays stateless. The cell that turns on a single page is the
 * one whose photo the *next* full page would have brought in anyway, so a single
 * turn neither repeats a photo nor skips one — it previews one cell of the wall
 * that is coming.
 */
object OrigamiPlan {

    /** Pages per cycle: two whole-wall turns, then one single cell. */
    const val PERIOD = 3L

    /** True when every cell turns on [page]. */
    fun isFull(page: Long): Boolean = Math.floorMod(page, PERIOD) != PERIOD - 1

    fun wall(page: Long, count: Int, slots: Int): OrigamiWall {
        if (count <= 0 || slots <= 0) return OrigamiWall(page, false, emptyList())
        val p = page.coerceAtLeast(0L)
        val full = isFull(p)
        // A single page still shows the window the last whole-wall turn left up.
        val window = if (full) fullsBefore(p) else fullsBefore(p - 1)
        val cells = ArrayList<OrigamiCell>(slots)
        for (slot in 0 until slots) {
            val to = photo(window, slot, count, slots)
            // A whole-wall turn starts from the window that is already up; the
            // first page has nothing before it, so it simply appears.
            val from = if (full && window > 0L) photo(window - 1, slot, count, slots) else to
            cells.add(OrigamiCell(from, to))
        }
        if (!full) {
            val cell = (singlesBefore(p) % slots).toInt()
            cells[cell] = OrigamiCell(
                from = photo(window, cell, count, slots),
                to = photo(window + 1, cell, count, slots),
            )
        }
        return OrigamiWall(p, full, cells)
    }

    /**
     * Photos the player must keep decoded: the wall that is up and the one after
     * it. Both ends of every cell are included, because a cell that is turning is
     * drawing the photo it is leaving as well as the one arriving.
     */
    fun prefetch(page: Long, count: Int, slots: Int): List<Int> {
        if (count <= 0 || slots <= 0) return emptyList()
        val here = page.coerceAtLeast(0L)
        val indices = LinkedHashSet<Int>(slots * 4)
        for (p in here..here + 1) {
            for (cell in wall(p, count, slots).cells) {
                indices.add(cell.from)
                indices.add(cell.to)
            }
        }
        return indices.toList()
    }

    /** Whole-wall turns before [page]. */
    fun fullsBefore(page: Long): Long = page.coerceAtLeast(0L) - singlesBefore(page)

    /** Single-cell turns before [page]. */
    fun singlesBefore(page: Long): Long = Math.floorDiv(page.coerceAtLeast(0L), PERIOD)

    private fun photo(window: Long, slot: Int, count: Int, slots: Int): Int =
        Math.floorMod(window * slots + slot, count.toLong()).toInt()
}
