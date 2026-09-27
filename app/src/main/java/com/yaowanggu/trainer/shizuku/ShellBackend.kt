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

/** 后端抽象：不管走「用户服务」还是「远端进程」，上层只面对这一组操作。 */
interface ShellBackend {

    /** 用于 UI 显示当前通道 */
    val name: String

    suspend fun exec(cmd: String): String
    suspend fun readFile(path: String): ByteArray
    suspend fun writeFile(path: String, data: ByteArray)
    suspend fun copyFile(from: String, to: String)
    suspend fun listSaveSlots(): List<Slot>
    suspend fun isRunning(packageName: String): Boolean
    suspend fun id(): String

    data class Slot(val slot: Int, val path: String, val size: Long, val mtimeSec: Long)

    companion object {
        const val GAME_PACKAGE = "com.hydrozoa.yyg"
        const val SERVICE_CLASS = "com.yaowanggu.trainer.shell.ShellUserService"
        private const val GAME_DIR = "/sdcard/Android/data/com.hydrozoa.yyg/files"
        private const val GAME_DATA = "/sdcard/Android/data/com.hydrozoa.yyg"
        private const val GAME_MEDIA = "/sdcard/Android/media/com.hydrozoa.yyg"

        /**
         * 依次探测几个可能的存档目录，输出 "slot|size|mtime|path"。
         * 第一个含 nfile*.save 的目录胜出；都不命中则全局 find。
         */
        fun listSavesCmd(): String = buildString {
            append(slotFnCmd())
            append("; ")
            append("dirs='$GAME_DIR $GAME_DATA $GAME_MEDIA/files /sdcard/yaowanggu'; ")
            append("for d in \$dirs; do ")
            append("if ls \$d/nfile*.save >/dev/null 2>&1; then ")
            append("listSlots \$d; exit 0; fi; ")
            append("done; ")
            append("listSlots_find '$GAME_DATA' '$GAME_MEDIA' /sdcard")
        }

        /** shell 函数定义：listSlots <dir> 与 listSlots_find <dirs...> */
        private fun slotFnCmd(): String = buildString {
            append("listSlots() { ")
            append("d=\$1; ")
            append("for f in \$d/nfile*.save; do ")
            append("[ -f \"\$f\" ] || continue; ")
            append("s=\${f##*/nfile}; s=\${s%.save}; ")
            append("b=\$(stat -c %s \"\$f\" 2>/dev/null || echo 0); ")
            append("m=\$(stat -c %Y \"\$f\" 2>/dev/null || echo 0); ")
            append("echo \"\$s|\$b|\$m|\$f\"; ")
            append("done; }; ")
            append("listSlots_find() { ")
            append("find \$* 2>/dev/null | grep -E '/nfile[0-9]+\\.save$' | while read -r f; do ")
            append("s=\${f##*/nfile}; s=\${s%.save}; ")
            append("b=\$(stat -c %s \"\$f\" 2>/dev/null || echo 0); ")
            append("m=\$(stat -c %Y \"\$f\" 2>/dev/null || echo 0); ")
            append("echo \"\$s|\$b|\$m|\$f\"; ")
            append("done; }; ")
        }

        fun parseSlots(out: String): List<Slot> = out.lineSequence()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val p = line.split("|")
                if (p.size < 4) return@mapNotNull null
                val slot = p[0].trim().toIntOrNull() ?: return@mapNotNull null
                Slot(slot, p[3], p[1].trim().toLongOrNull() ?: 0L, p[2].trim().toLongOrNull() ?: 0L)
            }
            .sortedWith(compareBy({ -it.mtimeSec }, { it.slot }))
            .toList()
    }
}

/** 用户服务后端：Shizuku 把 [IUserService] 实现实例化在自己的进程里并以 uid 2000 运行。 */
class UserServiceBackend(private val context: Context) : ShellBackend {

    override val name: String = "user-service"

    private val _service = MutableStateFlow<IUserService?>(null)
    val service: StateFlow<IUserService?> = _service.asStateFlow()

    private val lock = Any()
    private var pending: CompletableDeferred<IUserService?>? = null
    private var lastArgs: Shizuku.UserServiceArgs? = null
    private var lastConn: ServiceConnection? = null

    /** 最近一次绑定失败的原因（诊断页展示） */
    @Volatile
    var lastBindError: Throwable? = null

    suspend fun ensureBound(): IUserService {
        _service.value?.let { return it }
        val d = startBind()
        withTimeoutOrNull(15_000) { d.await() }
        _service.value?.let { return it }
        throw lastBindError ?: IllegalStateException("用户服务未在 15 秒内连接（服务端没有创建进程）")
    }

    private fun startBind(): CompletableDeferred<IUserService?> = synchronized(lock) {
        lastBindError = null
        _service.value?.let { s ->
            return@synchronized CompletableDeferred<IUserService?>().apply { complete(s) }
        }
        pending?.let { return@synchronized it }

        val d = CompletableDeferred<IUserService?>()
        pending = d
        val app = context.applicationContext
        val args = Shizuku.UserServiceArgs(
            ComponentName(app.packageName, ShellBackend.SERVICE_CLASS)
        ).daemon(false).version(1).tag("yaowanggu-shell")

        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                Log.i(TAG, "user service connected: $name")
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
            lastBindError = e
            synchronized(lock) { pending = null }
            if (!d.isCompleted) d.complete(null)
            d
        }
        return@synchronized result
    }

    fun unbind() {
        val conn = lastConn ?: return
        try {
            Shizuku.unbindUserService(lastArgs!!, conn, true)
        } catch (e: Throwable) {
            Log.w(TAG, "unbind failed", e)
        }
        _service.value = null
        lastConn = null
    }

    override suspend fun exec(cmd: String): String =
        withContext(Dispatchers.IO) { ensureBound().exec(cmd) }

    override suspend fun readFile(path: String): ByteArray =
        withContext(Dispatchers.IO) { ensureBound().readFile(path) }

    override suspend fun writeFile(path: String, data: ByteArray) {
        withContext(Dispatchers.IO) { ensureBound().writeFile(path, data) }
    }

    override suspend fun copyFile(from: String, to: String) {
        withContext(Dispatchers.IO) { ensureBound().copyFile(from, to) }
    }

    override suspend fun listSaveSlots(): List<ShellBackend.Slot> =
        withContext(Dispatchers.IO) {
            ShellBackend.parseSlots(ensureBound().listSaveSlots().joinToString("\n"))
        }

    override suspend fun isRunning(packageName: String): Boolean =
        withContext(Dispatchers.IO) { ensureBound().isRunning(packageName) }

    override suspend fun id(): String =
        withContext(Dispatchers.IO) { ensureBound().id() }

    companion object {
        private const val TAG = "UserServiceBackend"
    }
}
