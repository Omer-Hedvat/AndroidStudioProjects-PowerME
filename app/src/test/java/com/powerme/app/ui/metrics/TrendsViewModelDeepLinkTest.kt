package com.powerme.app.ui.metrics

import androidx.lifecycle.SavedStateHandle
import com.powerme.app.analytics.ReadinessEngine
import com.powerme.app.data.database.ExerciseWithHistory
import com.powerme.app.data.repository.TrendsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Tests for the History → Trends deep-link exercise pre-selection.
 *
 * When a user taps "View Trend" on a history exercise card, the nav deep-link carries
 * `exerciseId` as a Long nav argument. TrendsViewModel reads it via SavedStateHandle in
 * init{} and pre-selects the matching chip in E1RMProgressionCard.
 *
 * The one-shot invariant: deepLinkPending is consumed after scroll completes via consumeDeepLink().
 * Manually navigating back to Trends (restoreState = true from bottom nav) reuses the saved
 * ViewModel, so init{} does NOT re-run and deepLinkPending stays false.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TrendsViewModelDeepLinkTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var trendsRepository: TrendsRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        trendsRepository = mock()
    }

    @After
    fun tearDown() {
        // Vico's CartesianChartModelProducer.runTransaction uses a real background thread.
        // Give it time to finish before resetMain() unsets Dispatchers.Main.
        Thread.sleep(100)
        Dispatchers.resetMain()
    }

    // ── Repository stubs ──────────────────────────────────────────────────────

    private suspend fun stubDefaults(
        pickerItems: List<ExerciseWithHistory> = emptyList()
    ) {
        whenever(trendsRepository.getReadinessScore()).thenReturn(ReadinessEngine.ReadinessScore.NoData)
        whenever(trendsRepository.getReadinessSubMetrics()).thenReturn(ReadinessSubMetrics(null, null, null))
        whenever(trendsRepository.getWeeklyVolume(any())).thenReturn(WeeklyVolumeData(emptyList(), 0.0))
        whenever(trendsRepository.getWeeklyMuscleGroupVolume(any())).thenReturn(emptyList())
        whenever(trendsRepository.getWeeklyEffectiveSets(any())).thenReturn(emptyList())
        whenever(trendsRepository.getEffectiveSetsCoverage(any())).thenReturn(0f)
        whenever(trendsRepository.getBodyCompositionData(any())).thenReturn(
            BodyCompositionData(emptyList(), emptyList(), emptyList())
        )
        whenever(trendsRepository.getChronotypeData(any())).thenReturn(
            ChronotypeData(emptyList(), emptyList(), null, null)
        )
        whenever(trendsRepository.getBodyStressMap()).thenReturn(emptyList())
        whenever(trendsRepository.getExercisePicker(any())).thenReturn(pickerItems)
        // Default E1RM stub — specific exercises override below
        whenever(trendsRepository.getE1RMProgression(any(), any(), any())).thenReturn(
            E1RMProgressionData(0L, "", emptyList(), emptyList(), null)
        )
    }

    // ── Deep-link pre-selection ───────────────────────────────────────────────

    @Test
    fun `deep-link sets selectedExerciseId immediately — before loadAll completes`() = runTest(testDispatcher) {
        // Don't even stub repo — we're checking the synchronous init{} path
        stubDefaults()

        val vm = TrendsViewModel(
            trendsRepository,
            SavedStateHandle(mapOf("exerciseId" to 42L))
        )

        // selectedExerciseId and deepLinkPending are set synchronously in init before loadAll() runs
        assertEquals(42L, vm.selectedExerciseId.value)
        assertTrue(vm.deepLinkPending.value)
    }

    @Test
    fun `deep-link pre-selects correct chip even when picker has multiple exercises`() = runTest(testDispatcher) {
        val pickerItems = listOf(
            ExerciseWithHistory(1L, "Bench Press"),
            ExerciseWithHistory(42L, "Squat"),
            ExerciseWithHistory(99L, "Deadlift")
        )
        stubDefaults(pickerItems)
        whenever(trendsRepository.getE1RMProgression(eq(42L), eq("Squat"), any())).thenReturn(
            E1RMProgressionData(42L, "Squat", emptyList(), emptyList(), null)
        )

        val vm = TrendsViewModel(
            trendsRepository,
            SavedStateHandle(mapOf("exerciseId" to 42L))
        )
        runCurrent()

        // Must be 42L (deep-linked), NOT 1L (first in list)
        assertEquals(42L, vm.selectedExerciseId.value)
    }

    @Test
    fun `deep-link skips auto-select in loadExercisePicker`() = runTest(testDispatcher) {
        // First item in picker has id=1 — deep link is for id=42 — loadExercisePicker must NOT override
        val pickerItems = listOf(
            ExerciseWithHistory(1L, "Bench Press"),
            ExerciseWithHistory(42L, "Squat")
        )
        stubDefaults(pickerItems)

        val vm = TrendsViewModel(
            trendsRepository,
            SavedStateHandle(mapOf("exerciseId" to 42L))
        )
        runCurrent()

        assertEquals(42L, vm.selectedExerciseId.value)
    }

    @Test
    fun `deep-link with exerciseId not in picker keeps selectedExerciseId but e1rmData is null`() = runTest(testDispatcher) {
        // Picker only has exercise 99 — deep link wants 42
        val pickerItems = listOf(ExerciseWithHistory(99L, "Deadlift"))
        stubDefaults(pickerItems)

        val vm = TrendsViewModel(
            trendsRepository,
            SavedStateHandle(mapOf("exerciseId" to 42L))
        )
        runCurrent()

        // selectedExerciseId stays as the deep-linked value
        assertEquals(42L, vm.selectedExerciseId.value)
        // E1RM data is null because loadE1RM() couldn't find exercise name for id=42
        assertNull(vm.e1rmData.value)
    }

    // ── One-shot: consumeDeepLink ─────────────────────────────────────────────

    @Test
    fun `consumeDeepLink clears deepLinkPending`() = runTest(testDispatcher) {
        stubDefaults()

        val vm = TrendsViewModel(
            trendsRepository,
            SavedStateHandle(mapOf("exerciseId" to 42L))
        )
        assertTrue(vm.deepLinkPending.value)

        vm.consumeDeepLink()

        assertFalse(vm.deepLinkPending.value)
    }

    @Test
    fun `consumeDeepLink does not affect selectedExerciseId`() = runTest(testDispatcher) {
        stubDefaults(listOf(ExerciseWithHistory(42L, "Squat")))
        whenever(trendsRepository.getE1RMProgression(eq(42L), eq("Squat"), any())).thenReturn(
            E1RMProgressionData(42L, "Squat", emptyList(), emptyList(), null)
        )

        val vm = TrendsViewModel(
            trendsRepository,
            SavedStateHandle(mapOf("exerciseId" to 42L))
        )
        runCurrent()

        vm.consumeDeepLink()

        // Exercise stays selected after scroll is consumed
        assertEquals(42L, vm.selectedExerciseId.value)
        assertFalse(vm.deepLinkPending.value)
    }

    // ── Default (no deep-link) behaviour ─────────────────────────────────────

    @Test
    fun `no deep-link argument — auto-selects first exercise in picker`() = runTest(testDispatcher) {
        val pickerItems = listOf(
            ExerciseWithHistory(7L, "Overhead Press"),
            ExerciseWithHistory(8L, "Row")
        )
        stubDefaults(pickerItems)

        val vm = TrendsViewModel(trendsRepository, SavedStateHandle())
        runCurrent()

        assertEquals(7L, vm.selectedExerciseId.value)
        assertFalse(vm.deepLinkPending.value)
    }

    @Test
    fun `exerciseId = -1 (default nav arg value) behaves identically to absent arg`() = runTest(testDispatcher) {
        val pickerItems = listOf(ExerciseWithHistory(5L, "Squat"))
        stubDefaults(pickerItems)

        val vm = TrendsViewModel(
            trendsRepository,
            SavedStateHandle(mapOf("exerciseId" to -1L))
        )
        runCurrent()

        // -1 is the nav defaultValue; must trigger auto-select not deep-link
        assertEquals(5L, vm.selectedExerciseId.value)
        assertFalse(vm.deepLinkPending.value)
    }

    @Test
    fun `no deep-link with empty picker — selectedExerciseId stays null`() = runTest(testDispatcher) {
        stubDefaults(emptyList())

        val vm = TrendsViewModel(trendsRepository, SavedStateHandle())
        runCurrent()

        assertNull(vm.selectedExerciseId.value)
        assertFalse(vm.deepLinkPending.value)
    }
}
