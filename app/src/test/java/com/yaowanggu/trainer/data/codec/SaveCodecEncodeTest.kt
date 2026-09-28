package com.yaowanggu.trainer.data.codec

import com.yaowanggu.trainer.data.TestWrap
import com.yaowanggu.trainer.data.msgpack.MessagePack
import com.yaowanggu.trainer.data.msgpack.MpValue
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveCodecEncodeTest {

    private val sampleTree = MpValue.Map(linkedMapOf(
        MpValue.Str("hero") to MpValue.Map(linkedMapOf(
            MpValue.Str("face") to MpValue.Arr((1..20).map { MpValue.Int(it.toLong()) }),
        )),
    ))

    private fun sampleBytes(): ByteArray = MessagePack.serialize(sampleTree)

    @Test
    fun zlibRoundTrip() {
        val inner = sampleBytes()
        val enc = SaveCodec.encode("zlib+msgpack", inner, null)
        assertEquals(0x78, enc[0].toInt() and 0xFF) // zlib 头，游戏侧可 Inflate
        val d = SaveCodec.decode(enc)
        assertEquals("zlib+msgpack", d.container)
        assertArrayEquals(inner, d.innerBytes)
    }

    @Test
    fun gzipRoundTrip() {
        val inner = sampleBytes()
        val enc = SaveCodec.encode("gzip+msgpack", inner, null)
        assertEquals(0x1F, enc[0].toInt() and 0xFF)
        assertEquals(0x8B, enc[1].toInt() and 0xFF)
        val d = SaveCodec.decode(enc)
        assertEquals("gzip+msgpack", d.container)
        assertArrayEquals(inner, d.innerBytes)
    }

    @Test
    fun plainRoundTrip() {
        val inner = sampleBytes()
        val enc = SaveCodec.encode("msgpack", inner, null)
        assertArrayEquals(inner, enc)
    }

    @Test
    fun lz4BlockRoundTrip() {
        val inner = sampleBytes()
        val enc = SaveCodec.encode("msgpack+lz4block", inner, null)
        val d = SaveCodec.decode(enc)
        assertEquals("msgpack+lz4block", d.container)
        assertArrayEquals(inner, d.innerBytes)
    }

    @Test
    fun lz4BlockArrayRoundTrip() {
        val inner = sampleBytes()
        val enc = SaveCodec.encode("msgpack+lz4blockarray", inner, null)
        val d = SaveCodec.decode(enc)
        assertEquals("msgpack+lz4blockarray", d.container)
        assertArrayEquals(inner, d.innerBytes)
    }

    @Test
    fun lz4BlockLargePayload() {
        // >64KB：ext32/bin16 框路径
        val inner = MessagePack.serialize(MpValue.Arr(List(30000) { MpValue.Int(it.toLong()) }))
        assertTrue(inner.size > 65535)
        val enc = SaveCodec.encode("msgpack+lz4block", inner, null)
        val d = SaveCodec.decode(enc)
        assertEquals("msgpack+lz4block", d.container)
        assertArrayEquals(inner, d.innerBytes)
    }

    @Test
    fun headerSkipPreservesPrefix() {
        val inner = sampleBytes()
        val junk = ByteArray(7) { 0x42 }
        val original = junk + inner
        val newInner = MessagePack.serialize(MpValue.Map(linkedMapOf(MpValue.Str("x") to MpValue.Int(9))))
        val enc = SaveCodec.encode("msgpack@+7", newInner, original)
        assertArrayEquals(junk, enc.copyOfRange(0, 7))
        val d = SaveCodec.decode(enc)
        assertEquals("msgpack@+7", d.container)
        assertArrayEquals(newInner, d.innerBytes)
    }

    @Test
    fun unknownPassthrough() {
        val bytes = byteArrayOf(1, 2, 3, 4)
        assertArrayEquals(bytes, SaveCodec.encode("unknown+raw", bytes, null))
        assertArrayEquals(bytes, SaveCodec.encode("unknown", bytes, null))
    }

    @Test(expected = IllegalStateException::class)
    fun verifyRejectsBadEncode() {
        SaveCodec.verify("zlib+msgpack", byteArrayOf(1, 2, 3), sampleBytes())
    }

    @Test
    fun zlibDecodeMatchesGameFile() {
        // 模拟游戏文件：zlib(msgpack) 解码 → 编辑 → 重压缩 → 再解码
        val inner = sampleBytes()
        val gameFile = TestWrap.zlib(inner)
        val d1 = SaveCodec.decode(gameFile)
        assertEquals("zlib+msgpack", d1.container)
        val edited = MessagePack.serialize(MpValue.Map(linkedMapOf(MpValue.Str("hero") to MpValue.Int(1))))
        val rewritten = SaveCodec.encode(d1.container, edited, gameFile)
        val d2 = SaveCodec.decode(rewritten)
        assertEquals("zlib+msgpack", d2.container)
        assertArrayEquals(edited, d2.innerBytes)
    }
}
