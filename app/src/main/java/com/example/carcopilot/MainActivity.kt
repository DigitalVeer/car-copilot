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
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
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
import com.example.carcopilot.ui.SplashScreen
import com.example.carcopilot.ui.WalkthroughScreen
import com.example.carcopilot.ui.theme.CarCopilotTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CarCopilotTheme {
                val ctx = LocalContext.current
                val app = ctx.applicationContext as CarCopilotApp
                val gemma = app.gemma

                // Hold the splash until BOTH the engine is ready AND the
                // minimum display time has elapsed. Engine init + prewarm
                // dominates on a cold start; on a warm relaunch (model
                // already staged, engine still alive in a prior process)
                // the floor keeps the splash from blinking for one frame.
                var ready by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    val minSplashMs = 2500L
                    val started = System.currentTimeMillis()
                    gemma.awaitReady()
                    val elapsed = System.currentTimeMillis() - started
                    if (elapsed < minSplashMs) delay(minSplashMs - elapsed)
                    ready = true
                }

                Crossfade(
                    targetState = ready,
                    animationSpec = tween(durationMillis = 400),
                    label = "splash-to-main",
                ) { isReady ->
                    if (!isReady) {
                        SplashScreen()
                    } else {
                        MainContent(app)
                    }
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun MainContent(app: CarCopilotApp) {
    val gemma = app.gemma
    val misfire = remember { app.initialIssue }
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        if (misfire == null) {
            // No diagnosable Issue at startup — either BLUETOOTH mode
            // (dongle scan is async), a healthy car with no DTCs, or
            // the snapshot read failed. Render the raw snapshot view
            // as the "All clear / connect" fallback so the user sees
            // what the adapter is reporting instead of a blank screen.
            BluetoothGatedSnapshotViewer(app, Modifier.padding(innerPadding))
        } else {
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
                        gemma = gemma,
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

/**
 * Wraps [SnapshotViewerScreen] with the runtime BLUETOOTH_CONNECT permission
 * gate required by API 31+ before any [android.bluetooth.BluetoothAdapter]
 * access. For FIXTURE and EMULATOR builds the permission is irrelevant — we
 * report it as granted so the scan runs immediately.
 */
@androidx.compose.runtime.Composable
private fun BluetoothGatedSnapshotViewer(
    app: CarCopilotApp,
    modifier: Modifier,
) {
    val ctx = LocalContext.current
    val needsBtPermission = BuildConfig.DATA_SOURCE == "BLUETOOTH" &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    var granted by remember {
        mutableStateOf(
            !needsBtPermission ||
                ContextCompat.checkSelfPermission(
                    ctx, Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { result -> granted = result }

    SnapshotViewerScreen(
        obd = app.obd,
        btPermissionGranted = granted,
        onRequestPermission = {
            if (needsBtPermission) {
                launcher.launch(Manifest.permission.BLUETOOTH_CONNECT)
            }
        },
        modifier = modifier,
    )
}
