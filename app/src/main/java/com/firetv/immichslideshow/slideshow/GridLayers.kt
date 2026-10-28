package com.firetv.immichslideshow.slideshow

/**
 * One thing to paint on the Photo Grid wall: a photo, the cell it occupies, and
 * how opaque it is. [incoming] marks the layer that is arriving, so the renderer
 * can keep every incoming tile above the outgoing ones.
 */
data class GridDraw(
    val index: Int,
    val cell: GridRect,
    val alpha: Float,
    val incoming: Boolean,
)

/**
 * Turns a [GridPage] into paint layers.
 *
 * The wall on screen keeps its own layout until a tile turns over: a photo that
 * is on its way out is painted in the cell the *old* layout gave it, and only the
 * photo arriving takes the new layout's cell. Without this the new page's layout
 * is applied to photos that are still leaving, and the whole wall visibly
 * re-flows before a single photo has changed.
 */
object GridLayers {

    fun plan(
        page: GridPage,
        currentCells: List<GridRect>,
        previousCells: List<GridRect>,
    ): List<GridDraw> {
        if (page.tiles.isEmpty()) return emptyList()

        val outgoing = ArrayList<GridDraw>(page.tiles.size)
        val incoming = ArrayList<GridDraw>(page.tiles.size)

        for (tile in page.tiles) {
            val mix = tile.mix.coerceIn(0f, 1f)

            if (mix < 1f) {
                val cell = previousCells.getOrNull(tile.slot)
                if (cell != null) {
                    outgoing.add(GridDraw(tile.fromIndex, cell, 1f - mix, incoming = false))
                }
            }
            if (mix > 0f) {
                val cell = currentCells.getOrNull(tile.slot)
                if (cell != null) {
                    incoming.add(GridDraw(tile.toIndex, cell, mix, incoming = true))
                }
            }
        }
        // Outgoing layers first: whatever is arriving must land on top.
        return outgoing + incoming
    }
}
