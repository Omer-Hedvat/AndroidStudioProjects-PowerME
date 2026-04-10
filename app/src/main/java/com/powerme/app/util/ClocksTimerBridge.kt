package com.powerme.app.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton bridge that allows the screen-scoped ToolsViewModel to publish countdown
 * timer state to NavHost-scoped consumers (WorkoutViewModel, MinimizedWorkoutBar).
 */
@Singleton
class ClocksTimerBridge @Inject constructor() {

    data class Snapshot(val remainingSeconds: Int, val totalSeconds: Int) {
        val progress: Float get() =
            if (totalSeconds > 0) remainingSeconds.toFloat() / totalSeconds else 0f
    }

    private val _state = MutableStateFlow<Snapshot?>(null)
    val state: StateFlow<Snapshot?> = _state.asStateFlow()

    fun update(remaining: Int, total: Int) { _state.value = Snapshot(remaining, total) }
    fun clear() { _state.value = null }
}
