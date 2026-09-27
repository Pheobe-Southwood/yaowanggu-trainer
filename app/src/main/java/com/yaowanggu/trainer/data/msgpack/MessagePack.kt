package com.yaowanggu.trainer.data.msgpack

/**
 * Minimal MessagePack value tree (pure Kotlin, no Android deps).
 * Enough to round-trip anything the game save may contain.
 */
sealed class MpValue {
    data object Nil : MpValue()
    data class Bool(val v: Boolean) : MpValue()
    data class Int(val v: Long) : MpValue()      // signed 64-bit
    data class Float32(val v: Float) : MpValue()
    data class Float64(val v: Double) : MpValue()
    data class Str(val v: String) : MpValue()
    data class Bin(val v: ByteArray) : MpValue() {
        override fun equals(other: Any?) = other is Bin && v.contentEquals(other.v)
        override fun hashCode() = v.contentHashCode()
    }
    data class Arr(val items: List<MpValue>) : MpValue()
    data class Map(val entries: Map<MpValue, MpValue>) : MpValue()
    data class Ext(val type: Byte, val data: ByteArray) : MpValue() {
        override fun equals(other: Any?) = other is Ext && type == other.type && data.contentEquals(other.data)
        override fun hashCode() = 31 * type.hashCode() + data.contentHashCode()
    }

    fun asIntOrNull(): Long? = when (this) {
        is Int -> v
        is Float32 -> if (v % 1f == 0f) v.toLong() else null
        is Float64 -> if (v % 1.0 == 0.0) v.toLong() else null
        else -> null
    }

    companion object {
        fun of(v: Long) = Int(v)
    }
}

class MessagePackException(message: String) : Exception(message)

/**
 * Streaming MessagePack reader. Consumes exactly one value from the front.
 */
class MpReader(private val data: ByteArray, private var pos: Int = 0) {

    val position: Int get() = pos

    fun hasMore(): Boolean = pos < data.size

    fun readValue(): MpValue {
        if (pos >= data.size) throw MessagePackException("unexpected end of data")
        val c = data[pos].toInt() and 0xFF
        pos++
        return when {
            c <= 0x7F -> MpValue.Int(c.toLong())                       // positive fixint
            c >= 0xE0 -> MpValue.Int((c - 0x100).toLong())             // negative fixint
            c == 0xC0 -> MpValue.Nil
            c == 0xC2 -> MpValue.Bool(false)
            c == 0xC3 -> MpValue.Bool(true)
            c == 0xCA -> readF32()
            c == 0xCB -> readF64()
            c == 0xCC -> readU8()
            c == 0xCD -> readU16()
            c == 0xCE -> readU32()
            c == 0xCF -> readU64()
            c == 0xD0 -> readI8()
            c == 0xD1 -> readI16()
            c == 0xD2 -> readI32()
            c == 0xD3 -> readI64()
            c in 0xA0..0xBF -> readStr(c and 0x1F)
            c == 0xD9 -> readStr(readU8Length())
            c == 0xDA -> readStr(readU16Length())
            c == 0xDB -> readStr(readU32Length())
            c in 0x90..0x9F -> readArr(c and 0x0F)
            c == 0xDC -> readArr(readU16Length())
            c == 0xDD -> readArr(readU32Length())
            c in 0x80..0x8F -> readMap(c and 0x0F)
            c == 0xDE -> readMap(readU16Length())
            c == 0xDF -> readMap(readU32Length())
            c == 0xC4 -> readBin(readU8Length())
            c == 0xC5 -> readBin(readU16Length())
            c == 0xC6 -> readBin(readU32Length())
            c in 0xD4..0xD8 -> readFixExt(c)                            // fixext1/2/4/8/16
            c == 0xC7 -> readExt(readU8Length())
            c == 0xC8 -> readExt(readU16Length())
            c == 0xC9 -> readExt(readU32Length())
            else -> throw MessagePackException("unsupported msgpack code 0x%02X at %d".format(c, pos - 1))
        }
    }

    private fun need(n: Int) {
        if (pos + n > data.size) throw MessagePackException("unexpected end of data")
    }

    private fun readF32(): MpValue { need(4); val v = java.nio.ByteBuffer.wrap(data, pos, 4).order(java.nio.ByteOrder.BIG_ENDIAN).float; pos += 4; return MpValue.Float32(v) }
    private fun readF64(): MpValue { need(8); val v = java.nio.ByteBuffer.wrap(data, pos, 8).order(java.nio.ByteOrder.BIG_ENDIAN).double; pos += 8; return MpValue.Float64(v) }
    private fun readU8(): MpValue = MpValue.Int(take(1))
    private fun readU16(): MpValue = MpValue.Int(take(2))
    private fun readU32(): MpValue = MpValue.Int(take(4))
    private fun readU64(): MpValue = MpValue.Int(take(8))
    private fun readI8(): MpValue = MpValue.Int(take(1).let { (it shl 56) shr 56 })
    private fun readI16(): MpValue = MpValue.Int(take(2).let { (it shl 48) shr 48 })
    private fun readI32(): MpValue = MpValue.Int(take(4).toInt().toLong())
    private fun readI64(): MpValue = MpValue.Int(take(8))

    /** Read n bytes as unsigned big-endian long, advancing the cursor. */
    private fun take(n: Int): Long {
        need(n)
        var v = 0L
        for (i in 0 until n) v = (v shl 8) or (data[pos + i].toLong() and 0xFF)
        pos += n
        return v
    }
    private fun readU8Length() = take(1)
    private fun readU16Length() = take(2)
    private fun readU32Length(): Int {
        val v = take(4)
        if (v > data.size) throw MessagePackException("length too large")
        return v.toInt()
    }
    private fun readStr(len: Int): MpValue { need(len); val s = String(data, pos, len, Charsets.UTF_8); pos += len; return MpValue.Str(s) }
    private fun readBin(len: Int): MpValue { need(len); val b = data.copyOfRange(pos, pos + len); pos += len; return MpValue.Bin(b) }
    private fun readArr(len: Int): MpValue {
        val items = ArrayList<MpValue>(len)
        repeat(len) { items.add(readValue()) }
        return MpValue.Arr(items)
    }
    private fun readMap(len: Int): MpValue {
        val m = LinkedHashMap<MpValue, MpValue>()
        repeat(len) {
            val k = readValue()
            val v = readValue()
            m[k] = v
        }
        return MpValue.Map(m)
    }
    private fun readFixExt(code: Int): MpValue {
        val len = 1 shl (code - 0xD4)   // 0xD4→1, 0xD5→2, 0xD6→4, 0xD7→8, 0xD8→16
        need(1 + len)
        val type = data[pos]
        pos += 1
        val b = data.copyOfRange(pos, pos + len)
        pos += len
        return MpValue.Ext(type, b)
    }
    private fun readExt(len: Int): MpValue {
        need(1 + len)
        val type = data[pos]
        pos += 1
        val b = data.copyOfRange(pos, pos + len)
        pos += len
        return MpValue.Ext(type, b)
    }
}

/**
 * Streaming MessagePack writer.
 */
class MpWriter {
    private val out = java.io.ByteArrayOutputStream()

    fun write(v: MpValue) {
        when (v) {
            is MpValue.Nil -> out.write(0xC0)
            is MpValue.Bool -> out.write(if (v.v) 0xC3 else 0xC2)
            is MpValue.Int -> writeInt(v.v)
            is MpValue.Float32 -> { out.write(0xCA); val bb = java.nio.ByteBuffer.allocate(4).order(java.nio.ByteOrder.BIG_ENDIAN).putFloat(v.v); out.write(bb.array()) }
            is MpValue.Float64 -> { out.write(0xCB); val bb = java.nio.ByteBuffer.allocate(8).order(java.nio.ByteOrder.BIG_ENDIAN).putDouble(v.v); out.write(bb.array()) }
            is MpValue.Str -> writeStr(v.v)
            is MpValue.Bin -> writeBin(v.v)
            is MpValue.Arr -> writeArr(v.items)
            is MpValue.Map -> writeMap(v.entries)
            is MpValue.Ext -> writeExt(v.type, v.data)
        }
    }

    private fun writeInt(v: Long) {
        when {
            v >= 0 && v <= 0x7F -> out.write(v.toInt())
            v < 0 && v >= -32 -> out.write((v and 0xFF).toInt())
            v >= 0 && v <= 0xFF -> { out.write(0xCC); out.write(v.toInt()) }
            v >= 0 && v <= 0xFFFF -> { out.write(0xCD); writeU16(v) }
            v >= 0 && v <= 0xFFFFFFFFL -> { out.write(0xCE); writeU32(v) }
            v < 0 && v >= Byte.MIN_VALUE -> { out.write(0xD0); out.write(v.toInt()) }
            v < 0 && v >= Short.MIN_VALUE -> { out.write(0xD1); writeU16(v) }
            v < 0 && v >= Int.MIN_VALUE -> { out.write(0xD2); writeU32(v) }
            else -> { out.write(0xD3); writeU64(v) }
        }
    }
    private fun writeU16(v: Long) { out.write((v shr 8).toInt() and 0xFF); out.write(v.toInt() and 0xFF) }
    private fun writeU32(v: Long) { for (i in 3 downTo 0) out.write((v shr (8 * i)).toInt() and 0xFF) }
    private fun writeU64(v: Long) { val bb = java.nio.ByteBuffer.allocate(8).order(java.nio.ByteOrder.BIG_ENDIAN).putLong(v); out.write(bb.array()) }

    private fun writeStr(s: String) {
        val b = s.toByteArray(Charsets.UTF_8)
        when {
            b.size <= 31 -> out.write(0xA0 or b.size)
            b.size <= 0xFF -> { out.write(0xD9); out.write(b.size) }
            b.size <= 0xFFFF -> { out.write(0xDA); writeU16(b.size.toLong()) }
            else -> { out.write(0xDB); writeU32(b.size.toLong()) }
        }
        out.write(b)
    }
    private fun writeBin(b: ByteArray) {
        when {
            b.size <= 0xFF -> { out.write(0xC4); out.write(b.size) }
            b.size <= 0xFFFF -> { out.write(0xC5); writeU16(b.size.toLong()) }
            else -> { out.write(0xC6); writeU32(b.size.toLong()) }
        }
        out.write(b)
    }
    private fun writeArr(items: List<MpValue>) {
        when {
            items.size <= 15 -> out.write(0x90 or items.size)
            items.size <= 0xFFFF -> { out.write(0xDC); writeU16(items.size.toLong()) }
            else -> { out.write(0xDD); writeU32(items.size.toLong()) }
        }
        items.forEach { write(it) }
    }
    private fun writeMap(m: Map<MpValue, MpValue>) {
        when {
            m.size <= 15 -> out.write(0x80 or m.size)
            m.size <= 0xFFFF -> { out.write(0xDE); writeU16(m.size.toLong()) }
            else -> { out.write(0xDF); writeU32(m.size.toLong()) }
        }
        m.forEach { (k, v) -> write(k); write(v) }
    }
    private fun writeExt(type: Byte, b: ByteArray) {
        when (b.size) {
            1 -> { out.write(0xD4); out.write(type.toInt()); out.write(b) }
            2 -> { out.write(0xD5); out.write(type.toInt()); out.write(b) }
            4 -> { out.write(0xD6); out.write(type.toInt()); out.write(b) }
            8 -> { out.write(0xD7); out.write(type.toInt()); out.write(b) }
            16 -> { out.write(0xD8); out.write(type.toInt()); out.write(b) }
            else -> {
                out.write(0xC8)
                if (b.size <= 0xFF) { out.write(b.size); out.write(1) }
                else { writeU16(b.size.toLong()); out.write(2) }
                out.write(type.toInt())
                out.write(b)
            }
        }
    }

    fun toByteArray(): ByteArray = out.toByteArray()
}

object MessagePack {
    fun parse(data: ByteArray): MpValue = MpReader(data).readValue()

    fun tryParse(data: ByteArray): MpValue? {
        if (data.isEmpty()) return null
        return try {
            val reader = MpReader(data)
            val v = reader.readValue()
            // require full consumption: trailing garbage means this isn't msgpack
            if (reader.hasMore()) return null
            if (v is MpValue.Map || v is MpValue.Arr) v else null
        } catch (e: Exception) {
            null
        }
    }

    fun serialize(v: MpValue): ByteArray = MpWriter().apply { write(v) }.toByteArray()
}
