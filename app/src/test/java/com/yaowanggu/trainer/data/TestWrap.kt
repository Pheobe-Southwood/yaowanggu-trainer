package com.yaowanggu.trainer.data

import java.io.ByteArrayOutputStream

/**
 * 测试辅助：构造 LZ4「纯字面量块」与 MessagePack-CSharp 的两种压缩外壳。
 * 纯字面量块是合法的 LZ4 block（无 match 部分），无需编码器即可产出。
 */
object TestWrap {

    /** 合法 LZ4 block：单条字面量 sequence 承载全部数据。 */
    fun literalBlock(data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        val litLen = data.size
        if (litLen >= 15) {
            out.write(0xF0) // litLen=15 + 扩展链, matchLen=0
            var rem = litLen - 15
            while (rem >= 255) { out.write(255); rem -= 255 }
            out.write(rem)
        } else {
            out.write(litLen shl 4)
        }
        out.write(data)
        return out.toByteArray()
    }

    private fun msgpackInt32BE(v: Int): ByteArray = byteArrayOf(
        0xD2.toByte(),
        ((v shr 24) and 0xFF).toByte(), ((v shr 16) and 0xFF).toByte(),
        ((v shr 8) and 0xFF).toByte(), (v and 0xFF).toByte(),
    )

    /** ext(type=99) 外壳：payload = [msgpack int32 解压长度][LZ4 block]。 */
    fun ext99(inner: ByteArray): ByteArray {
        val block = literalBlock(inner)
        val payload = ByteArrayOutputStream()
        payload.write(msgpackInt32BE(inner.size))
        payload.write(block)
        val p = payload.toByteArray()
        val out = ByteArrayOutputStream()
        if (p.size <= 0xFF) { out.write(0xC7); out.write(p.size) }
        else { out.write(0xC8); out.write((p.size shr 8) and 0xFF); out.write(p.size and 0xFF) }
        out.write(99)
        out.write(p)
        return out.toByteArray()
    }

    /** Lz4BlockArray 外壳（单 chunk）：[Ext(98: 长度...), bin]。 */
    fun ext98Array(inner: ByteArray): ByteArray {
        val block = literalBlock(inner)
        val out = ByteArrayOutputStream()
        out.write(0x92) // array(2)
        val lens = msgpackInt32BE(inner.size)
        out.write(0xC7); out.write(lens.size); out.write(98); out.write(lens)
        if (block.size <= 0xFF) { out.write(0xC4); out.write(block.size) }
        else { out.write(0xC5); out.write((block.size shr 8) and 0xFF); out.write(block.size and 0xFF) }
        out.write(block)
        return out.toByteArray()
    }

    fun gzip(data: ByteArray): ByteArray {
        val bos = ByteArrayOutputStream()
        java.util.zip.GZIPOutputStream(bos).use { it.write(data) }
        return bos.toByteArray()
    }
}
