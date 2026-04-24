package com.powerme.app.util.timer

enum class TimerPhase { IDLE, SETUP, WORK, REST }

data class TimerEngineState(
    val phase: TimerPhase = TimerPhase.IDLE,
    val currentRound: Int = 0,
    val totalRounds: Int = 0,
    val displaySeconds: Int = 0,
    val elapsedSeconds: Int = 0,
    val tickEpochMs: Long = 0L,
    val isRunning: Boolean = false,
    val phaseTotalSeconds: Int = 0
)
