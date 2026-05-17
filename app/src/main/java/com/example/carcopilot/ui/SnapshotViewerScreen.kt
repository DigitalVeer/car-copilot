package com.example.carcopilot.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.carcopilot.data.OBDDataSource
import com.example.carcopilot.data.OBDSnapshot
import com.example.carcopilot.model.LiveStatus
import kotlinx.coroutines.launch

private sealed interface ViewState {
    data object Connecting : ViewState
    data class Success(val snapshot: OBDSnapshot) : ViewState
    data class Error(val message: String) : ViewState
}

/**
 * Dev screen — renders the raw [OBDSnapshot] straight from the dongle with
 * no classification, no Gemma, no DTCTable lookup. Use this to verify what
 * the real vehicle actually reports before building the diagnostic layer on
 * top of it.
 */
@Composable
fun SnapshotViewerScreen(
    obd: OBDDataSource,
    btPermissionGranted: Boolean,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var state by remember { mutableStateOf<ViewState>(ViewState.Connecting) }
    val scope = rememberCoroutineScope()

    fun scan() {
        state = ViewState.Connecting
        scope.launch {
            state = obd.readSnapshot().fold(
                onSuccess = { ViewState.Success(it) },
                onFailure = { ViewState.Error(it.message ?: "unknown error") },
            )
        }
    }

    LaunchedEffect(btPermissionGranted) {
        if (btPermissionGranted) scan()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F1117))
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = "OBD Snapshot",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Raw vehicle state — no interpretation",
            color = Color(0xFF8A8FA8),
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(20.dp))

        when {
            !btPermissionGranted -> {
                InfoBlock(label = "Permission required") {
                    Text(
                        "BLUETOOTH_CONNECT permission is needed to reach the dongle.",
                        color = Color(0xFFCCCCCC),
                        fontSize = 14.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onRequestPermission) {
                        Text("Grant permission")
                    }
                }
            }

            state is ViewState.Connecting -> {
                InfoBlock(label = "Connecting") {
                    Text("Scanning for dongle…", color = Color(0xFF8A8FA8), fontSize = 14.sp)
                }
            }

            state is ViewState.Error -> {
                InfoBlock(label = "Connection failed") {
                    Text(
                        (state as ViewState.Error).message,
                        color = Color(0xFFFF6B6B),
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = ::scan) { Text("Retry") }
                }
            }

            state is ViewState.Success -> {
                val snapshot = (state as ViewState.Success).snapshot
                SnapshotContent(snapshot = snapshot, onRescan = ::scan)
            }
        }
    }
}

@Composable
private fun SnapshotContent(snapshot: OBDSnapshot, onRescan: () -> Unit) {
    // ── Header ────────────────────────────────────────────────────────────────
    InfoBlock(label = "Vehicle") {
        MonoRow("Source", snapshot.source.name)
        MonoRow("Captured", snapshot.capturedAt.take(19).replace("T", "  "))
        MonoRow("Engine", snapshot.engineFamily.name)
    }
    Spacer(Modifier.height(16.dp))

    // ── DTCs ──────────────────────────────────────────────────────────────────
    InfoBlock(label = "Fault codes (${snapshot.dtcs.size} confirmed)") {
        if (snapshot.dtcs.isEmpty()) {
            Text("No confirmed DTCs", color = Color(0xFF8A8FA8), fontSize = 14.sp)
        } else {
            snapshot.dtcs.forEach { dtc ->
                MonoRow(dtc.code, dtc.description)
            }
        }
        if (snapshot.pendingDtcs.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Pending: ${snapshot.pendingDtcs.joinToString { it.code }}",
                color = Color(0xFFFFCC44),
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
        if (snapshot.permanentDtcs.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                "Permanent: ${snapshot.permanentDtcs.joinToString { it.code }}",
                color = Color(0xFFFF6B6B),
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
    Spacer(Modifier.height(16.dp))

    // ── Live readings ─────────────────────────────────────────────────────────
    InfoBlock(label = "Live readings (${snapshot.liveReadings.size} PIDs)") {
        if (snapshot.liveReadings.isEmpty()) {
            Text("No readings returned", color = Color(0xFF8A8FA8), fontSize = 14.sp)
        } else {
            snapshot.liveReadings.forEach { r ->
                val statusColor = when (r.status) {
                    LiveStatus.severe  -> Color(0xFFFF6B6B)
                    LiveStatus.warning -> Color(0xFFFFCC44)
                    LiveStatus.normal  -> Color(0xFF4ADE80)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(r.key, color = Color(0xFF8A8FA8), fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "${r.value}${r.unit?.let { " $it" } ?: ""}",
                            color = statusColor,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                        )
                        r.note?.let {
                            Text("($it)", color = statusColor.copy(alpha = 0.7f), fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(24.dp))
    Button(onClick = onRescan, modifier = Modifier.fillMaxWidth()) {
        Text("Re-scan")
    }
}

@Composable
private fun InfoBlock(label: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF1A1D27))
            .padding(14.dp),
    ) {
        Text(
            text = label.uppercase(),
            color = Color(0xFF4A5068),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
        )
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun MonoRow(key: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(key, color = Color(0xFF8A8FA8), fontSize = 13.sp)
        Text(
            value,
            color = Color(0xFFCCCCCC),
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}
