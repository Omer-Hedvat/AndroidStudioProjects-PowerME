package com.powerme.app.data.repository

import com.powerme.app.data.database.Exercise
import com.powerme.app.data.database.ExerciseType
import org.junit.Assert.*
import org.junit.Test

class MovementTransferTableTest {

    // ── ratio() ────────────────────────────────────────────────────────────────

    @Test
    fun `ratio - same exercise id - returns 1_0`() {
        val ex = makeExercise(id = 1, name = "Barbell Back Squat", familyId = "squat_family")
        assertEquals(1.0, MovementTransferTable.ratio(ex, ex), 0.001)
    }

    @Test
    fun `ratio - same family back squat to front squat - returns approx 0_80`() {
        val source = makeExercise(1, "Barbell Back Squat", "squat_family")
        val target = makeExercise(2, "Front Squat", "squat_family")
        // targetFactor 0.80 / sourceFactor 1.00 = 0.80
        assertEquals(0.80, MovementTransferTable.ratio(source, target), 0.001)
    }

    @Test
    fun `ratio - same family front squat to back squat - inverse greater than 1`() {
        val source = makeExercise(1, "Front Squat", "squat_family")
        val target = makeExercise(2, "Barbell Back Squat", "squat_family")
        // 1.00 / 0.80 = 1.25
        assertEquals(1.25, MovementTransferTable.ratio(source, target), 0.01)
    }

    @Test
    fun `ratio - cross family squat to bench - returns 1_0`() {
        val source = makeExercise(1, "Barbell Back Squat", "squat_family")
        val target = makeExercise(2, "Barbell Flat Bench Press", "bench_family")
        assertEquals(1.0, MovementTransferTable.ratio(source, target), 0.001)
    }

    @Test
    fun `ratio - null family source - returns 1_0`() {
        val source = makeExercise(1, "Some Exercise", null)
        val target = makeExercise(2, "Front Squat", "squat_family")
        assertEquals(1.0, MovementTransferTable.ratio(source, target), 0.001)
    }

    @Test
    fun `ratio - null family target - returns 1_0`() {
        val source = makeExercise(1, "Barbell Back Squat", "squat_family")
        val target = makeExercise(2, "Unknown Exercise", null)
        assertEquals(1.0, MovementTransferTable.ratio(source, target), 0.001)
    }

    @Test
    fun `ratio - exercise not in seed table defaults to 1_0`() {
        val source = makeExercise(1, "Barbell Back Squat", "squat_family")
        val target = makeExercise(2, "Zombie Squat", "squat_family")
        // "zombie squat" not in seed → factorFor returns 1.0 → 1.0/1.0 = 1.0
        assertEquals(1.0, MovementTransferTable.ratio(source, target), 0.001)
    }

    @Test
    fun `ratio - both exercises not in seed table - returns 1_0`() {
        val source = makeExercise(1, "Mystery Lift A", "squat_family")
        val target = makeExercise(2, "Mystery Lift B", "squat_family")
        assertEquals(1.0, MovementTransferTable.ratio(source, target), 0.001)
    }

    @Test
    fun `ratio - same family bench conventional to incline - returns below 1`() {
        val source = makeExercise(1, "Barbell Flat Bench Press", "bench_family")
        val target = makeExercise(2, "Incline Barbell Bench Press", "bench_family")
        // 0.85 / 1.00 = 0.85
        assertEquals(0.85, MovementTransferTable.ratio(source, target), 0.001)
    }

    @Test
    fun `ratio - deadlift conventional to rack pull - returns above 1`() {
        val source = makeExercise(1, "Conventional Deadlift", "deadlift_family")
        val target = makeExercise(2, "Rack Pull", "deadlift_family")
        // 1.20 / 1.00 = 1.20
        assertEquals(1.20, MovementTransferTable.ratio(source, target), 0.001)
    }

    // ── factorFor() ────────────────────────────────────────────────────────────

    @Test
    fun `factorFor - null family - returns 1_0`() {
        assertEquals(1.0, MovementTransferTable.factorFor(null, "Barbell Back Squat"), 0.001)
    }

    @Test
    fun `factorFor - unknown family - returns 1_0`() {
        assertEquals(1.0, MovementTransferTable.factorFor("unknown_family", "Any Name"), 0.001)
    }

    @Test
    fun `factorFor - case insensitive match - returns correct factor`() {
        assertEquals(1.0, MovementTransferTable.factorFor("squat_family", "BARBELL BACK SQUAT"), 0.001)
        assertEquals(0.80, MovementTransferTable.factorFor("squat_family", "Front Squat"), 0.001)
        assertEquals(0.80, MovementTransferTable.factorFor("squat_family", "FRONT SQUAT"), 0.001)
        assertEquals(0.80, MovementTransferTable.factorFor("squat_family", "front squat"), 0.001)
    }

    @Test
    fun `factorFor - squat family anchors - back squat is 1_0`() {
        assertEquals(1.0, MovementTransferTable.factorFor("squat_family", "Barbell Back Squat"), 0.001)
    }

    @Test
    fun `factorFor - pullup family chin-up slightly above pull-up`() {
        val pullUp = MovementTransferTable.factorFor("pullup_family", "Pull-Up")
        val chinUp = MovementTransferTable.factorFor("pullup_family", "Chin-Up")
        assertTrue("Chin-Up factor should exceed Pull-Up factor", chinUp > pullUp)
    }

    // ── seedKeys_matchMasterExerciseJson ──────────────────────────────────────

    @Test
    fun `seedKeys - all seed exercise names exist in master_exercises json`() {
        // Locate the JSON via the filesystem — works from JVM unit tests since we know the project root.
        val candidates = listOf(
            java.io.File("src/main/res/raw/master_exercises.json"),
            java.io.File("app/src/main/res/raw/master_exercises.json"),
        )
        val jsonFile = candidates.firstOrNull { it.exists() } ?: return // skip if path not resolvable
        val jsonText = jsonFile.readText()

        val namePattern = Regex(""""name"\s*:\s*"([^"]+)"""")
        val jsonNames = namePattern.findAll(jsonText).map { it.groupValues[1].lowercase(java.util.Locale.ROOT) }.toSet()

        val seedSamples = listOf(
            "squat_family" to "barbell back squat",
            "squat_family" to "front squat",
            "squat_family" to "bulgarian split squat",
            "squat_family" to "pistol squat",
            "squat_family" to "hack squat",
            "squat_family" to "leg press",
            "bench_family" to "barbell flat bench press",
            "bench_family" to "incline barbell bench press",
            "bench_family" to "close-grip bench press",
            "deadlift_family" to "conventional deadlift",
            "deadlift_family" to "romanian deadlift (rdl) - bb",
            "deadlift_family" to "trap bar deadlift",
            "deadlift_family" to "rack pull",
            "deadlift_family" to "good morning",
            "overhead_press_family" to "standing barbell overhead press",
            "overhead_press_family" to "arnold press",
            "pullup_family" to "pull-up",
            "pullup_family" to "chin-up",
            "pullup_family" to "lat pulldown",
            "row_family" to "barbell row",
            "row_family" to "pendlay row",
            "pushup_family" to "archer push-up",
            "olympic_family" to "clean and jerk",
            "olympic_family" to "power clean",
            "leg_curl_family" to "lying leg curl",
            "leg_curl_family" to "nordic curl",
        )

        val missing = seedSamples.filter { (_, name) -> name !in jsonNames }
        assertTrue(
            "Seed names not found in master_exercises.json (possible rename?): $missing",
            missing.isEmpty()
        )
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun makeExercise(id: Long, name: String, familyId: String?) = Exercise(
        id = id,
        name = name,
        muscleGroup = "Legs",
        equipmentType = "Barbell",
        familyId = familyId,
        exerciseType = ExerciseType.STRENGTH,
    )
}
