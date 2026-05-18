package com.example.carcopilot

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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

private val EaseOutQuart = CubicBezierEasing(0.25f, 1f, 0.5f, 1f)
private val EaseInQuart = CubicBezierEasing(0.5f, 0f, 0.75f, 0f)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Light theme — force dark system-bar icons regardless of device
        // light/dark setting, so the icons stay legible on the light
        // PhoneBg surface.
        val lightBars = SystemBarStyle.light(
            scrim = android.graphics.Color.TRANSPARENT,
            darkScrim = android.graphics.Color.TRANSPARENT,
        )
        enableEdgeToEdge(statusBarStyle = lightBars, navigationBarStyle = lightBars)
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
    val misfire by app.issueState.collectAsState()
    val classification by app.classificationState.collectAsState()
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        val issue = misfire
        if (issue == null) {
            // No diagnosable Issue yet — either BLUETOOTH mode
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
            // iOS-style horizontal slide with a fade companion. Forward push:
            // new screen slides in from the right while the outgoing screen
            // dims. Pop: current slides out to the right while the previous
            // dims back in. EaseOutQuart on entry / EaseInQuart on exit so
            // motion decelerates into place and accelerates away from it,
            // matching the rest of the app's exponential easing language.
            val slideDistance: (Int) -> Int = { full -> full / 6 }
            NavHost(
                navController = nav,
                startDestination = "home",
                modifier = Modifier.padding(innerPadding),
                enterTransition = {
                    slideInHorizontally(
                        animationSpec = tween(durationMillis = 280, easing = EaseOutQuart),
                        initialOffsetX = slideDistance,
                    ) + fadeIn(animationSpec = tween(durationMillis = 220))
                },
                exitTransition = {
                    fadeOut(animationSpec = tween(durationMillis = 160))
                },
                popEnterTransition = {
                    fadeIn(animationSpec = tween(durationMillis = 220))
                },
                popExitTransition = {
                    slideOutHorizontally(
                        animationSpec = tween(durationMillis = 280, easing = EaseInQuart),
                        targetOffsetX = slideDistance,
                    ) + fadeOut(animationSpec = tween(durationMillis = 220))
                },
            ) {
                composable("home") {
                    HomeScreen(
                        misfire = issue,
                        onIssueClick = { nav.navigate("issue") },
                        onHistoryTab = toHistory,
                    )
                }
                composable("issue") {
                    IssueScreen(
                        issue = issue,
                        gemma = gemma,
                        classification = classification,
                        onBack = { nav.popBackStack() },
                        onWalkthrough = { nav.navigate("walkthrough") },
                        onMechanicDraft = { nav.navigate("draft") },
                        onHistoryTab = toHistory,
                    )
                }
                composable("walkthrough") {
                    WalkthroughScreen(
                        issue = issue,
                        gemma = gemma,
                        onBack = { nav.popBackStack() },
                        onFinish = toHome,
                        onHomeTab = toHome,
                        onHistoryTab = toHistory,
                    )
                }
                composable("draft") {
                    MechanicDraftScreen(
                        issue = issue,
                        gemma = gemma,
                        onBack = { nav.popBackStack() },
                        onHomeTab = toHome,
                        onHistoryTab = toHistory,
                    )
                }
                composable("history") {
                    HistoryScreen(
                        gemma = gemma,
                        currentIssue = issue,
                        onHomeTab = toHome,
                        onBack = { nav.popBackStack() },
                    )
                }
            }
        }
    }
}

/**
 * Wraps [SnapshotViewerScreen] with the runtime Bluetooth permission gate
 * required by API 31+ before any [android.bluetooth.BluetoothAdapter] access.
 * Both BLUETOOTH_CONNECT (RFCOMM socket) and BLUETOOTH_SCAN (adapter state
 * queries like isDiscovering) are needed — the OS throws SecurityException
 * on isDiscovering without SCAN even when SCAN's neverForLocation flag is set.
 * For FIXTURE and EMULATOR builds the permissions are irrelevant — we report
 * granted so the scan runs immediately.
 */
@androidx.compose.runtime.Composable
private fun BluetoothGatedSnapshotViewer(
    app: CarCopilotApp,
    modifier: Modifier,
) {
    val ctx = LocalContext.current
    val needsBtPermission = BuildConfig.DATA_SOURCE == "BLUETOOTH" &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val requiredPerms = arrayOf(
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.BLUETOOTH_SCAN,
    )
    var granted by remember {
        mutableStateOf(
            !needsBtPermission || requiredPerms.all { p ->
                ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
            }
        )
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result -> granted = result.values.all { it } }

    SnapshotViewerScreen(
        obd = app.obd,
        btPermissionGranted = granted,
        onRequestPermission = {
            if (needsBtPermission) {
                launcher.launch(requiredPerms)
            }
        },
        modifier = modifier,
    )
}
