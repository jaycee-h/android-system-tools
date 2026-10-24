package com.pagnchanak.systemtools.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pagnchanak.systemtools.tools.Tool
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
                navController.popBackStack()
            } else {
                ToolScaffold(tool, onBack = { navController.popBackStack() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToolScaffold(tool: Tool, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tool.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            tool.content()
        }
    }
}
