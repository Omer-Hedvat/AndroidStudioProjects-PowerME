package com.powerme.app

import android.app.Application
import com.powerme.app.data.database.DatabaseSeeder
import com.powerme.app.data.database.MasterExerciseSeeder
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class PowerMeApplication : Application() {

    @Inject
    lateinit var databaseSeeder: DatabaseSeeder

    @Inject
    lateinit var masterExerciseSeeder: MasterExerciseSeeder

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        seedDatabase()
    }

    private fun seedDatabase() {
        applicationScope.launch {
            // Seed basic data (warmups, etc.)
            databaseSeeder.seedDatabaseIfNeeded()

            // Seed master exercises (150+ with YouTube)
            masterExerciseSeeder.seedIfNeeded()
        }
    }
}
