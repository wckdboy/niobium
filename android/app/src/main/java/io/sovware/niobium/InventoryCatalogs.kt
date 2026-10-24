package io.sovware.niobium

import org.json.JSONObject

/** A malformed refresh must never replace a known inventory or unlock another mutation. */
internal object InventoryCatalogs {
    fun objects(result: JSONObject, key: String): List<JSONObject> {
        val rows = result.optJSONArray(key) ?: error("Missing $key inventory")
        return (0 until rows.length()).map { rows.optJSONObject(it) ?: error("Invalid $key row") }
    }
    fun skills(result: JSONObject): Map<String,List<String>> {
        val categories = result.optJSONObject("skills") ?: error("Missing skills inventory")
        return buildMap {
            categories.keys().forEach { category ->
                val rows = categories.optJSONArray(category) ?: error("Invalid skill category")
                val names = (0 until rows.length()).map { index ->
                    (rows.opt(index) as? String)?.takeIf { it.isNotBlank() } ?: error("Invalid skill name")
                }.distinct()
                if (names.isNotEmpty()) put(category, names)
            }
        }
    }
}
