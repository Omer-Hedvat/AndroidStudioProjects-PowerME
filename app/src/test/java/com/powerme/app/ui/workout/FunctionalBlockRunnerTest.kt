package com.powerme.app.ui.workout

import com.powerme.app.data.database.WorkoutBlock
import com.powerme.app.data.database.WorkoutBlockDao
import com.powerme.app.util.WakeLockManager
import com.powerme.app.util.timer.TimerEngine
import com.powerme.app.util.timer.TimerEngineState
import com.powerme.app.util.timer.TimerPhase
import com.powerme.app.util.timer.TimerSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class FunctionalBlockRunnerTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var dao: WorkoutBlockDao
    private lateinit var wakeLockManager: WakeLockManager
    private lateinit var fakeEngine: FakeTimerEngine
    private lateinit var runner: FunctionalBlockRunner

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        dao = mock()
        wakeLockManager = mock()
        fakeEngine = FakeTimerEngine()
        runner = FunctionalBlockRunner(fakeEngine, dao, wakeLockManager)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Block constructors / plans ───────────────────────────────────────────

    private fun amrapBlock(
        id: String = "wb-1",
        runStartMs: Long? = null,
        durationSeconds: Int = 720,
    ) = WorkoutBlock(
        id = id, workoutId = "w-1", order = 1, type = "AMRAP",
        durationSeconds = durationSeconds, runStartMs = runStartMs,
    )

    private fun rftBlock(id: String = "wb-2", targetRounds: Int = 5, cap: Int? = 1500) =
        WorkoutBlock(id = id, workoutId = "w-1", order = 1, type = "RFT",
            targetRounds = targetRounds, durationSeconds = cap)

    private fun emomBlock(id: String = "wb-3", duration: Int = 600, interval: Int = 60) =
        WorkoutBlock(id = id, workoutId = "w-1", order = 1, type = "EMOM",
            durationSeconds = duration, emomRoundSeconds = interval)

    private fun tabataBlock(id: String = "wb-4") = WorkoutBlock(
        id = id, workoutId = "w-1", order = 1, type = "TABATA",
        tabataWorkSeconds = 20, tabataRestSeconds = 10,
        targetRounds = 8, tabataSkipLastRest = 0,
    )

    private val emptyPlan = BlockPlan(
        durationSeconds = null, targetRounds = null, emomRoundSeconds = null,
        tabataWorkSeconds = null, tabataRestSeconds = null, tabataSkipLastRest = false,
        recipe = emptyList(),
    )

    // ── Tests ────────────────────────────────────────────────────────────────

    @Test
    fun `start acquires wakelock and sets runStart and calls timerEngine run`() = runTest(testDispatcher) {
        runner.start(amrapBlock(), emptyPlan)
        runCurrent()
        verify(wakeLockManager).acquire()
        verify(dao).setRunStart(eq("wb-1"), any())
        assertTrue(runner.isActive.value)
        assertNotNull(runner.state.value)
        assertEquals(TimerSpec.Amrap::class, fakeEngine.lastSpec!!::class)
    }

    @Test
    fun `start skips setRunStart when runStartMs already populated`() = runTest(testDispatcher) {
        runner.start(amrapBlock(runStartMs = 1_000L), emptyPlan)
        runCurrent()
        verify(dao, never()).setRunStart(any(), any())
    }

    @Test
    fun `start is no-op while already active`() = runTest(testDispatcher) {
        runner.start(amrapBlock(), emptyPlan)
        runCurrent()
        runner.start(amrapBlock(id = "wb-other"), emptyPlan)
        runCurrent()
        verify(wakeLockManager, times(1)).acquire()
    }

    @Test
    fun `pause and resume delegate to engine`() = runTest(testDispatcher) {
        runner.start(amrapBlock(), emptyPlan); runCurrent()
        runner.pause()
        assertEquals(1, fakeEngine.pauseCalls)
        runner.resume()
        assertEquals(1, fakeEngine.resumeCalls)
    }

    @Test
    fun `finish persists result releases wakelock and clears state`() = runTest(testDispatcher) {
        runner.start(amrapBlock(), emptyPlan); runCurrent()
        runner.finish(rounds = 8, extraReps = 3, rpe = 7, notes = null)
        runCurrent()
        verify(dao, atLeastOnce()).saveResult(
            any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(),
            anyOrNull(), anyOrNull(), anyOrNull(), any()
        )
        verify(wakeLockManager).release()
        assertFalse(runner.isActive.value)
        assertNull(runner.state.value)
    }

    @Test
    fun `finish rejects both rpe and perExerciseRpeJson set`() = runTest(testDispatcher) {
        runner.start(amrapBlock(), emptyPlan); runCurrent()
        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking {
                runner.finish(rpe = 7, perExerciseRpeJson = "{}")
            }
        }
    }

    @Test
    fun `abandon clears state without saveResult and leaves runStartMs intact`() = runTest(testDispatcher) {
        runner.start(amrapBlock(), emptyPlan); runCurrent()
        runner.abandon(); runCurrent()
        verify(dao, never()).saveResult(any(), anyOrNull(), anyOrNull(), anyOrNull(),
            anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), any())
        verify(wakeLockManager).release()
        assertFalse(runner.isActive.value)
    }

    @Test
    fun `appendRoundTap calls dao with serialized JSON entry`() = runTest(testDispatcher) {
        runner.start(amrapBlock(), emptyPlan); runCurrent()
        runner.appendRoundTap(round = 3, elapsedMs = 12_345L, completed = true)
        runCurrent()
        verify(dao).appendRoundTap(eq("wb-1"), any(), any())
    }

    @Test
    fun `isActive transitions correctly`() = runTest(testDispatcher) {
        assertFalse(runner.isActive.value)
        runner.start(amrapBlock(), emptyPlan); runCurrent()
        assertTrue(runner.isActive.value)
        runner.abandon(); runCurrent()
        assertFalse(runner.isActive.value)
    }

    @Test
    fun `resumeFromKill calls timerEngine resumeAt with correct offset for AMRAP`() =
        runTest(testDispatcher) {
            val now = System.currentTimeMillis()
            // 60s elapsed since runStartMs
            val block = amrapBlock(runStartMs = now - 60_000L, durationSeconds = 720)
            runner.resumeFromKill(block, emptyPlan)
            runCurrent()
            verify(wakeLockManager).acquire()
            assertEquals(TimerSpec.Amrap::class, fakeEngine.lastSpec!!::class)
            // Allow ±2s tolerance for clock drift between runStartMs and now in test
            assertTrue("expected ~60s, got ${fakeEngine.lastResumeElapsed}",
                kotlin.math.abs((fakeEngine.lastResumeElapsed ?: -1) - 60) <= 2)
        }

    @Test
    fun `mapToTimerSpec produces correct TABATA mapping`() = runTest(testDispatcher) {
        runner.start(tabataBlock(), emptyPlan); runCurrent()
        val spec = fakeEngine.lastSpec as TimerSpec.Tabata
        assertEquals(20, spec.workSeconds)
        assertEquals(10, spec.restSeconds)
        assertEquals(8, spec.rounds)
        assertFalse(spec.skipLastRest)  // tabataSkipLastRest=0 → false
    }

    @Test
    fun `mapToTimerSpec produces correct EMOM mapping`() = runTest(testDispatcher) {
        runner.start(emomBlock(duration = 600, interval = 120), emptyPlan); runCurrent()
        val spec = fakeEngine.lastSpec as TimerSpec.Emom
        assertEquals(600, spec.totalDurationSeconds)
        assertEquals(120, spec.intervalSeconds)
    }

    @Test
    fun `mapToTimerSpec produces correct RFT mapping`() = runTest(testDispatcher) {
        runner.start(rftBlock(targetRounds = 5, cap = 1500), emptyPlan); runCurrent()
        val spec = fakeEngine.lastSpec as TimerSpec.Rft
        assertEquals(5, spec.targetRounds)
        assertEquals(1500, spec.capSeconds)
    }
}

/** Minimal TimerEngine fake used by FunctionalBlockRunnerTest. */
private class FakeTimerEngine : TimerEngine {
    private val _state = MutableStateFlow(TimerEngineState())
    override val state: StateFlow<TimerEngineState> = _state

    var lastSpec: TimerSpec? = null
    var lastResumeElapsed: Int? = null
    var pauseCalls = 0
    var resumeCalls = 0
    var stopCalls = 0

    override suspend fun run(spec: TimerSpec, setupSeconds: Int) {
        lastSpec = spec
        // Don't loop — just record + return
    }

    override suspend fun resumeAt(spec: TimerSpec, elapsedSeconds: Int, setupSeconds: Int) {
        lastSpec = spec
        lastResumeElapsed = elapsedSeconds
    }

    override fun pause() { pauseCalls++ }
    override fun resume() { resumeCalls++ }
    override fun stop() { stopCalls++ }
    override fun addSeconds(delta: Int) {}
}
