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
