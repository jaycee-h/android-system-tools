package com.pagnchanak.systemtools.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pagnchanak.systemtools.tools.ToolRegistry
import com.pagnchanak.systemtools.ui.home.HomeScreen

private const val HOME = "home"
private const val TOOL = "tool"

@Composable
fun SystemToolsNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = HOME) {
        composable(HOME) {
            HomeScreen(onOpenTool = { navController.navigate("$TOOL/${it.id}") })
        }
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
