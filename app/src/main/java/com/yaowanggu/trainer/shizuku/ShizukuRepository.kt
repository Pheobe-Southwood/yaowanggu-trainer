package com.yaowanggu.trainer.shizuku

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

/**
 * Shizuku 统一入口：选后端 + 通用操作 + 诊断。
 *
 * 后端优先级：remote-process（ShizukuBinderWrapper + newProcess，公开 API，
 * adb/root 两种后端都可用）优先；user-service 兜底。
 */
object ShizukuRepository {

    private const val TAG = "ShizukuRepository"

    data class Diagnostics(
        val binderAlive: Boolean,
        val permissionOk: Boolean,
        val serverVersion: Int,
        val serverUid: Int,
        val selinuxContext: String,
        val activeBackend: String,
        val notes: List<String>,
        val disabledReason: String,
    )

    @Volatile
    private var backend: ShellBackend? = null

    @Volatile
    private var lastError: Throwable? = null

    /** 当前生效后端名（user-service / remote-process / unknown） */
    fun activeBackendName(): String = backend?.name ?: "unknown"

    fun binderAlive(): Boolean = try { Shizuku.pingBinder() } catch (e: Throwable) { false }

    fun permissionGranted(): Boolean = try {
        binderAlive() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (e: Throwable) { false }

    fun requestPermission(listener: (Boolean) -> Unit) {
        if (!binderAlive()) { listener(false); return }
        if (permissionGranted()) { listener(true); return }
        val l = object : Shizuku.OnRequestPermissionResultListener {
            override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                Shizuku.removeRequestPermissionResultListener(this)
                listener(grantResult == PackageManager.PERMISSION_GRANTED)
            }
        }
        Shizuku.addRequestPermissionResultListener(l)
        Shizuku.requestPermission(1001)
    }

    /** 重置后端（切后端/重试按钮用） */
    fun resetBackend() {
        backend = null
        lastError = null
    }

    /** 取得后端：先远端进程，再用户服务兜底。 */
    suspend fun backendFor(context: Context): ShellBackend {
        backend?.let { return it }

        try {
            val rp = RemoteProcessBackend()
            rp.exec("true")
            backend = rp
            lastError = null
            return rp
        } catch (e: Throwable) {
            Log.w(TAG, "remote process backend unavailable", e)
            lastError = e
        }

        try {
            val us = UserServiceBackend(context)
            us.ensureBound()
            backend = us
            lastError = null
            return us
        } catch (e: Throwable) {
            Log.w(TAG, "user service backend unavailable", e)
            lastError = e
        }

        throw IllegalStateException("两条通道都不可用（remote-process: ${describe(lastError)}）")
    }

    private fun describe(t: Throwable?): String =
        t?.let { "${it::class.java.simpleName}: ${it.message}" } ?: "未记录"

    // ---------------- 操作 ----------------

    suspend fun listSaveSlots(context: Context): List<ShellBackend.Slot> =
        backendFor(context).listSaveSlots()

    suspend fun readFile(context: Context, path: String): ByteArray =
        backendFor(context).readFile(path)

    suspend fun writeFile(context: Context, path: String, bytes: ByteArray) =
        backendFor(context).writeFile(path, bytes)

    suspend fun copyFile(context: Context, from: String, to: String) =
        backendFor(context).copyFile(from, to)

    suspend fun gameRunning(context: Context): Boolean =
        runCatching { backendFor(context).isRunning(ShellBackend.GAME_PACKAGE) }.getOrDefault(false)

    suspend fun whoAmI(context: Context): String =
        runCatching { backendFor(context).id() }.getOrDefault("")

    // ---------------- 诊断 ----------------

    fun diagnose(context: Context): Diagnostics {
        val notes = mutableListOf<String>()
        val alive = binderAlive()
        val perm = permissionGranted()
        val ver = if (alive) try { Shizuku.getVersion() } catch (e: Throwable) { -1 } else -1
        val uid = if (alive) try { Shizuku.getUid() } catch (e: Throwable) { -1 } else -1
        val ctx = if (alive) {
            try { Shizuku.getSELinuxContext() ?: "" } catch (e: Throwable) { "获取失败: ${e.message}" }
        } else ""

        if (!alive) notes += "Shizuku binder 未就绪：请先打开 Shizuku 并完成配对"
        else if (!perm) notes += "权限未授予：点「授权 Shizuku」"
        if (alive && ver in 0..10) notes += "服务端版本 $ver < 11，新特性不受支持"
        when (uid) {
            0 -> notes += "当前是 root(uid 0) 后端（Sui/Magisk 或 su 启动）"
            2000 -> notes += "当前是 adb shell(uid 2000) 后端"
            -1 -> if (alive) notes += "取不到服务端 uid（服务端实现可能不完整，如旧版 Sui）"
        }
        if (ctx.isNotBlank() && ctx.count { it == ':' } < 3) notes += "SELinux 上下文异常：$ctx"

        val disabled = describe(lastError)
        if (lastError != null) notes += "已禁用通道的错误：$disabled"

        return Diagnostics(
            binderAlive = alive,
            permissionOk = perm,
            serverVersion = ver,
            serverUid = uid,
            selinuxContext = ctx,
            activeBackend = activeBackendName(),
            notes = notes,
            disabledReason = disabled,
        )
    }

    /** 诊断页用：把当前存档探测命令跑一遍看看有没有文件 */
    suspend fun probeSaves(context: Context): String {
        return try {
            val b = backendFor(context)
            val out = b.exec(ShellBackend.listSavesCmd())
            if (out.isBlank()) {
                val dirs = b.exec(
                    "ls -la /sdcard/Android/data/com.hydrozoa.yyg/files 2>&1 | head -20"
                )
                "（没有找到 nfile*.save）\n$dirs"
            } else out
        } catch (e: Throwable) {
            "探测失败：${e::class.java.simpleName}: ${e.message}"
        }
    }
}
