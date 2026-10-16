package com.richizu.fixrkn.bypass

/** Splits a command line into arguments, honouring single/double quotes. */
fun splitArgs(line: String): List<String> {
    val out = mutableListOf<String>()
    val cur = StringBuilder()
    var quote: Char? = null
    var hasToken = false
    var i = 0
    while (i < line.length) {
        val c = line[i]
        when {
            quote != null && c == '\\' && i + 1 < line.length && line[i + 1] == quote -> {
                cur.append(quote); i++
            }
            quote != null && c == quote -> quote = null
            quote == null && (c == '"' || c == '\'') -> {
                quote = c; hasToken = true
            }
            quote == null && c.isWhitespace() -> {
                if (hasToken || cur.isNotEmpty()) out += cur.toString()
                cur.clear(); hasToken = false
            }
            else -> cur.append(c)
        }
        i++
    }
    if (hasToken || cur.isNotEmpty()) out += cur.toString()
    return out
}

private val listenFlags = setOf("-i", "--ip", "-p", "--port")
private val ignoredFlags = setOf("-h", "--help", "-v", "--version", "-D", "--daemon", "-E", "--transparent")

/**
 * Cleans a strategy pasted by the user (e.g. copied from ByeByeDPI or a forum):
 * drops the binary name and listen address/port, joins lines.
 */
fun sanitizeStrategy(raw: String): String {
    val tokens = splitArgs(raw.replace('\n', ' ').trim()).toMutableList()
    if (tokens.firstOrNull()?.substringAfterLast('/')?.let { it == "ciadpi" || it == "byedpi" } == true) {
        tokens.removeAt(0)
    }
    val kept = mutableListOf<String>()
    var skipValue = false
    for (t in tokens) {
        when {
            skipValue -> skipValue = false
            t in listenFlags -> skipValue = true
            t in ignoredFlags -> Unit
            t.startsWith("--ip=") || t.startsWith("--port=") -> Unit
            !t.startsWith("--") && (t.startsWith("-i") || t.startsWith("-p")) && t.length > 2 -> Unit
            else -> kept += if (t.any { it.isWhitespace() }) "\"$t\"" else t
        }
    }
    return kept.joinToString(" ")
}

fun byeDpiArgs(strategy: String, port: Int): Array<String> =
    (listOf("ciadpi", "--ip", "127.0.0.1", "--port", port.toString()) + splitArgs(strategy)).toTypedArray()
