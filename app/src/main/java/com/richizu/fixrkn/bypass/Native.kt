package com.richizu.fixrkn.bypass

/**
 * byedpi SOCKS5 proxy (libbyedpi.so, see cpp/bridge/byedpi_jni.c). Only one instance can
 * run per process: [begin] claims it and returns a token, [run] blocks until [stop].
 */
object ByeDpiNative {
    init {
        System.loadLibrary("byedpi")
    }

    /** Claims the instance; returns a token, or -1 when another owner is running. */
    @JvmStatic external fun begin(): Int

    /** Runs the proxy on the calling thread until [stop]. Returns byedpi's exit code. */
    @JvmStatic external fun run(token: Int, args: Array<String>): Int

    /** Gives the instance back when [run] was never called after [begin]. */
    @JvmStatic external fun release(token: Int)

    @JvmStatic external fun stop(token: Int)

    @JvmStatic external fun forceStop(token: Int)
}

/** hev-socks5-tunnel: forwards the VPN TUN interface into a SOCKS5 proxy. */
object Tun2Socks {
    init {
        System.loadLibrary("hev-socks5-tunnel")
    }

    @JvmStatic external fun TProxyStartService(configPath: String, fd: Int, dropQuic: Boolean): Boolean

    @JvmStatic external fun TProxyStopService(): Boolean

    @JvmStatic external fun TProxyIsRunning(): Boolean

    /** [txPackets, txBytes, rxPackets, rxBytes] */
    @JvmStatic external fun TProxyGetStats(): LongArray
}
