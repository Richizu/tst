package com.richizu.fixrkn.bypass

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Runs the real byedpi + JNI bridge on the development machine. Build libbyedpi.so for the host
 * (all byedpi sources + bridge/byedpi_jni.c, -Dmain=byedpi_main, a stub android/log.h) and run with
 * RKN_HOST_LIBS=<dir with the .so>.
 */
class ByeDpiHostTest {
    @Test
    fun argsAndSanitizing() {
        assertEquals(listOf("-n", "a b", "-s1"), splitArgs("""-n "a b" -s1"""))
        assertEquals("-s1 -d1", sanitizeStrategy("ciadpi --ip 0.0.0.0 -p 1080 -s1\n-d1"))
        assertEquals("-o1 -a1", sanitizeStrategy("/data/ciadpi -i127.0.0.1 --port=1 -o1 -a1"))
        assertTrue(Strategies.builtIn.size > 60)
        assertEquals(Strategies.builtIn.size, Strategies.builtIn.toSet().size)
    }

    @Test
    fun runsRepeatedlyAndProxiesTraffic() {
        assumeTrue(System.getenv("RKN_HOST_LIBS") != null)
        val prober = Prober()
        runBlocking {
            // Fake-packet strategies depend on the real path to the site, so only the plain
            // split strategy is required to fetch; the others prove restarts work.
            val strategies = listOf(Strategies.builtIn[0], Strategies.builtIn[13], Strategies.builtIn.last(), Strategies.builtIn[0])
            for ((i, strategy) in strategies.withIndex()) {
                val ms = prober.withStrategy(strategy, -2L) { port -> prober.fetch("www.youtube.com", port, 10_000) }
                println("$strategy -> $ms ms")
                assertTrue("byedpi did not start for $strategy", ms != -2L)
                if (System.getenv("RKN_NET_TEST") == "1" && (i == 0 || i == 3)) assertTrue("fetch failed for $strategy", ms >= 0)
            }
            // A second owner must not get the instance while the first holds it.
            val token = ByeDpiNative.begin()
            assertTrue(token > 0)
            assertEquals(-1, ByeDpiNative.begin())
            ByeDpiNative.release(token)
            assertTrue(ByeDpiNative.begin() > 0)
        }
    }
}
