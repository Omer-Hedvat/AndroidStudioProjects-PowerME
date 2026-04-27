package com.powerme.app.data.database

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseStressVectorSeedDataTest {

    private val vectors = ExerciseStressVectorSeedData.vectors

    @Test
    fun `all 240 exercises are present`() {
        assertEquals(240, vectors.size)
    }

    @Test
    fun `all coefficients are in range 0_0 to 1_0`() {
        val outOfRange = vectors.flatMap { (name, regions) ->
            regions.filter { (_, coeff) -> coeff < 0.0 || coeff > 1.0 }
                .map { (region, coeff) -> "$name / $region = $coeff" }
        }
        assertTrue(
            "Coefficients out of [0.0, 1.0] range: $outOfRange",
            outOfRange.isEmpty()
        )
    }

    @Test
    fun `no duplicate exercise-region pairs within same exercise`() {
        val duplicates = mutableListOf<String>()
        for ((name, regions) in vectors) {
            val seen = mutableSetOf<BodyRegion>()
            for ((region, _) in regions) {
                if (!seen.add(region)) {
                    duplicates += "$name / $region"
                }
            }
        }
        assertTrue("Duplicate (exercise, region) pairs found: $duplicates", duplicates.isEmpty())
    }

    @Test
    fun `all bodyRegion values are valid BodyRegion enum names`() {
        val validNames = BodyRegion.entries.map { it.name }.toSet()
        val invalid = vectors.flatMap { (name, regions) ->
            regions.filter { (region, _) -> region.name !in validNames }
                .map { (region, _) -> "$name / ${region.name}" }
        }
        assertTrue("Invalid BodyRegion values: $invalid", invalid.isEmpty())
    }

    @Test
    fun `each exercise has at least one vector`() {
        val empty = vectors.filter { (_, regions) -> regions.isEmpty() }.keys
        assertTrue("Exercises with no vectors: $empty", empty.isEmpty())
    }

    @Test
    fun `total vector count is within expected range`() {
        val total = vectors.values.sumOf { it.size }
        // 240 exercises × average 3–8 regions each: expect 720–1920 rows
        assertTrue("Total vector count $total is too low (expected >= 720)", total >= 720)
        assertTrue("Total vector count $total is too high (expected <= 1920)", total <= 1920)
    }

    @Test
    fun `key exercises are present`() {
        val required = listOf(
            "Barbell Back Squat",
            "Conventional Deadlift",
            "Barbell Flat Bench Press",
            "Standing Barbell Overhead Press",
            "Pull-Up",
            "Hip Thrust",
            "Nordic Curl"
        )
        val missing = required.filter { it !in vectors }
        assertTrue("Missing required exercises: $missing", missing.isEmpty())
    }

    @Test
    fun `squat stresses knee and quads at high coefficients`() {
        val squatVectors = vectors["Barbell Back Squat"] ?: error("Squat not in seed data")
        val kneeCoeff = squatVectors.firstOrNull { it.first == BodyRegion.KNEE_JOINT }?.second
        val quadCoeff = squatVectors.firstOrNull { it.first == BodyRegion.QUADS }?.second
        assertTrue("Squat should stress knee joint >= 0.7", (kneeCoeff ?: 0.0) >= 0.7)
        assertTrue("Squat should stress quads >= 0.7", (quadCoeff ?: 0.0) >= 0.7)
    }

    @Test
    fun `deadlift stresses lower back at high coefficient`() {
        val deadliftVectors = vectors["Conventional Deadlift"] ?: error("Deadlift not in seed data")
        val lowerBackCoeff = deadliftVectors.firstOrNull { it.first == BodyRegion.LOWER_BACK }?.second
        assertTrue("Deadlift should stress lower back >= 0.8", (lowerBackCoeff ?: 0.0) >= 0.8)
    }

    @Test
    fun `curl stresses elbow joint at high coefficient`() {
        val curlVectors = vectors["Barbell Curl"] ?: error("Barbell Curl not in seed data")
        val elbowCoeff = curlVectors.firstOrNull { it.first == BodyRegion.ELBOW_JOINT }?.second
        assertTrue("Curl should stress elbow >= 0.8", (elbowCoeff ?: 0.0) >= 0.8)
    }

    @Test
    fun `all 240 canonical exercise names from master library are present`() {
        val allExerciseNames = listOf(
            "Barbell Back Squat", "Front Squat", "Goblet Squat", "Bulgarian Split Squat",
            "Leg Press", "Hack Squat", "Safety Bar Squat", "Box Squat", "Pause Squat",
            "Sumo Squat", "Conventional Deadlift", "Romanian Deadlift (RDL) - BB",
            "Trap Bar Deadlift", "Sumo Deadlift", "Single-Leg RDL", "Deficit Deadlift",
            "Rack Pull", "Stiff-Leg Deadlift", "Barbell Flat Bench Press",
            "Incline Barbell Bench Press", "Decline Barbell Bench Press",
            "Dumbbell Flat Bench Press", "Incline Dumbbell Bench Press", "Cable Chest Fly",
            "Machine Chest Press", "Close-Grip Bench Press", "Push-Ups", "Barbell Row", "Dumbbell Row",
            "Chest-Supported Row", "Cable Row", "T-Bar Row", "Seal Row", "Pendlay Row",
            "Inverted Row", "Standing Barbell Overhead Press", "Seated Dumbbell Overhead Press",
            "Arnold Press", "Machine Shoulder Press", "Landmine Press", "Pike Push-Up",
            "Pull-Up", "Chin-Up", "Neutral Grip Pull-Up", "Lat Pulldown",
            "Close-Grip Lat Pulldown", "Straight-Arm Pulldown", "Assisted Pull-Up",
            "Lying Leg Curl", "Seated Leg Curl", "Nordic Curl", "Leg Extension",
            "Standing Calf Raise", "Seated Calf Raise", "Barbell Curl", "Dumbbell Curl",
            "Hammer Curl", "Cable Curl", "Tricep Pushdown", "Overhead Tricep Extension",
            "Skull Crusher", "Dips", "Lateral Raise", "Face Pull", "Rear Delt Fly", "Shrugs",
            "Plank", "Ab Wheel Rollout", "Cable Crunch", "Russian Twist", "Hanging Leg Raise",
            "Farmer's Walk", "Sled Push", "Sled Pull", "Battle Ropes", "Box Jump",
            "Medicine Ball Slam", "Broad Jump", "Russian Kettlebell Swing", "Turkish Get-Up",
            "Glute Bridge", "Hip Thrust", "Wall Sit", "Step-Up", "Reverse Lunge",
            "Walking Lunge", "Sissy Squat", "Good Morning", "Preacher Curl",
            "Concentration Curl", "Incline Curl", "Tricep Kickback", "Diamond Push-Up",
            "Bench Dips", "Upright Row", "Front Raise", "Cable Lateral Raise",
            "Reverse Pec Deck", "Adductor Machine", "Abductor Machine", "Donkey Calf Raise",
            "Wrist Curl", "Dead Hang", "Decline Dumbbell Press", "Pec Deck Fly",
            "Cable Crossover", "Ring Push-Up", "Weighted Push-Up", "Smith Machine Bench Press",
            "Dumbbell Pullover", "Decline Push-Up", "Dumbbell Chest Fly", "Dead Bug",
            "Pallof Press", "Dragon Flag", "Landmine Rotation", "Hollow Body Hold",
            "Side Plank", "Bicycle Crunch", "L-Sit", "Landmine Anti-Rotation Press",
            "Suitcase Carry", "EZ Bar Curl", "EZ Bar Skullcrusher",
            "Cable Overhead Tricep Extension", "Reverse Curl", "Rope Tricep Pushdown",
            "Zottman Curl", "Tate Press", "Machine Preacher Curl", "Cable Hammer Curl",
            "Spider Curl", "JM Press", "Romanian Deadlift (RDL) - DB", "Weighted Pull-Up",
            "Incline Dumbbell Row", "Single-Arm Cable Row", "Meadows Row",
            "Wide-Grip Lat Pulldown", "Cable Pullover", "Barbell Shrug",
            "Kneeling Single-Arm Lat Pulldown", "Seated Barbell Overhead Press", "Z-Press",
            "Cable Front Raise", "Machine Lateral Raise", "Cable Upright Row",
            "Band Pull-Apart", "Pistol Squat", "Single-Leg Press", "Belt Squat",
            "Leg Press Calf Raise", "Cable Pull-Through", "Landmine Squat", "Kettlebell Clean",
            "Kettlebell Press", "Kettlebell Snatch", "Kettlebell Row",
            "Kettlebell Romanian Deadlift", "Band Bicep Curl", "Band Tricep Pushdown",
            "Band Hip Thrust", "Band Lateral Walk", "Band Pull-Apart (Resistance Band)",
            "Smith Machine Squat", "Smith Machine Overhead Press",
            "Smith Machine Romanian Deadlift", "Smith Machine Bulgarian Split Squat",
            "Power Clean", "Barbell Snatch", "Clean and Jerk", "Muscle-Up (Rings)",
            "Handstand Push-Up", "Burpee (Standard)", "Toes-to-Bar", "Wall Ball Shot",
            "Kettlebell Thruster", "Overhead Squat", "Arch-Body Hold (Superman)",
            "Chest-to-Bar Pull-Up", "Ring Dip", "Front-Rack Reverse Lunge", "Box Step-Over",
            "Kettlebell Swing", "Bear Crawl", "Dumbbell Devil Press", "Tuck Sit",
            "Handstand Hold", "Pseudo-Planche Hold", "Horse Stance", "Duck Walk", "Bird-Dog",
            "Pallof Press (Iso-Band)", "Archer Push-up", "Typewriter Push-up",
            "Pseudo-Planche Push-up", "Clap Push-up", "Single-Arm Push-up",
            "Staggered-Hand Push-up", "Tiger Bend Push-up", "Bench Dip (Feet Elevated)",
            "Incline Push-up", "Hindu Push-up", "DB Around the World",
            "Around the World (Pull-up Bar)", "Negative Pull-up", "Commando Pull-up",
            "Archer Pull-up", "Australian Pull-up (Inverted Row)", "Face Pull (Resistance Band)",
            "L-Sit Pull-up", "Explosive Pull-up (Chest-to-Bar)", "Single-Arm Australian Row",
            "Scapular Pull-up", "Shrimp Squat", "Skater Lunge", "Curtsy Lunge",
            "Sissy Squat (Bodyweight)", "Reverse Nordic", "Nordic Hamstring Curl",
            "Cossack Squat", "Single-Leg Glute Bridge", "Hip Thrust (Couch/Bench)",
            "Calf Raise (Single-Leg)", "Tibialis Raise (Wall)", "Tuck Jump", "Hollow Rock",
            "V-Up", "Windshield Wiper (Floor)", "Windshield Wiper (Bar)",
            "Knee-to-Elbow (Bar)", "Mountain Climber", "Spiderman Plank", "Plank Shoulder Tap",
            "Double Under (Jump Rope)", "Single Under", "Burpee", "Shadow Boxing",
            "Farmer's Walk (Heavy Bags)", "Jumping Jacks"
        )
        val missing = allExerciseNames.filter { it !in vectors }
        assertTrue(
            "Exercises missing from seed data (${missing.size}): $missing",
            missing.isEmpty()
        )
    }
}
