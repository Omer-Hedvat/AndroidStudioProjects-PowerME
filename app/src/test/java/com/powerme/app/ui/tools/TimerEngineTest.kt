package com.powerme.app.ui.tools

import com.powerme.app.util.AlertType
import com.powerme.app.util.RestTimerNotifier
import com.powerme.app.util.TimerSound
import com.powerme.app.util.timer.TimerEngineImpl
import com.powerme.app.util.timer.TimerPhase
import com.powerme.app.util.timer.TimerSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

@OptIn(ExperimentalCoroutinesApi::class)
class TimerEngineTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var mockNotifier: RestTimerNotifier

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockNotifier = mock()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun engine() = TimerEngineImpl(mockNotifier) { TimerSound.BEEP }

    // ─── EMOM 3-round run ────────────────────────────────────────────────────

    @Test
    fun `EMOM 3-round run starts in WORK phase with correct round`() = runTest(testDispatcher) {
        val e = engine()
        val job = launch { e.run(TimerSpec.Emom(totalDurationSeconds = 180, intervalSeconds = 60)) }
        runCurrent()

        assertEquals(TimerPhase.WORK, e.state.value.phase)
        assertEquals(1, e.state.value.currentRound)
        assertEquals(3, e.state.value.totalRounds)
        assertTrue(e.state.value.isRunning)

        job.cancel()
    }

    @Test
    fun `EMOM 3-round run advances rounds correctly`() = runTest(testDispatcher) {
        val e = engine()
        val job = launch { e.run(TimerSpec.Emom(totalDurationSeconds = 180, intervalSeconds = 60)) }
        runCurrent()

        // Advance through round 1 (60s) — round 2 should start
        advanceTimeBy(60_001L)
        runCurrent()
        assertEquals(2, e.state.value.currentRound)

        // Advance through round 2
        advanceTimeBy(60_001L)
        runCurrent()
        assertEquals(3, e.state.value.currentRound)

        job.cancel()
    }

    @Test
    fun `EMOM 3-round run completes with IDLE phase and FINISH alert`() = runTest(testDispatcher) {
        val e = engine()
        val job = launch { e.run(TimerSpec.Emom(totalDurationSeconds = 180, intervalSeconds = 60)) }
        runCurrent()

        // Advance through all 3 rounds
        advanceTimeBy(180_001L)
        runCurrent()

        assertEquals(TimerPhase.IDLE, e.state.value.phase)
        assertFalse(e.state.value.isRunning)
        verify(mockNotifier).triggerAudioAlert(AlertType.FINISH, TimerSound.BEEP)

        job.join()
    }

    @Test
    fun `EMOM countdown tick fires at remaining 1 2 3 each round`() = runTest(testDispatcher) {
        val e = engine()
        // 1 round × 10s interval
        val job = launch { e.run(TimerSpec.Emom(totalDurationSeconds = 10, intervalSeconds = 10)) }
        runCurrent()

        advanceTimeBy(10_001L)
        runCurrent()

        // 3 countdown ticks per round (remaining = 3, 2, 1)
        verify(mockNotifier, times(3))
            .triggerAudioAlert(AlertType.COUNTDOWN_TICK, TimerSound.BEEP)

        job.join()
    }

    @Test
    fun `EMOM fires ROUND_START for each round`() = runTest(testDispatcher) {
        val e = engine()
        val job = launch { e.run(TimerSpec.Emom(totalDurationSeconds = 60, intervalSeconds = 20)) }
        runCurrent()

        advanceTimeBy(60_001L)
        runCurrent()

        // 3 rounds → 3 ROUND_START alerts
        verify(mockNotifier, times(3))
            .triggerAudioAlert(AlertType.ROUND_START, TimerSound.BEEP)

        job.join()
    }

    // ─── Warn-at firing ──────────────────────────────────────────────────────

    @Test
    fun `EMOM warn-at fires exactly once per round at correct remaining`() = runTest(testDispatcher) {
        val e = engine()
        // 2 rounds × 60s, warnAt = 30
        val job = launch {
            e.run(TimerSpec.Emom(totalDurationSeconds = 120, intervalSeconds = 60, warnAtSeconds = 30))
        }
        runCurrent()

        // Advance 30s → warnAt fires on round 1
        advanceTimeBy(30_001L)
        runCurrent()
        verify(mockNotifier, times(1)).triggerAudioAlert(AlertType.WARNING, TimerSound.BEEP)

        // Advance through rest of round 1 and first 30s of round 2
        advanceTimeBy(60_001L)
        runCurrent()
        verify(mockNotifier, times(2)).triggerAudioAlert(AlertType.WARNING, TimerSound.BEEP)

        job.cancel()
    }

    @Test
    fun `EMOM warn-at does not fire when warnAtSeconds is null`() = runTest(testDispatcher) {
        val e = engine()
        val job = launch { e.run(TimerSpec.Emom(totalDurationSeconds = 60, intervalSeconds = 60)) }
        runCurrent()

        advanceTimeBy(60_001L)
        runCurrent()

        verify(mockNotifier, times(0)).triggerAudioAlert(AlertType.WARNING, TimerSound.BEEP)

        job.join()
    }

    // ─── Pause / Resume ──────────────────────────────────────────────────────

    @Test
    fun `pause stops ticking and resume continues from same position`() = runTest(testDispatcher) {
        val e = engine()
        val job = launch { e.run(TimerSpec.Emom(totalDurationSeconds = 300, intervalSeconds = 300)) }
        runCurrent()

        // Tick 20s
        advanceTimeBy(20_001L)
        runCurrent()

        // Pause
        e.pause()
        // Let the in-flight delay complete so the coroutine reaches awaitNotPaused()
        advanceTimeBy(1_001L)
        runCurrent()

        val pausedDisplay = e.state.value.displaySeconds
        assertFalse(e.state.value.isRunning)

        // Advance significant time while paused — display must not change
        advanceTimeBy(50_001L)
        runCurrent()
        assertEquals(pausedDisplay, e.state.value.displaySeconds)

        // Resume
        e.resume()
        runCurrent()
        assertTrue(e.state.value.isRunning)

        // Tick 5s more
        advanceTimeBy(5_001L)
        runCurrent()
        assertTrue(e.state.value.displaySeconds < pausedDisplay)

        job.cancel()
    }

    @Test
    fun `resume after pause fires isRunning true`() = runTest(testDispatcher) {
        val e = engine()
        val job = launch { e.run(TimerSpec.Emom(totalDurationSeconds = 120, intervalSeconds = 120)) }
        runCurrent()

        e.pause()
        advanceTimeBy(1_001L)
        runCurrent()
        assertFalse(e.state.value.isRunning)

        e.resume()
        runCurrent()
        assertTrue(e.state.value.isRunning)

        job.cancel()
    }

    // ─── Stop mid-phase ──────────────────────────────────────────────────────

    @Test
    fun `stop mid-phase resets state to IDLE`() = runTest(testDispatcher) {
        val e = engine()
        val job = launch { e.run(TimerSpec.Emom(totalDurationSeconds = 180, intervalSeconds = 60)) }
        runCurrent()

        advanceTimeBy(30_001L)
        runCurrent()
        assertEquals(TimerPhase.WORK, e.state.value.phase)

        e.stop()
        job.cancel()
        runCurrent()

        assertEquals(TimerPhase.IDLE, e.state.value.phase)
        assertFalse(e.state.value.isRunning)
    }

    @Test
    fun `stop mid-phase during pause also resets state`() = runTest(testDispatcher) {
        val e = engine()
        val job = launch { e.run(TimerSpec.Emom(totalDurationSeconds = 180, intervalSeconds = 60)) }
        runCurrent()

        e.pause()
        advanceTimeBy(1_001L)
        runCurrent()

        e.stop()
        job.cancel()
        runCurrent()

        assertEquals(TimerPhase.IDLE, e.state.value.phase)
        assertFalse(e.state.value.isRunning)
        assertEquals(0, e.state.value.displaySeconds)
    }

    // ─── addSeconds ──────────────────────────────────────────────────────────

    @Test
    fun `addSeconds increases displaySeconds during countdown`() = runTest(testDispatcher) {
        val e = engine()
        val job = launch { e.run(TimerSpec.Countdown(durationSeconds = 60)) }
        runCurrent()

        advanceTimeBy(10_001L)
        runCurrent()
        val displayBefore = e.state.value.displaySeconds

        e.addSeconds(15)
        runCurrent()
        assertEquals(displayBefore + 15, e.state.value.displaySeconds)

        job.cancel()
    }

    @Test
    fun `addSeconds never goes below zero`() = runTest(testDispatcher) {
        val e = engine()
        e.addSeconds(-999)
        assertEquals(0, e.state.value.displaySeconds)
    }

    // ─── Tabata ──────────────────────────────────────────────────────────────

    @Test
    fun `Tabata alternates WORK and REST phases`() = runTest(testDispatcher) {
        val e = engine()
        val job = launch { e.run(TimerSpec.Tabata(workSeconds = 20, restSeconds = 10, rounds = 2)) }
        runCurrent()

        assertEquals(TimerPhase.WORK, e.state.value.phase)

        advanceTimeBy(20_001L)
        runCurrent()
        assertEquals(TimerPhase.REST, e.state.value.phase)

        advanceTimeBy(10_001L)
        runCurrent()
        assertEquals(TimerPhase.WORK, e.state.value.phase)

        job.cancel()
    }

    @Test
    fun `Tabata skipLastRest skips REST in final round`() = runTest(testDispatcher) {
        val e = engine()
        val job = launch {
            e.run(TimerSpec.Tabata(workSeconds = 10, restSeconds = 10, rounds = 2, skipLastRest = true))
        }
        runCurrent()

        // Round 1 work + rest
        advanceTimeBy(20_001L)
        runCurrent()
        assertEquals(TimerPhase.WORK, e.state.value.phase)

        // Round 2 work — should go to IDLE, not REST
        advanceTimeBy(10_001L)
        runCurrent()
        assertEquals(TimerPhase.IDLE, e.state.value.phase)

        job.join()
    }

    // ─── Countdown ───────────────────────────────────────────────────────────

    @Test
    fun `Countdown counts down to zero and finishes`() = runTest(testDispatcher) {
        val e = engine()
        val job = launch { e.run(TimerSpec.Countdown(durationSeconds = 5)) }
        runCurrent()

        assertEquals(TimerPhase.WORK, e.state.value.phase)

        advanceTimeBy(5_001L)
        runCurrent()

        assertEquals(TimerPhase.IDLE, e.state.value.phase)
        assertEquals(0, e.state.value.displaySeconds)
        verify(mockNotifier).triggerAudioAlert(AlertType.FINISH, TimerSound.BEEP)

        job.join()
    }

    // ─── Setup countdown ─────────────────────────────────────────────────────

    @Test
    fun `setup countdown transitions SETUP then WORK`() = runTest(testDispatcher) {
        val e = engine()
        val job = launch { e.run(TimerSpec.Countdown(durationSeconds = 60), setupSeconds = 3) }
        runCurrent()

        assertEquals(TimerPhase.SETUP, e.state.value.phase)

        advanceTimeBy(3_001L)
        runCurrent()

        assertEquals(TimerPhase.WORK, e.state.value.phase)

        job.cancel()
    }
}
