package com.powerme.app

import android.app.Application
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import com.powerme.app.data.csvimport.StrongCsvImporter
import com.powerme.app.data.database.DatabaseSeeder
import com.powerme.app.data.database.MasterExerciseSeeder
import com.powerme.app.data.database.StressVectorSeeder
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class PowerMeApplication : Application(), ImageLoaderFactory {

    @Inject
    lateinit var databaseSeeder: DatabaseSeeder

    @Inject
    lateinit var masterExerciseSeeder: MasterExerciseSeeder

    @Inject
    lateinit var strongCsvImporter: StrongCsvImporter

    @Inject
    lateinit var stressVectorSeeder: StressVectorSeeder

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                if (Build.VERSION.SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()
    }

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

            // Seed stress vectors for top 30 exercises (Body Heatmap P6 foundation)
            // Must run AFTER masterExerciseSeeder so exercise IDs are resolved correctly
            stressVectorSeeder.seedIfNeeded()

            // One-time import of Strong CSV export (runs once, then no-ops)
            strongCsvImporter.importIfNeeded()
        }
    }
}
