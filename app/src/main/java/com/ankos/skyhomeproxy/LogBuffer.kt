package com.ankos.skyhomeproxy

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LogBuffer {
    private const val MAX_LINES = 500
    private val _logs = mutableListOf<String>()
    private val listeners = mutableListOf<() -> Unit>()
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    val logs: List<String> get() = _logs.toList()

    fun add(tag: String, msg: String) {
        val line = "[${dateFormat.format(Date())} $tag] $msg"
        _logs.add(line)
        if (_logs.size > MAX_LINES) {
            _logs.removeAt(0)
        }
        listeners.forEach { it.invoke() }
    }

    fun clear() {
        _logs.clear()
        listeners.forEach { it.invoke() }
    }

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }
}
