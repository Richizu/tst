package com.richizu.fixrkn.bypass

/** byedpi SOCKS5 proxy (libbyedpi.so, see cpp/bridge/byedpi_jni.c). One instance per process. */
object ByeDpiNative {
    init {
        System.loadLibrary("byedpi")
    }

    /** Runs the proxy on the calling thread until [stop] is called. Returns byedpi's exit code. */
    @JvmStatic external fun run(args: Array<String>): Int

    @JvmStatic external fun stop()

    @JvmStatic external fun forceStop()
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
