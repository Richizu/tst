package com.richizu.fixrkn.tg

import com.richizu.fixrkn.core.AppLog
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

data class RelayConfig(
    val port: Int,
    val secretHex: String,
    /** DC number -> IP of a reachable Telegram WebSocket endpoint (negative DC = media). */
    val dcIps: Map<Int, String>,
    val poolSize: Int,
    val cloudflare: Boolean,
    val cloudflareFirst: Boolean
)

data class RelayStats(
    val active: Int = 0,
    val total: Long = 0,
    val viaWs: Long = 0,
    val viaCf: Long = 0,
    val viaTcp: Long = 0,
    val failed: Long = 0,
    val bytesUp: Long = 0,
    val bytesDown: Long = 0
)

/**
 * Local MTProto proxy (secret "dd…") that carries Telegram traffic over WebSocket:
 * directly to Telegram's kws endpoints, via Cloudflare fronts, or as a plain TCP fallback.
 */
class TgRelay(@Volatile var config: RelayConfig) {
    companion object {
        private const val TAG = "TgRelay"
        private val DEFAULT_DC_IPS = mapOf(
            1 to "149.154.175.50", 2 to "149.154.167.51", 3 to "149.154.175.100",
            4 to "149.154.167.91", 5 to "149.154.171.5", 203 to "91.105.192.100"
        )
        private const val CONNECT_TIMEOUT = 8000
        private const val POOL_MAX_AGE_MS = 100_000L

        fun parseDcIps(text: String): Map<Int, String> = text.split(',', ';', '\n', ' ')
            .mapNotNull { pair ->
                val (dc, ip) = pair.split(':').takeIf { it.size == 2 } ?: return@mapNotNull null
                val n = dc.trim().toIntOrNull() ?: return@mapNotNull null
                val addr = ip.trim().takeIf { it.matches(Regex("""\d{1,3}(\.\d{1,3}){3}""")) } ?: return@mapNotNull null
                n to addr
            }.toMap()
    }

    private enum class Route { Direct, Cloudflare }

    private var server: ServerSocket? = null
    private val clients = ConcurrentHashMap.newKeySet<Socket>()
    private val pools = ConcurrentHashMap<String, ConcurrentLinkedDeque<WsConnection>>()
    private val usedDcs = ConcurrentHashMap.newKeySet<Int>()
    private val failingUntil = ConcurrentHashMap<Int, Long>()
    private val workers = Executors.newCachedThreadPool { r -> Thread(r, "tg-relay").apply { isDaemon = true } }
    private var timer: ScheduledExecutorService? = null
    @Volatile var cloudflareSuspended = false

    private val active = AtomicInteger()
    private val total = AtomicLong()
    private val viaWs = AtomicLong()
    private val viaCf = AtomicLong()
    private val viaTcp = AtomicLong()
    private val failed = AtomicLong()
    private val up = AtomicLong()
    private val down = AtomicLong()

    fun stats() = RelayStats(active.get(), total.get(), viaWs.get(), viaCf.get(), viaTcp.get(), failed.get(), up.get(), down.get())

    /** Binds the listening socket; throws if the port is busy. */
    fun start() {
        val ss = ServerSocket()
        ss.reuseAddress = true
        ss.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), config.port))
        server = ss
        thread(name = "tg-accept", isDaemon = true) { acceptLoop(ss) }
        timer = Executors.newSingleThreadScheduledExecutor().apply {
            scheduleWithFixedDelay({ runCatching { maintainPools() } }, 2, 20, TimeUnit.SECONDS)
        }
        workers.execute { CfFronts.refreshIfStale() }
        AppLog.i(TAG, "Listening on 127.0.0.1:${config.port}")
    }

    fun stop() {
        runCatching { server?.close() }
        server = null
        timer?.shutdownNow()
        clients.forEach { runCatching { it.close() } }
        clients.clear()
        flushPools()
        workers.shutdownNow()
    }

    /** Drops pre-opened connections (e.g. after the network changed). */
    fun flushPools() {
        pools.values.forEach { q -> while (true) (q.poll() ?: break).close() }
    }

    private fun acceptLoop(ss: ServerSocket) {
        while (!ss.isClosed) {
            val client = try {
                ss.accept()
            } catch (_: IOException) {
                break
            }
            client.tcpNoDelay = true
            clients += client
            try {
                workers.execute { serve(client) }
            } catch (_: Exception) {
                runCatching { client.close() }
            }
        }
    }

    private fun serve(client: Socket) {
        active.incrementAndGet()
        total.incrementAndGet()
        try {
            client.soTimeout = 10_000
            val input = client.getInputStream()
            val init = ByteArray(64)
            if (!readFully(input, init)) return
            if (ClientHandshake.looksLikeHttp(init)) {
                client.getOutputStream().write("HTTP/1.1 404 Not Found\r\nConnection: close\r\n\r\n".toByteArray())
                return
            }
            val secret = config.secretHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            val hs = ClientHandshake.parse(init, secret)
            if (hs == null) {
                failed.incrementAndGet()
                // Hold the bad connection a little: answering instantly helps active probing.
                Thread.sleep(3000)
                return
            }
            client.soTimeout = 0
            val tag = "DC${hs.dc}${if (hs.media) "m" else ""}"
            usedDcs += if (hs.media) -hs.dc else hs.dc
            val upstream = UpstreamHandshake(hs.transport, hs.dc, hs.media)
            val ws = connectWs(hs.dc, hs.media, upstream.init)
            if (ws != null) {
                AppLog.d(TAG, "$tag via ${ws.second} ${ws.first.host}")
                if (ws.second == Route.Cloudflare) viaCf.incrementAndGet() else viaWs.incrementAndGet()
                bridgeWs(client, input, hs, upstream, ws.first)
                return
            }
            val ip = DEFAULT_DC_IPS[hs.dc] ?: DEFAULT_DC_IPS[2]!!
            AppLog.d(TAG, "$tag via TCP $ip")
            val tcp = Socket()
            tcp.tcpNoDelay = true
            tcp.connect(InetSocketAddress(ip, 443), CONNECT_TIMEOUT)
            viaTcp.incrementAndGet()
            tcp.getOutputStream().write(upstream.init)
            bridgeTcp(client, input, hs, upstream, tcp)
        } catch (e: Exception) {
            if (e !is SocketException) AppLog.d(TAG, "Client error: ${e.message}")
        } finally {
            runCatching { client.close() }
            clients -= client
            active.decrementAndGet()
        }
    }

    // --- upstream selection --------------------------------------------------

    private fun wsHosts(dc: Int, media: Boolean): List<String> {
        val effective = if (dc == 203) 2 else dc
        val main = "kws$effective.web.telegram.org"
        val alt = "kws$effective-1.web.telegram.org"
        return if (media) listOf(alt, main) else listOf(main, alt)
    }

    private fun connectWs(dc: Int, media: Boolean, init: ByteArray): Pair<WsConnection, Route>? {
        val cfg = config
        val directIp = (if (media) cfg.dcIps[-dc] else null) ?: cfg.dcIps[dc]
        val direct = directIp != null && (failingUntil[dc] ?: 0) < System.currentTimeMillis()
        val cf = cfg.cloudflare && !cloudflareSuspended
        val order = buildList {
            if (cf && cfg.cloudflareFirst) add(Route.Cloudflare)
            if (direct) add(Route.Direct)
            if (cf && !cfg.cloudflareFirst) add(Route.Cloudflare)
        }
        for (route in order) {
            val ws = takePooled(dc, media, route) ?: when (route) {
                Route.Direct -> openDirect(dc, media, directIp!!)
                Route.Cloudflare -> openCloudflare(dc)
            }
            if (ws != null) {
                try {
                    ws.send(init)
                    return ws to route
                } catch (e: IOException) {
                    ws.close()
                }
            }
        }
        return null
    }

    private fun openDirect(dc: Int, media: Boolean, ip: String): WsConnection? {
        for (host in wsHosts(dc, media)) {
            try {
                return WsConnection.open(ip, host, "/apiws", CONNECT_TIMEOUT)
            } catch (e: Exception) {
                AppLog.d(TAG, "Direct $host@$ip failed: ${e.message}")
            }
        }
        failingUntil[dc] = System.currentTimeMillis() + 30_000
        return null
    }

    /** Races a few fronts with a short stagger; the first successful handshake wins. */
    private fun openCloudflare(dc: Int): WsConnection? {
        val effective = if (dc == 203) 2 else dc
        val candidates = CfFronts.candidates().take(4)
        if (candidates.isEmpty()) return null
        val winner = java.util.concurrent.atomic.AtomicReference<WsConnection?>()
        val pending = AtomicInteger(candidates.size)
        val done = java.util.concurrent.CountDownLatch(1)
        candidates.forEachIndexed { i, domain ->
            workers.execute {
                try {
                    Thread.sleep(i * 600L)
                    if (winner.get() != null) return@execute
                    val host = "kws$effective.$domain"
                    val ip = CfFronts.resolve(host) ?: throw IOException("cannot resolve $host")
                    val ws = WsConnection.open(ip, host, "/apiws", CONNECT_TIMEOUT)
                    if (winner.compareAndSet(null, ws)) {
                        CfFronts.markGood(domain)
                        done.countDown()
                    } else ws.close()
                } catch (e: Exception) {
                    CfFronts.markFailed(domain, (e as? WsHandshakeException)?.status == 429)
                    AppLog.d(TAG, "CF $domain failed: ${e.message}")
                } finally {
                    if (pending.decrementAndGet() == 0) done.countDown()
                }
            }
        }
        done.await(16, TimeUnit.SECONDS)
        return winner.get()
    }

    // --- pool -------------------------------------------------------------------

    private fun poolKey(dc: Int, media: Boolean, route: Route) = "$route:$dc:$media"

    private fun takePooled(dc: Int, media: Boolean, route: Route): WsConnection? {
        val q = pools[poolKey(dc, media, route)] ?: return null
        while (true) {
            val ws = q.poll() ?: return null
            if (!ws.closed && System.currentTimeMillis() - ws.createdAt < POOL_MAX_AGE_MS) return ws
            ws.close()
        }
    }

    /** Keeps a few ready connections for the DCs the client actually uses. */
    private fun maintainPools() {
        val cfg = config
        if (cfg.poolSize <= 0) return
        for (raw in usedDcs.toList()) {
            val dc = kotlin.math.abs(raw)
            val media = raw < 0
            val directIp = (if (media) cfg.dcIps[-dc] else null) ?: cfg.dcIps[dc]
            val route = when {
                cfg.cloudflare && !cloudflareSuspended && (cfg.cloudflareFirst || directIp == null) -> Route.Cloudflare
                directIp != null -> Route.Direct
                else -> continue
            }
            val q = pools.getOrPut(poolKey(dc, media, route)) { ConcurrentLinkedDeque() }
            q.removeIf { ws -> (ws.closed || System.currentTimeMillis() - ws.createdAt > POOL_MAX_AGE_MS).also { if (it) ws.close() } }
            val target = if (media) 1 else cfg.poolSize.coerceAtMost(8)
            repeat(target - q.size) {
                val ws = when (route) {
                    Route.Direct -> openDirect(dc, media, directIp!!)
                    Route.Cloudflare -> openCloudflare(dc)
                } ?: return@repeat
                q.add(ws)
            }
        }
    }

    // --- pumping ------------------------------------------------------------------

    private fun bridgeWs(client: Socket, input: InputStream, hs: ClientHandshake, upstream: UpstreamHandshake, ws: WsConnection) {
        val clientOut = client.getOutputStream()
        val cutter = PacketCutter(hs.transport)
        val downstream = thread(name = "tg-ws-down", isDaemon = true) {
            try {
                while (true) {
                    val msg = ws.receive() ?: break
                    val plain = upstream.decrypt.apply(msg)
                    clientOut.write(hs.encrypt.apply(plain))
                    down.addAndGet(msg.size.toLong())
                }
            } catch (_: IOException) {
            } finally {
                runCatching { client.close() }
            }
        }
        val pinger = timer?.scheduleWithFixedDelay({ runCatching { ws.ping() } }, 30, 30, TimeUnit.SECONDS)
        try {
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                val plain = hs.decrypt.apply(buf, 0, n)
                for (packet in cutter.feed(plain)) {
                    ws.send(upstream.encrypt.apply(packet))
                }
                up.addAndGet(n.toLong())
            }
        } catch (_: IOException) {
        } finally {
            pinger?.cancel(false)
            ws.close()
            downstream.join(1000)
        }
    }

    private fun bridgeTcp(client: Socket, input: InputStream, hs: ClientHandshake, upstream: UpstreamHandshake, tcp: Socket) {
        val clientOut = client.getOutputStream()
        val tgOut: OutputStream = tcp.getOutputStream()
        val downstream = thread(name = "tg-tcp-down", isDaemon = true) {
            try {
                val buf = ByteArray(64 * 1024)
                val tgIn = tcp.getInputStream()
                while (true) {
                    val n = tgIn.read(buf)
                    if (n < 0) break
                    clientOut.write(hs.encrypt.apply(upstream.decrypt.apply(buf, 0, n)))
                    down.addAndGet(n.toLong())
                }
            } catch (_: IOException) {
            } finally {
                runCatching { client.close() }
            }
        }
        try {
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                tgOut.write(upstream.encrypt.apply(hs.decrypt.apply(buf, 0, n)))
                up.addAndGet(n.toLong())
            }
        } catch (_: IOException) {
        } finally {
            runCatching { tcp.close() }
            downstream.join(1000)
        }
    }

    private fun readFully(input: InputStream, buf: ByteArray): Boolean {
        var off = 0
        while (off < buf.size) {
            val n = try {
                input.read(buf, off, buf.size - off)
            } catch (_: IOException) {
                -1
            }
            if (n < 0) return false
            off += n
        }
        return true
    }
}
