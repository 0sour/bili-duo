package cn.xfangfang.biliduo.ui.update

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cn.xfangfang.biliduo.BuildConfig
import cn.xfangfang.biliduo.data.local.AppSettings
import cn.xfangfang.biliduo.data.update.UpdateClient
import cn.xfangfang.biliduo.data.update.UpdateDownloader
import cn.xfangfang.biliduo.data.update.UpdateInfo
import cn.xfangfang.biliduo.data.update.UpdateSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

enum class UpdateErrorKind {
    CHECK,
    DOWNLOAD,
    INSTALL,
}

sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data object Latest : UpdateUiState

    data class Available(val info: UpdateInfo) : UpdateUiState

    data class Downloading(
        val info: UpdateInfo,
        val source: UpdateSource,
        val downloaded: Long,
        val total: Long,
        val speedBytesPerSec: Long,
    ) : UpdateUiState

    data class Ready(
        val info: UpdateInfo,
        val source: UpdateSource,
        val file: File,
    ) : UpdateUiState

    data class NeedsPermission(
        val info: UpdateInfo,
        val source: UpdateSource,
        val file: File,
    ) : UpdateUiState

    data class Installing(val info: UpdateInfo) : UpdateUiState

    data class Error(
        val message: String,
        val kind: UpdateErrorKind = UpdateErrorKind.CHECK,
    ) : UpdateUiState
}

/**
 * Activity-scoped update state shared by the Settings page, the rail badge and
 * the bottom-screen update overlay.
 */
class UpdateViewModel(application: Application) : AndroidViewModel(application) {

    private val client = UpdateClient()
    private val downloader = UpdateDownloader(application)

    private val _state = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    private val _available = MutableStateFlow<UpdateInfo?>(null)
    val available: StateFlow<UpdateInfo?> = _available.asStateFlow()

    private var checkJob: Job? = null
    private var downloadJob: Job? = null
    private var autoCheckStarted = false
    private var readyAfterInstallError: UpdateUiState.Ready? = null

    fun autoCheckIfEnabled() {
        if (autoCheckStarted) return
        if (!AppSettings.autoCheckUpdate) return
        autoCheckStarted = true
        check(manual = false)
    }

    fun check(manual: Boolean) {
        if (checkJob?.isActive == true || downloadJob?.isActive == true) return
        checkJob = viewModelScope.launch {
            if (manual) _state.value = UpdateUiState.Checking
            client.check(BuildConfig.VERSION_CODE)
                .onSuccess { info ->
                    if (info == null) {
                        _available.value = null
                        if (manual) {
                            _state.value = UpdateUiState.Latest
                        } else if (_state.value is UpdateUiState.Checking) {
                            _state.value = UpdateUiState.Idle
                        }
                    } else {
                        _available.value = info
                        _state.value = UpdateUiState.Available(info)
                    }
                }
                .onFailure { error ->
                    if (manual) {
                        _state.value = UpdateUiState.Error(
                            message = friendlyCheckError(error),
                            kind = UpdateErrorKind.CHECK,
                        )
                    } else if (_state.value is UpdateUiState.Checking) {
                        _state.value = UpdateUiState.Idle
                    }
                }
        }
    }

    fun startDownload(source: UpdateSource) {
        val info = _available.value ?: return
        if (downloadJob?.isActive == true) return
        if (info.downloadUrl(source) == null) {
            _state.value = UpdateUiState.Error("该下载源暂不可用", UpdateErrorKind.DOWNLOAD)
            return
        }
        readyAfterInstallError = null
        downloadJob = viewModelScope.launch {
            _state.value = UpdateUiState.Downloading(
                info = info,
                source = source,
                downloaded = 0L,
                total = info.size,
                speedBytesPerSec = 0L,
            )
            var lastBytes = 0L
            var lastAt = SystemClock.elapsedRealtime()
            var lastEmit = 0L
            try {
                val result = downloader.download(info, source) { downloaded, total ->
                    val now = SystemClock.elapsedRealtime()
                    val elapsed = now - lastAt
                    val speed = if (elapsed > 0L) {
                        ((downloaded - lastBytes) * 1000L / elapsed).coerceAtLeast(0L)
                    } else {
                        0L
                    }
                    if (now - lastEmit >= 200L || (total > 0L && downloaded >= total)) {
                        lastBytes = downloaded
                        lastAt = now
                        lastEmit = now
                        _state.value = UpdateUiState.Downloading(
                            info = info,
                            source = source,
                            downloaded = downloaded,
                            total = total.coerceAtLeast(info.size),
                            speedBytesPerSec = speed,
                        )
                    }
                }
                result.fold(
                    onSuccess = { file ->
                        val ready = UpdateUiState.Ready(info, source, file)
                        readyAfterInstallError = ready
                        _state.value = ready
                    },
                    onFailure = { error ->
                        _state.value = UpdateUiState.Error(
                            message = "下载失败：${error.message ?: "未知错误"}",
                            kind = UpdateErrorKind.DOWNLOAD,
                        )
                    },
                )
            } catch (e: CancellationException) {
                _state.value = UpdateUiState.Available(info)
                throw e
            } catch (e: Exception) {
                _state.value = UpdateUiState.Error(
                    message = "下载失败：${e.message ?: "未知错误"}",
                    kind = UpdateErrorKind.DOWNLOAD,
                )
            }
        }
    }

    fun cancelDownload() {
        if (downloadJob?.isActive == true) {
            downloadJob?.cancel()
            downloadJob = null
            _available.value?.let { _state.value = UpdateUiState.Available(it) }
        }
    }

    fun install() {
        val current = _state.value
        val ready = when (current) {
            is UpdateUiState.Ready -> current
            is UpdateUiState.NeedsPermission -> UpdateUiState.Ready(
                current.info,
                current.source,
                current.file,
            )
            else -> readyAfterInstallError
        } ?: return
        if (!downloader.hasInstallPermission()) {
            _state.value = UpdateUiState.NeedsPermission(
                info = ready.info,
                source = ready.source,
                file = ready.file,
            )
            return
        }
        downloader.install(ready.file).fold(
            onSuccess = {
                _state.value = UpdateUiState.Installing(ready.info)
            },
            onFailure = { error ->
                _state.value = UpdateUiState.Error(
                    message = "无法打开系统安装器：${error.message ?: "未知错误"}",
                    kind = UpdateErrorKind.INSTALL,
                )
            },
        )
    }

    fun openInstallPermissionSettings() {
        downloader.openInstallPermissionSettings()
    }

    fun dismissError() {
        val info = _available.value ?: run {
            _state.value = UpdateUiState.Idle
            return
        }
        val error = _state.value as? UpdateUiState.Error
        _state.value = when {
            error?.kind == UpdateErrorKind.INSTALL && readyAfterInstallError != null ->
                readyAfterInstallError!!
            else -> UpdateUiState.Available(info)
        }
    }

    private fun friendlyCheckError(error: Throwable): String {
        val message = error.message.orEmpty()
        return if (message.contains("系统版本")) {
            message
        } else {
            "检查更新失败，请检查网络"
        }
    }
}
