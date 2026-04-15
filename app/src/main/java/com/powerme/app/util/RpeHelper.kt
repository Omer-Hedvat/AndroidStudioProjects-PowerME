package com.powerme.app.util

enum class RpeCategory { LOW, MODERATE, GOLDEN, MAX_EFFORT }

fun rpeCategory(rpeValue: Int): RpeCategory = when {
    rpeValue < 70  -> RpeCategory.LOW
    rpeValue < 80  -> RpeCategory.MODERATE
    rpeValue <= 90 -> RpeCategory.GOLDEN
    else           -> RpeCategory.MAX_EFFORT
}
