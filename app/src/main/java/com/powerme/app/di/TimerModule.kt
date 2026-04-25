package com.powerme.app.di

import android.content.Context
import com.powerme.app.data.AppSettingsDataStore
import com.powerme.app.util.RestTimerNotifier
import com.powerme.app.util.TimerSound
import com.powerme.app.util.timer.TimerEngine
import com.powerme.app.util.timer.TimerEngineImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Singleton TimerEngine shared between FunctionalBlockRunner and WorkoutTimerService.
 * The shared instance enforces single-active-timer via FunctionalBlockRunner.isActive.
 */
@Module
@InstallIn(SingletonComponent::class)
object TimerModule {

    @Provides
    @Singleton
    @Named("functionalBlockNotifier")
    fun provideFunctionalBlockNotifier(@ApplicationContext ctx: Context): RestTimerNotifier =
        RestTimerNotifier(ctx)

    @Provides
    @Singleton
    fun provideTimerEngine(
        @Named("functionalBlockNotifier") notifier: RestTimerNotifier,
        appSettingsDataStore: AppSettingsDataStore,
    ): TimerEngine = TimerEngineImpl(notifier) {
        runBlocking { appSettingsDataStore.timerSound.first() }
    }
}
