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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.powerme.app.analytics.ReadinessEngine
import com.powerme.app.ui.metrics.charts.E1RMProgressionCard
import com.powerme.app.ui.metrics.charts.EffectiveSetsCard
import com.powerme.app.ui.metrics.charts.MuscleGroupVolumeCard
import com.powerme.app.ui.metrics.charts.VolumeTrendCard

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
        VolumeTrendCard(
            volumeData = weeklyVolume,
            timeRange = timeRange,
            unitSystem = unitSystem,
            onTimeRangeChange = trendsViewModel::setTimeRange,
            modelProducer = trendsViewModel.volumeModelProducer
        )

        // ── E1RM Progression ───────────────────────────
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

        // ── Muscle Balance ─────────────────────────────
        MuscleGroupVolumeCard(
            muscleGroupData = muscleGroupVolume,
            timeRange = timeRange,
            onTimeRangeChange = trendsViewModel::setTimeRange,
            modelProducer = trendsViewModel.muscleGroupModelProducer
        )

        // ── Effective Sets ────────────────────────────
        EffectiveSetsCard(
            effectiveSetsData = effectiveSets,
            coveragePct = effectiveSetsCoverage,
            timeRange = timeRange,
            onTimeRangeChange = trendsViewModel::setTimeRange,
            modelProducer = trendsViewModel.effectiveSetsModelProducer
        )
    }
}
