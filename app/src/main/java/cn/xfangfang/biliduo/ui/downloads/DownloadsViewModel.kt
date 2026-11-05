package cn.xfangfang.biliduo.ui.downloads

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import cn.xfangfang.biliduo.data.download.DownloadManager
import cn.xfangfang.biliduo.data.model.DownloadInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DownloadGroup(
    val bvid: String,
    val title: String,
    val cover: String,
    val downloads: List<DownloadInfo>
)

sealed class DownloadListItem {
    data class Group(val group: DownloadGroup) : DownloadListItem()
    data class Single(val download: DownloadInfo) : DownloadListItem()
}

enum class DownloadFilter(val label: String) {
    ALL("全部"),
    ACTIVE("进行中"),
    COMPLETED("已完成"),
}

internal fun DownloadListItem.isCompleted(): Boolean = when (this) {
    is DownloadListItem.Single -> download.status == DownloadInfo.STATUS_COMPLETED
    is DownloadListItem.Group ->
        group.downloads.isNotEmpty() &&
            group.downloads.all { it.status == DownloadInfo.STATUS_COMPLETED }
}

internal fun filterDownloadItems(
    items: List<DownloadListItem>,
    query: String,
    filter: DownloadFilter = DownloadFilter.ALL,
): List<DownloadListItem> {
    val byStatus = when (filter) {
        DownloadFilter.ALL -> items
        DownloadFilter.ACTIVE -> items.filter { !it.isCompleted() }
        DownloadFilter.COMPLETED -> items.filter { it.isCompleted() }
    }
    val q = query.trim().lowercase()
    if (q.isEmpty()) return byStatus
    return byStatus.filter { item ->
        when (item) {
            is DownloadListItem.Single -> matchesDownload(item.download, q)
            is DownloadListItem.Group ->
                item.group.title.lowercase().contains(q) ||
                    item.group.downloads.any { matchesDownload(it, q) }
        }
    }
}

private fun matchesDownload(download: DownloadInfo, query: String): Boolean =
    download.title.lowercase().contains(query) || download.part.lowercase().contains(query)

class DownloadsViewModel(application: Application) : AndroidViewModel(application) {

    private val downloadManager = DownloadManager.getInstance(application)

    private val _items = MutableStateFlow<List<DownloadListItem>>(emptyList())
    val items: StateFlow<List<DownloadListItem>> = _items.asStateFlow()

    private val callback: () -> Unit = { load() }

    init {
        downloadManager.onDownloadsChanged = callback
    }

    override fun onCleared() {
        super.onCleared()
        if (downloadManager.onDownloadsChanged === callback) {
            downloadManager.onDownloadsChanged = null
        }
    }

    fun load() {
        val downloads = downloadManager.getDownloads()
        val grouped = downloads.groupBy { it.bvid }
        val items = mutableListOf<DownloadListItem>()

        for ((bvid, group) in grouped) {
            if (group.size > 1) {
                val sorted = group.sortedBy { it.page }
                items.add(
                    DownloadListItem.Group(
                        DownloadGroup(
                            bvid = bvid,
                            title = sorted.first().title,
                            cover = sorted.first().cover,
                            downloads = sorted
                        )
                    )
                )
            } else {
                items.add(DownloadListItem.Single(group.first()))
            }
        }

        _items.value = items
    }

    fun deleteDownload(download: DownloadInfo) {
        downloadManager.deleteDownload(download.id)
        load()
    }

    fun deleteGroup(group: DownloadGroup) {
        group.downloads.forEach { downloadManager.deleteDownload(it.id) }
        load()
    }

    fun pauseDownload(download: DownloadInfo) {
        downloadManager.pauseDownload(download.id)
        load()
    }

    fun resumeDownload(download: DownloadInfo) {
        downloadManager.resumeDownload(download.id)
        load()
    }

    fun clearCompleted() {
        downloadManager.clearCompleted()
        load()
    }

    fun pauseAll() {
        downloadManager.pauseAll()
        load()
    }

    fun resumeAll() {
        downloadManager.resumeAll()
        load()
    }

    fun clearAll() {
        downloadManager.clearAll()
        load()
    }
}
