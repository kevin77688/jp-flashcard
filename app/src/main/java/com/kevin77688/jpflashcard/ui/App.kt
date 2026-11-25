package com.kevin77688.jpflashcard.ui

import androidx.activity.ComponentActivity
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kevin77688.jpflashcard.data.AppContainer
import com.kevin77688.jpflashcard.ui.components.Hanko

val LocalContainer = staticCompositionLocalOf<AppContainer> { error("no container") }
val LocalIsWide = staticCompositionLocalOf { false }

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Home("home", "今日", Icons.Rounded.Home),
    Kana("kana", "五十音", Icons.Rounded.Translate),
    Words("words", "單字", Icons.Rounded.Style),
    Settings("settings", "設定", Icons.Rounded.Settings),
}

object Routes {
    const val REVIEW = "review/{deck}"
    const val EDITOR = "editor?id={id}"
    fun review(deck: String) = "review/$deck"
    fun editor(id: String? = null) = if (id == null) "editor" else "editor?id=$id"
}

@Composable
fun App(activity: ComponentActivity, container: AppContainer) {
    val user by container.auth.userFlow.collectAsState(initial = container.auth.currentUser)
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    CompositionLocalProvider(LocalContainer provides container, LocalIsWide provides wide) {
        if (user == null) {
            SignInScreen(activity)
        } else {
            MainScaffold()
        }
    }
}

@Composable
private fun MainScaffold() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val currentTab = Tab.entries.firstOrNull { it.route == route }
    val wide = LocalIsWide.current

    if (wide) {
        Row(Modifier.fillMaxSize()) {
            if (currentTab != null) {
                NavigationRail(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    header = { Hanko("字", Modifier.padding(top = 24.dp, bottom = 16.dp)) },
                ) {
                    Tab.entries.forEach { tab ->
                        NavigationRailItem(
                            selected = tab == currentTab,
                            onClick = { nav.switchTab(tab) },
                            icon = { Icon(tab.icon, null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
            Box(Modifier.weight(1f)) { AppNavHost(nav) }
        }
    } else {
        Scaffold(
            bottomBar = {
                if (currentTab != null) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                        Tab.entries.forEach { tab ->
                            NavigationBarItem(
                                selected = tab == currentTab,
                                onClick = { nav.switchTab(tab) },
                                icon = { Icon(tab.icon, null) },
                                label = { Text(tab.label) },
                            )
                        }
                    }
                }
            },
        ) { padding ->
            Box(Modifier.padding(bottom = padding.calculateBottomPadding())) { AppNavHost(nav) }
        }
    }
}

private fun NavHostController.switchTab(tab: Tab) {
    navigate(tab.route) {
        popUpTo(Tab.Home.route) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun AppNavHost(nav: NavHostController) {
    NavHost(
        navController = nav,
        startDestination = Tab.Home.route,
        enterTransition = { fadeIn() },
        exitTransition = { fadeOut() },
    ) {
        composable(Tab.Home.route) {
            HomeScreen(
                onReview = { nav.navigate(Routes.review(it)) },
                onAddWord = { nav.navigate(Routes.editor()) },
                onOpenWord = { nav.navigate(Routes.editor(it)) },
                onOpenKana = { nav.switchTab(Tab.Kana) },
            )
        }
        composable(Tab.Kana.route) { KanaScreen(onPractice = { nav.navigate(Routes.review(it)) }) }
        composable(Tab.Words.route) {
            WordsScreen(
                onAdd = { nav.navigate(Routes.editor()) },
                onOpen = { nav.navigate(Routes.editor(it)) },
                onReview = { nav.navigate(Routes.review(it)) },
            )
        }
        composable(Tab.Settings.route) { SettingsScreen() }
        composable(Routes.REVIEW, arguments = listOf(navArgument("deck") { type = NavType.StringType })) { entry ->
            ReviewScreen(deck = entry.arguments?.getString("deck").orEmpty(), onClose = { nav.popBackStack() })
        }
        composable(
            Routes.EDITOR,
            arguments = listOf(navArgument("id") { type = NavType.StringType; nullable = true; defaultValue = null }),
        ) { entry ->
            EditorScreen(wordId = entry.arguments?.getString("id"), onClose = { nav.popBackStack() })
        }
    }
}
