package cn.xfangfang.biliduo.ui.player

/** Positions shorter than this are treated as "never really started". */
internal const val MIN_RESUME_POSITION_MS = 5_000L

/** Do not resume into the last 5% of a video; restart it instead. */
private const val RESUME_END_PERCENT = 95L

/**
 * Chooses where a page should start.
 *
 * Local progress wins because it is written on this device and reflects the
 * most recent session; cloud progress only fills the gap on a fresh install or
 * when the same account watched elsewhere. A 0 local value means "no usable
 * local record", not "start at zero".
 */
internal fun pickResumePosition(localMs: Long, cloudMs: Long, durationMs: Long): Long {
    if (localMs > 0L) return localMs
    return normalizeCloudResume(cloudMs, durationMs)
}

internal fun normalizeCloudResume(cloudMs: Long, durationMs: Long): Long {
    if (cloudMs < MIN_RESUME_POSITION_MS) return 0L
    if (durationMs > 0L && cloudMs >= durationMs * RESUME_END_PERCENT / 100L) return 0L
    return cloudMs
}
