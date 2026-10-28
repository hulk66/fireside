package com.firetv.immichslideshow.slideshow

/**
 * The scrapbook desk: a ring of polaroids where the photo that has just arrived
 * always lands on the next place, so exactly one card is replaced per step and
 * the rest of the desk stays untouched.
 */
object ScrapbookDesk {

    /** Polaroids on the desk. */
    const val SLOTS = 5

    /** The place the current photo lands on. */
    fun newestSlot(index: Int): Int = Math.floorMod(index, SLOTS)

    /**
     * Photo shown in [slot] while photo [index] is current: the newest photo that
     * maps onto that slot. Older slots wrap into the end of the album, so the
     * desk is never half empty at the start of a show.
     */
    fun photoIndex(index: Int, slot: Int, count: Int): Int {
        if (count <= 0) return 0
        val stepsBack = Math.floorMod(index - slot, SLOTS)
        return Math.floorMod(index - stepsBack, count)
    }
}
