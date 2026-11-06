package org.himstedt.fireside.slideshow

/**
 * Deterministic pseudo-randomness. Every motion is derived from the photo id +
 * a salt so a photo always animates the same way (and previews don't jitter on
 * recomposition).
 */
object Rng {

    fun splitMix64(seed: Long): Long {
        var z = seed + -0x61c8864680b583ebL
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        return z xor (z ushr 31)
    }

    /** Stable unit float in [0, 1) for a (seed, salt) pair. */
    fun unit(seed: Long, salt: Int): Float {
        val h = splitMix64(seed * 0x9E3779B1L + salt * 0x85EBCA77L)
        val bits = ((h ushr 40).toInt() and 0xFFFFFF)
        return bits / 16_777_216f
    }

    fun range(seed: Long, salt: Int, min: Float, max: Float): Float =
        min + (max - min) * unit(seed, salt)

    fun int(seed: Long, salt: Int, bound: Int): Int {
        if (bound <= 1) return 0
        return (unit(seed, salt) * bound).toInt().coerceIn(0, bound - 1)
    }

    fun sign(seed: Long, salt: Int): Float = if (unit(seed, salt) < 0.5f) -1f else 1f

    fun bool(seed: Long, salt: Int): Boolean = unit(seed, salt) >= 0.5f
}
