package cn.xfangfang.biliduo.data.local

import android.content.SharedPreferences

/**
 * SharedPreferences-backed store for progress reports that failed to reach
 * the server (typically offline playback). Persists the queue so it can be
 * flushed on next app start / player entry.
 */
class PendingReportManager(private val prefs: SharedPreferences) {

    companion object {
        private const val KEY = "pending_reports"
    }

    private fun load(): List<PendingReportEntry> =
        PendingReportLogic.deserialize(prefs.getString(KEY, null))

    private fun store(entries: List<PendingReportEntry>) {
        prefs.edit().putString(KEY, PendingReportLogic.serialize(entries)).apply()
    }

    fun add(aid: Long, cid: Long, progress: Long, mid: Long = 0L) {
        store(
            PendingReportLogic.add(
                load(),
                aid,
                cid,
                progress,
                System.currentTimeMillis(),
                mid,
            )
        )
    }

    fun remove(aid: Long, cid: Long, mid: Long? = null) {
        store(PendingReportLogic.remove(load(), aid, cid, mid))
    }

    fun all(): List<PendingReportEntry> = load()

    fun clear() {
        prefs.edit().remove(KEY).apply()
    }
}
