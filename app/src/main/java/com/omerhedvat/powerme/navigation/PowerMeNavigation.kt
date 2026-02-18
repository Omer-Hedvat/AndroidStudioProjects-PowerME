package com.omerhedvat.powerme.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.omerhedvat.powerme.ui.auth.ProfileSetupScreen
import com.omerhedvat.powerme.ui.auth.WelcomeScreen
import com.omerhedvat.powerme.ui.chat.WarRoomChatScreen
import com.omerhedvat.powerme.ui.exercises.ExercisesScreen
import com.omerhedvat.powerme.ui.metrics.MetricsScreen
import com.omerhedvat.powerme.ui.settings.SettingsScreen
import com.omerhedvat.powerme.ui.theme.DeepNavy
import com.omerhedvat.powerme.ui.theme.JetBrainsMono
import com.omerhedvat.powerme.ui.theme.NavySurface
import com.omerhedvat.powerme.ui.theme.NeonBlue
import com.omerhedvat.powerme.ui.tools.ToolsScreen
import com.omerhedvat.powerme.ui.workout.ActiveWorkoutScreen

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object WarRoom   : Screen("warroom",   "War Room",  Icons.Default.Forum)
    object Exercises : Screen("exercises", "Exercises", Icons.Default.FitnessCenter)
    object Tools     : Screen("tools",     "Tools",     Icons.Default.Timer)
    object Trends    : Screen("trends",    "Trends",    Icons.Default.BarChart)
}

private object Routes {
    const val AUTH_WELCOME = "auth_welcome"
    const val AUTH_PROFILE_SETUP = "auth_profile_setup"
    const val WORKOUT = "workout"
    const val SETTINGS = "settings"
}

@Composable
fun PowerMeApp() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.AUTH_WELCOME
    ) {
        // Auth screens (no bottom nav)
        composable(Routes.AUTH_WELCOME) {
            WelcomeScreen(
                onSignedIn = {
                    navController.navigate(Screen.WarRoom.route) {
                        popUpTo(Routes.AUTH_WELCOME) { inclusive = true }
                    }
                },
                onNeedsProfile = {
                    navController.navigate(Routes.AUTH_PROFILE_SETUP) {
                        popUpTo(Routes.AUTH_WELCOME) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.AUTH_PROFILE_SETUP) {
            ProfileSetupScreen(
                onProfileSaved = {
                    navController.navigate(Screen.WarRoom.route) {
                        popUpTo(Routes.AUTH_PROFILE_SETUP) { inclusive = true }
                    }
                }
            )
        }

        // Full-screen workout (launched from Exercises tab)
        composable(Routes.WORKOUT) {
            ActiveWorkoutScreen()
        }

        // Settings (launched from top-bar icon)
        composable(Routes.SETTINGS) {
            SettingsScreen()
        }

        // Main app tabs — each wrapped in MainAppScaffold
        composable(Screen.WarRoom.route) {
            MainAppScaffold(
                navController = navController,
                currentScreen = Screen.WarRoom,
                onSettingsClick = { navController.navigate(Routes.SETTINGS) }
            ) {
                WarRoomChatScreen(
                    onNavigateToSettings = { navController.navigate(Routes.SETTINGS) }
                )
            }
        }

        composable(Screen.Exercises.route) {
            MainAppScaffold(
                navController = navController,
                currentScreen = Screen.Exercises,
                onSettingsClick = { navController.navigate(Routes.SETTINGS) }
            ) {
                ExercisesScreen(
                    onStartWorkout = { navController.navigate(Routes.WORKOUT) }
                )
            }
        }

        composable(Screen.Tools.route) {
            MainAppScaffold(
                navController = navController,
                currentScreen = Screen.Tools,
                onSettingsClick = { navController.navigate(Routes.SETTINGS) }
            ) {
                ToolsScreen()
            }
        }

        composable(Screen.Trends.route) {
            MainAppScaffold(
                navController = navController,
                currentScreen = Screen.Trends,
                onSettingsClick = { navController.navigate(Routes.SETTINGS) }
            ) {
                MetricsScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScaffold(
    navController: androidx.navigation.NavHostController,
    currentScreen: Screen,
    onSettingsClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val tabs = listOf(Screen.WarRoom, Screen.Exercises, Screen.Tools, Screen.Trends)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "PowerME",
                        fontFamily = JetBrainsMono,
                        fontWeight = FontWeight.Bold,
                        color = NeonBlue
                    )
                },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = NeonBlue
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = NavySurface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = NavySurface,
                contentColor = NeonBlue
            ) {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                tabs.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
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
                            selectedIconColor = DeepNavy,
                            selectedTextColor = NeonBlue,
                            indicatorColor = NeonBlue,
                            unselectedIconColor = NeonBlue.copy(alpha = 0.4f),
                            unselectedTextColor = NeonBlue.copy(alpha = 0.4f)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            content()
        }
    }
}
