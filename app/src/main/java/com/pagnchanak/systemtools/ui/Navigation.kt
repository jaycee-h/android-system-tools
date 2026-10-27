package com.pagnchanak.systemtools.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pagnchanak.systemtools.tools.ToolRegistry
import com.pagnchanak.systemtools.ui.home.HomeScreen
import com.pagnchanak.systemtools.ui.settings.SettingsScreen
import com.pagnchanak.systemtools.update.StartupUpdatePrompt

private const val HOME = "home"
private const val TOOL = "tool"
private const val SETTINGS = "settings"

@Composable
fun SystemToolsNavHost() {
    val navController = rememberNavController()
    StartupUpdatePrompt()
    NavHost(navController = navController, startDestination = HOME) {
        composable(HOME) {
            HomeScreen(
                onOpenTool = { navController.navigate("$TOOL/${it.id}") },
                onOpenSettings = { navController.navigate(SETTINGS) },
            )
        }
        composable(SETTINGS) { SettingsScreen(onBack = { navController.popBackStack() }) }
        composable("$TOOL/{id}") { entry ->
            val tool = ToolRegistry.find(entry.arguments?.getString("id").orEmpty())
            if (tool == null) {
                LaunchedEffect(Unit) { navController.popBackStack() }
            } else {
                tool.content { navController.popBackStack() }
            }
        }
    }
}
