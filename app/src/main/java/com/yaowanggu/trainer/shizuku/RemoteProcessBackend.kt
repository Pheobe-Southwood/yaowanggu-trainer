package com.yaowanggu.trainer.shizuku

import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.shizuku.server.IRemoteProcess
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper

/**
 * 远端进程后端：通过 Shizuku 的 BinderWrapper 调用服务端 newProcess，
 * 以 shell（或 root）身份运行 sh 命令并捕获 stdout/stderr。
 *
 * 这是 Shizuku README 里「从 su 迁移」的官方推荐路径，只依赖公开 API
 * （Shizuku.getBinder + ShizukuBinderWrapper + transactRemote），
 * 不依赖 app_process 拉起用户服务，因此 root/Sui/低版本服务端下都可用。
 *
 * 注意：
 * - IRemoteProcess.getOutputStream() 对应子进程的标准输入（STDIN）
 * - IRemoteProcess.getInputStream() 对应子进程的标准输出（STDOUT）
 * - IRemoteProcess.getErrorStream() 对应子进程的标准错误（STDERR）
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
        // 子进程 stdin 不需要输入，立即关闭以发送 EOF
        closeQuietly(proc.outputStream)
        val stdout = readAllText(proc.inputStream)
        val stderr = readAllText(proc.errorStream)
        val code = try { proc.waitFor() } catch (_: Throwable) { -1 }
        runCatching { proc.destroy() }
        if (code != 0) throw IllegalStateException("命令失败 (exit=$code): ${stderr.trim()}")
        stdout
    }

    override suspend fun readFile(path: String): ByteArray = withContext(Dispatchers.IO) {
        val safePath = path.replace("'", "'\\''")
        val proc = spawn("cat '$safePath'")
        closeQuietly(proc.outputStream)
        val bytes = readAllBytes(proc.inputStream)
        val stderr = readAllText(proc.errorStream)
        val code = try { proc.waitFor() } catch (_: Throwable) { -1 }
        runCatching { proc.destroy() }
        if (code != 0) throw IllegalStateException("读取失败 (exit=$code): ${stderr.trim()}")
        bytes
    }

    override suspend fun writeFile(path: String, data: ByteArray) {
        withContext(Dispatchers.IO) {
            val safePath = path.replace("'", "'\\''")
            val proc = spawn("cat > '$safePath'")
            val inFd = proc.outputStream
                ?: throw IllegalStateException("拿不到进程 stdin")
            try {
                ParcelFileDescriptor.AutoCloseOutputStream(inFd).use { stream ->
                    stream.write(data)
                    stream.flush()
                }
            } catch (t: Throwable) {
                closeQuietly(inFd)
                runCatching { proc.destroy() }
                throw IllegalStateException("向写入管道发送数据失败: ${t.message}", t)
            }
            val stderr = readAllText(proc.errorStream)
            val code = try { proc.waitFor() } catch (_: Throwable) { -1 }
            runCatching { proc.destroy() }
            if (code != 0) {
                throw IllegalStateException("写入失败 (exit=$code): ${stderr.trim()}")
            }
        }
    }

    override suspend fun copyFile(from: String, to: String) {
        val safeFrom = from.replace("'", "'\\''")
        val safeTo = to.replace("'", "'\\''")
        exec("cp -f '$safeFrom' '$safeTo'")
    }

    override suspend fun listSaveSlots(): List<ShellBackend.Slot> {
        val out = exec(ShellBackend.listSavesCmd())
        return ShellBackend.parseSlots(out)
    }

    override suspend fun isRunning(packageName: String): Boolean {
        val safePkg = packageName.replace("'", "'\\''")
        return exec("pidof '$safePkg' || true").trim().isNotEmpty()
    }

    override suspend fun id(): String = exec("id").trim()

    // ---------- helpers ----------

    private fun readAllBytes(pfd: ParcelFileDescriptor?): ByteArray {
        if (pfd == null) return ByteArray(0)
        return try {
            ParcelFileDescriptor.AutoCloseInputStream(pfd).use { it.readBytes() }
        } catch (_: Throwable) {
            ByteArray(0)
        }
    }

    private fun readAllText(pfd: ParcelFileDescriptor?): String {
        return readAllBytes(pfd).toString(Charsets.UTF_8)
    }

    private fun closeQuietly(pfd: ParcelFileDescriptor?) {
        if (pfd == null) return
        try { pfd.close() } catch (_: Throwable) {}
    }
}
