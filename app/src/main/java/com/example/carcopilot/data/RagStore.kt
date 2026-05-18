package com.example.carcopilot.data

import android.content.Context
import org.json.JSONObject

/**
 * Loads [rag/dtc_context.json] from assets once and retrieves relevant
 * documents by DTC code for prompt injection.
 *
 * Each call to [retrieve] returns the specific document(s) matching the
 * given code plus the always-applicable general context document (identified
 * by an empty `dtcs` array in the JSON). This gives Gemma both the
 * code-specific background and the regional/vehicle context in one block.
 */
class RagStore(context: Context) {

    private data class RagDocument(
        val dtcs: List<String>,
        val body: String,
    )

    private val documents: List<RagDocument>

    init {
        val raw = context.assets.open("rag/dtc_context.json").bufferedReader().use { it.readText() }
        val root = JSONObject(raw)
        val arr = root.getJSONArray("documents")
        documents = (0 until arr.length()).map { i ->
            val doc = arr.getJSONObject(i)
            val dtcArr = doc.getJSONArray("dtcs")
            RagDocument(
                dtcs = (0 until dtcArr.length()).map { dtcArr.getString(it) },
                body = doc.getString("body"),
            )
        }
    }

    /**
     * Returns the concatenated body text of all documents relevant to
     * [dtcCode]: exact-match documents first, then the general document
     * (empty dtcs list). Returns an empty string when nothing matches.
     */
    fun retrieve(dtcCode: String): String {
        val specific = documents.filter { it.dtcs.isNotEmpty() && dtcCode in it.dtcs }
        val general  = documents.filter { it.dtcs.isEmpty() }
        return (specific + general).joinToString("\n\n") { it.body }
    }
}
