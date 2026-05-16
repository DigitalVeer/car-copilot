package com.example.carcopilot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.carcopilot.model.Fixtures
import com.example.carcopilot.ui.HomeScreen
import com.example.carcopilot.ui.IssueScreen
import com.example.carcopilot.ui.WalkthroughScreen
import com.example.carcopilot.ui.theme.CarCopilotTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CarCopilotTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val ctx = LocalContext.current
                    val gemma = (ctx.applicationContext as CarCopilotApp).gemma
                    val misfire = remember { Fixtures.loadMisfireIssue(ctx) }
                    val nav = rememberNavController()
                    NavHost(
                        navController = nav,
                        startDestination = "home",
                        modifier = Modifier.padding(innerPadding),
                    ) {
                        composable("home") {
                            HomeScreen(misfire = misfire, onIssueClick = { nav.navigate("issue") })
                        }
                        composable("issue") {
                            IssueScreen(
                                issue = misfire,
                                gemma = gemma,
                                onBack = { nav.popBackStack() },
                                onWalkthrough = { nav.navigate("walkthrough") },
                            )
                        }
                        composable("walkthrough") {
                            WalkthroughScreen(
                                issue = misfire,
                                onBack = { nav.popBackStack() },
                                onFinish = {
                                    nav.popBackStack(route = "home", inclusive = false)
                                },
                                onHomeTab = {
                                    nav.popBackStack(route = "home", inclusive = false)
                                },
                                onHistoryTab = { /* Phase 7C will wire this */ },
                            )
                        }
                    }
                }
            }
        }
    }
}
