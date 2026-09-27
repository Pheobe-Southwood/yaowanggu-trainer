package com.yaowanggu.trainer.data.codec

import com.yaowanggu.trainer.data.TestWrap
import com.yaowanggu.trainer.data.msgpack.MessagePack
import com.yaowanggu.trainer.data.msgpack.MpValue
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SaveCodecTest {

    private val sampleTree = MpValue.Map(linkedMapOf(
        MpValue.Str("hero") to MpValue.Map(linkedMapOf(
            MpValue.Str("name") to MpValue.Str("叶星华"),
            MpValue.Str("face") to MpValue.Arr((1..20).map { MpValue.Int(it.toLong()) }),
        )),
    ))

    private fun sampleBytes(): ByteArray = MessagePack.serialize(sampleTree)

    @Test
    fun plainPassthrough() {
        val inner = sampleBytes()
        val d = SaveCodec.decode(inner)
        assertEquals("msgpack", d.container)
        assertNotNull(d.tree)
        assertArrayEquals(inner, d.innerBytes)
    }

    @Test
    fun lz4BlockFraming() {
        val inner = sampleBytes()
        val d = SaveCodec.decode(TestWrap.ext99(inner))
        assertEquals("msgpack+lz4block", d.container)
        assertNotNull(d.tree)
        assertArrayEquals(inner, d.innerBytes)
        // 树内容与原树一致（重序列化相等）
        assertArrayEquals(MessagePack.serialize(sampleTree), MessagePack.serialize(d.tree!!))
    }

    @Test
    fun lz4BlockArrayFraming() {
        val inner = sampleBytes()
        val d = SaveCodec.decode(TestWrap.ext98Array(inner))
        assertEquals("msgpack+lz4blockarray", d.container)
        assertNotNull(d.tree)
        assertArrayEquals(inner, d.innerBytes)
    }

    @Test
    fun gzipFraming() {
        val inner = sampleBytes()
        val d = SaveCodec.decode(TestWrap.gzip(inner))
        assertEquals("gzip+msgpack", d.container)
        assertNotNull(d.tree)
        assertArrayEquals(inner, d.innerBytes)
    }

    @Test
    fun headerSkipFraming() {
        val inner = sampleBytes()
        val junk = ByteArray(7) { 0x42 }
        val d = SaveCodec.decode(junk + inner)
        assertEquals("msgpack@+7", d.container)
        assertArrayEquals(inner, d.innerBytes)
        assertNotNull(d.tree)
    }

    @Test
    fun unknownFallback() {
        val d = SaveCodec.decode(byteArrayOf(1, 2, 3, 4))
        assertEquals("unknown", d.container)
        assertNull(d.tree)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), d.innerBytes)
    }

    @Test
    fun emptyInput() {
        val d = SaveCodec.decode(ByteArray(0))
        assertEquals("empty", d.container)
        assertNull(d.tree)
    }
}
