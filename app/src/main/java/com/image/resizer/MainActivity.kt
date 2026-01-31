package com.image.resizer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.image.resizer.ui.screens.AboutScreen
import com.image.resizer.ui.screens.CompressScreen
import com.image.resizer.ui.screens.ConvertScreen
import com.image.resizer.ui.screens.HomeScreen
import com.image.resizer.ui.theme.ImageReSizerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ImageReSizerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    AppNavigation()
                }
            }
        }
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                onNavigateToCompress = { navController.navigate("compress") },
                onNavigateToConvert = { navController.navigate("convert") },
                onNavigateToAbout = { navController.navigate("about") }
            )
        }
        composable("compress") {
            CompressScreen(onBack = { navController.popBackStack() })
        }
        composable("convert") {
            ConvertScreen(onBack = { navController.popBackStack() })
        }
        composable("about") {
            AboutScreen(onBack = { navController.popBackStack() })
        }
    }
}
