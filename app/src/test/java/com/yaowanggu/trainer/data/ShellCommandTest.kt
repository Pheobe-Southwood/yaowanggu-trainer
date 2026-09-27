package com.yaowanggu.trainer.data

import com.yaowanggu.trainer.shizuku.ShellBackend
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 校验「存档探测命令」解析逻辑：远程进程后端与用户服务后端共用同一套解析器，
 * 这里的用例锁住 "slot|size|mtime|path" 契约。
 */
class ShellCommandTest {

    @Test
    fun parseSlots_normal() {
        val out = """
            0|4096|1730000000|/sdcard/Android/data/com.hydrozoa.yyg/files/nfile0.save
            3|8192|1730100000|/sdcard/Android/data/com.hydrozoa.yyg/files/nfile3.save
        """.trimIndent()
        val slots = ShellBackend.parseSlots(out)
        assertEquals(2, slots.size)
        // 默认按 mtime 倒序：3 是最新
        assertEquals(3, slots[0].slot)
        assertEquals("/sdcard/Android/data/com.hydrozoa.yyg/files/nfile3.save", slots[0].path)
        assertEquals(8192L, slots[0].size)
        assertEquals(1730100000L, slots[0].mtimeSec)
        assertEquals(0, slots[1].slot)
    }

    @Test
    fun parseSlots_ignoresJunk() {
        val out = "junk line\n1|10|20|/p/nfile1.save\n\n  \nno|pipes"
        val slots = ShellBackend.parseSlots(out)
        assertEquals(1, slots.size)
        assertEquals(1, slots[0].slot)
    }

    @Test
    fun listSavesCmd_containsProbeDirs() {
        val cmd = ShellBackend.listSavesCmd()
        // 脚本里必须保留探测目录与 slot 函数
        listOf(
            "listSlots()",
            "nfile*.save",
            "/sdcard/Android/data/com.hydrozoa.yyg/files",
            "/sdcard/Android/media/com.hydrozoa.yyg",
        ).forEach { needle ->
            assert(cmd.contains(needle)) { "命令里缺少: $needle\n$cmd" }
        }
    }

    @Test
    fun listSavesCmd_syntaxValid() {
        val cmd = ShellBackend.listSavesCmd()
        val proc = ProcessBuilder("sh", "-n", "-c", cmd).redirectErrorStream(true).start()
        val out = proc.inputStream.bufferedReader().readText()
        val exit = proc.waitFor()
        assertEquals("Shell 脚本语法检查失败: $out", 0, exit)
    }
}
