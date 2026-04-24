package com.powerme.app.ui.exercises.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.SubcomposeAsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import com.powerme.app.data.database.Exercise
import com.powerme.app.data.database.Joint
import com.powerme.app.ui.theme.ReadinessAmber
import kotlinx.coroutines.launch
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseDetailScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToWorkout: (String) -> Unit = {},
    onNavigateToExerciseDetail: (Long) -> Unit = {},
    viewModel: ExerciseDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val affectedJoints by viewModel.affectedJoints.collectAsState()
    val exercise = uiState.exercise

    val pagerState = rememberPagerState(pageCount = { 4 })
    val coroutineScope = rememberCoroutineScope()

    val tabTitles = listOf("ABOUT", "HISTORY", "CHARTS", "RECORDS")

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = exercise?.name ?: "",
                        maxLines = 1,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (exercise != null) {
                        IconButton(onClick = viewModel::toggleFavorite) {
                            Icon(
                                imageVector = if (exercise.isFavorite) Icons.Filled.Star else Icons.Outlined.Star,
                                contentDescription = if (exercise.isFavorite) "Remove from favourites" else "Add to favourites",
                                tint = if (exercise.isFavorite) ReadinessAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        if (uiState.isLoading || exercise == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Pinned: Hero animation
            ExerciseAnimationImage(exercise)

            // Pinned: Header
            HeaderSection(uiState = uiState, affectedJoints = affectedJoints)

            // Tab row
            SecondaryTabRow(selectedTabIndex = pagerState.currentPage) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(index)
                            }
                        },
                        text = {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (pagerState.currentPage == index)
                                    FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            // Tab content via HorizontalPager
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 0
            ) { page ->
                when (page) {
                    0 -> AboutTabContent(
                        uiState = uiState,
                        affectedJoints = affectedJoints,
                        onNavigateToExerciseDetail = onNavigateToExerciseDetail,
                        onNavigateToRecordsTab = {
                            coroutineScope.launch { pagerState.animateScrollToPage(3) }
                        },
                        onUserNoteChanged = viewModel::onUserNoteChanged
                    )
                    1 -> HistoryTabContent(
                        history = uiState.workoutHistory,
                        hasMore = uiState.hasMoreHistory,
                        onLoadMore = viewModel::loadMoreHistory,
                        onWorkoutClick = onNavigateToWorkout
                    )
                    2 -> ChartsTabContent(
                        trendData = uiState.trendData,
                        timeRange = uiState.timeRange,
                        onTimeRangeChanged = viewModel::onTimeRangeChanged,
                        e1rmProducer = viewModel.e1rmProducer,
                        maxWeightProducer = viewModel.maxWeightProducer,
                        volumeProducer = viewModel.volumeProducer,
                        bestSetProducer = viewModel.bestSetProducer,
                        rpeProducer = viewModel.rpeProducer
                    )
                    3 -> RecordsTabContent(
                        prs = uiState.personalRecords,
                        userBodyWeightKg = uiState.userBodyWeightKg,
                        overloadSuggestion = uiState.overloadSuggestion
                    )
                }
            }
        }
    }
}

// ── Pinned: Hero Animation ──────────────────────────────────────────────────

@Composable
private fun ExerciseAnimationImage(exercise: Exercise) {
    val context = LocalContext.current
    SubcomposeAsyncImage(
        model = ImageRequest.Builder(context)
            .data("file:///android_asset/exercise_animations/${exercise.searchName}.webp")
            .crossfade(true)
            .build(),
        contentDescription = "${exercise.name} demonstration",
        imageLoader = context.imageLoader,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 220.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        error = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.FitnessCenter,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}

// ── Pinned: Header ──────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeaderSection(uiState: ExerciseDetailUiState, affectedJoints: Set<Joint>) {
    val exercise = uiState.exercise ?: return
    val hasInjuryWarning = remember(exercise, affectedJoints) {
        (Joint.fromJsonString(exercise.primaryJoints) +
                Joint.fromJsonString(exercise.secondaryJoints)).any { it in affectedJoints }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Injury warning banner
        if (hasInjuryWarning) {
            Surface(
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "You have an active health note for a joint used in this exercise.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        // Exercise type + metadata tags
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            TagChip(exercise.muscleGroup, MaterialTheme.colorScheme.primary)
            TagChip(exercise.equipmentType, MaterialTheme.colorScheme.secondary)
            TagChip(
                exercise.exerciseType.name.lowercase().replaceFirstChar { it.uppercase() },
                MaterialTheme.colorScheme.tertiary
            )
        }

        // Last performed + session frequency
        val lastPerformed = uiState.lastPerformed
        val sessionCount = uiState.sessionCount
        if (lastPerformed != null || sessionCount > 0) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (lastPerformed != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Schedule,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Last: ${dateFormat.format(java.util.Date(lastPerformed.timestampMs))}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (sessionCount > 0) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Repeat,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "$sessionCount session${if (sessionCount != 1) "s" else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
