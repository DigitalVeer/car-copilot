package com.example.carcopilot

import android.app.Application
import com.example.carcopilot.data.DTCTable
import com.example.carcopilot.data.FixtureOBDDataSource
import com.example.carcopilot.data.IssueBuilder
import com.example.carcopilot.data.OBDDataSource
import com.example.carcopilot.inference.GemmaService
import com.example.carcopilot.model.Issue
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
    lateinit var initialIssue: Issue
        private set
    lateinit var gemma: GemmaService
        private set

    override fun onCreate() {
        super.onCreate()
        obd = FixtureOBDDataSource(this)
        // Fixture asset read is a few ms; resolving the initial Issue
        // synchronously here preserves the existing composition flow
        // (HomeScreen takes a non-null Issue) and avoids a one-frame
        // empty state. Phase 12 BLE will replace this with an async
        // scan UX driven off OBDDataSource.connectionState.
        initialIssue = runBlocking {
            val snapshot = obd.readSnapshot().getOrThrow()
            IssueBuilder.build(snapshot, DTCTable.DEFAULT)
        }
        gemma = GemmaService(this, appScope)
    }
}
