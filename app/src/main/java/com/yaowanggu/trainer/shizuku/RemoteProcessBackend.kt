package com.yaowanggu.trainer.shizuku

import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.shizuku.server.IRemoteProcess
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import java.io.Closeable
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

/**
 * 远端进程后端：通过 Shizuku 的 BinderWrapper 调用服务端 newProcess，
 * 以 shell（或 root）身份运行 sh 命令并捕获 stdout/stderr。
 *
 * 这是 Shizuku README 里「从 su 迁移」的官方推荐路径，只依赖公开 API
 * （Shizuku.getBinder + ShizukuBinderWrapper + transactRemote），
 * 不依赖 app_process 拉起用户服务，因此 root/Sui/低版本服务端下都可用。
 *
 * 注意：IShizukuService.aidl / IRemoteProcess.aidl 的包名、描述符、
 * 事务 id（newProcess = 7）必须和服务端完全一致。
 */
class RemoteProcessBackend(override val name: String = "remote-process") : ShellBackend {

    private fun service(): IShizukuService {
        val binder = Shizuku.getBinder()
            ?: throw IllegalStateException("Shizuku binder 未就绪")
        return IShizukuService.Stub.asInterface(ShizukuBinderWrapper(binder))
    }

    /** 启动一个 sh 进程。 */
    private fun spawn(cmd: String): IRemoteProcess {
        val svc = service()
        val proc = svc.newProcess(arrayOf("sh", "-c", cmd), null, null)
            ?: throw IllegalStateException("newProcess 返回 null：服务端拒绝（版本过旧或权限不足）")
        return proc
    }

    /** 一次性执行命令，返回 stdout；退出码非 0 时抛异常并附带 stderr。 */
    override suspend fun exec(cmd: String): String = withContext(Dispatchers.IO) {
        val proc = spawn(cmd)
        val stdout = readFd(proc.outputStream)
        val stderr = readFd(proc.errorStream)
        closeQuietly(proc.inputStream)
        val code = try { proc.waitFor() } catch (t: Throwable) { -1 }
        runCatching { proc.destroy() }
        if (code != 0) throw IllegalStateException("命令失败 (exit=$code): ${stderr.trim()}")
        stdout
    }

    override suspend fun readFile(path: String): ByteArray = withContext(Dispatchers.IO) {
        val proc = spawn("cat '$path'")
        val stdout = readFd(proc.outputStream)
        val stderr = readFd(proc.errorStream)
        closeQuietly(proc.inputStream)
        val code = try { proc.waitFor() } catch (t: Throwable) { -1 }
        runCatching { proc.destroy() }
        if (code != 0) throw IllegalStateException("读取失败 (exit=$code): ${stderr.trim()}")
        stdout.toByteArray(Charsets.ISO_8859_1)
    }

    override suspend fun writeFile(path: String, data: ByteArray) {
        withContext(Dispatchers.IO) {
            // 用 dd 从 stdin 精确写入（truncate + 写固定字节数，不受 sed/cat 语义影响）
            val proc = spawn("dd of='$path' bs=4096 2>/dev/null")
            val inFd = proc.inputStream
                ?: throw IllegalStateException("拿不到进程 stdin")
            var ok = false
            try {
                FileOutputStream(inFd.fileDescriptor).use { out ->
                    out.write(data)
                    out.flush()
                }
                ok = true
            } finally {
                closeQuietly(inFd)
            }
            val stderr = readFd(proc.errorStream)
            val stdout = readFd(proc.outputStream)
            val code = try { proc.waitFor() } catch (t: Throwable) { -1 }
            runCatching { proc.destroy() }
            if (!ok || code != 0) {
                throw IllegalStateException("写入失败 (exit=$code): ${stderr.trim()}")
            }
        }
    }

    override suspend fun copyFile(from: String, to: String) {
        exec("cp -f '$from' '$to'")
    }

    override suspend fun listSaveSlots(): List<ShellBackend.Slot> {
        val out = exec(ShellBackend.listSavesCmd())
        return ShellBackend.parseSlots(out)
    }

    override suspend fun isRunning(packageName: String): Boolean =
        exec("pidof $packageName").trim().isNotEmpty()

    override suspend fun id(): String = exec("id").trim()

    // ---------- helpers ----------

    private fun readFd(fd: ParcelFileDescriptor?): String {
        if (fd == null) return ""
        return try {
            FileInputStream(fd.fileDescriptor).use { it.readBytes().toString(Charsets.ISO_8859_1) }
        } catch (e: IOException) {
            ""
        }
    }

    private fun closeQuietly(c: Closeable?) {
        if (c == null) return
        try { c.close() } catch (_: Throwable) {}
    }

    private fun closeQuietly(fd: ParcelFileDescriptor?) {
        if (fd == null) return
        try { fd.close() } catch (_: Throwable) {}
    }
}
