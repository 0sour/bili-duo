package cn.xfangfang.biliduo.duo

import kotlin.math.abs

/**
 * Turns the right stick's vertical axis into discrete tab steps.
 *
 * The stick is analog and a single flick produces dozens of motion events.
 * A step is emitted when the axis first crosses [pressThreshold], when the
 * direction reverses, or every [repeatMs] while the stick is held. Releasing
 * below [releaseThreshold] resets the direction so the next flick works.
 */
class RightStickTabStepper(
    private val pressThreshold: Float = 0.65f,
    private val releaseThreshold: Float = 0.40f,
    private val repeatMs: Long = 200L,
) {
    private var direction = 0
    private var lastEmitAt = 0L

    /** -1 = up/previous, +1 = down/next, null = no step for this event. */
    fun onAxis(y: Float, now: Long): Int? {
        val current = when {
            y <= -pressThreshold -> -1
            y >= pressThreshold -> 1
            abs(y) < releaseThreshold -> 0
            // Between release and press: keep the previous direction so
            // minor jitter around the threshold does not re-trigger.
            else -> direction
        }
        if (current == 0) {
            direction = 0
            return null
        }
        if (current != direction || now - lastEmitAt >= repeatMs) {
            direction = current
            lastEmitAt = now
            return current
        }
        return null
    }

    fun reset() {
        direction = 0
        lastEmitAt = 0L
    }
}
