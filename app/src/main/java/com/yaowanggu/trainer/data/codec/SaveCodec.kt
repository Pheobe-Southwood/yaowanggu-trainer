package com.yaowanggu.trainer.data.codec

import com.yaowanggu.trainer.data.msgpack.MessagePack
import com.yaowanggu.trainer.data.msgpack.MpReader
import com.yaowanggu.trainer.data.msgpack.MpValue
import com.yaowanggu.trainer.util.AppLog
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.InflaterInputStream

/**
 * 解码结果：innerBytes = 真正的 msgpack 流（解压后），container = 命中的容器名。
 */
data class DecodedSave(
    val innerBytes: ByteArray,
    val container: String,
    val tree: MpValue?,
) {
    override fun equals(other: Any?): Boolean =
        other is DecodedSave && innerBytes.contentEquals(other.innerBytes)
    override fun hashCode(): Int = innerBytes.contentHashCode()
}

/**
 * 存档容器嗅探 + 多层解压。
 *
 * 游戏（MessagePack-CSharp）可能的磁盘格式，按命中概率排序：
 * 1. plain msgpack（序列化结果小于 CompressionMinLength 时原样写）；
 * 2. msgpack ext(type=99)  Lz4Block：payload = [msgpack int32 解压长度][LZ4 block]；
 * 3. msgpack array + 首元素 ext(type=98) Lz4BlockArray：[ext98(各chunk长度), bin, bin...]；
 * 4. gzip / zlib 包裹的 msgpack（兜底）；
 * 5. 自定义头部跳过（offset 1..32 内能完整消费到 EOF 的 msgpack）。
 *
 * 官方反序列化对「未压缩输入」直接放行（TryDecompress 返回 false 即按原样读），
 * 因此写回时输出 plain msgpack 即可，无需 LZ4 编码器。
 */
object SaveCodec {

    private const val LZ4_BLOCK: Byte = 99
    private const val LZ4_BLOCK_ARRAY: Byte = 98
    private const val MAX_INNER: Long = 64L * 1024 * 1024

    fun decode(bytes: ByteArray): DecodedSave {
        if (bytes.isEmpty()) return DecodedSave(bytes, "empty", null)

        // 官方 TryDecompress 的顺序：先识别 ext99 / array98 压缩外壳，再按 plain 读。
        // （Lz4BlockArray 外壳本身是合法 msgpack array，必须先于 plain 检查，否则会被误判为未压缩。）
        val root = runCatching { MpReader(bytes).readValue() }.getOrNull()

        // 1) Lz4Block
        if (root is MpValue.Ext && root.type == LZ4_BLOCK) {
            tryDecodeLz4Block(root)?.let {
                AppLog.i("codec: container=msgpack+lz4block inner=${it.innerBytes.size}")
                return it
            }
        }

        // 2) Lz4BlockArray
        if (root is MpValue.Arr) {
            tryDecodeLz4BlockArray(root)?.let {
                AppLog.i("codec: container=msgpack+lz4blockarray inner=${it.innerBytes.size}")
                return it
            }
        }

        // 3) plain msgpack（完整消费 + 根为 Map/Arr）
        MessagePack.tryParse(bytes)?.let {
            AppLog.i("codec: container=msgpack")
            return DecodedSave(bytes, "msgpack", it)
        }

        // 4) gzip / zlib
        if (bytes.size > 3 && bytes[0] == 0x1F.toByte() && bytes[1] == 0x8B.toByte()) {
            runCatching {
                val inner = GZIPInputStream(bytes.inputStream()).use { it.readBytes() }
                MessagePack.tryParse(inner)?.let {
                    AppLog.i("codec: container=gzip+msgpack inner=${inner.size}")
                    return DecodedSave(inner, "gzip+msgpack", it)
                }
            }.onFailure { AppLog.w("codec: gzip decode failed: ${it.message}") }
        }
        if (bytes.size > 2 && bytes[0] == 0x78.toByte() &&
            (bytes[1].toInt() and 0xFF) in setOf(0x01, 0x5E, 0x9C, 0xDA)
        ) {
            runCatching {
                val inner = InflaterInputStream(bytes.inputStream()).use { it.readBytes() }
                MessagePack.tryParse(inner)?.let {
                    AppLog.i("codec: container=zlib+msgpack inner=${inner.size}")
                    return DecodedSave(inner, "zlib+msgpack", it)
                }
            }.onFailure { AppLog.w("codec: zlib decode failed: ${it.message}") }
        }

        // 5) header skip
        for (off in 1..32) {
            if (off >= bytes.size) break
            val slice = bytes.copyOfRange(off, bytes.size)
            MessagePack.tryParse(slice)?.let {
                AppLog.i("codec: container=msgpack@+$off inner=${slice.size}")
                return DecodedSave(slice, "msgpack@+$off", it)
            }
        }

        AppLog.w("codec: container=unknown size=${bytes.size} head=${bytes.take(16).joinToString(" ") { "%02X".format(it) }}")
        return DecodedSave(bytes, "unknown", null)
    }

    /**
     * 按原容器把 newInner 重新打包成可写回的文件字节。
     * 游戏对 nfile30 等模块使用 zlib 外层：直接写 plain msgpack 会导致游戏解压失败并回退 _backup。
     * 写回前必须通过 [verify] 自检，失败抛 IllegalStateException（调用方中止写入）。
     */
    fun encode(container: String, newInner: ByteArray, originalBytes: ByteArray? = null): ByteArray {
        val encoded = when {
            container == "msgpack" || container.isEmpty() -> newInner
            container == "zlib+msgpack" -> zlibCompress(newInner)
            container == "gzip+msgpack" -> gzipCompress(newInner)
            container == "msgpack+lz4block" -> lz4BlockFrame(newInner)
            container == "msgpack+lz4blockarray" -> lz4BlockArrayFrame(newInner)
            container.startsWith("msgpack@+") -> {
                val n = container.removePrefix("msgpack@+").toIntOrNull()
                    ?: throw IllegalStateException("非法容器名: $container")
                if (originalBytes == null || originalBytes.size < n) {
                    throw IllegalStateException("无法保留 $n 字节前缀（缺少原始文件字节）")
                }
                originalBytes.copyOfRange(0, n) + newInner
            }
            // unknown / unknown+raw / empty：raw patch 模式产出的就是完整文件字节，原样写
            else -> newInner
        }
        verify(container, encoded, newInner)
        return encoded
    }

    /** 自检：encode 产物必须能 decode 回相同 inner，且容器一致。 */
    fun verify(container: String, encoded: ByteArray, expectedInner: ByteArray) {
        val back = decode(encoded)
        val containerOk = when {
            container == "unknown" || container == "unknown+raw" || container == "empty" -> true
            container.startsWith("msgpack@+") -> back.container == container
            container.endsWith("+raw") -> back.container == container.removeSuffix("+raw")
            else -> back.container == container
        }
        if (!containerOk || !back.innerBytes.contentEquals(expectedInner)) {
            throw IllegalStateException(
                "写回自检失败: container=$container → ${back.container}, inner ${back.innerBytes.size}/${expectedInner.size}"
            )
        }
        AppLog.i("codec: encode ok container=$container out=${encoded.size}B verify=pass")
    }

    // ---------- encode helpers ----------

    private fun zlibCompress(data: ByteArray): ByteArray {
        val def = java.util.zip.Deflater(java.util.zip.Deflater.BEST_SPEED)
        val out = ByteArrayOutputStream()
        java.util.zip.DeflaterOutputStream(out, def).use { it.write(data) }
        return out.toByteArray()
    }

    private fun gzipCompress(data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        java.util.zip.GZIPOutputStream(out).use { it.write(data) }
        return out.toByteArray()
    }

    private fun lz4BlockFrame(inner: ByteArray): ByteArray {
        val block = Lz4.compressLiteralBlock(inner)
        val payload = ByteArrayOutputStream()
        payload.write(0xD2) // int32
        payload.write((inner.size ushr 24) and 0xFF)
        payload.write((inner.size ushr 16) and 0xFF)
        payload.write((inner.size ushr 8) and 0xFF)
        payload.write(inner.size and 0xFF)
        payload.write(block)
        return extFrame(LZ4_BLOCK, payload.toByteArray())
    }

    private fun lz4BlockArrayFrame(inner: ByteArray): ByteArray {
        val block = Lz4.compressLiteralBlock(inner)
        val lens = byteArrayOf(
            0xD2.toByte(),
            ((inner.size ushr 24) and 0xFF).toByte(),
            ((inner.size ushr 16) and 0xFF).toByte(),
            ((inner.size ushr 8) and 0xFF).toByte(),
            (inner.size and 0xFF).toByte(),
        )
        val out = ByteArrayOutputStream()
        out.write(0x92) // array(2)
        out.write(extFrame(LZ4_BLOCK_ARRAY, lens))
        out.write(binFrame(block))
        return out.toByteArray()
    }

    private fun extFrame(type: Byte, payload: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        when {
            payload.size <= 0xFF -> { out.write(0xC7); out.write(payload.size) }
            payload.size <= 0xFFFF -> { out.write(0xC8); out.write((payload.size ushr 8) and 0xFF); out.write(payload.size and 0xFF) }
            else -> {
                out.write(0xC9)
                out.write((payload.size ushr 24) and 0xFF); out.write((payload.size ushr 16) and 0xFF)
                out.write((payload.size ushr 8) and 0xFF); out.write(payload.size and 0xFF)
            }
        }
        out.write(type.toInt())
        out.write(payload)
        return out.toByteArray()
    }

    private fun binFrame(data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        when {
            data.size <= 0xFF -> { out.write(0xC4); out.write(data.size) }
            data.size <= 0xFFFF -> { out.write(0xC5); out.write((data.size ushr 8) and 0xFF); out.write(data.size and 0xFF) }
            else -> {
                out.write(0xC6)
                out.write((data.size ushr 24) and 0xFF); out.write((data.size ushr 16) and 0xFF)
                out.write((data.size ushr 8) and 0xFF); out.write(data.size and 0xFF)
            }
        }
        out.write(data)
        return out.toByteArray()
    }

    // ---------- helpers ----------

    private fun tryDecodeLz4Block(ext: MpValue.Ext): DecodedSave? {
        return try {
        val r = MpReader(ext.data)
        val lenV = r.readValue() as? MpValue.Int ?: return null
        val len = lenV.v
        if (len < 0 || len > MAX_INNER) return null
        val rest = ext.data.copyOfRange(r.position, ext.data.size)
        val inner = Lz4.decompressBlock(rest, len.toInt())
        val tree = MessagePack.tryParse(inner) ?: return null
        DecodedSave(inner, "msgpack+lz4block", tree)
        } catch (e: Throwable) {
            AppLog.w("codec: lz4block decode failed: ${e.message}")
            null
        }
    }

    private fun tryDecodeLz4BlockArray(arr: MpValue.Arr): DecodedSave? {
        return try {
        val first = arr.items.firstOrNull() as? MpValue.Ext ?: return null
        if (first.type != LZ4_BLOCK_ARRAY) return null
        val chunkCount = arr.items.size - 1
        if (chunkCount <= 0) return null
        val lr = MpReader(first.data)
        val lengths = IntArray(chunkCount)
        for (i in 0 until chunkCount) {
            val v = lr.readValue() as? MpValue.Int ?: return null
            if (v.v < 0 || v.v > MAX_INNER) return null
            lengths[i] = v.v.toInt()
        }
        val out = ByteArrayOutputStream()
        for (i in 0 until chunkCount) {
            val bin = arr.items[i + 1] as? MpValue.Bin ?: return null
            out.write(Lz4.decompressBlock(bin.v, lengths[i]))
        }
        val inner = out.toByteArray()
        val tree = MessagePack.tryParse(inner) ?: return null
        DecodedSave(inner, "msgpack+lz4blockarray", tree)
        } catch (e: Throwable) {
            AppLog.w("codec: lz4blockarray decode failed: ${e.message}")
            null
        }
    }
}
