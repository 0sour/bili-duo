package cn.xfangfang.biliduo.data.download

import android.content.Context
import android.content.Intent
import android.net.Uri
import cn.xfangfang.biliduo.BilibiliApp
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * User-selected SAF tree for new downloads. A null URI means the private
 * `getExternalFilesDir(null)/downloads` directory.
 */
object DownloadLocation {
    private const val PREFS = "bili_prefs"
    private const val KEY_TREE = "download_tree_uri"

    private val prefs
        get() = BilibiliApp.instance.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _treeUri = MutableStateFlow(prefs.getString(KEY_TREE, null))
    val treeUri = _treeUri.asStateFlow()

    private val _pickRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val pickRequests = _pickRequests.asSharedFlow()

    fun requestPicker() {
        _pickRequests.tryEmit(Unit)
    }

    fun setTreeUri(context: Context, uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        prefs.edit().putString(KEY_TREE, uri.toString()).apply()
        _treeUri.value = uri.toString()
    }

    fun clear() {
        prefs.edit().remove(KEY_TREE).apply()
        _treeUri.value = null
    }

    fun displayName(treeUri: String?): String? = DownloadSaf.treeDisplayName(treeUri)
}
