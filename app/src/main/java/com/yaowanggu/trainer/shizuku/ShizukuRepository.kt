package com.yaowanggu.trainer.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import com.yaowanggu.trainer.shell.IUserService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku

/**
 * Shizuku 用户服务封装。
 *
 * Shizuku API 13 起 `Shizuku.newProcess` 已不公开，改用官方「用户服务」机制：
 * 把 [IUserService] 实现交给 Shizuku，由 Shizuku 以 shell(uid 2000) 身份实例化
 * 后交回 Binder；之后所有文件操作都在那个进程里执行，
 * 从而读写被 Android 11+ Scoped Storage 限制的 /sdcard/Android/data/... 目录。
 */
object ShizukuRepository {

    private const val TAG = "ShizukuRepository"
    const val GAME_PACKAGE = "com.hydrozoa.yyg"
    const val SERVICE_CLASS = "com.yaowanggu.trainer.shell.ShellUserService"

    data class SaveSlot(val slot: Int, val path: String, val size: Long, val mtimeSec: Long)

    private val _service = MutableStateFlow<IUserService?>(null)
    val service: StateFlow<IUserService?> = _service.asStateFlow()

    private val lock = Any()
    private var pending: CompletableDeferred<IUserService?>? = null
    private var lastArgs: Shizuku.UserServiceArgs? = null
    private var lastConn: ServiceConnection? = null

    // ---------------- binder + permission ----------------

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

    // ---------------- binding the user service ----------------

    /** 确保用户服务已绑定（幂等）。挂起直到拿到服务，失败抛异常。 */
    suspend fun ensureBound(context: Context): IUserService {
        _service.value?.let { return it }
        val d = startBind(context)
        withTimeoutOrNull(20_000) { d.await() }
        _service.value?.let { return it }
        throw IllegalStateException("绑定 Shizuku 用户服务失败：请确认 Shizuku 正在运行并已授权")
    }

    private fun startBind(context: Context): CompletableDeferred<IUserService?> = synchronized(lock) {
        _service.value?.let { s ->
            return@synchronized CompletableDeferred<IUserService?>().apply { complete(s) }
        }
        pending?.let { return@synchronized it }

        val d = CompletableDeferred<IUserService?>()
        pending = d
        val app = context.applicationContext
        val args = Shizuku.UserServiceArgs(
            ComponentName(app.packageName, SERVICE_CLASS)
        ).daemon(false).version(1)
        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                Log.i(TAG, "user service connected")
                val s = IUserService.Stub.asInterface(binder)
                _service.value = s
                synchronized(lock) { pending = null }
                d.complete(s)
            }
            override fun onServiceDisconnected(name: ComponentName) {
                Log.w(TAG, "user service disconnected")
                _service.value = null
                synchronized(lock) { pending = null; lastConn = null }
                if (!d.isCompleted) d.complete(null)
            }
        }
        lastArgs = args
        lastConn = conn
        val result = try {
            Shizuku.bindUserService(args, conn)
            d
        } catch (e: Throwable) {
            Log.e(TAG, "bindUserService failed", e)
            synchronized(lock) { pending = null }
            d.complete(null)
            d
        }
        return@synchronized result
    }

    fun unbind(context: Context) {
        val conn = lastConn ?: return
        try {
            Shizuku.unbindUserService(lastArgs!!, conn, true)
        } catch (e: Throwable) {
            Log.w(TAG, "unbind failed", e)
        }
        _service.value = null
        lastConn = null
    }

    // ---------------- save file ops (shell uid) ----------------

    suspend fun listSaveSlots(context: Context): List<SaveSlot> = withContext(Dispatchers.IO) {
        val s = ensureBound(context)
        s.listSaveSlots().mapNotNull { line ->
            val p = line.split("|")
            if (p.size < 4) return@mapNotNull null
            val slot = p[0].toIntOrNull() ?: return@mapNotNull null
            SaveSlot(slot, p[3], p[1].toLongOrNull() ?: 0L, p[2].toLongOrNull() ?: 0L)
        }.sortedWith(compareBy({ -it.mtimeSec }, { it.slot }))
    }

    suspend fun readFile(context: Context, path: String): ByteArray = withContext(Dispatchers.IO) {
        ensureBound(context).readFile(path)
    }

    suspend fun writeFile(context: Context, path: String, bytes: ByteArray) {
        withContext(Dispatchers.IO) { ensureBound(context).writeFile(path, bytes) }
    }

    suspend fun copyFile(context: Context, from: String, to: String) {
        withContext(Dispatchers.IO) { ensureBound(context).copyFile(from, to) }
    }

    suspend fun gameRunning(context: Context): Boolean = withContext(Dispatchers.IO) {
        runCatching { ensureBound(context).isRunning(GAME_PACKAGE) }.getOrDefault(false)
    }

    suspend fun whoAmI(context: Context): String = withContext(Dispatchers.IO) {
        runCatching { ensureBound(context).id() }.getOrDefault("")
    }
}
