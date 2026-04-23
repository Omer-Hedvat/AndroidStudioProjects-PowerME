package com.powerme.app.di

import com.powerme.app.ai.GeminiWorkoutParser
import com.powerme.app.ai.WorkoutParserRouter
import com.powerme.app.ai.WorkoutTextParser
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AiModule {

    @Binds
    @Singleton
    abstract fun bindWorkoutTextParser(impl: WorkoutParserRouter): WorkoutTextParser

    companion object {
        @Provides
        @Singleton
        @Named("cloud")
        fun provideCloudParser(gemini: GeminiWorkoutParser): WorkoutTextParser = object : WorkoutTextParser {
            override suspend fun parseWorkoutText(userInput: String, exerciseNames: List<String>) =
                gemini.parseWorkoutText(userInput, exerciseNames)
        }
    }
}
