package com.example.carcopilot

import android.app.Application
import android.util.Log
import com.example.carcopilot.data.BluetoothOBDDataSource
import com.example.carcopilot.data.DTCTable
import com.example.carcopilot.data.EngineFamily
import com.example.carcopilot.data.FixtureOBDDataSource
import com.example.carcopilot.data.IssueBuilder
import com.example.carcopilot.data.OBDDataSource
import com.example.carcopilot.data.OBDSnapshot
import com.example.carcopilot.data.RulesEngine
import com.example.carcopilot.data.TcpOBDDataSource
import com.example.carcopilot.data.ThinDtcLoader
import com.example.carcopilot.inference.GemmaService
import com.example.carcopilot.model.Classification
import com.example.carcopilot.model.Issue
import com.example.carcopilot.model.VehicleInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Process-scoped service locator. Owns the [OBDDataSource] seam, the
 * current [Issue] and [Classification] as observable [StateFlow]s, and
 * the [GemmaService] whose engine starts warming at process start.
 *
 * FIXTURE reads once synchronously so the NavHost has data on first
 * composition. EMULATOR polls every [BuildConfig.POLL_INTERVAL_MS]
 * milliseconds so switching the Python emulator scenario is reflected
 * automatically — no app restart needed. BLUETOOTH is driven by the UI.
 */
class CarCopilotApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob())

    lateinit var obd: OBDDataSource
        private set

    private val _issueState = MutableStateFlow<Issue?>(null)
    val issueState: StateFlow<Issue?> = _issueState.asStateFlow()

    private val _classificationState = MutableStateFlow<Classification?>(null)
    val classificationState: StateFlow<Classification?> = _classificationState.asStateFlow()

    lateinit var gemma: GemmaService
        private set

    override fun onCreate() {
        super.onCreate()
        obd = when (BuildConfig.DATA_SOURCE) {
            "EMULATOR" -> TcpOBDDataSource(
                host = BuildConfig.OBD_EMULATOR_HOST,
                port = 35000,
                vehicle = VehicleInfo(
                    year = 2009, make = "Toyota", model = "Corolla",
                    mileage = 187_000, displayName = "2009 Corolla",
                ),
                engineFamily = EngineFamily.PETROL,
            )
            "BLUETOOTH" -> BluetoothOBDDataSource(
                context = this,
                vehicle = VehicleInfo(
                    year = 2021, make = "VW", model = "Jetta",
                    mileage = 0, displayName = "2021 VW Jetta",
                ),
                engineFamily = EngineFamily.PETROL,
            )
            else -> FixtureOBDDataSource(this)
        }

        val dtcTable = DTCTable.DEFAULT.withThin(ThinDtcLoader.load(this))

        when (BuildConfig.DATA_SOURCE) {
            "EMULATOR" -> appScope.launch(Dispatchers.IO) {
                // Poll continuously. First iteration runs immediately so the
                // UI has data as soon as the coroutine starts rather than
                // waiting a full interval.
                while (true) {
                    poll(dtcTable)
                    delay(BuildConfig.POLL_INTERVAL_MS)
                }
            }
            "BLUETOOTH" -> { /* SnapshotViewerScreen drives the first read */ }
            else -> runBlocking { poll(dtcTable) }
        }

        gemma = GemmaService(this, appScope)
    }

    private suspend fun poll(dtcTable: DTCTable) {
        obd.readSnapshot()
            .onFailure { Log.w(TAG, "snapshot read failed: ${it.message}") }
            .getOrNull()
            ?.takeIf { it.dtcs.isNotEmpty() }
            ?.let { snapshot -> updateIfChanged(snapshot, dtcTable) }
    }

    private fun updateIfChanged(snapshot: OBDSnapshot, dtcTable: DTCTable) {
        val incomingCode = snapshot.dtcs.first().code
        if (_issueState.value?.dtcs?.firstOrNull()?.code == incomingCode) return
        _issueState.value = IssueBuilder.build(snapshot, dtcTable)
        _classificationState.value = RulesEngine.classify(snapshot)
        Log.i(TAG, "scenario updated → $incomingCode")
    }

    companion object {
        private const val TAG = "CarCopilot"
    }
}
