package com.powerme.app.ai

interface WorkoutTextParser {
    suspend fun parseWorkoutText(userInput: String, exerciseNames: List<String>): ParseResult
}
