package com.example.carcopilot

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.carcopilot.ui.HistoryScreen
import com.example.carcopilot.ui.HomeScreen
import com.example.carcopilot.ui.IssueScreen
import com.example.carcopilot.ui.MechanicDraftScreen
import com.example.carcopilot.ui.SnapshotViewerScreen
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
                    val app = ctx.applicationContext as CarCopilotApp

                    if (BuildConfig.DATA_SOURCE == "BLUETOOTH") {
                        // ── Bluetooth mode ────────────────────────────────────────
                        // Gate on BLUETOOTH_CONNECT permission (API 31+) before
                        // attempting any adapter access — missing permission throws
                        // SecurityException on bondedDevices / createRfcommSocket.
                        var btPermissionGranted by remember {
                            mutableStateOf(
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                                    ContextCompat.checkSelfPermission(
                                        ctx, Manifest.permission.BLUETOOTH_CONNECT
                                    ) == PackageManager.PERMISSION_GRANTED
                                else true // legacy: permission granted at install time
                            )
                        }
                        val permissionLauncher = rememberLauncherForActivityResult(
                            ActivityResultContracts.RequestPermission()
                        ) { granted -> btPermissionGranted = granted }

                        SnapshotViewerScreen(
                            obd = app.obd,
                            btPermissionGranted = btPermissionGranted,
                            onRequestPermission = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    permissionLauncher.launch(
                                        Manifest.permission.BLUETOOTH_CONNECT
                                    )
                                }
                            },
                            modifier = Modifier.padding(innerPadding),
                        )
                    } else {
                        // ── Fixture / Emulator mode ───────────────────────────────
                        val gemma = app.gemma
                        val misfire = remember { app.initialIssue }
                            ?: return@Scaffold // no DTCs — blank screen is fine for dev
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
                                    gemma = gemma,
                                    currentIssue = misfire,
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
}
