package com.powerme.app.ui.metrics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.powerme.app.analytics.ReadinessEngine
import com.powerme.app.ui.metrics.charts.BodyCompositionCard
import com.powerme.app.ui.metrics.charts.BodyStressHeatmapCard
import com.powerme.app.ui.metrics.charts.ChronotypeCard
import com.powerme.app.ui.metrics.charts.E1RMProgressionCard
import com.powerme.app.ui.metrics.charts.EffectiveSetsCard
import com.powerme.app.ui.metrics.charts.MuscleGroupVolumeCard
import com.powerme.app.ui.metrics.charts.VolumeTrendCard
import com.powerme.app.ui.theme.PowerMeDefaults
import com.powerme.app.ui.theme.ProSubGrey

@Composable
fun MetricsScreen(
    onNavigateToSettings: () -> Unit = {},
    viewModel: MetricsViewModel = hiltViewModel(),
    trendsViewModel: TrendsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val unitSystem by viewModel.unitSystem.collectAsState()
    val readinessScore by trendsViewModel.readinessScore.collectAsState()
    val readinessSubMetrics by trendsViewModel.readinessSubMetrics.collectAsState()
    val weeklyVolume by trendsViewModel.weeklyVolume.collectAsState()
    val timeRange by trendsViewModel.timeRange.collectAsState()
    val e1rmData by trendsViewModel.e1rmData.collectAsState()
    val exercisePickerItems by trendsViewModel.exercisePickerItems.collectAsState()
    val selectedExerciseId by trendsViewModel.selectedExerciseId.collectAsState()
    val deepLinkPending by trendsViewModel.deepLinkPending.collectAsState()
    val muscleGroupVolume by trendsViewModel.muscleGroupVolume.collectAsState()
    val effectiveSets by trendsViewModel.effectiveSets.collectAsState()
    val effectiveSetsCoverage by trendsViewModel.effectiveSetsCoverage.collectAsState()
    val bodyComposition by trendsViewModel.bodyComposition.collectAsState()
    val chronotypeData by trendsViewModel.chronotypeData.collectAsState()
    val bodyStressMap by trendsViewModel.bodyStressMap.collectAsState()
    val selectedBodyRegion by trendsViewModel.selectedBodyRegion.collectAsState()
    val hasVolumeData by trendsViewModel.hasVolumeData.collectAsState()
    val hasE1rmData by trendsViewModel.hasE1rmData.collectAsState()
    val hasMuscleGroupData by trendsViewModel.hasMuscleGroupData.collectAsState()
    val hasEffectiveSetsData by trendsViewModel.hasEffectiveSetsData.collectAsState()
    val hasBodyCompositionData by trendsViewModel.hasBodyCompositionData.collectAsState()
    val hasChronotypeData by trendsViewModel.hasChronotypeData.collectAsState()

    // Auto-scroll to the E1RM card when arriving via a deep-link.
    val scrollState = rememberScrollState()
    var e1rmCardY by remember { mutableIntStateOf(0) }
    LaunchedEffect(deepLinkPending, e1rmCardY) {
        if (deepLinkPending && e1rmCardY > 0) {
            scrollState.animateScrollTo(e1rmCardY)
            trendsViewModel.consumeDeepLink()
        }
    }

    // Re-check HC permissions every time the tab becomes visible.
    // Needed because saveState/restoreState keeps the ViewModel alive across tab switches,
    // so init{} only runs once — permissions granted on another tab would otherwise be missed.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.loadBodyVitals()
                trendsViewModel.refreshReadiness()
                trendsViewModel.refreshBodyStressMap()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── Body & Vitals ──────────────────────────────
        BodyVitalsCard(
            state = uiState.bodyVitals,
            onSyncClick = { viewModel.syncHealthConnect() },
            onConnectClick = onNavigateToSettings,
            unitSystem = unitSystem
        )

        // ── Readiness Gauge ────────────────────────────
        ReadinessGaugeCard(
            readinessScore = readinessScore,
            hcAvailability = uiState.bodyVitals.hcAvailability,
            hrvDelta = readinessSubMetrics.hrvDelta,
            rhrDelta = readinessSubMetrics.rhrDelta,
            sleepMinutes = readinessSubMetrics.sleepMinutes
        )

        // ── Volume Trend ───────────────────────────────
        if (hasVolumeData) {
            VolumeTrendCard(
                volumeData = weeklyVolume,
                timeRange = timeRange,
                unitSystem = unitSystem,
                onTimeRangeChange = trendsViewModel::setTimeRange,
                modelProducer = trendsViewModel.volumeModelProducer
            )
        }

        // ── E1RM Progression ───────────────────────────
        if (hasE1rmData) {
            E1RMProgressionCard(
                e1rmData = e1rmData,
                exercisePickerItems = exercisePickerItems,
                selectedExerciseId = selectedExerciseId,
                unitSystem = unitSystem,
                onExerciseSelected = trendsViewModel::selectExercise,
                modelProducer = trendsViewModel.e1rmModelProducer,
                modifier = Modifier.onGloballyPositioned { coords ->
                    e1rmCardY = coords.positionInParent().y.toInt()
                }
            )
        }

        // ── Muscle Balance ─────────────────────────────
        if (hasMuscleGroupData) {
            MuscleGroupVolumeCard(
                muscleGroupData = muscleGroupVolume,
                timeRange = timeRange,
                unitSystem = unitSystem,
                onTimeRangeChange = trendsViewModel::setTimeRange,
                modelProducer = trendsViewModel.muscleGroupModelProducer
            )
        }

        // ── Effective Sets ────────────────────────────
        if (hasEffectiveSetsData) {
            EffectiveSetsCard(
                effectiveSetsData = effectiveSets,
                coveragePct = effectiveSetsCoverage,
                timeRange = timeRange,
                onTimeRangeChange = trendsViewModel::setTimeRange,
                modelProducer = trendsViewModel.effectiveSetsModelProducer
            )
        }

        // ── Body Stress Map ───────────────────────────
        BodyStressHeatmapCard(
            stressData = bodyStressMap,
            selectedRegion = selectedBodyRegion,
            onRegionTapped = { region -> trendsViewModel.selectBodyRegion(region) }
        )

        // ── Body Composition ──────────────────────────
        if (hasBodyCompositionData) {
            BodyCompositionCard(
                bodyCompositionData = bodyComposition,
                timeRange = timeRange,
                unitSystem = unitSystem,
                onTimeRangeChange = trendsViewModel::setTimeRange,
                modelProducer = trendsViewModel.bodyCompositionModelProducer
            )
        }

        // ── Chronotype ────────────────────────────────
        if (hasChronotypeData) {
            ChronotypeCard(
                chronotypeData = chronotypeData,
                timeRange = timeRange,
                unitSystem = unitSystem,
                onTimeRangeChange = trendsViewModel::setTimeRange,
                sleepModelProducer = trendsViewModel.sleepModelProducer
            )
        }

        // ── Hidden-cards info notice ──────────────────
        // Shown when ≥ 3 of the 6 data-driven cards are currently hidden.
        val hiddenCardCount = listOf(
            !hasVolumeData, !hasE1rmData, !hasMuscleGroupData,
            !hasEffectiveSetsData, !hasBodyCompositionData, !hasChronotypeData
        ).count { it }
        if (hiddenCardCount >= 3) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = PowerMeDefaults.cardColors(),
                elevation = PowerMeDefaults.cardElevation()
            ) {
                Text(
                    text = "Some charts are hidden — they appear once you have enough data logged.",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    fontSize = 13.sp,
                    color = ProSubGrey,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
