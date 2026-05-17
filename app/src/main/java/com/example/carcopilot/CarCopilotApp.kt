package com.example.carcopilot

import android.app.Application
import android.util.Log
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
 * Issue resolved from its initial snapshot (if there is one), and the
 * [GemmaService] whose engine starts warming as soon as the Application
 * is created so the user doesn't pay the full ~30–60s init when they
 * tap into IssueScreen.
 *
 * No DI framework — a few `lateinit var` fields on the Application is
 * enough until something needs scoped lifecycles or test substitution
 * beyond what a direct field reassignment can do.
 *
 * The concrete [OBDDataSource] is chosen at build time via
 * `BuildConfig.DATA_SOURCE` (FIXTURE / EMULATOR / BLUETOOTH). FIXTURE
 * is the locked demo path and the default; EMULATOR and BLUETOOTH are
 * dev builds for the Phase-12 hardware track.
 */
class CarCopilotApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob())

    lateinit var obd: OBDDataSource
        private set

    /**
     * Resolved from the initial snapshot when the data source can produce
     * one synchronously and the snapshot contains a known DTC. Null when:
     *  - data source is BLUETOOTH (dongle scan is driven from the UI),
     *  - the snapshot read fails (ignition off, no dongle in range, etc),
     *  - the snapshot has no DTCs (healthy car — "All clear" path).
     *
     * MainActivity gates the NavHost on this being non-null and falls back
     * to [SnapshotViewerScreen] otherwise, so screens that take a non-null
     * Issue never see a null.
     */
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

        // Bluetooth opens a fresh RFCOMM connection per readSnapshot and
        // can only do so once BLUETOOTH_CONNECT is granted at runtime —
        // we let SnapshotViewerScreen drive that flow. Fixture and emulator
        // can read synchronously here so the NavHost composes against a
        // ready Issue and the user doesn't see a one-frame empty state.
        if (BuildConfig.DATA_SOURCE != "BLUETOOTH") {
            initialIssue = runBlocking {
                obd.readSnapshot()
                    .onFailure { Log.w(TAG, "initial snapshot failed: ${it.message}") }
                    .getOrNull()
                    ?.takeIf { it.dtcs.isNotEmpty() }
                    ?.let { IssueBuilder.build(it, DTCTable.DEFAULT) }
            }
        }

        gemma = GemmaService(this, appScope)
    }

    companion object {
        private const val TAG = "CarCopilot"
    }
}
