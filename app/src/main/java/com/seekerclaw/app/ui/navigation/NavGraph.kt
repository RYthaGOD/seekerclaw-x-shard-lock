package com.shardclaw.app.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import com.shardclaw.app.ui.theme.RethinkSans
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.shardclaw.app.config.ConfigManager
import com.shardclaw.app.ui.dashboard.DashboardScreen
import com.shardclaw.app.ui.logs.LogsScreen
import com.shardclaw.app.ui.settings.SettingsScreen
import com.shardclaw.app.ui.setup.SetupScreen
import com.shardclaw.app.ui.skills.SkillsScreen
import com.shardclaw.app.ui.system.SystemScreen
import com.shardclaw.app.R
import com.shardclaw.app.ui.theme.shardclawColors
import com.shardclaw.app.util.Analytics
import kotlinx.serialization.Serializable

// Route definitions
@Serializable object SetupRoute
@Serializable object DashboardRoute
@Serializable object LogsRoute
@Serializable object SkillsRoute
@Serializable object SettingsRoute
@Serializable object SystemRoute
@Serializable object ProviderConfigRoute
@Serializable object TelegramConfigRoute
@Serializable object SearchConfigRoute

data class BottomNavItem(
    val label: String,
    val iconRes: Int,
    val route: Any,
)

val bottomNavItems = listOf(
    BottomNavItem("Home", R.drawable.ic_lucide_layout_grid, DashboardRoute),
    BottomNavItem("Logs", R.drawable.ic_lucide_terminal, LogsRoute),
    BottomNavItem("Skills", R.drawable.ic_lucide_layers, SkillsRoute),
    BottomNavItem("Settings", R.drawable.ic_lucide_settings, SettingsRoute),
)

@Composable
fun shardclawNavHost() {
    val context = LocalContext.current
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // Track screen views
    DisposableEffect(navController) {
        val listener = androidx.navigation.NavController.OnDestinationChangedListener { _, dest, _ ->
            val screenName = when {
                dest.hasRoute(SetupRoute::class) -> "Setup"
                dest.hasRoute(DashboardRoute::class) -> "Dashboard"
                dest.hasRoute(LogsRoute::class) -> "Logs"
                dest.hasRoute(SkillsRoute::class) -> "Skills"
                dest.hasRoute(SettingsRoute::class) -> "Settings"
                dest.hasRoute(SystemRoute::class) -> "System"
                dest.hasRoute(SearchConfigRoute::class) -> "SearchProviderConfig"
                else -> dest.route ?: "Unknown"
            }
            Analytics.logScreenView(screenName)
        }
        navController.addOnDestinationChangedListener(listener)
        onDispose { navController.removeOnDestinationChangedListener(listener) }
    }

    val startDestination: Any = if (ConfigManager.isSetupComplete(context)) {
        DashboardRoute
    } else {
        SetupRoute
    }

    val showBottomBar = currentDestination?.let { dest ->
        bottomNavItems.any { item ->
            dest.hierarchy.any { it.hasRoute(item.route::class) }
        }
    } ?: false

    Scaffold(
        containerColor = shardclawColors.Background,
        bottomBar = {
            if (showBottomBar) {
                Column {
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = shardclawColors.CardBorder,
                    )
                    NavigationBar(
                        containerColor = shardclawColors.Background,
                        tonalElevation = 0.dp,
                    ) {
                        bottomNavItems.forEach { item ->
                            val selected = currentDestination?.hierarchy?.any {
                                it.hasRoute(item.route::class)
                            } == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(item.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        painter = painterResource(item.iconRes),
                                        contentDescription = item.label,
                                    )
                                },
                                label = {
                                    Text(
                                        text = item.label,
                                        fontFamily = RethinkSans,
                                        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                                        fontSize = 11.sp,
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = shardclawColors.Primary,
                                    selectedTextColor = shardclawColors.Primary,
                                    unselectedIconColor = shardclawColors.TextDim,
                                    unselectedTextColor = shardclawColors.TextDim,
                                    indicatorColor = Color.Transparent,
                                ),
                            )
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        val fadeSpec = tween<Float>(durationMillis = 200)
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding),
            enterTransition = { fadeIn(animationSpec = fadeSpec) },
            exitTransition = { fadeOut(animationSpec = fadeSpec) },
            popEnterTransition = { fadeIn(animationSpec = fadeSpec) },
            popExitTransition = { fadeOut(animationSpec = fadeSpec) },
        ) {
            composable<SetupRoute> {
                SetupScreen(
                    onSetupComplete = {
                        navController.navigate(DashboardRoute) {
                            popUpTo(SetupRoute) { inclusive = true }
                        }
                    }
                )
            }
            composable<DashboardRoute> {
                DashboardScreen(
                    onNavigateToSystem = {
                        navController.navigate(SystemRoute)
                    },
                    onNavigateToSettings = {
                        navController.navigate(SettingsRoute) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToSetup = {
                        navController.navigate(SetupRoute) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                inclusive = true
                            }
                        }
                    },
                )
            }
            composable<SystemRoute> {
                SystemScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable<LogsRoute> {
                LogsScreen()
            }
            composable<SkillsRoute> {
                SkillsScreen()
            }
            composable<SettingsRoute> {
                SettingsScreen(
                    onRunSetupAgain = {
                        navController.navigate(SetupRoute) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                inclusive = true
                            }
                        }
                    },
                    onNavigateToAiConfig = {
                        navController.navigate(ProviderConfigRoute)
                    },
                    onNavigateToTelegram = {
                        navController.navigate(TelegramConfigRoute)
                    },
                    onNavigateToSearchConfig = {
                        navController.navigate(SearchConfigRoute)
                    }
                )
            }
            composable<ProviderConfigRoute> {
                com.shardclaw.app.ui.settings.ProviderConfigScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable<TelegramConfigRoute> {
                com.shardclaw.app.ui.settings.TelegramConfigScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable<SearchConfigRoute> {
                com.shardclaw.app.ui.settings.SearchProviderConfigScreen(
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
