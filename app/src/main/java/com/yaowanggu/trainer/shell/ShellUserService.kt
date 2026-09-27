package com.yaowanggu.trainer.shell

import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile

/**
 * 由 Shizuku 以 shell (uid 2000) 身份拉起的用户服务。
 * 这个类运行在 Shizuku 服务进程里，因此这里可以直接做普通文件 IO，
 * 访问被 Android 11+ Scoped Storage 挡住的 /sdcard/Android/data/... 目录。
 *
 * 注意：不要在这里调用任何 Shizuku API；本服务本身就是 Shizuku 的“用户服务”。
 */
class ShellUserService : IUserService.Stub() {

    /** Shizuku API v13 会用带 Context 的构造器实例化（R8 下需保留）。 */
    @Suppress("unused")
    constructor()

    @Suppress("unused", "UNUSED_PARAMETER")
    constructor(context: android.content.Context)

    override fun id(): String = "uid=${android.os.Process.myUid()} pid=${android.os.Process.myPid()}"

    override fun destroy() {
        Log.i(TAG, "destroy")
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    override fun exec(command: String): String {
        val p = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
        val out = p.inputStream.readBytes()
        val err = p.errorStream.readBytes()
        val code = p.waitFor()
        if (code != 0) {
            throw IllegalStateException("command failed ($code): " + String(err, Charsets.UTF_8).trim())
        }
        return String(out, Charsets.UTF_8)
    }

    override fun readFile(path: String): ByteArray = File(path).readBytes()

    override fun writeFile(path: String, data: ByteArray) {
        val f = File(path)
        if (!f.exists()) f.createNewFile()
        RandomAccessFile(f, "rw").use { raf ->
            raf.setLength(0)          // 先截断，保证短于原文件也能正确覆盖
            raf.write(data)
        }
    }

    override fun copyFile(from: String, to: String) {
        File(from).inputStream().use { ins ->
            File(to).outputStream().use { outs -> ins.copyTo(outs) }
        }
    }

    override fun listSaveSlots(): Array<String> {
        val dir = File(GAME_DIR)
        if (!dir.isDirectory) return emptyArray()
        val out = ByteArrayOutputStream()
        dir.listFiles { f -> f.isFile && f.name.startsWith("nfile") && f.name.endsWith(".save") }
            ?.sortedBy { it.name }
            ?.forEach { f ->
                val slot = f.name.removePrefix("nfile").removeSuffix(".save").toIntOrNull()
                if (slot != null) {
                    out.write("$slot|${f.length()}|${f.lastModified() / 1000}|${f.absolutePath}\n".toByteArray())
                }
            }
        return String(out.toByteArray(), Charsets.UTF_8).lines().filter { it.isNotBlank() }.toTypedArray()
    }

    override fun isRunning(packageName: String): Boolean {
        val out = exec("pidof $packageName")
        return out.trim().isNotEmpty()
    }

    companion object {
        private const val TAG = "ShellUserService"
        const val GAME_DIR = "/sdcard/Android/data/com.hydrozoa.yyg/files"
    }
}
