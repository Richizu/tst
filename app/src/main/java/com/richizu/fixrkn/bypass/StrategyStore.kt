package com.richizu.fixrkn.bypass

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Result of checking one strategy: how many test sites were fully reachable. */
data class CheckResult(val passed: Int, val total: Int, val thorough: Boolean, val at: Long) {
    val ok get() = passed > 0
    val score get() = if (total == 0) 0.0 else passed.toDouble() / total
    val label get() = if (ok) "$passed/$total" else "не работает"
}

/** Result of a detailed single-strategy probe. */
data class ProbeResult(
    val youtubeOk: Int,
    val youtubeTotal: Int,
    val discordOk: Int,
    val discordTotal: Int,
    val averageMs: Long
)

data class StrategyEntry(
    val command: String,
    val custom: Boolean,
    val pinned: Boolean,
    val result: CheckResult?,
    val probe: ProbeResult?,
    /** User's answer to "did it work?": true/false, or null when not asked. */
    val feedback: Boolean?
)

/** Persistent strategy knowledge: scan results, favourites, custom strategies, user feedback. */
object StrategyStore {
    private lateinit var prefs: SharedPreferences
    private val results = HashMap<String, CheckResult>()
    private val probes = HashMap<String, ProbeResult>()
    private val feedback = HashMap<String, Boolean>()
    private val pinned = LinkedHashSet<String>()
    private val custom = mutableListOf<String>()

    private val _entries = MutableStateFlow<List<StrategyEntry>>(emptyList())
    val entries: StateFlow<List<StrategyEntry>> = _entries

    fun init(context: Context) {
        prefs = context.getSharedPreferences("strategies", Context.MODE_PRIVATE)
        load()
        publish()
    }

    fun all(): List<String> = custom + Strategies.builtIn.filter { it !in custom }

    fun result(command: String): CheckResult? = synchronized(this) { results[command] }

    fun setResult(command: String, result: CheckResult) = mutate { results[command] = result }

    fun setProbe(command: String, probe: ProbeResult) = mutate { probes[command] = probe }

    fun setFeedback(command: String, worked: Boolean?) = mutate {
        if (worked == null) feedback.remove(command) else feedback[command] = worked
    }

    fun togglePin(command: String) = mutate { if (!pinned.remove(command)) pinned += command }

    fun addCustom(command: String): Boolean {
        val clean = sanitizeStrategy(command)
        if (clean.isBlank()) return false
        mutate {
            if (clean !in custom) custom.add(0, clean)
        }
        return true
    }

    fun replaceCustom(old: String, new: String): Boolean {
        val clean = sanitizeStrategy(new)
        if (clean.isBlank()) return false
        mutate {
            val i = custom.indexOf(old)
            if (i >= 0) custom[i] = clean else custom.add(0, clean)
            results.remove(old); probes.remove(old); feedback.remove(old)
            if (pinned.remove(old)) pinned += clean
        }
        return true
    }

    fun removeCustom(command: String) = mutate {
        custom.remove(command)
        results.remove(command); probes.remove(command); feedback.remove(command); pinned.remove(command)
    }

    fun clearResults() = mutate {
        results.clear(); probes.clear(); feedback.clear()
    }

    fun resetAll() = mutate {
        results.clear(); probes.clear(); feedback.clear(); pinned.clear(); custom.clear()
    }

    /** Working strategies, best first; strategies the user rejected go last. */
    fun ranked(): List<String> = synchronized(this) {
        all().filter { results[it]?.ok == true }
            .sortedWith(
                compareBy<String> { feedback[it] == false }
                    .thenByDescending { feedback[it] == true }
                    .thenByDescending { results[it]?.thorough == true }
                    .thenByDescending { results[it]?.score ?: 0.0 }
            )
    }

    fun exportJson(): JSONObject = synchronized(this) { toJson() }

    fun importJson(root: JSONObject) = mutate { fromJson(root) }

    private inline fun mutate(block: () -> Unit) {
        synchronized(this) {
            block()
            prefs.edit().putString("data", toJson().toString()).apply()
        }
        publish()
    }

    private fun publish() {
        val list = synchronized(this) {
            all().map { cmd ->
                StrategyEntry(cmd, cmd in custom, cmd in pinned, results[cmd], probes[cmd], feedback[cmd])
            }
        }
        _entries.value = list.sortedWith(
            compareByDescending<StrategyEntry> { it.pinned }
                .thenBy { it.feedback == false }
                .thenByDescending { it.result?.ok == true }
                .thenByDescending { it.result?.thorough == true }
                .thenByDescending { it.result?.score ?: -1.0 }
        )
    }

    private fun load() {
        val raw = prefs.getString("data", null) ?: return
        runCatching { fromJson(JSONObject(raw)) }
    }

    private fun toJson(): JSONObject {
        val root = JSONObject()
        root.put("custom", JSONArray(custom))
        root.put("pinned", JSONArray(pinned.toList()))
        root.put("results", JSONObject().apply {
            results.forEach { (k, r) ->
                put(k, JSONObject().put("p", r.passed).put("t", r.total).put("f", r.thorough).put("at", r.at))
            }
        })
        root.put("probes", JSONObject().apply {
            probes.forEach { (k, p) ->
                put(k, JSONObject().put("yo", p.youtubeOk).put("yt", p.youtubeTotal)
                    .put("do", p.discordOk).put("dt", p.discordTotal).put("ms", p.averageMs))
            }
        })
        root.put("feedback", JSONObject().apply { feedback.forEach { (k, v) -> put(k, v) } })
        return root
    }

    private fun fromJson(root: JSONObject) {
        custom.clear(); pinned.clear(); results.clear(); probes.clear(); feedback.clear()
        root.optJSONArray("custom")?.let { a -> for (i in 0 until a.length()) custom += a.getString(i) }
        root.optJSONArray("pinned")?.let { a -> for (i in 0 until a.length()) pinned += a.getString(i) }
        root.optJSONObject("results")?.let { o ->
            for (k in o.keys()) {
                val r = o.getJSONObject(k)
                results[k] = CheckResult(r.getInt("p"), r.getInt("t"), r.optBoolean("f"), r.optLong("at"))
            }
        }
        root.optJSONObject("probes")?.let { o ->
            for (k in o.keys()) {
                val p = o.getJSONObject(k)
                probes[k] = ProbeResult(p.getInt("yo"), p.getInt("yt"), p.getInt("do"), p.getInt("dt"), p.getLong("ms"))
            }
        }
        root.optJSONObject("feedback")?.let { o -> for (k in o.keys()) feedback[k] = o.getBoolean(k) }
    }
}
