package com.qingheng.weight.meal

import org.json.JSONArray
import org.json.JSONObject

data class CodexModelOption(
    val id: String,
    val displayName: String,
    val reasoningLevels: List<String>,
    val defaultReasoning: String,
    val inputModalities: List<String>,
)

data class CodexModelCatalog(
    val models: List<CodexModelOption>,
    val source: String,
    val updatedAt: String,
    val stale: Boolean,
    val warning: String,
) {
    companion object {
        fun parse(json: JSONObject): CodexModelCatalog {
            val entries = json.optJSONArray("models")
                ?: throw CodexTaskException.Protocol("模型目录缺少 models")
            val models = (0 until entries.length()).mapNotNull { index ->
                val item = entries.optJSONObject(index) ?: return@mapNotNull null
                val id = item.optString("id").trim()
                if (id.isEmpty()) return@mapNotNull null
                val levels = strings(item.optJSONArray("reasoningLevels"))
                val default = if (item.isNull("defaultReasoning")) "" else item.optString("defaultReasoning")
                CodexModelOption(id, item.optString("displayName").ifBlank { id }, levels,
                    default.takeIf { it in levels } ?: levels.firstOrNull().orEmpty(),
                    strings(item.optJSONArray("inputModalities")))
            }.distinctBy { it.id }
            return CodexModelCatalog(models, json.optString("source"),
                if (json.isNull("updatedAt")) "" else json.optString("updatedAt"),
                json.optBoolean("stale"), json.optString("warning"))
        }

        private fun strings(array: JSONArray?): List<String> =
            (0 until (array?.length() ?: 0)).mapNotNull { array?.opt(it) as? String }
                .filter { it.isNotBlank() }.distinct()
    }
}
