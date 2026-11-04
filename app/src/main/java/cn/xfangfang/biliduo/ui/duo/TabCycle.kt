package cn.xfangfang.biliduo.ui.duo

/**
 * Wraps [current] by [delta] inside a list of [size] entries.
 *
 * Shoulder shortcuts (L1/R1 for top tabs, L2/R2 for filter rows) wrap around at
 * both ends, so repeatedly pressing the same button visits every entry.
 * Unknown or negative [current] falls back to the first entry; an empty list
 * keeps the index at 0.
 */
internal fun cycleIndex(current: Int, delta: Int, size: Int): Int {
    if (size <= 0) return 0
    val start = if (current in 0 until size) current else 0
    return ((start + delta) % size + size) % size
}
