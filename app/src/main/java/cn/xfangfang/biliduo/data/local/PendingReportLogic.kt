package cn.xfangfang.biliduo.data.local

data class PendingReportEntry(
    val aid: Long,
    val cid: Long,
    val progress: Long,
    val ts: Long,
    /** Account that produced this report; 0 means "logged out/legacy". */
    val mid: Long = 0L,
)

enum class QueueAction { NONE, ADD, REMOVE }

/**
 * Pure logic for the offline-report retry queue: dedup per (aid, cid),
 * bounded size, simple line-based serialization, and stop-on-first-failure flush.
 * No Android dependencies so it stays unit-testable.
 */
object PendingReportLogic {

    const val MAX_ENTRIES = 20

    fun add(
        entries: List<PendingReportEntry>,
        aid: Long,
        cid: Long,
        progress: Long,
        ts: Long,
        mid: Long = 0L,
    ): List<PendingReportEntry> {
        val key = entryKey(aid, cid, mid)
        val filtered = entries.filterNot { entryKey(it.aid, it.cid, it.mid) == key }
        val result = filtered + PendingReportEntry(aid, cid, progress, ts, mid)
        return if (result.size > MAX_ENTRIES) {
            result.sortedBy { it.ts }.takeLast(MAX_ENTRIES)
        } else {
            result
        }
    }

    fun remove(
        entries: List<PendingReportEntry>,
        aid: Long,
        cid: Long,
        mid: Long? = null,
    ): List<PendingReportEntry> {
        return entries.filterNot {
            it.aid == aid && it.cid == cid && (mid == null || it.mid == mid)
        }
    }

    /** Only entries produced by [mid] are eligible; anonymous/legacy entries never leak. */
    fun forAccount(entries: List<PendingReportEntry>, mid: Long): List<PendingReportEntry> =
        if (mid == 0L) emptyList() else entries.filter { it.mid == mid }

    fun serialize(entries: List<PendingReportEntry>): String =
        entries.joinToString("\n") { "${it.aid}|${it.cid}|${it.progress}|${it.ts}|${it.mid}" }

    fun deserialize(raw: String?): List<PendingReportEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.lineSequence()
            .mapNotNull { line ->
                val parts = line.split("|")
                if (parts.size != 4 && parts.size != 5) return@mapNotNull null
                val aid = parts[0].toLongOrNull() ?: return@mapNotNull null
                val cid = parts[1].toLongOrNull() ?: return@mapNotNull null
                val progress = parts[2].toLongOrNull() ?: return@mapNotNull null
                val ts = parts[3].toLongOrNull() ?: return@mapNotNull null
                // 4-field rows predate account scoping and belong to no account.
                val mid = if (parts.size == 5) parts[4].toLongOrNull() ?: 0L else 0L
                PendingReportEntry(aid, cid, progress, ts, mid)
            }
            .toList()
    }

    fun newestFirst(entries: List<PendingReportEntry>): List<PendingReportEntry> =
        entries.sortedByDescending { it.ts }

    /**
     * Decides queue handling after an optional server report attempt.
     * [reportSuccess] is null when no report was attempted (e.g. not logged in, aid == 0).
     */
    fun queueAction(loggedIn: Boolean, reportSuccess: Boolean?): QueueAction = when {
        !loggedIn -> QueueAction.NONE
        reportSuccess == null -> QueueAction.NONE
        reportSuccess -> QueueAction.REMOVE
        else -> QueueAction.ADD
    }

    /**
     * Sends entries newest-first; stops at the first failure (likely offline) so a dead
     * network does not burn rate limit on the rest. Returns how many were sent.
     */
    suspend fun flush(
        entries: List<PendingReportEntry>,
        report: suspend (PendingReportEntry) -> Result<Unit>
    ): Int {
        var sent = 0
        for (entry in newestFirst(entries)) {
            val result = report(entry)
            if (result.isFailure) break
            sent++
        }
        return sent
    }

    private fun entryKey(aid: Long, cid: Long, mid: Long) = "${mid}_${aid}_$cid"
}
