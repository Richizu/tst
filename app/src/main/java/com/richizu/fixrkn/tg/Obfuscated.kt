package com.richizu.fixrkn.tg

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/** AES-256-CTR keystream (MTProto "obfuscated2" transport). */
class CtrStream(key: ByteArray, iv: ByteArray) {
    private val cipher = Cipher.getInstance("AES/CTR/NoPadding").apply {
        init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
    }

    fun apply(data: ByteArray, offset: Int = 0, length: Int = data.size - offset): ByteArray =
        cipher.update(data, offset, length) ?: ByteArray(0)
}

enum class Transport(val tag: Int) {
    Abridged(0xefefefef.toInt()),
    Intermediate(0xeeeeeeee.toInt()),
    PaddedIntermediate(0xdddddddd.toInt());

    companion object {
        fun of(tag: Int) = entries.firstOrNull { it.tag == tag }
    }
}

private val random = SecureRandom()

private fun sha256(vararg parts: ByteArray): ByteArray =
    MessageDigest.getInstance("SHA-256").run { parts.forEach { update(it) }; digest() }

private fun leInt(b: ByteArray, o: Int) =
    (b[o].toInt() and 0xff) or ((b[o + 1].toInt() and 0xff) shl 8) or
        ((b[o + 2].toInt() and 0xff) shl 16) or ((b[o + 3].toInt() and 0xff) shl 24)

/** Parsed 64-byte handshake of a Telegram client connecting to our "dd"-secret proxy. */
class ClientHandshake(
    val transport: Transport,
    /** Positive DC number. */
    val dc: Int,
    val media: Boolean,
    /** Decrypts what the client sends (keystream already advanced past the handshake). */
    val decrypt: CtrStream,
    /** Encrypts what we send to the client. */
    val encrypt: CtrStream
) {
    companion object {
        fun parse(init: ByteArray, secret: ByteArray): ClientHandshake? {
            if (init.size != 64) return null
            val decrypt = CtrStream(sha256(init.copyOfRange(8, 40), secret), init.copyOfRange(40, 56))
            val plain = decrypt.apply(init)
            val transport = Transport.of(leInt(plain, 56)) ?: return null
            val rawDc = ((plain[60].toInt() and 0xff) or (plain[61].toInt() shl 8)).toShort().toInt()
            val reversed = init.copyOfRange(8, 56).reversedArray()
            val encrypt = CtrStream(sha256(reversed.copyOfRange(0, 32), secret), reversed.copyOfRange(32, 48))
            return ClientHandshake(transport, kotlin.math.abs(rawDc), rawDc < 0, decrypt, encrypt)
        }

        fun looksLikeHttp(init: ByteArray): Boolean {
            val head = String(init, 0, 5, Charsets.ISO_8859_1)
            return head.startsWith("GET ") || head.startsWith("POST ") || head.startsWith("HEAD ") ||
                head.startsWith("PUT ") || head.startsWith("OPTIO")
        }
    }
}

/** Our own obfuscated2 handshake towards Telegram, plus the resulting stream ciphers. */
class UpstreamHandshake(transport: Transport, dc: Int, media: Boolean) {
    val init: ByteArray
    val encrypt: CtrStream
    val decrypt: CtrStream

    init {
        val raw = ByteArray(64)
        do {
            random.nextBytes(raw)
        } while (!acceptable(raw))
        encrypt = CtrStream(raw.copyOfRange(8, 40), raw.copyOfRange(40, 56))
        val reversed = raw.copyOfRange(8, 56).reversedArray()
        decrypt = CtrStream(reversed.copyOfRange(0, 32), reversed.copyOfRange(32, 48))

        val keystreamed = encrypt.apply(raw)
        val tail = ByteArray(8)
        val tag = transport.tag
        for (i in 0 until 4) tail[i] = (tag ushr (8 * i)).toByte()
        val dcIndex = if (media) -dc else dc
        tail[4] = dcIndex.toByte()
        tail[5] = (dcIndex shr 8).toByte()
        tail[6] = random.nextInt().toByte()
        tail[7] = random.nextInt().toByte()
        // Bytes 56..63 travel encrypted: plaintext tail XOR the keystream used for that position.
        for (i in 0 until 8) raw[56 + i] = (tail[i].toInt() xor (keystreamed[56 + i].toInt() xor raw[56 + i].toInt())).toByte()
        init = raw
    }

    private fun acceptable(b: ByteArray): Boolean {
        if (b[0] == 0xef.toByte()) return false
        val first = leInt(b, 0)
        val forbidden = listOf("HEAD", "POST", "GET ", "OPTI").map { leInt(it.toByteArray(), 0) } +
            listOf(0xeeeeeeee.toInt(), 0xdddddddd.toInt(), 0x02010316)
        if (first in forbidden) return false
        return leInt(b, 4) != 0
    }
}

/**
 * Cuts the client's plaintext transport stream into whole MTProto packets so each one can be
 * sent as a separate WebSocket message (Telegram's WebSocket endpoints expect that).
 */
class PacketCutter(private val transport: Transport) {
    private var buf = ByteArray(0)
    /** Set when the stream cannot be parsed; data is then passed through unchanged. */
    var passthrough = false
        private set

    fun feed(data: ByteArray): List<ByteArray> {
        if (passthrough) return listOf(data)
        buf += data
        val out = mutableListOf<ByteArray>()
        while (true) {
            val len = nextLength()
            if (len < 0) break
            if (len == 0) {
                passthrough = true
                out += buf
                buf = ByteArray(0)
                break
            }
            out += buf.copyOfRange(0, len)
            buf = buf.copyOfRange(len, buf.size)
        }
        return out
    }

    private fun nextLength(): Int {
        if (buf.isEmpty()) return -1
        return when (transport) {
            Transport.Abridged -> {
                val first = buf[0].toInt() and 0x7f
                val (header, payload) = if (first == 0x7f) {
                    if (buf.size < 4) return -1
                    4 to (((buf[1].toInt() and 0xff) or ((buf[2].toInt() and 0xff) shl 8) or ((buf[3].toInt() and 0xff) shl 16)) * 4)
                } else 1 to first * 4
                when {
                    payload <= 0 -> 0
                    buf.size < header + payload -> -1
                    else -> header + payload
                }
            }
            Transport.Intermediate, Transport.PaddedIntermediate -> {
                if (buf.size < 4) return -1
                val payload = leInt(buf, 0) and 0x7fffffff
                when {
                    payload <= 0 || payload > 16 * 1024 * 1024 -> 0
                    buf.size < 4 + payload -> -1
                    else -> 4 + payload
                }
            }
        }
    }
}
