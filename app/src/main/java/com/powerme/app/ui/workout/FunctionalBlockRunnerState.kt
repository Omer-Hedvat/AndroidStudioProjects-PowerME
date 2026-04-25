package com.powerme.app.ui.workout

import com.powerme.app.util.timer.TimerPhase

data class FunctionalBlockRunnerState(
    val blockId: String,
    val blockType: String,
    val blockName: String?,
    val plan: BlockPlan,
    val timerPhase: TimerPhase,
    val displaySeconds: Int,
    val elapsedSeconds: Int,
    val currentRound: Int,
    val totalRounds: Int,
    val phaseTotalSeconds: Int,
    val isRunning: Boolean,
    val isPaused: Boolean,
    val roundTapCount: Int,
    val capRemainingSeconds: Int? = null,
)

data class BlockPlan(
    val durationSeconds: Int?,
    val targetRounds: Int?,
    val emomRoundSeconds: Int?,
    val tabataWorkSeconds: Int?,
    val tabataRestSeconds: Int?,
    val tabataSkipLastRest: Boolean,
    val recipe: List<RecipeRow>,
)

data class RecipeRow(
    val exerciseId: Long,
    val exerciseName: String,
    val reps: Int?,
    val holdSeconds: Int?,
)
