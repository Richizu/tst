package com.richizu.fixrkn.tg

import java.util.Base64
import java.io.BufferedInputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.security.SecureRandom
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

class WsHandshakeException(val status: Int, message: String) : IOException(message)

/**
 * Minimal WebSocket client (RFC 6455, binary messages only) over TLS.
 * Connects to [ip] while presenting [host] as SNI/Host, so a blocked DNS name is not needed.
 */
class WsConnection private constructor(
    private val socket: SSLSocket,
    private val input: InputStream,
    private val output: OutputStream,
    val host: String
) {
    companion object {
        private val random = SecureRandom()

        fun open(ip: String, host: String, path: String, timeoutMs: Int): WsConnection {
            val raw = Socket()
            try {
                raw.tcpNoDelay = true
                raw.connect(InetSocketAddress(ip, 443), timeoutMs)
                raw.soTimeout = timeoutMs
                val tls = (SSLSocketFactory.getDefault() as SSLSocketFactory).createSocket(raw, host, 443, true) as SSLSocket
                // Verify the certificate against the host name (we connect by IP).
                tls.sslParameters = tls.sslParameters.apply { endpointIdentificationAlgorithm = "HTTPS" }
                tls.startHandshake()
                val keyBytes = ByteArray(16).also { random.nextBytes(it) }
                val key = Base64.getEncoder().encodeToString(keyBytes)
                val request = "GET $path HTTP/1.1\r\n" +
                    "Host: $host\r\n" +
                    "Upgrade: websocket\r\n" +
                    "Connection: Upgrade\r\n" +
                    "Sec-WebSocket-Key: $key\r\n" +
                    "Sec-WebSocket-Version: 13\r\n" +
                    "Sec-WebSocket-Protocol: binary\r\n\r\n"
                val out = tls.outputStream
                out.write(request.toByteArray(Charsets.ISO_8859_1))
                out.flush()
                val input = BufferedInputStream(tls.inputStream, 64 * 1024)
                val statusLine = readLine(input)
                val status = statusLine.split(' ').getOrNull(1)?.toIntOrNull() ?: 0
                while (readLine(input).isNotEmpty()) Unit
                if (status != 101) throw WsHandshakeException(status, "HTTP $status from $host")
                tls.soTimeout = 0
                return WsConnection(tls, input, out, host)
            } catch (e: Exception) {
                runCatching { raw.close() }
                throw e
            }
        }

        private fun readLine(input: InputStream): String {
            val sb = StringBuilder()
            while (true) {
                val c = input.read()
                if (c < 0) throw EOFException("connection closed during handshake")
                if (c == '\n'.code) break
                if (c != '\r'.code) sb.append(c.toChar())
                if (sb.length > 8192) throw IOException("header line too long")
            }
            return sb.toString()
        }
    }

    val createdAt = System.currentTimeMillis()
    @Volatile var closed = false
        private set

    fun send(payload: ByteArray) = sendFrame(0x2, payload)

    fun ping() = sendFrame(0x9, ByteArray(0))

    @Synchronized
    private fun sendFrame(opcode: Int, payload: ByteArray) {
        val len = payload.size
        val header = when {
            len < 126 -> byteArrayOf((0x80 or opcode).toByte(), (0x80 or len).toByte())
            len < 65536 -> byteArrayOf((0x80 or opcode).toByte(), (0x80 or 126).toByte(), (len shr 8).toByte(), len.toByte())
            else -> ByteArray(10).also {
                it[0] = (0x80 or opcode).toByte(); it[1] = (0x80 or 127).toByte()
                for (i in 0 until 8) it[9 - i] = (len.toLong() ushr (8 * i)).toByte()
            }
        }
        val mask = ByteArray(4).also { random.nextBytes(it) }
        val body = ByteArray(len)
        for (i in 0 until len) body[i] = (payload[i].toInt() xor mask[i and 3].toInt()).toByte()
        output.write(header + mask + body)
        output.flush()
    }

    /** Reads the next data message (control frames are handled internally). Null when closed. */
    fun receive(): ByteArray? {
        var message: ByteArray? = null
        while (true) {
            val b0 = input.read()
            if (b0 < 0) return null
            val b1 = input.read()
            if (b1 < 0) return null
            val fin = b0 and 0x80 != 0
            val opcode = b0 and 0x0f
            var len = (b1 and 0x7f).toLong()
            if (len == 126L) len = ((read() shl 8) or read()).toLong()
            else if (len == 127L) {
                len = 0
                repeat(8) { len = (len shl 8) or read().toLong() }
            }
            val masked = b1 and 0x80 != 0
            val mask = if (masked) ByteArray(4).also { readFully(it) } else null
            if (len > 32L * 1024 * 1024) throw IOException("frame too large")
            val data = ByteArray(len.toInt()).also { readFully(it) }
            if (mask != null) for (i in data.indices) data[i] = (data[i].toInt() xor mask[i and 3].toInt()).toByte()
            when (opcode) {
                0x8 -> { closed = true; return null }
                0x9 -> sendFrame(0xA, data)
                0xA -> Unit
                0x0, 0x1, 0x2 -> {
                    message = if (message == null) data else message + data
                    if (fin) return message
                }
            }
        }
    }

    private fun read(): Int = input.read().also { if (it < 0) throw EOFException() }

    private fun readFully(buf: ByteArray) {
        var off = 0
        while (off < buf.size) {
            val n = input.read(buf, off, buf.size - off)
            if (n < 0) throw EOFException()
            off += n
        }
    }

    fun close() {
        if (closed) return
        closed = true
        runCatching { sendFrame(0x8, ByteArray(0)) }
        runCatching { socket.close() }
    }
}
