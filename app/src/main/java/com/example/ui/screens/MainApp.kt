package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.StickerViewModel

sealed class AppScreen(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    object Home : AppScreen("home", "Home", Icons.Filled.Home, Icons.Outlined.Home)
    object Favorites : AppScreen("favorites", "Favorites", Icons.Filled.Favorite, Icons.Outlined.FavoriteBorder)
    object History : AppScreen("history", "History", Icons.Filled.History, Icons.Outlined.History)
    object Settings : AppScreen("settings", "Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
    object Preview : AppScreen("preview/{packId}", "Preview", Icons.Filled.Home, Icons.Outlined.Home)
}

@Composable
fun MainApp() {
    val viewModel: StickerViewModel = viewModel()
    val themeMode by viewModel.theme.collectAsState()
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val darkTheme = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }

    MyApplicationTheme(darkTheme = darkTheme) {
        Scaffold(
            bottomBar = {
                // Only show bottom navigation on primary parent screens
                val showBottomBar = currentRoute in listOf(
                    AppScreen.Home.route,
                    AppScreen.Favorites.route,
                    AppScreen.History.route,
                    AppScreen.Settings.route
                )

                if (showBottomBar) {
                    NavigationBar(
                        tonalElevation = 8.dp,
                        windowInsets = WindowInsets.navigationBars
                    ) {
                        val items = listOf(
                            AppScreen.Home,
                            AppScreen.Favorites,
                            AppScreen.History,
                            AppScreen.Settings
                        )
                        items.forEach { screen ->
                            val selected = currentRoute == screen.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) screen.selectedIcon else screen.unselectedIcon,
                                        contentDescription = screen.title
                                    )
                                },
                                label = { Text(screen.title) }
                            )
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = AppScreen.Home.route,
                modifier = Modifier.padding(innerPadding)
            ) {
                composable(AppScreen.Home.route) {
                    HomeScreen(
                        viewModel = viewModel,
                        onNavigateToPack = { packId ->
                            navController.navigate("preview/$packId")
                        }
                    )
                }

                composable(AppScreen.Favorites.route) {
                    FavoritesScreen(
                        viewModel = viewModel,
                        onNavigateToPack = { packId ->
                            navController.navigate("preview/$packId")
                        }
                    )
                }

                composable(AppScreen.History.route) {
                    HistoryScreen(
                        viewModel = viewModel
                    )
                }

                composable(AppScreen.Settings.route) {
                    SettingsScreen(
                        viewModel = viewModel
                    )
                }

                composable(
                    route = AppScreen.Preview.route,
                    arguments = listOf(navArgument("packId") { type = NavType.StringType })
                ) { backStackEntry ->
                    val packId = backStackEntry.arguments?.getString("packId") ?: ""
                    StickerPreviewScreen(
                        packId = packId,
                        viewModel = viewModel,
                        onBackClick = {
                            navController.navigateUp()
                        }
                    )
                }
            }
        }
    }
}
