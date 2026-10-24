package com.pagnchanak.systemtools

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.pagnchanak.systemtools.ui.SystemToolsNavHost
import com.pagnchanak.systemtools.ui.theme.SystemToolsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            SystemToolsTheme {
                SystemToolsNavHost()
            }
        }
    }
}
