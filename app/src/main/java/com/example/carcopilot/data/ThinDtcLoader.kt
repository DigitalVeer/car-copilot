package com.example.carcopilot.data

import android.content.Context
import com.example.carcopilot.model.Route
import com.example.carcopilot.model.Severity
import org.json.JSONObject

/**
 * Loads [assets/dtc_codes.json] and converts it to a map of thin
 * [DTCEntry] objects — description, category, severity, route, title, and
 * subtitle only. No walkthrough steps, no mechanic draft, no cost estimates.
 *
 * These supplement [DTCTable.DEFAULT]'s deep entries via [DTCTable.withThin].
 * Deep entries always win on conflict so the curated data is never overridden.
 */
object ThinDtcLoader {

    fun load(context: Context): Map<String, DTCEntry> {
        val raw = context.assets.open("dtc_codes.json").bufferedReader().use { it.readText() }
        val root = JSONObject(raw)
        val arr = root.getJSONArray("codes")
        val result = LinkedHashMap<String, DTCEntry>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val code = obj.getString("code")
            result[code] = DTCEntry(
                code = code,
                description = obj.getString("description"),
                category = obj.getString("category"),
                severity = Severity.valueOf(obj.getString("severity")),
                route = Route.valueOf(obj.getString("route")),
                title = obj.getString("title"),
                subtitle = obj.getString("subtitle"),
            )
        }
        return result
    }
}
