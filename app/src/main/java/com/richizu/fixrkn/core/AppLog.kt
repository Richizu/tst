package com.richizu.fixrkn.core

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** In-memory application log shown on the "Logs" screen, mirrored to logcat. */
object AppLog {
    enum class Level { Debug, Info, Warn, Error }

    data class Entry(val time: Long, val level: Level, val tag: String, val message: String) {
        fun format(): String {
            val stamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(time))
            val mark = when (level) {
                Level.Debug -> "D"
                Level.Info -> "I"
                Level.Warn -> "W"
                Level.Error -> "E"
            }
            return "$stamp $mark/$tag: $message"
        }
    }

    private const val CAPACITY = 2000
    private val buffer = ArrayDeque<Entry>(CAPACITY)
    private val _revision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = _revision

    fun d(tag: String, message: String) = add(Level.Debug, tag, message, null)
    fun i(tag: String, message: String) = add(Level.Info, tag, message, null)
    fun w(tag: String, message: String, error: Throwable? = null) = add(Level.Warn, tag, message, error)
    fun e(tag: String, message: String, error: Throwable? = null) = add(Level.Error, tag, message, error)

    fun entries(): List<Entry> = synchronized(buffer) { buffer.toList() }

    fun clear() {
        synchronized(buffer) { buffer.clear() }
        _revision.value++
    }

    fun dump(): String = entries().joinToString("\n") { it.format() }

    private fun add(level: Level, tag: String, message: String, error: Throwable?) {
        val text = if (error != null) "$message: ${error.javaClass.simpleName}: ${error.message}" else message
        when (level) {
            Level.Debug -> Log.d(tag, text)
            Level.Info -> Log.i(tag, text)
            Level.Warn -> Log.w(tag, text, error)
            Level.Error -> Log.e(tag, text, error)
        }
        synchronized(buffer) {
            if (buffer.size >= CAPACITY) buffer.removeFirst()
            buffer.addLast(Entry(System.currentTimeMillis(), level, tag, text))
        }
        _revision.value++
    }
}
