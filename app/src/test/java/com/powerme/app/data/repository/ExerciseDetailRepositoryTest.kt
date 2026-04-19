package com.powerme.app.data.repository

import com.powerme.app.data.database.Exercise
import com.powerme.app.data.database.ExerciseDao
import com.powerme.app.data.database.ExerciseLastPerformedRow
import com.powerme.app.data.database.ExercisePRSet
import com.powerme.app.data.database.ExerciseSessionBestSet
import com.powerme.app.data.database.ExerciseSessionMaxWeight
import com.powerme.app.data.database.ExerciseSessionVolume
import com.powerme.app.data.database.ExerciseStressVectorDao
import com.powerme.app.data.database.ExerciseType
import com.powerme.app.data.database.ExerciseWorkoutHistoryRow
import com.powerme.app.data.database.ExerciseRpeTrendPoint
import com.powerme.app.data.database.MetricLog
import com.powerme.app.data.database.MetricType
import com.powerme.app.data.database.TrendsDao
import com.powerme.app.data.database.WorkoutSetDao
import com.powerme.app.ui.exercises.detail.OverloadSuggestion
import com.powerme.app.ui.metrics.TrendsTimeRange
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ExerciseDetailRepositoryTest {

    private lateinit var repository: ExerciseDetailRepository
    private lateinit var mockExerciseDao: ExerciseDao
    private lateinit var mockWorkoutSetDao: WorkoutSetDao
    private lateinit var mockTrendsDao: TrendsDao
    private lateinit var mockStressVectorDao: ExerciseStressVectorDao
    private lateinit var mockMetricLogRepository: MetricLogRepository

    @Before
    fun setup() {
        mockExerciseDao = mock()
        mockWorkoutSetDao = mock()
        mockTrendsDao = mock()
        mockStressVectorDao = mock()
        mockMetricLogRepository = mock()
        repository = ExerciseDetailRepository(
            mockExerciseDao,
            mockWorkoutSetDao,
            mockTrendsDao,
            mockStressVectorDao,
            mockMetricLogRepository
        )
    }

    // ── computePersonalRecords ────────────────────────────────────────────────

    @Test
    fun `computePersonalRecords - empty history - returns all nulls`() = runTest {
        whenever(mockWorkoutSetDao.getAllSetsForExercisePRs(1L)).thenReturn(emptyList())

        val result = repository.computePersonalRecords(1L)

        assertNull(result.bestE1RM)
        assertNull(result.bestSetWeight)
        assertNull(result.bestSessionVolume)
        assertNull(result.bestTotalReps)
    }

    @Test
    fun `computePersonalRecords - single set - returns that set as all PRs`() = runTest {
        val ts = 1_700_000_000_000L
        whenever(mockWorkoutSetDao.getAllSetsForExercisePRs(1L)).thenReturn(
            listOf(ExercisePRSet(weight = 100.0, reps = 5, timestamp = ts, setVolume = 500.0))
        )

        val result = repository.computePersonalRecords(1L)

        assertNotNull(result.bestE1RM)
        assertTrue(result.bestE1RM!! > 100.0) // Epley adds reps/30 bonus
        assertEquals(100.0, result.bestSetWeight!!, 0.01)
        assertEquals(5, result.bestSetReps)
        assertEquals(ts, result.bestE1RMTimestampMs)
        assertEquals(500.0, result.bestSessionVolume!!, 0.01)
        assertEquals(5, result.bestTotalReps)
    }

    @Test
    fun `computePersonalRecords - multiple sessions - correctly attributes PRs to different sessions`() = runTest {
        val ts1 = 1_700_000_000_000L
        val ts2 = 1_700_100_000_000L
        whenever(mockWorkoutSetDao.getAllSetsForExercisePRs(1L)).thenReturn(listOf(
            // Session 1: high volume (many reps)
            ExercisePRSet(weight = 60.0, reps = 15, timestamp = ts1, setVolume = 900.0),
            ExercisePRSet(weight = 60.0, reps = 15, timestamp = ts1, setVolume = 900.0),
            // Session 2: high weight (best e1RM)
            ExercisePRSet(weight = 140.0, reps = 3, timestamp = ts2, setVolume = 420.0)
        ))

        val result = repository.computePersonalRecords(1L)

        // e1RM PR comes from session 2 (heavy weight)
        assertEquals(ts2, result.bestE1RMTimestampMs)
        // Volume PR comes from session 1 (2 × 900 = 1800 vs 420)
        assertEquals(ts1, result.bestSessionTimestampMs)
        // Best total reps comes from session 1 (30 vs 3)
        assertEquals(ts1, result.bestTotalRepsTimestampMs)
        assertEquals(30, result.bestTotalReps)
    }

    @Test
    fun `computePersonalRecords - e1RM calculation uses Epley formula`() = runTest {
        val ts = 1_700_000_000_000L
        whenever(mockWorkoutSetDao.getAllSetsForExercisePRs(1L)).thenReturn(
            listOf(ExercisePRSet(weight = 100.0, reps = 10, timestamp = ts, setVolume = 1000.0))
        )

        val result = repository.computePersonalRecords(1L)

        // Epley: 100 * (1 + 10/30) = 100 * 1.333 = 133.33
        assertEquals(133.33, result.bestE1RM!!, 0.5)
    }

    // ── computeOverloadSuggestion ─────────────────────────────────────────────

    @Test
    fun `computeOverloadSuggestion - no history - returns NoData`() = runTest {
        whenever(mockWorkoutSetDao.getPreviousSessionCompletedSets(1L, Long.MAX_VALUE)).thenReturn(emptyList())

        val result = repository.computeOverloadSuggestion(1L)

        assertEquals(OverloadSuggestion.NoData, result)
    }

    @Test
    fun `computeOverloadSuggestion - average reps below 12 - suggests rep increase`() = runTest {
        val sets = listOf(
            makeWorkoutSet(weight = 80.0, reps = 10),
            makeWorkoutSet(weight = 80.0, reps = 10),
            makeWorkoutSet(weight = 80.0, reps = 10)
        )
        whenever(mockWorkoutSetDao.getPreviousSessionCompletedSets(1L, Long.MAX_VALUE)).thenReturn(sets)

        val result = repository.computeOverloadSuggestion(1L)

        assertTrue(result is OverloadSuggestion.IncreaseReps)
        val inc = result as OverloadSuggestion.IncreaseReps
        assertEquals(10, inc.currentReps)
        assertEquals(11, inc.targetReps)
        assertEquals(3, inc.targetSets)
    }

    @Test
    fun `computeOverloadSuggestion - average reps at or above 12 - suggests weight increase and resets reps to 8`() = runTest {
        val sets = listOf(
            makeWorkoutSet(weight = 80.0, reps = 12),
            makeWorkoutSet(weight = 80.0, reps = 13)
        )
        whenever(mockWorkoutSetDao.getPreviousSessionCompletedSets(1L, Long.MAX_VALUE)).thenReturn(sets)

        val result = repository.computeOverloadSuggestion(1L)

        assertTrue(result is OverloadSuggestion.IncreaseWeight)
        val inc = result as OverloadSuggestion.IncreaseWeight
        assertEquals(8, inc.targetReps)
        assertTrue(inc.suggestedWeight > inc.currentWeight)
    }

    // ── computeWarmUpRamp ─────────────────────────────────────────────────────

    @Test
    fun `computeWarmUpRamp - no history - returns empty list`() = runTest {
        whenever(mockWorkoutSetDao.getPreviousSessionCompletedSets(1L, Long.MAX_VALUE)).thenReturn(emptyList())

        val result = repository.computeWarmUpRamp(1L)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `computeWarmUpRamp - working weight 100kg - generates 4 sets with correct percentages`() = runTest {
        val sets = listOf(
            makeWorkoutSet(weight = 100.0, reps = 8),
            makeWorkoutSet(weight = 100.0, reps = 8)
        )
        whenever(mockWorkoutSetDao.getPreviousSessionCompletedSets(1L, Long.MAX_VALUE)).thenReturn(sets)

        val result = repository.computeWarmUpRamp(1L)

        assertEquals(4, result.size)
        assertEquals("50%", result[0].percentageLabel)
        assertEquals(8, result[0].reps)
        assertEquals("70%", result[1].percentageLabel)
        assertEquals(5, result[1].reps)
        assertEquals("85%", result[2].percentageLabel)
        assertEquals(3, result[2].reps)
        assertEquals("100%", result[3].percentageLabel)
        // All weights should be rounded to nearest 2.5
        result.dropLast(1).forEach { set ->
            assertTrue("weight ${set.weight} not multiple of 2.5", set.weight % 2.5 < 0.01)
        }
    }

    @Test
    fun `computeWarmUpRamp - bodyweight exercise (weight 0) - returns empty list`() = runTest {
        val sets = listOf(makeWorkoutSet(weight = 0.0, reps = 12))
        whenever(mockWorkoutSetDao.getPreviousSessionCompletedSets(1L, Long.MAX_VALUE)).thenReturn(sets)

        val result = repository.computeWarmUpRamp(1L)

        assertTrue(result.isEmpty())
    }

    // ── findAlternatives ─────────────────────────────────────────────────────

    @Test
    fun `findAlternatives - familyId match outranks muscle group match`() = runTest {
        val target = makeExercise(id = 1, familyId = "squat_family", muscleGroup = "Quads", equipment = "Barbell")
        val sameFamily = makeExercise(id = 2, familyId = "squat_family", muscleGroup = "Quads", equipment = "Dumbbell")
        val sameMuscle = makeExercise(id = 3, familyId = null, muscleGroup = "Quads", equipment = "Machine")

        whenever(mockExerciseDao.getAllExercisesSync()).thenReturn(listOf(sameFamily, sameMuscle))
        whenever(mockWorkoutSetDao.getExerciseSessionCount(2L)).thenReturn(0)
        whenever(mockWorkoutSetDao.getExerciseSessionCount(3L)).thenReturn(0)
        whenever(mockWorkoutSetDao.getHistoricalBestE1RM(any(), any())).thenReturn(null)

        val result = repository.findAlternatives(target)

        assertEquals(2L, result.first().exercise.id)
    }

    @Test
    fun `findAlternatives - different muscle group - excluded from results`() = runTest {
        val target = makeExercise(id = 1, muscleGroup = "Chest", equipment = "Barbell")
        val different = makeExercise(id = 2, muscleGroup = "Back", equipment = "Barbell")

        whenever(mockExerciseDao.getAllExercisesSync()).thenReturn(listOf(different))

        val result = repository.findAlternatives(target)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `findAlternatives - user has history - no estimated starting weight`() = runTest {
        val target = makeExercise(id = 1, muscleGroup = "Biceps", equipment = "Barbell")
        val alt = makeExercise(id = 2, muscleGroup = "Biceps", equipment = "Dumbbell")

        whenever(mockExerciseDao.getAllExercisesSync()).thenReturn(listOf(alt))
        whenever(mockWorkoutSetDao.getExerciseSessionCount(2L)).thenReturn(5)

        val result = repository.findAlternatives(target)

        assertNull(result.first().estimatedStartingWeight)
    }

    @Test
    fun `findAlternatives - user has no history on alternative - estimates starting weight from main exercise e1RM`() = runTest {
        // Main exercise (being viewed): Barbell Curl — user HAS history (e1RM = 100 kg)
        val target = makeExercise(id = 1, muscleGroup = "Biceps", equipment = "Barbell")
        // Alternative: Dumbbell Curl — user has NO history
        val alt = makeExercise(id = 2, muscleGroup = "Biceps", equipment = "Dumbbell")

        whenever(mockExerciseDao.getAllExercisesSync()).thenReturn(listOf(alt))
        whenever(mockWorkoutSetDao.getExerciseSessionCount(2L)).thenReturn(0) // alt has no history
        whenever(mockWorkoutSetDao.getHistoricalBestE1RM(eq(1L), any())).thenReturn(100.0) // main has history

        val result = repository.findAlternatives(target)

        // Source: Barbell (main exercise, e1RM = 100 kg)
        // Target: Dumbbell (alternative) → Barbell→Dumbbell ratio = 0.35
        // Estimate = 100 * 0.35 * 0.80 = 28 → rounded to 27.5
        val estimated = result.first().estimatedStartingWeight
        assertNotNull(estimated)
        assertTrue("Expected ~27.5, got $estimated", estimated!! in 25.0..32.5)
    }

    // ── getLatestBodyWeightKg ─────────────────────────────────────────────────

    @Test
    fun `getLatestBodyWeightKg - no entry - returns null`() = runTest {
        whenever(mockMetricLogRepository.getLatestForType(MetricType.WEIGHT)).thenReturn(null)

        assertNull(repository.getLatestBodyWeightKg())
    }

    @Test
    fun `getLatestBodyWeightKg - entry exists - returns value`() = runTest {
        val entry = MetricLog(id = 1, timestamp = System.currentTimeMillis(), type = MetricType.WEIGHT, value = 82.5)
        whenever(mockMetricLogRepository.getLatestForType(MetricType.WEIGHT)).thenReturn(entry)

        assertEquals(82.5, repository.getLatestBodyWeightKg()!!, 0.01)
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun makeWorkoutSet(weight: Double, reps: Int) =
        com.powerme.app.data.database.WorkoutSet(
            id = java.util.UUID.randomUUID().toString(),
            workoutId = "w1",
            exerciseId = 1L,
            setOrder = 0,
            weight = weight,
            reps = reps,
            setType = com.powerme.app.data.database.SetType.NORMAL,
            isCompleted = true
        )

    private fun makeExercise(
        id: Long,
        name: String = "Exercise $id",
        muscleGroup: String = "Chest",
        equipment: String = "Barbell",
        familyId: String? = null,
        exerciseType: ExerciseType = ExerciseType.STRENGTH
    ) = Exercise(
        id = id,
        name = name,
        muscleGroup = muscleGroup,
        equipmentType = equipment,
        familyId = familyId,
        exerciseType = exerciseType
    )
}
