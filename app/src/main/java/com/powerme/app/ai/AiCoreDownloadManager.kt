package com.powerme.app.ai

import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.prompt.Generation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(val progressPercent: Int = 0) : DownloadState()
    object Complete : DownloadState()
    data class Failed(val message: String) : DownloadState()
}

@Singleton
class AiCoreDownloadManager @Inject constructor() {

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    private val isDownloading = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun triggerDownload() {
        if (!isDownloading.compareAndSet(false, true)) {
            Timber.d("AiCoreDownloadManager: download already in progress, skipping")
            return
        }
        scope.launch {
            var totalBytes = 0L
            try {
                Timber.i("AiCoreDownloadManager: triggering model download")
                val model = Generation.getClient()
                model.download().collect { status ->
                    when (status) {
                        is DownloadStatus.DownloadStarted -> {
                            totalBytes = status.bytesToDownload
                            Timber.d("AiCoreDownloadManager: download started, totalBytes=%d", totalBytes)
                            _downloadState.value = DownloadState.Downloading(0)
                        }
                        is DownloadStatus.DownloadProgress -> {
                            val pct = if (totalBytes > 0L) {
                                ((status.totalBytesDownloaded * 100L) / totalBytes)
                                    .toInt().coerceIn(0, 100)
                            } else 0
                            _downloadState.value = DownloadState.Downloading(pct)
                        }
                        DownloadStatus.DownloadCompleted -> {
                            Timber.i("AiCoreDownloadManager: download complete")
                            _downloadState.value = DownloadState.Complete
                            isDownloading.set(false)
                        }
                        is DownloadStatus.DownloadFailed -> {
                            val msg = status.e.message ?: "Unknown download error"
                            Timber.e("AiCoreDownloadManager: download failed: $msg")
                            _downloadState.value = DownloadState.Failed(msg)
                            isDownloading.set(false)
                        }
                    }
                }
            } catch (e: Throwable) {
                Timber.e(e, "AiCoreDownloadManager: download error")
                _downloadState.value = DownloadState.Failed(e.message ?: "Download failed")
                isDownloading.set(false)
            }
        }
    }
}
