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
import com.example.carcopilot.ui.HistoryScreen
import com.example.carcopilot.ui.HomeScreen
import com.example.carcopilot.ui.IssueScreen
import com.example.carcopilot.ui.MechanicDraftScreen
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
                    val toHistory: () -> Unit = {
                        nav.navigate("history") { launchSingleTop = true }
                    }
                    val toHome: () -> Unit = {
                        nav.popBackStack(route = "home", inclusive = false)
                    }
                    NavHost(
                        navController = nav,
                        startDestination = "home",
                        modifier = Modifier.padding(innerPadding),
                    ) {
                        composable("home") {
                            HomeScreen(
                                misfire = misfire,
                                onIssueClick = { nav.navigate("issue") },
                                onHistoryTab = toHistory,
                            )
                        }
                        composable("issue") {
                            IssueScreen(
                                issue = misfire,
                                gemma = gemma,
                                onBack = { nav.popBackStack() },
                                onWalkthrough = { nav.navigate("walkthrough") },
                                onMechanicDraft = { nav.navigate("draft") },
                                onHistoryTab = toHistory,
                            )
                        }
                        composable("walkthrough") {
                            WalkthroughScreen(
                                issue = misfire,
                                onBack = { nav.popBackStack() },
                                onFinish = toHome,
                                onHomeTab = toHome,
                                onHistoryTab = toHistory,
                            )
                        }
                        composable("draft") {
                            MechanicDraftScreen(
                                issue = misfire,
                                gemma = gemma,
                                onBack = { nav.popBackStack() },
                                onHomeTab = toHome,
                                onHistoryTab = toHistory,
                            )
                        }
                        composable("history") {
                            HistoryScreen(
                                onHomeTab = toHome,
                                onBack = { nav.popBackStack() },
                            )
                        }
                    }
                }
            }
        }
    }
}
