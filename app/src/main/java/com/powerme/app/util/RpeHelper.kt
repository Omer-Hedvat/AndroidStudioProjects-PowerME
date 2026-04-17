package com.powerme.app.util

enum class RpeCategory { LOW, MODERATE, GOLDEN, MAX_EFFORT }

fun rpeCategory(rpeValue: Int): RpeCategory = when {
    rpeValue < 70  -> RpeCategory.LOW
    rpeValue < 80  -> RpeCategory.MODERATE
    rpeValue <= 90 -> RpeCategory.GOLDEN
    else           -> RpeCategory.MAX_EFFORT
}

data class RpeInfo(
    val value: Int,
    val display: String,
    val description: String,
    val category: RpeCategory
)

val RPE_SCALE: List<RpeInfo> = listOf(
    RpeInfo(60,  "6",   "4+ reps left \u2014 very light",       RpeCategory.LOW),
    RpeInfo(65,  "6.5", "3\u20134 reps left \u2014 light",      RpeCategory.LOW),
    RpeInfo(70,  "7",   "3 reps left \u2014 moderate",          RpeCategory.MODERATE),
    RpeInfo(75,  "7.5", "2\u20133 reps left \u2014 challenging", RpeCategory.MODERATE),
    RpeInfo(80,  "8",   "2 reps left \u2014 hard",              RpeCategory.GOLDEN),
    RpeInfo(85,  "8.5", "1\u20132 reps left \u2014 very hard",  RpeCategory.GOLDEN),
    RpeInfo(90,  "9",   "1 rep left \u2014 near max",           RpeCategory.GOLDEN),
    RpeInfo(95,  "9.5", "Maybe 1 rep left \u2014 grinding",     RpeCategory.MAX_EFFORT),
    RpeInfo(100, "10",  "Absolute failure \u2014 nothing left",  RpeCategory.MAX_EFFORT),
)

fun RpeCategory.displayLabel(): String = when (this) {
    RpeCategory.LOW        -> "WARM-UP ZONE"
    RpeCategory.MODERATE   -> "WORKING ZONE"
    RpeCategory.GOLDEN     -> "GOLDEN ZONE \u2736"
    RpeCategory.MAX_EFFORT -> "MAX EFFORT"
}
