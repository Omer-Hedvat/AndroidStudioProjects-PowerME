package com.powerme.app.ai

import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class WorkoutParserRouter @Inject constructor(
    @Named("cloud") private val cloudParser: WorkoutTextParser
) : WorkoutTextParser {

    override suspend fun parseWorkoutText(userInput: String, exerciseNames: List<String>): ParseResult =
        cloudParser.parseWorkoutText(userInput, exerciseNames)
}
