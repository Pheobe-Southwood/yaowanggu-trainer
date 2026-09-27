package com.yaowanggu.trainer.shizuku

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

/**
 * Thin wrapper around the Shizuku API. Everything here runs shell (uid 2000) commands
 * so we can read/write the game's Android/data save files on Android 11+ without root.
 */
object ShizukuRepository {

    const val GAME_PACKAGE = "com.hydrozoa.yyg"
    const val GAME_SAVE_DIR = "/sdcard/Android/data/$GAME_PACKAGE/files"
    const val SAVE_PREFIX = "nfile"
    const val SAVE_SUFFIX = ".save"
    private const val REQUEST_CODE_PERMISSION = 1001

    data class SaveSlot(
        val slot: Int,
        val path: String,
        val size: Long,
        val mtimeSec: Long,
    )

    data class ExecResult(val exit: Int, val stdout: ByteArray, val stderr: String) {
        override fun equals(other: Any?) = other is ExecResult && exit == other.exit && stdout.contentEquals(other.stdout)
        override fun hashCode() = 31 * exit + stdout.contentHashCode()
    }

    /** ---------------- binder + permission state ---------------- */

    fun binderAlive(): Boolean = try { Shizuku.pingBinder() } catch (e: Throwable) { false }

    fun permissionGranted(): Boolean = try {
        if (!binderAlive()) false
        else Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (e: Throwable) { false }

    fun requestPermission(listener: (Boolean) -> Unit) {
        if (!binderAlive()) { listener(false); return }
        if (permissionGranted()) { listener(true); return }
        val l = object : Shizuku.OnRequestPermissionResultListener {
            override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                Shizuku.removeRequestPermissionResultListener(this)
                listener(requestCode == REQUEST_CODE_PERMISSION &&
                    grantResult == PackageManager.PERMISSION_GRANTED)
            }
        }
        Shizuku.addRequestPermissionResultListener(l)
        Shizuku.requestPermission(REQUEST_CODE_PERMISSION)
    }

    /** ---------------- shell execution ---------------- */

    private fun sh(cmd: String): ExecResult {
        val p = Shizuku.newProcess(arrayOf("sh", "-c", cmd), null, null)
        val out = p.inputStream.readBytes()           // read fully before waitFor
        val err = p.errorStream.readBytes().toString(Charsets.UTF_8)
        val exit = try { p.waitFor() } catch (e: Throwable) { -1 }
        return ExecResult(exit, out, err)
    }

    fun runShell(cmd: String): ExecResult = sh(cmd)

    fun gameRunning(): Boolean {
        val r = sh("pidof $GAME_PACKAGE")
        return r.exit == 0 && r.stdout.toString(Charsets.UTF_8).trim().isNotEmpty()
    }

    // ---------------- save files ----------------

    /** List nfile*.save slots. Returns empty list if the directory doesn't exist. */
    fun listSaveSlots(): List<SaveSlot> {
        val dir = GAME_SAVE_DIR
        val cmd = "if [ -d $dir ]; then " +
            "for f in $dir/nfile*.save; do " +
            "[ -e \"$f\" ] || continue; " +
            "s=\$(stat -c %s \"$f\" 2>/dev/null); m=\$(stat -c %Y \"$f\" 2>/dev/null); " +
            "echo \"\${s:-0}|\${m:-0}|$f\"; " +
            "done; fi"
        val text = sh(cmd).stdout.toString(Charsets.UTF_8)
        val slots = ArrayList<SaveSlot>()
        text.lineSequence().forEach { line ->
            val t = line.trim()
            if (t.isEmpty()) return@forEach
            val parts = t.split("|")
            if (parts.size < 3) return@forEach
            val size = parts[0].trim().toLongOrNull() ?: 0L
            val mtime = parts[1].trim().toLongOrNull() ?: 0L
            val path = parts[2].trim()
            val name = path.substringAfterLast('/')
            val slot = name.removePrefix(SAVE_PREFIX).removeSuffix(SAVE_SUFFIX).toIntOrNull()
                ?: return@forEach
            slots.add(SaveSlot(slot, path, size, mtime))
        }
        return slots.sortedWith(compareBy({ -it.mtimeSec }, { it.slot }))
    }

    fun readFile(path: String): ByteArray {
        val r = sh("cat '$path'")
        if (r.exit != 0) error("读取失败: ${r.stderr.ifBlank { "exit ${r.exit}" }}")
        return r.stdout
    }

    /** Overwrite file in place (keeps inode/owner). */
    fun writeFile(path: String, bytes: ByteArray) {
        val p = Shizuku.newProcess(arrayOf("sh", "-c", "cat > '$path'"), null, null)
        val os = p.outputStream
        os.write(bytes)
        os.flush()
        os.close()
        val err = p.errorStream.readBytes().toString(Charsets.UTF_8)
        val exit = try { p.waitFor() } catch (e: Throwable) { -1 }
        if (exit != 0) error("写入失败: ${err.ifBlank { "exit $exit" }}")
    }

    fun copyFile(from: String, to: String) {
        val r = sh("cp -f '$from' '$to'")
        if (r.exit != 0) error("备份失败: ${r.stderr.ifBlank { "exit ${r.exit}" }}")
    }

    fun shellWhoami(): String {
        val r = sh("id")
        return r.stdout.toString(Charsets.UTF_8).trim().ifBlank { "unknown" }
    }
}
