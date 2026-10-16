package com.richizu.fixrkn.tg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.DataInputStream
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.security.SecureRandom

class TgRelayTest {
    private val random = SecureRandom()

    private fun sha256(vararg parts: ByteArray) = MessageDigest.getInstance("SHA-256").run { parts.forEach { update(it) }; digest() }

    /** What a Telegram app does when connecting to a "dd" proxy. */
    private class FakeClient(val init: ByteArray, val enc: CtrStream, val dec: CtrStream)

    private fun fakeClient(secret: ByteArray, dc: Int): FakeClient {
        val init = ByteArray(64)
        do random.nextBytes(init) while (init[0] == 0xef.toByte() || init.copyOfRange(4, 8).all { it == 0.toByte() })
        for (i in 56 until 60) init[i] = 0xdd.toByte()
        init[60] = dc.toByte(); init[61] = (dc shr 8).toByte()
        val enc = CtrStream(sha256(init.copyOfRange(8, 40), secret), init.copyOfRange(40, 56))
        val encrypted = enc.apply(init)
        val wire = init.copyOfRange(0, 56) + encrypted.copyOfRange(56, 64)
        val rev = init.copyOfRange(8, 56).reversedArray()
        val dec = CtrStream(sha256(rev.copyOfRange(0, 32), secret), rev.copyOfRange(32, 48))
        return FakeClient(wire, enc, dec)
    }

    private fun reqPqMulti(): ByteArray {
        val b = ByteBuffer.allocate(40).order(ByteOrder.LITTLE_ENDIAN)
        b.putLong(0)
        b.putLong((System.currentTimeMillis() / 1000) shl 32)
        b.putInt(20)
        b.putInt(0xbe7e8ef1.toInt())
        b.put(ByteArray(16).also { random.nextBytes(it) })
        return b.array()
    }

    @Test
    fun handshakeRoundTrip() {
        val secret = ByteArray(16).also { random.nextBytes(it) }
        val c = fakeClient(secret, -4)
        val hs = ClientHandshake.parse(c.init, secret)!!
        assertEquals(Transport.PaddedIntermediate, hs.transport)
        assertEquals(4, hs.dc)
        assertTrue(hs.media)
        val msg = "hello".toByteArray()
        assertEquals(String(msg), String(hs.decrypt.apply(c.enc.apply(msg))))
        assertEquals(String(msg), String(c.dec.apply(hs.encrypt.apply(msg))))
    }

    @Test
    fun packetCutter() {
        val cutter = PacketCutter(Transport.Intermediate)
        val p1 = byteArrayOf(4, 0, 0, 0, 1, 2, 3, 4)
        val p2 = byteArrayOf(2, 0, 0, 0, 9, 9)
        val all = p1 + p2
        val out = cutter.feed(all.copyOfRange(0, 5)) + cutter.feed(all.copyOfRange(5, all.size))
        assertEquals(2, out.size)
        assertTrue(out[0].contentEquals(p1))
        assertTrue(out[1].contentEquals(p2))
    }

    @Test
    fun cfDomainDecoding() {
        assertEquals("pclead.co.uk", CfFronts.decode("virkgj.com"))
    }

    /** End-to-end through real Telegram servers. Run with RKN_NET_TEST=1. */
    @Test
    fun liveRoutes() {
        assumeTrue(System.getenv("RKN_NET_TEST") == "1")
        val routes = mapOf(
            "direct-ws" to RelayConfig(0, "", mapOf(2 to "149.154.167.220"), 0, cloudflare = false, cloudflareFirst = false),
            "cloudflare" to RelayConfig(0, "", emptyMap(), 0, cloudflare = true, cloudflareFirst = true)
            // The plain TCP fallback uses the same obfuscated2 handshake; add
            // "tcp" to RelayConfig(0, "", emptyMap(), 0, false, false) where raw TCP to Telegram is reachable.
        )
        var port = 18440
        val failures = mutableListOf<String>()
        for ((name, base) in routes) try {
            val secretHex = ByteArray(16).also { random.nextBytes(it) }.joinToString("") { "%02x".format(it) }
            val secret = secretHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            val relay = TgRelay(base.copy(port = ++port, secretHex = secretHex))
            relay.start()
            try {
                Socket("127.0.0.1", port).use { s ->
                    s.soTimeout = 20_000
                    val c = fakeClient(secret, 2)
                    val out = s.getOutputStream()
                    out.write(c.init)
                    val body = reqPqMulti()
                    val packet = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(body.size).array() + body
                    out.write(c.enc.apply(packet))
                    out.flush()
                    val input = DataInputStream(s.getInputStream())
                    val lenRaw = ByteArray(4).also { input.readFully(it) }
                    val len = ByteBuffer.wrap(c.dec.apply(lenRaw)).order(ByteOrder.LITTLE_ENDIAN).int
                    val resp = c.dec.apply(ByteArray(len).also { input.readFully(it) })
                    val ctor = ByteBuffer.wrap(resp, 20, 4).order(ByteOrder.LITTLE_ENDIAN).int
                    println("$name: got ${resp.size} bytes, constructor 0x%08x, stats ${relay.stats()}".format(ctor))
                    assertEquals("resPQ via $name", 0x05162463, ctor)
                }
            } finally {
                relay.stop()
            }
        } catch (e: Throwable) {
            failures += "$name: $e"
        } finally {
            println("---- log after $name ----\n" + com.richizu.fixrkn.core.AppLog.dump())
            com.richizu.fixrkn.core.AppLog.clear()
        }
        assertTrue(failures.joinToString(), failures.isEmpty())
    }
}
