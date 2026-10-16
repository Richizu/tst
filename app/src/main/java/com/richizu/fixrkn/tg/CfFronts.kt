package com.richizu.fixrkn.tg

import android.content.Context
import com.richizu.fixrkn.core.AppLog
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * Community-run Cloudflare Worker fronts that relay Telegram's WebSocket API
 * (kws<DC>.<domain>/apiws). The list is published by the tg-ws-proxy project
 * (github.com/Flowseal/tg-ws-proxy, MIT) in a lightly obfuscated form.
 */
object CfFronts {
    private const val TAG = "CfFronts"
    private const val LIST_URL = "https://raw.githubusercontent.com/Flowseal/tg-ws-proxy/main/.github/cfproxy-domains.txt"
    private const val REFRESH_MS = 12 * 3600 * 1000L
    private const val COOLDOWN_MS = 60_000L

    private val builtIn = listOf(
        "virkgj.com", "vmmzovy.com", "mkuosckvso.com", "zaewayzmplad.com", "twdmbzcm.com",
        "awzwsldi.com", "clngqrflngqin.com", "tjacxbqtj.com", "bxaxtxmrw.com", "dmohrsgmohcrwb.com",
        "vwbmtmoi.com", "khgrre.com", "ulihssf.com", "tmhqsdqmfpmk.com", "xwuwoqbm.com"
    )

    @Volatile private var domains: List<String> = builtIn.map(::decode)
    @Volatile var preferred: String? = null
    private val cooldownUntil = ConcurrentHashMap<String, Long>()
    private val dnsCache = ConcurrentHashMap<String, Pair<String, Long>>()
    private var cacheFile: File? = null

    /** Encoded entries are "<letters rotated by their count>.com" standing for "<name>.co.uk". */
    fun decode(entry: String): String {
        val e = entry.trim().lowercase()
        if (!e.endsWith(".com")) return e
        val body = e.removeSuffix(".com")
        val shift = body.count { it in 'a'..'z' } % 26
        val name = body.map { c -> if (c in 'a'..'z') 'a' + ((c - 'a' - shift + 26) % 26) else c }.joinToString("")
        return "$name.co.uk"
    }

    fun init(context: Context) {
        val file = File(context.filesDir, "cf-fronts.txt")
        cacheFile = file
        if (file.exists()) {
            val cached = file.readLines().map { it.trim() }.filter { it.isNotEmpty() }
            if (cached.isNotEmpty()) domains = (cached + domains).distinct()
        }
    }

    /** Refreshes the list from GitHub at most every 12 hours. Blocking. */
    fun refreshIfStale() {
        val file = cacheFile ?: return
        if (file.exists() && System.currentTimeMillis() - file.lastModified() < REFRESH_MS) return
        try {
            val conn = URL(LIST_URL).openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            val fresh = text.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.map(::decode)
            if (fresh.isNotEmpty()) {
                domains = (fresh + builtIn.map(::decode)).distinct()
                file.writeText(fresh.joinToString("\n"))
                AppLog.i(TAG, "Updated list: ${fresh.size} domains")
            }
        } catch (e: Exception) {
            AppLog.d(TAG, "List refresh failed: ${e.message}")
        }
    }

    /** Domains to try, the last successful one first, skipping those in cooldown. */
    fun candidates(): List<String> {
        val now = System.currentTimeMillis()
        val usable = domains.filter { (cooldownUntil[it] ?: 0) < now }
        val first = preferred?.takeIf { it in usable }
        return listOfNotNull(first) + usable.filter { it != first }.shuffled()
    }

    fun markFailed(domain: String, rateLimited: Boolean) {
        cooldownUntil[domain] = System.currentTimeMillis() + if (rateLimited) COOLDOWN_MS * 3 else COOLDOWN_MS
        if (preferred == domain) preferred = null
    }

    fun markGood(domain: String) {
        cooldownUntil.remove(domain)
        preferred = domain
    }

    fun resetCooldowns() = cooldownUntil.clear()

    /**
     * Resolves via DNS-over-HTTPS first (system DNS may be poisoned), then the system resolver.
     */
    fun resolve(host: String): String? {
        dnsCache[host]?.let { (ip, until) -> if (until > System.currentTimeMillis()) return ip }
        val resolvers = listOf(
            "https://cloudflare-dns.com/dns-query?name=%s&type=A",
            "https://dns.google/resolve?name=%s&type=A"
        )
        for (r in resolvers) {
            try {
                val conn = URL(r.format(host)).openConnection() as HttpURLConnection
                conn.connectTimeout = 4000
                conn.readTimeout = 4000
                conn.setRequestProperty("Accept", "application/dns-json")
                val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                val answers = json.optJSONArray("Answer") ?: continue
                for (i in 0 until answers.length()) {
                    val a = answers.getJSONObject(i)
                    if (a.optInt("type") == 1) {
                        val ip = a.getString("data")
                        dnsCache[host] = ip to System.currentTimeMillis() + maxOf(60, a.optInt("TTL", 300)) * 1000L
                        return ip
                    }
                }
            } catch (_: Exception) {
            }
        }
        return runCatching { InetAddress.getByName(host).hostAddress }.getOrNull()
    }
}
