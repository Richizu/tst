package com.richizu.fixrkn.bypass

/**
 * Built-in byedpi strategies, generated from a small set of desync primitives.
 * Ordered from the cheapest/most common techniques to heavier combinations.
 */
object Strategies {
    private const val FAKE_SNI = "www.google.com"

    /** Ways to desync the first TLS/HTTP packet. */
    private val primary = listOf(
        "-s1+s",
        "-d1+s",
        "-o1+s",
        "-q1+s",
        "-s1:3+sm",
        "-d1:3+sm",
        "-s0+sm",
        "-d1 -s1+s",
        "-s1 -d3+s",
        "-o1 -d1+s",
        "-q1 -s5+s",
        "-d1 -d3+s -s6+s -d9+s -s12+s",
        "-f-1 -t8",
        "-f1+s -t6",
        "-f-1 -Qr -n $FAKE_SNI",
        "-d1 -f-1 -t8 -n $FAKE_SNI",
        "-f1+nme -t6",
        "-s1+s -f-1 -t5 -Qr",
        "-o1 -f-1 -S",
        "-d1+s -f-1 -S"
    )

    /** TLS record splitting added on top. */
    private val records = listOf("", "-r1+s", "-r3+s", "-r-5+se")

    /** Two-stage strategies: the second group kicks in when byedpi detects a block (torst/redirect/ssl_err). */
    private val chained = listOf(
        "-s1+s" to "-d1 -f-1 -t8",
        "-d1+s" to "-f-1 -Qr -n $FAKE_SNI",
        "-o1+s" to "-d1 -r1+s",
        "-s1:3+sm" to "-f1+s -t6 -r1+s",
        "-q1+s" to "-s1 -d3+s -r3+s",
        "-d1:3+sm" to "-o1 -f-1 -S",
        "-f-1 -t8" to "-s1+s -r-5+se",
        "-s0+sm -r1+s" to "-d1 -f-1 -t6 -n $FAKE_SNI"
    )

    private fun group(body: String) = "$body -a1"

    val builtIn: List<String> by lazy {
        val out = LinkedHashSet<String>()
        for (record in records) {
            for (p in primary) out += group(listOf(p, record).filter { it.isNotEmpty() }.joinToString(" "))
        }
        for ((first, second) in chained) out += group(first) + " -At,r,s " + group(second)
        out.toList()
    }
}
