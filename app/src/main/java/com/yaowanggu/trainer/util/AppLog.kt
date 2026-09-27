package com.yaowanggu.trainer.util

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 进程内环形缓冲日志（上限 [MAX] 行）。
 * 同时写入 logcat，便于有电脑时用 adb 抓取；无电脑时通过「导出诊断包」带走。
 */
object AppLog {

    private const val TAG = "yaowanggu"
    private const val MAX = 2000

    private val buf = ArrayDeque<String>(MAX)
    private val lock = Any()
    private val fmt = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    fun i(msg: String) = add("I", msg, null)

    fun w(msg: String) = add("W", msg, null)

    fun e(msg: String, t: Throwable? = null) = add("E", msg, t)

    private fun add(level: String, msg: String, t: Throwable?) {
        val ts = synchronized(lock) { fmt.format(Date()) }
        val trace = t?.let { "\n" + it.stackTraceToString() } ?: ""
        val line = "$ts $level $msg$trace"
        when (level) {
            "E" -> Log.e(TAG, msg, t)
            "W" -> Log.w(TAG, msg)
            else -> Log.i(TAG, msg)
        }
        synchronized(lock) {
            buf.addLast(line)
            while (buf.size > MAX) buf.removeFirst()
        }
    }

    fun snapshot(): List<String> = synchronized(lock) { buf.toList() }

    fun snapshotText(): String = snapshot().joinToString("\n")
}
