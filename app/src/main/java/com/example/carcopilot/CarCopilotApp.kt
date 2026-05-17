package com.example.carcopilot

import android.app.Application
import com.example.carcopilot.BuildConfig
import com.example.carcopilot.data.BluetoothOBDDataSource
import com.example.carcopilot.data.DTCTable
import com.example.carcopilot.data.EngineFamily
import com.example.carcopilot.data.FixtureOBDDataSource
import com.example.carcopilot.data.IssueBuilder
import com.example.carcopilot.data.OBDDataSource
import com.example.carcopilot.data.TcpOBDDataSource
import com.example.carcopilot.inference.GemmaService
import com.example.carcopilot.model.Issue
import com.example.carcopilot.model.VehicleInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking

/**
 * Process-scoped service locator. Owns the [OBDDataSource] seam, the
 * Issue resolved from its initial snapshot, and the [GemmaService]
 * whose engine starts warming as soon as the Application is created so
 * the user doesn't pay the full ~30–60s init when they tap into
 * IssueScreen.
 *
 * No DI framework — a few `lateinit var` fields on the Application is
 * enough until something needs scoped lifecycles or test substitution
 * beyond what a direct field reassignment can do.
 */
class CarCopilotApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob())

    lateinit var obd: OBDDataSource
        private set
    // Null when DATA_SOURCE=BLUETOOTH (snapshot is read async in SnapshotViewerScreen)
    // or when the connected car has no active DTCs.
    var initialIssue: Issue? = null
        private set
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
        // Bluetooth skips the blocking read — SnapshotViewerScreen drives the
        // async connect flow instead. Fixture and emulator read synchronously.
        if (BuildConfig.DATA_SOURCE != "BLUETOOTH") {
            initialIssue = runBlocking {
                obd.readSnapshot().getOrNull()?.let { snapshot ->
                    if (snapshot.dtcs.isNotEmpty())
                        IssueBuilder.build(snapshot, DTCTable.DEFAULT)
                    else null
                }
            }
        }
        gemma = GemmaService(this, appScope)
    }
}
