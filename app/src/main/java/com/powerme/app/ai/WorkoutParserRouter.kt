package com.powerme.app.ai

import com.powerme.app.analytics.AnalyticsTracker
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class WorkoutParserRouter @Inject constructor(
    @Named("cloud") private val cloudParser: WorkoutTextParser,
    private val onDeviceParser: OnDeviceWorkoutParser,
    private val aiCoreAvailability: AiCoreAvailability,
    private val downloadManager: AiCoreDownloadManager,
    private val analyticsTracker: AnalyticsTracker
) : WorkoutTextParser {

    override suspend fun parseWorkoutText(userInput: String, exerciseNames: List<String>): ParseResult {
        return when (aiCoreAvailability.check()) {
            AiCoreStatus.Ready -> {
                val onDeviceResult = runCatching {
                    onDeviceParser.parseWorkoutText(userInput, exerciseNames)
                }.getOrElse {
                    Timber.w(it, "Router: on-device inference threw; falling back to cloud")
                    null
                }
                if (onDeviceResult != null && onDeviceResult.error == null) {
                    analyticsTracker.logAiGeneration("on_device", onDeviceResult.exercises.size)
                    onDeviceResult
                } else {
                    onDeviceResult?.error?.let { Timber.w("Router: on-device error '%s'; falling back to cloud", it) }
                    val cloudResult = cloudParser.parseWorkoutText(userInput, exerciseNames)
                    analyticsTracker.logAiGeneration("cloud_fallback", cloudResult.exercises.size)
                    cloudResult
                }
            }
            AiCoreStatus.NeedsDownload -> {
                downloadManager.triggerDownload()
                val result = cloudParser.parseWorkoutText(userInput, exerciseNames)
                analyticsTracker.logAiGeneration("cloud", result.exercises.size)
                result
            }
            AiCoreStatus.NotSupported -> {
                val result = cloudParser.parseWorkoutText(userInput, exerciseNames)
                analyticsTracker.logAiGeneration("cloud", result.exercises.size)
                result
            }
        }
    }
}
