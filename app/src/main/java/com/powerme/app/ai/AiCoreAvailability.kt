package com.powerme.app.ai

import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

sealed class AiCoreStatus {
    object Ready : AiCoreStatus()
    object NeedsDownload : AiCoreStatus()
    object NotSupported : AiCoreStatus()
}

@Singleton
open class AiCoreAvailability @Inject constructor() {

    private val _status = MutableStateFlow<AiCoreStatus>(AiCoreStatus.NotSupported)
    val status: StateFlow<AiCoreStatus> = _status.asStateFlow()

    protected open suspend fun createModel(): GenerativeModel = Generation.getClient()

    suspend fun check(): AiCoreStatus {
        val result = try {
            val model = createModel()
            when (model.checkStatus()) {
                FeatureStatus.AVAILABLE -> AiCoreStatus.Ready
                FeatureStatus.DOWNLOADABLE -> AiCoreStatus.NeedsDownload
                FeatureStatus.DOWNLOADING -> AiCoreStatus.NeedsDownload
                else -> AiCoreStatus.NotSupported
            }
        } catch (e: Throwable) {
            Timber.w(e, "AiCoreAvailability: status check failed, falling back to NotSupported")
            AiCoreStatus.NotSupported
        }
        _status.value = result
        return result
    }
}
