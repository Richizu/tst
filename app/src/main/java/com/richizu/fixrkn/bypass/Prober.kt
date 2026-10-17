package com.richizu.fixrkn.bypass

import com.richizu.fixrkn.core.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.net.URL
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

/** Starts byedpi with a given strategy on a private port and checks sites through it. */
class Prober {
    companion object {
        private const val TAG = "Prober"
        private const val PORT = 10881
        private const val BODY_LIMIT = 96 * 1024L
        private const val PARALLEL = 24

        val YOUTUBE_HOSTS = listOf(
            "www.youtube.com",
            "m.youtube.com",
            "youtubei.googleapis.com",
            "i.ytimg.com",
            "yt3.ggpht.com",
            "redirector.googlevideo.com",
            "manifest.googlevideo.com"
        )
        val DISCORD_HOSTS = listOf(
            "discord.com",
            "gateway.discord.gg",
            "cdn.discordapp.com",
            "media.discordapp.net"
        )
        /** Small set for the first pass over all strategies. */
        val QUICK_HOSTS = listOf(
            "www.youtube.com",
            "youtubei.googleapis.com",
            "i.ytimg.com",
            "yt3.ggpht.com",
            "redirector.googlevideo.com",
            "manifest.googlevideo.com",
            "discord.com",
            "cdn.discordapp.com"
        )
        /** Wider set for the finalists. */
        val FULL_HOSTS = YOUTUBE_HOSTS + listOf(
            "www.instagram.com",
            "static.cdninstagram.com",
            "scontent.cdninstagram.com",
            "www.facebook.com",
            "youtu.be",
            "music.youtube.com",
            "i9.ytimg.com",
            "yt4.ggpht.com",
            "jnn-pa.googleapis.com",
            "yt3.googleusercontent.com",
            "play.googleapis.com"
        ) + DISCORD_HOSTS + listOf("discordapp.com", "dl.discordapp.net")
    }

    private val open: MutableSet<HttpURLConnection> = Collections.newSetFromMap(ConcurrentHashMap())

    /** Cancels in-flight HTTP checks (used when the user stops a scan). */
    fun abort() {
        open.toList().forEach { runCatching { it.disconnect() } }
    }

    /** The single byedpi instance is held by someone else (the VPN or another probe). */
    class BusyException : Exception("byedpi is busy")

    suspend fun <T> withStrategy(strategy: String, fallback: T, block: suspend (port: Int) -> T): T =
        withContext(Dispatchers.IO) {
            var token = ByeDpiNative.begin()
            var waited = 0
            while (token < 0 && waited < 4000) {
                delay(200); waited += 200
                token = ByeDpiNative.begin()
            }
            if (token < 0) throw BusyException()
            var exitCode = 0
            val worker = thread(name = "byedpi-probe", isDaemon = true) {
                exitCode = ByeDpiNative.run(token, byeDpiArgs(strategy, PORT))
            }
            try {
                if (waitForPort()) block(PORT) else {
                    AppLog.w(TAG, "byedpi did not start (code $exitCode) for: $strategy")
                    fallback
                }
            } finally {
                withContext(NonCancellable) {
                    ByeDpiNative.stop(token)
                    worker.join(2000)
                    if (worker.isAlive) {
                        ByeDpiNative.forceStop(token)
                        worker.join(2000)
                    }
                    delay(120)
                }
            }
        }

    suspend fun countReachable(hosts: List<String>, port: Int, timeoutMs: Int): Int = coroutineScope {
        val gate = Semaphore(PARALLEL)
        hosts.map { host -> async(Dispatchers.IO) { gate.withPermit { fetch(host, port, timeoutMs) >= 0 } } }
            .awaitAll().count { it }
    }

    suspend fun probe(strategy: String): ProbeResult {
        val empty = ProbeResult(0, YOUTUBE_HOSTS.size, 0, DISCORD_HOSTS.size, -1)
        return withStrategy(strategy, empty) { port ->
            coroutineScope {
                val yt = YOUTUBE_HOSTS.map { async(Dispatchers.IO) { fetch(it, port, 6000) } }
                val dc = DISCORD_HOSTS.map { async(Dispatchers.IO) { fetch(it, port, 6000) } }
                val ytMs = yt.awaitAll()
                val dcMs = dc.awaitAll()
                val ok = (ytMs + dcMs).filter { it >= 0 }
                ProbeResult(
                    youtubeOk = ytMs.count { it >= 0 },
                    youtubeTotal = ytMs.size,
                    discordOk = dcMs.count { it >= 0 },
                    discordTotal = dcMs.size,
                    averageMs = if (ok.isEmpty()) -1 else ok.average().toLong()
                )
            }
        }
    }

    private suspend fun waitForPort(): Boolean {
        val deadline = System.currentTimeMillis() + 2000
        while (System.currentTimeMillis() < deadline) {
            val up = runCatching {
                Socket().use { it.connect(InetSocketAddress("127.0.0.1", PORT), 100) }
            }.isSuccess
            if (up) return true
            delay(40)
        }
        return false
    }

    /**
     * Downloads the start of https://host/ through the SOCKS proxy. DPI often lets the
     * handshake through and stalls the transfer later, so a site only counts as reachable
     * when the body arrives completely (or the first [BODY_LIMIT] bytes of it).
     * Returns elapsed milliseconds, or -1.
     */
    fun fetch(host: String, port: Int, timeoutMs: Int): Long {
        val started = System.currentTimeMillis()
        var conn: HttpURLConnection? = null
        return try {
            val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", port))
            conn = (URL("https://$host/").openConnection(proxy) as HttpURLConnection).also { open += it }
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126 Mobile Safari/537.36")
            conn.setRequestProperty("Connection", "close")
            val code = conn.responseCode
            if (code <= 0) return -1
            val declared = conn.contentLengthLong
            val want = if (declared > 0) minOf(declared, BODY_LIMIT) else BODY_LIMIT
            var got = 0L
            val stream = (if (code < 400) conn.inputStream else conn.errorStream)
            var eof = stream == null
            stream?.use {
                val buf = ByteArray(16 * 1024)
                while (got < want) {
                    val n = it.read(buf, 0, minOf(buf.size.toLong(), want - got).toInt())
                    if (n < 0) { eof = true; break }
                    got += n
                }
            }
            if (got >= want || (eof && declared <= 0)) System.currentTimeMillis() - started else -1
        } catch (_: IOException) {
            -1
        } catch (_: RuntimeException) {
            -1
        } finally {
            conn?.let { open -= it; it.disconnect() }
        }
    }
}
