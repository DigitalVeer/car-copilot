package com.example.carcopilot

import android.app.Application
import com.example.carcopilot.inference.GemmaService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

/**
 * Process-scoped owner of [GemmaService]. The engine begins warming as soon
 * as the Application is created so the user doesn't pay the full ~30-60s
 * init when they tap into IssueScreen.
 */
class CarCopilotApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob())
    lateinit var gemma: GemmaService
        private set

    override fun onCreate() {
        super.onCreate()
        gemma = GemmaService(this, appScope)
    }
}
