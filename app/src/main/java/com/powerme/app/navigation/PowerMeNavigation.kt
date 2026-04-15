package com.powerme.app.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.layout.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.firebase.auth.FirebaseAuth
import com.powerme.app.R
import com.powerme.app.ui.auth.AuthViewModel
import com.powerme.app.ui.auth.ForgotPasswordScreen
import com.powerme.app.ui.auth.ProfileSetupScreen
import com.powerme.app.ui.auth.WelcomeScreen
import com.powerme.app.ui.exercises.ExercisesScreen
import com.powerme.app.ui.history.HistoryScreen
import com.powerme.app.ui.history.WorkoutDetailScreen
import com.powerme.app.ui.history.WorkoutSummaryScreen
import com.powerme.app.ui.metrics.MetricsScreen
import com.powerme.app.ui.profile.ProfileScreen
import com.powerme.app.ui.settings.SettingsScreen
import com.powerme.app.ui.theme.ProBackground
import com.powerme.app.ui.tools.ToolsScreen
import com.powerme.app.ui.workout.ActiveWorkoutScreen
import com.powerme.app.ui.workouts.TemplateBuilderScreen
import com.powerme.app.ui.workouts.WorkoutsScreen
import com.powerme.app.ui.workout.WorkoutViewModel
import com.powerme.app.util.UserSessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Workouts  : Screen("workouts",  "Workouts",  Icons.Default.FitnessCenter)
    object Exercises : Screen("exercises", "Exercises", Icons.Default.DirectionsRun)
    object Tools     : Screen("tools",     "Clocks",    Icons.Default.Timer)
    object Trends    : Screen("trends",    "Trends",    Icons.Default.BarChart)
    object History   : Screen("history",   "History",   Icons.Default.History)
}

private object Routes {
    const val AUTH_WELCOME = "auth_welcome"
    const val AUTH_PROFILE_SETUP = "auth_profile_setup"
    const val AUTH_FORGOT_PASSWORD = "auth_forgot_password"
    const val WORKOUT = "workout"
    const val SETTINGS = "settings"
    const val PROFILE = "profile"
    const val WORKOUT_DETAIL = "workout_detail/{workoutId}"
    const val WORKOUT_SUMMARY = "workout_summary/{workoutId}?isPostWorkout={isPostWorkout}&syncType={syncType}"
    const val TEMPLATE_BUILDER = "template_builder/{routineId}"
    const val EXERCISE_PICKER = "exercise_picker"
}

/** Handles startup auth/session check to determine the initial navigation route. */
@HiltViewModel
class AppStartupViewModel @Inject constructor(
    private val userSessionManager: UserSessionManager
) : ViewModel() {

    private val _startRoute = MutableStateFlow<String?>(null)
    val startRoute: StateFlow<String?> = _startRoute.asStateFlow()

    init {
        viewModelScope.launch {
            val firebaseUser = FirebaseAuth.getInstance().currentUser
            _startRoute.value = when {
                firebaseUser == null || !firebaseUser.isEmailVerified -> Routes.AUTH_WELCOME
                else -> {
                    val dbUser = userSessionManager.getCurrentUser()
                    if (dbUser != null) Screen.Workouts.route else Routes.AUTH_PROFILE_SETUP
                }
            }
        }
    }
}

@Composable
fun PowerMeApp(startupViewModel: AppStartupViewModel = hiltViewModel()) {
    val startRoute by startupViewModel.startRoute.collectAsState()
    val navController = rememberNavController()
    val workoutViewModel: WorkoutViewModel = hiltViewModel()
    val workoutState by workoutViewModel.workoutState.collectAsState()
    val clocksTimerState by workoutViewModel.clocksTimerState.collectAsState()

    // Show splash loading until startup check completes
    if (startRoute == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ProBackground)
                .systemBarsPadding(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(modifier = Modifier.size(40.dp))
        }
        return
    }

    // Handle Maximization logic: if state changes to !minimized and we are NOT on workout route, navigate there
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    LaunchedEffect(workoutState.isMinimized) {
        if (!workoutState.isMinimized && workoutState.isActive && currentRoute != Routes.WORKOUT) {
            navController.navigate(Routes.WORKOUT)
        }
    }

    // Edit guard dialog — shown when user tries to edit a routine while a workout is active
    if (workoutState.showEditGuard) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { workoutViewModel.clearEditGuard() },
            title = { androidx.compose.material3.Text("Workout in Progress") },
            text = { androidx.compose.material3.Text("You have an active workout in progress. You must finish or cancel it before editing templates.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    workoutViewModel.clearEditGuard()
                    navController.navigate(Routes.WORKOUT)
                }) { androidx.compose.material3.Text("Go to Workout") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { workoutViewModel.clearEditGuard() }) {
                    androidx.compose.material3.Text("Cancel")
                }
            }
        )
    }

    NavHost(
        navController = navController,
        startDestination = startRoute!!
    ) {
        // ... (auth routes unchanged)
        
        composable(
            route = Routes.AUTH_WELCOME,
            enterTransition = { slideInHorizontally(tween(300)) { it } },
            exitTransition = { slideOutHorizontally(tween(300)) { -it / 3 } },
            popEnterTransition = { slideInHorizontally(tween(300)) { -it / 3 } },
            popExitTransition = { slideOutHorizontally(tween(300)) { it } }
        ) {
            WelcomeScreen(
                onSignedIn = {
                    navController.navigate(Screen.Workouts.route) {
                        popUpTo(Routes.AUTH_WELCOME) { inclusive = true }
                    }
                },
                onNeedsProfile = {
                    navController.navigate(Routes.AUTH_PROFILE_SETUP) {
                        popUpTo(Routes.AUTH_WELCOME) { inclusive = true }
                    }
                },
                onForgotPassword = { navController.navigate(Routes.AUTH_FORGOT_PASSWORD) }
            )
        }

        composable(
            route = Routes.AUTH_FORGOT_PASSWORD,
            enterTransition = { slideInHorizontally(tween(300)) { it } },
            exitTransition = { slideOutHorizontally(tween(300)) { -it / 3 } },
            popEnterTransition = { slideInHorizontally(tween(300)) { -it / 3 } },
            popExitTransition = { slideOutHorizontally(tween(300)) { it } }
        ) {
            val authViewModel: AuthViewModel = hiltViewModel(
                navController.getBackStackEntry(Routes.AUTH_WELCOME)
            )
            ForgotPasswordScreen(
                viewModel = authViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.AUTH_PROFILE_SETUP,
            enterTransition = { slideInHorizontally(tween(300)) { it } },
            exitTransition = { slideOutHorizontally(tween(300)) { -it / 3 } },
            popEnterTransition = { slideInHorizontally(tween(300)) { -it / 3 } },
            popExitTransition = { slideOutHorizontally(tween(300)) { it } }
        ) {
            ProfileSetupScreen(
                onProfileSaved = {
                    navController.navigate(Screen.Workouts.route) {
                        popUpTo(Routes.AUTH_PROFILE_SETUP) { inclusive = true }
                    }
                }
            )
        }

        // Full-screen workout
        composable(
            route = Routes.WORKOUT,
            enterTransition = { slideInVertically(tween(350, easing = FastOutSlowInEasing)) { it } },
            exitTransition = { slideOutVertically(tween(350)) { it } },
            popEnterTransition = { slideInVertically(tween(350, easing = FastOutSlowInEasing)) { it } },
            popExitTransition = { slideOutVertically(tween(350)) { it } }
        ) {
            ActiveWorkoutScreen(
                onWorkoutFinished = {
                    val finishedId = workoutViewModel.lastFinishedWorkoutId
                    val syncType = workoutViewModel.lastPendingRoutineSync?.name ?: "NONE"
                    if (finishedId != null) {
                        navController.navigate("workout_summary/$finishedId?isPostWorkout=true&syncType=$syncType") {
                            popUpTo(Routes.WORKOUT) { inclusive = true }
                        }
                    } else {
                        navController.navigate(Screen.Workouts.route) {
                            popUpTo(Routes.WORKOUT) { inclusive = true }
                        }
                    }
                },
                onMinimize = { navController.popBackStack() },
                onNavigateToTimer = {
                    navController.popBackStack()
                    navController.navigate("${Screen.Tools.route}?mode=COUNTDOWN")
                },
                viewModel = workoutViewModel
            )
        }

        // Main app tabs — each wrapped in MainAppScaffold with minimized bar awareness
        composable(
            route = Screen.Workouts.route,
            enterTransition = { fadeIn(tween(200)) },
            exitTransition = { fadeOut(tween(200)) },
            popEnterTransition = { fadeIn(tween(200)) },
            popExitTransition = { fadeOut(tween(200)) }
        ) {
            MainAppScaffold(
                navController = navController,
                currentScreen = Screen.Workouts,
                workoutState = workoutState,
                onMaximizeWorkout = { workoutViewModel.maximizeWorkout() },
                onSettingsClick = { navController.navigate(Routes.SETTINGS) },
                onProfileClick = { navController.navigate(Routes.PROFILE) },
                clocksTimerProgress = clocksTimerState?.progress
            ) {
                WorkoutsScreen(
                    onStartWorkout = { routineId ->
                        if (routineId.isNotBlank()) {
                            workoutViewModel.startWorkoutFromRoutine(routineId)
                        } else {
                            workoutViewModel.startWorkout()
                        }
                        navController.navigate(Routes.WORKOUT)
                    },
                    isWorkoutActive = workoutState.isActive,
                    onResumeWorkout = { navController.navigate(Routes.WORKOUT) },
                    onCreateRoutine = { navController.navigate("template_builder/new") },
                    onEditRoutine = { routineId ->
                        workoutViewModel.startEditMode(routineId)
                        if (!workoutViewModel.workoutState.value.showEditGuard) {
                            navController.navigate(Routes.WORKOUT)
                        }
                    }
                )
            }
        }

        composable(
            route = Screen.Exercises.route,
            enterTransition = { fadeIn(tween(200)) },
            exitTransition = { fadeOut(tween(200)) },
            popEnterTransition = { fadeIn(tween(200)) },
            popExitTransition = { fadeOut(tween(200)) }
        ) {
            MainAppScaffold(
                navController = navController,
                currentScreen = Screen.Exercises,
                workoutState = workoutState,
                onMaximizeWorkout = { workoutViewModel.maximizeWorkout() },
                onSettingsClick = { navController.navigate(Routes.SETTINGS) },
                onProfileClick = { navController.navigate(Routes.PROFILE) },
                clocksTimerProgress = clocksTimerState?.progress
            ) {
                ExercisesScreen()
            }
        }

        composable(
            route = "${Screen.Tools.route}?mode={mode}",
            arguments = listOf(navArgument("mode") { type = NavType.StringType; nullable = true; defaultValue = null }),
            enterTransition = { fadeIn(tween(200)) },
            exitTransition = { fadeOut(tween(200)) },
            popEnterTransition = { fadeIn(tween(200)) },
            popExitTransition = { fadeOut(tween(200)) }
        ) {
            MainAppScaffold(
                navController = navController,
                currentScreen = Screen.Tools,
                workoutState = workoutState,
                onMaximizeWorkout = { workoutViewModel.maximizeWorkout() },
                onSettingsClick = { navController.navigate(Routes.SETTINGS) },
                onProfileClick = { navController.navigate(Routes.PROFILE) },
                clocksTimerProgress = clocksTimerState?.progress
            ) {
                ToolsScreen(
                    onTimerStarted = {
                        if (workoutState.isActive && workoutState.isMinimized) {
                            workoutViewModel.maximizeWorkout()
                        }
                    }
                )
            }
        }

        composable(
            route = Screen.Trends.route,
            enterTransition = { fadeIn(tween(200)) },
            exitTransition = { fadeOut(tween(200)) },
            popEnterTransition = { fadeIn(tween(200)) },
            popExitTransition = { fadeOut(tween(200)) }
        ) {
            MainAppScaffold(
                navController = navController,
                currentScreen = Screen.Trends,
                workoutState = workoutState,
                onMaximizeWorkout = { workoutViewModel.maximizeWorkout() },
                onSettingsClick = { navController.navigate(Routes.SETTINGS) },
                onProfileClick = { navController.navigate(Routes.PROFILE) },
                clocksTimerProgress = clocksTimerState?.progress
            ) {
                MetricsScreen(
                    onNavigateToSettings = { navController.navigate(Routes.SETTINGS) }
                )
            }
        }

        composable(
            route = Screen.History.route,
            enterTransition = { fadeIn(tween(200)) },
            exitTransition = { fadeOut(tween(200)) },
            popEnterTransition = { fadeIn(tween(200)) },
            popExitTransition = { fadeOut(tween(200)) }
        ) {
            MainAppScaffold(
                navController = navController,
                currentScreen = Screen.History,
                workoutState = workoutState,
                onMaximizeWorkout = { workoutViewModel.maximizeWorkout() },
                onSettingsClick = { navController.navigate(Routes.SETTINGS) },
                onProfileClick = { navController.navigate(Routes.PROFILE) },
                clocksTimerProgress = clocksTimerState?.progress
            ) {
                HistoryScreen(
                    onWorkoutClick = { workoutId ->
                        navController.navigate("workout_summary/$workoutId")
                    }
                )
            }
        }

        composable(
            route = Routes.SETTINGS,
            enterTransition = { slideInHorizontally(tween(300)) { it } },
            exitTransition = { slideOutHorizontally(tween(300)) { -it / 3 } },
            popEnterTransition = { slideInHorizontally(tween(300)) { -it / 3 } },
            popExitTransition = { slideOutHorizontally(tween(300)) { it } }
        ) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.PROFILE,
            enterTransition = { slideInHorizontally(tween(300)) { it } },
            exitTransition = { slideOutHorizontally(tween(300)) { -it / 3 } },
            popEnterTransition = { slideInHorizontally(tween(300)) { -it / 3 } },
            popExitTransition = { slideOutHorizontally(tween(300)) { it } }
        ) {
            ProfileScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.TEMPLATE_BUILDER,
            arguments = listOf(navArgument("routineId") { type = NavType.StringType }),
            enterTransition = { slideInHorizontally(tween(300)) { it } },
            exitTransition = { slideOutHorizontally(tween(300)) { -it / 3 } },
            popEnterTransition = { slideInHorizontally(tween(300)) { -it / 3 } },
            popExitTransition = { slideOutHorizontally(tween(300)) { it } }
        ) {
            TemplateBuilderScreen(navController = navController)
        }

        composable(
            route = Routes.EXERCISE_PICKER,
            enterTransition = { slideInHorizontally(tween(300)) { it } },
            exitTransition = { slideOutHorizontally(tween(300)) { -it / 3 } },
            popEnterTransition = { slideInHorizontally(tween(300)) { -it / 3 } },
            popExitTransition = { slideOutHorizontally(tween(300)) { it } }
        ) {
            ExercisesScreen(
                pickerMode = true,
                onExercisesSelected = { ids ->
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("selected_exercises", ArrayList(ids))
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = Routes.WORKOUT_DETAIL,
            arguments = listOf(navArgument("workoutId") { type = NavType.StringType }),
            enterTransition = { slideInHorizontally(tween(300)) { it } },
            exitTransition = { slideOutHorizontally(tween(300)) { -it / 3 } },
            popEnterTransition = { slideInHorizontally(tween(300)) { -it / 3 } },
            popExitTransition = { slideOutHorizontally(tween(300)) { it } }
        ) {
            WorkoutDetailScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(
            route = Routes.WORKOUT_SUMMARY,
            arguments = listOf(
                navArgument("workoutId") { type = NavType.StringType },
                navArgument("isPostWorkout") { type = NavType.BoolType; defaultValue = false },
                navArgument("syncType") { type = NavType.StringType; defaultValue = "NONE" }
            ),
            enterTransition = { slideInHorizontally(tween(300)) { it } },
            exitTransition = { slideOutHorizontally(tween(300)) { -it / 3 } },
            popEnterTransition = { slideInHorizontally(tween(300)) { -it / 3 } },
            popExitTransition = { slideOutHorizontally(tween(300)) { it } }
        ) {
            WorkoutSummaryScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToEdit = { workoutId ->
                    navController.navigate("workout_detail/$workoutId")
                },
                onNavigateToTrends = {
                    navController.navigate(Screen.Trends.route)
                },
                onConfirmSyncValues = { workoutViewModel.confirmUpdateRoutineValues() },
                onConfirmSyncStructure = { workoutViewModel.confirmUpdateRoutineStructure() },
                onConfirmSyncBoth = { workoutViewModel.confirmUpdateBoth() },
                onDismissSync = { workoutViewModel.dismissRoutineSync() },
                onSaveAsRoutine = { name -> workoutViewModel.saveWorkoutAsRoutine(name) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScaffold(
    navController: androidx.navigation.NavHostController,
    currentScreen: Screen?,
    workoutState: com.powerme.app.ui.workout.ActiveWorkoutState,
    onMaximizeWorkout: () -> Unit,
    onSettingsClick: () -> Unit,
    onProfileClick: () -> Unit = {},
    clocksTimerProgress: Float? = null,
    content: @Composable () -> Unit
) {
    val tabs = listOf(Screen.Workouts, Screen.History, Screen.Exercises, Screen.Tools, Screen.Trends)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Image(
                        painter = painterResource(id = R.drawable.ic_powerme_logo_source),
                        contentDescription = "PowerME",
                        modifier = Modifier.height(36.dp).aspectRatio(1f),
                        contentScale = ContentScale.Fit
                    )
                },
                actions = {
                    IconButton(onClick = onProfileClick) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = "Profile",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                windowInsets = TopAppBarDefaults.windowInsets
            )
        },
        bottomBar = {
            Column(modifier = Modifier.navigationBarsPadding()) {
                // Minimized Workout Bar
                AnimatedVisibility(
                    visible = (workoutState.isActive || workoutState.isEditMode) && workoutState.isMinimized,
                    enter = slideInVertically(tween(300)) { it },
                    exit = slideOutVertically(tween(300)) { it }
                ) {
                    MinimizedWorkoutBar(
                        workoutName = workoutState.workoutName,
                        elapsedSeconds = if (workoutState.isEditMode) -1 else workoutState.elapsedSeconds,
                        clocksTimerProgress = clocksTimerProgress,
                        onClick = onMaximizeWorkout
                    )
                }
                
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    windowInsets = WindowInsets(0)
                ) {
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentDestination = navBackStackEntry?.destination

                    tabs.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = screen.title) },
                            label = { Text(screen.title) },
                            selected = currentDestination?.hierarchy?.any {
                                it.route?.startsWith(screen.route) == true
                            } == true,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.background,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                                unselectedTextColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            content()
        }
    }
}

@Composable
private fun MinimizedWorkoutBar(
    workoutName: String,
    elapsedSeconds: Int,
    clocksTimerProgress: Float? = null,
    onClick: () -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    Column {
        // Clocks countdown reverse progress bar (TimerGreen, shrinks right→left as time runs out)
        if (clocksTimerProgress != null) {
            LinearProgressIndicator(
                progress = { clocksTimerProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
                color = com.powerme.app.ui.theme.TimerGreen,
                trackColor = com.powerme.app.ui.theme.TimerGreen.copy(alpha = 0.15f)
            )
        }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clickable(onClick = onClick)
                .drawBehind {
                    drawRect(
                        color = primaryColor,
                        topLeft = Offset.Zero,
                        size = Size(4.dp.toPx(), size.height)
                    )
                },
            color = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 20.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = workoutName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    if (elapsedSeconds >= 0) {
                        val m = (elapsedSeconds % 3600) / 60
                        val s = elapsedSeconds % 60
                        Text(
                            text = "%02d:%02d".format(m, s),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = com.powerme.app.ui.theme.JetBrainsMono
                        )
                    } else {
                        Text(
                            text = "Edit Mode",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Maximize")
            }
        }
    }
}
