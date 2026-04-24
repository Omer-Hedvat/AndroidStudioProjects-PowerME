package com.powerme.app.util.timer

sealed class TimerSpec {
    data class Amrap(val durationSeconds: Int) : TimerSpec()
    data class Rft(val targetRounds: Int, val capSeconds: Int? = null) : TimerSpec()
    data class Emom(
        val totalDurationSeconds: Int,
        val intervalSeconds: Int,
        val warnAtSeconds: Int? = null
    ) : TimerSpec()
    data class Tabata(
        val workSeconds: Int,
        val restSeconds: Int,
        val rounds: Int,
        val workWarnAtSeconds: Int? = null,
        val restWarnAtSeconds: Int? = null,
        val skipLastRest: Boolean = false
    ) : TimerSpec()
    data class Countdown(
        val durationSeconds: Int,
        val warnAtSeconds: Int? = null
    ) : TimerSpec()
    object Stopwatch : TimerSpec()
}
