package cn.xfangfang.biliduo.data.download

import android.net.Uri
import android.provider.DocumentsContract
import cn.xfangfang.biliduo.BilibiliApp
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

/** Platform SAF helpers used by both the picker and the download writer. */
object DownloadSaf {
    private const val MIME_MP4 = "video/mp4"

    private fun resolver() = BilibiliApp.instance.contentResolver

    fun isContent(path: String): Boolean = path.startsWith("content://")

    /** Creates an empty video document in the selected tree, or null when no tree is set. */
    fun createVideo(displayName: String): Uri? {
        val tree = DownloadLocation.treeUri.value?.let(Uri::parse) ?: return null
        return runCatching {
            val parent = DocumentsContract.buildDocumentUriUsingTree(
                tree,
                DocumentsContract.getTreeDocumentId(tree),
            )
            DocumentsContract.createDocument(resolver(), parent, MIME_MP4, displayName)
        }.getOrNull()
    }

    fun length(path: String): Long {
        if (!isContent(path)) return File(path).length()
        return queryLong(Uri.parse(path), DocumentsContract.Document.COLUMN_SIZE) ?: 0L
    }

    /**
     * Opens the video document for writing. SAF append mode is "wa"; providers
     * that do not support it throw, and the caller marks the task failed rather
     * than silently truncating a half-downloaded file.
     */
    fun openOutput(path: String, append: Boolean): OutputStream? {
        if (!isContent(path)) return FileOutputStream(path, append)
        val mode = if (append) "wa" else "w"
        return runCatching { resolver().openOutputStream(Uri.parse(path), mode) }.getOrNull()
    }

    fun delete(path: String): Boolean {
        if (path.isBlank()) return false
        if (!isContent(path)) return File(path).delete()
        return runCatching {
            DocumentsContract.deleteDocument(resolver(), Uri.parse(path))
        }.getOrDefault(false)
    }

    fun treeDisplayName(treeUri: String?): String? {
        if (treeUri.isNullOrBlank()) return null
        return runCatching {
            val uri = Uri.parse(treeUri)
            val document = DocumentsContract.buildDocumentUriUsingTree(
                uri,
                DocumentsContract.getTreeDocumentId(uri),
            )
            queryString(document, DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        }.getOrNull()
    }

    private fun queryLong(uri: Uri, column: String): Long? = runCatching {
        resolver().query(uri, arrayOf(column), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
        }
    }.getOrNull()

    private fun queryString(uri: Uri, column: String): String? = runCatching {
        resolver().query(uri, arrayOf(column), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null
        }
    }.getOrNull()
}
