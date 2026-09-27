package com.yaowanggu.trainer.data

import com.yaowanggu.trainer.data.msgpack.MessagePack
import com.yaowanggu.trainer.data.msgpack.MpValue
import com.yaowanggu.trainer.data.msgpack.MpWriter
import com.yaowanggu.trainer.data.schema.FaceSchema
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveLogicTest {

    // ---------- MessagePack ----------

    @Test
    fun msgpackRoundTrip_allTypes() {
        val root = MpValue.Map(
            linkedMapOf(
                MpValue.Int(1) to MpValue.Map(
                    linkedMapOf(
                        MpValue.Str("name") to MpValue.Str("药王谷"),
                        MpValue.Str("age") to MpValue.Int(3080),
                        MpValue.Str("neg") to MpValue.Int(-5),
                        MpValue.Str("u64") to MpValue.Int(1L shl 40),
                        MpValue.Str("d") to MpValue.Float64(3.5),
                        MpValue.Str("f") to MpValue.Float32(1.25f),
                        MpValue.Str("bin") to MpValue.Bin(byteArrayOf(1, 2, 3, 255.toByte())),
                        MpValue.Str("arr") to MpValue.Arr(listOf(MpValue.Int(7), MpValue.Bool(true), MpValue.Nil)),
                        MpValue.Str("ext") to MpValue.Ext(3, byteArrayOf(9, 9)),
                    )
                ),
            )
        )
        val bytes = MessagePack.serialize(root)
        val reparsed = MessagePack.parse(bytes)
        assertEquals(root, reparsed)
        assertArrayEquals(bytes, MessagePack.serialize(reparsed))
    }

    @Test
    fun msgpackKnownVectors() {
        // 0x92 0x01 0x02 => [1,2]
        assertEquals(MpValue.Arr(listOf(MpValue.Int(1), MpValue.Int(2))), MessagePack.parse(byteArrayOf(0x92.toByte(), 1, 2)))
        // fixmap {"a": 1}
        assertEquals(
            MpValue.Map(linkedMapOf(MpValue.Str("a") to MpValue.Int(1))),
            MessagePack.parse(byteArrayOf(0x81.toByte(), 0xA1.toByte(), 'a'.code.toByte(), 1)),
        )
        // uint32 0xCE 00 00 01 00
        assertEquals(MpValue.Int(256), MessagePack.parse(byteArrayOf(0xCE.toByte(), 0, 0, 1, 0)))
        // negative fixint -1
        assertEquals(MpValue.Int(-1), MessagePack.parse(byteArrayOf(0xFF.toByte())))
        // str8
        assertEquals(MpValue.Str("hi"), MessagePack.parse(byteArrayOf(0xD9.toByte(), 2, 'h'.code.toByte(), 'i'.code.toByte())))
    }

    @Test
    fun msgpackSerialize_lengths() {
        // positive fixint chosen for small values
        assertArrayEquals(byteArrayOf(1), MessagePack.serialize(MpValue.Int(1)))
        // u8 for 200
        assertArrayEquals(byteArrayOf(0xCC.toByte(), 200.toByte()), MessagePack.serialize(MpValue.Int(200)))
        // -5 落在 negative fixint 区间，只写一个字节
        assertArrayEquals(byteArrayOf(0xFB.toByte()), MessagePack.serialize(MpValue.Int(-5)))
        // -100 用 int8 编码；-200 超出 int8，用 int16
        assertArrayEquals(byteArrayOf(0xD0.toByte(), 156.toByte()), MessagePack.serialize(MpValue.Int(-100)))
        assertArrayEquals(byteArrayOf(0xD1.toByte(), 0xFF.toByte(), 0x38.toByte()), MessagePack.serialize(MpValue.Int(-200)))
    }

    @Test
    fun msgpackWriterReadWriterConsistent_randomShapes() {
        val rnd = java.util.Random(42)
        repeat(50) {
            val v = randomValue(rnd, depth = 0)
            val b = MessagePack.serialize(v)
            assertEquals(v, MessagePack.parse(b))
        }
    }

    private fun randomValue(rnd: java.util.Random, depth: Int): MpValue = when (rnd.nextInt(if (depth < 2) 8 else 4)) {
        0 -> MpValue.Int(rnd.nextInt(2000).toLong())
        1 -> MpValue.Int(-rnd.nextInt(1000).toLong())
        2 -> MpValue.Str("s" + rnd.nextInt(100))
        3 -> MpValue.Bool(rnd.nextBoolean())
        4 -> MpValue.Nil
        5 -> MpValue.Arr(List(rnd.nextInt(5)) { randomValue(rnd, depth + 1) })
        6 -> {
            val m = LinkedHashMap<MpValue, MpValue>()
            repeat(rnd.nextInt(5)) { m[MpValue.Int(rnd.nextInt(40).toLong())] = randomValue(rnd, depth + 1) }
            MpValue.Map(m)
        }
        else -> MpValue.Bin(ByteArray(rnd.nextInt(20)).also { rnd.nextBytes(it) })
    }

    // ---------- analyzer ----------

    private fun faceValues(id: Long = 149): List<Long> = listOf(
        id, 0, 6, 6, 9, 1, 10, 10, // ID..后发
        3, 0,                       // 性格, 痣
        76, 50,                     // 肤色 sat/val
        222, 0, 400,                // 发色 h/s/v
        236, 61, 205,               // 瞳色 h/s/v
        10, 10,                     // 幼前发/幼后发
    )

    private fun charValues(lifespan: Long = 3080): List<Long> = listOf(
        lifespan, 8, 3, 0, 3,       // 寿元 生日 未知 性别
        2, 1, 1, 0, 0,              // 所在地 门派 职位 妖族 未知
        10, 11, 12, 13, 99, 98, 97, // 父母师尊道侣 未知*3
        3, 0, 5, 1,                 // 境界 未知 渡劫率 灵根总纲
        1, 0, 1, 1, 0, 1, 1, 0,     // 八灵根
        4800, 217750, 0, 35, 0,     // 灵气 武力 状态 突破率 丹毒
        0, 0, 1, 1,                 // 未知×2 下界劫身 上界元身
        217750,                     // 武力上限
    )

    @Test
    fun analyzerFindsFaceInMapOfArrays() {
        val w = MpWriter()
        w.write(MpValue.Map(linkedMapOf(
            MpValue.Str("hero") to MpValue.Map(linkedMapOf(
                MpValue.Str("name") to MpValue.Str("叶星华"),
                MpValue.Str("face") to MpValue.Arr(faceValues().map { MpValue.Int(it) }),
            )),
        )))
        val st = SaveAnalyzer.analyze(w.toByteArray())
        assertEquals("messagepack", st.format)
        assertNotNull(st.bestFace)
        val vals = st.bestFace!!.values
        assertEquals(149L, vals[0])
        assertEquals(6L, vals[3]) // 眼睛=6
        assertEquals(222L, vals[12]) // 发色色相
    }

    @Test
    fun analyzerFindsCharRecord() {
        val root = MpValue.Map(linkedMapOf(
            MpValue.Str("people") to MpValue.Arr(listOf(
                MpValue.Map(linkedMapOf(MpValue.Str("x") to MpValue.Int(1))),
                MpValue.Map(linkedMapOf(MpValue.Str("y") to MpValue.Str("z"))),
                MpValue.Arr(charValues().map { MpValue.Int(it) }),
            )),
        ))
        val st = SaveAnalyzer.analyze(MessagePack.serialize(root))
        assertNotNull(st.bestChar)
        val v = st.bestChar!!.values
        assertEquals(3080L, v[0])
        assertEquals(8L, v[1])
        assertEquals(3L, v[2])
    }

    @Test
    fun analyzerFindsRecordsInRawBinary() {
        val face = faceValues()
        val chars = charValues()
        val buf = java.io.ByteArrayOutputStream()
        buf.write(ByteArray(16))                         // header junk
        face.forEach { buf.writeLe32(it.toInt()) }
        buf.write(ByteArray(8))
        chars.forEach { buf.writeLe32(it.toInt()) }
        val st = SaveAnalyzer.analyze(buf.toByteArray())
        assertEquals("raw-int32", st.format)
        assertNotNull(st.rawMatches)
        val faceMatch = st.rawMatches.first { it.kind == RecordKind.FACE }
        assertEquals(16, faceMatch.offset)
        assertEquals(222L, faceMatch.values[12])
        assertNotNull(st.rawMatches.firstOrNull { it.kind == RecordKind.CHAR })
    }

    @Test
    fun editorTreeRoundTrip() {
        val root = MpValue.Map(linkedMapOf(
            MpValue.Str("hero") to MpValue.Map(linkedMapOf(
                MpValue.Str("face") to MpValue.Arr(faceValues().map { MpValue.Int(it) }),
                MpValue.Str("lifespan") to MpValue.Int(100),
            )),
        ))
        val bytes = MessagePack.serialize(root)
        val st = SaveAnalyzer.analyze(bytes)
        val face = st.bestFace!!
        val edited = SaveEditor.apply(st, listOf(
            SaveEditor.FieldEdit(SaveEditor.FieldRef.TreeField(face.loc, 4), 9L),    // 眼睛 6→9
            SaveEditor.FieldEdit(SaveEditor.FieldRef.TreeField(face.loc, 13), 360L), // 发色相 222→360
        ))
        val st2 = SaveAnalyzer.analyze(edited)
        assertEquals(9L, st2.bestFace!!.values[3])
        assertEquals(360L, st2.bestFace!!.values[12])
        assertEquals(149L, st2.bestFace!!.values[0])
        // unrelated values preserved
        assertEquals(
            100L,
            ((st2.tree as MpValue.Map).entries[MpValue.Str("hero")] as MpValue.Map)
                .entries[MpValue.Str("lifespan")]!!.asIntOrNull(),
        )
    }

    @Test
    fun editorRawPatch() {
        val face = faceValues()
        val buf = java.io.ByteArrayOutputStream()
        buf.write(ByteArray(8))
        face.forEach { buf.writeLe32(it.toInt()) }
        val st = SaveAnalyzer.analyze(buf.toByteArray())
        val off = st.rawMatches.first { it.kind == RecordKind.FACE }.offset
        val out = SaveEditor.apply(st, listOf(
            SaveEditor.FieldEdit(SaveEditor.FieldRef.RawField(off + 4), 3L), // 脸型 0→3
        ))
        val st2 = SaveAnalyzer.analyze(out)
        assertEquals(3L, st2.rawMatches.first { it.kind == RecordKind.FACE }.values[1])
    }

    @Test
    fun editorReadFieldHelpers() {
        val face = faceValues()
        val root = MpValue.Map(linkedMapOf(
            MpValue.Str("hero") to MpValue.Map(linkedMapOf(
                MpValue.Str("face") to MpValue.Arr(face.map { MpValue.Int(it) }),
            )),
        ))
        val st = SaveAnalyzer.analyze(MessagePack.serialize(root))
        val eye = SaveEditor.FieldRef.TreeField(st.bestFace!!.loc, 4)
        assertEquals(6L, SaveEditor.readField(st, eye))   // 眼睛 = 6
        val mouth = SaveEditor.FieldRef.TreeField(st.bestFace!!.loc, 5)
        assertEquals(9L, SaveEditor.readField(st, mouth))  // 嘴巴 = 9
        val childBack = SaveEditor.FieldRef.TreeField(st.bestFace!!.loc, 20)
        assertEquals(10L, SaveEditor.readField(st, childBack))  // 幼后发 = 10
    }

    @Test
    fun faceSchemaShapeSanity() {
        assertEquals(20, FaceSchema.count)
        assertEquals(39, com.yaowanggu.trainer.data.schema.CharSchema.count)
        FaceSchema.fields.forEach { assertEquals(it.index, FaceSchema.fields.indexOf(it) + 1) }
    }

    @Test
    fun analyzerNoFalsePositiveOnBclLikeData() {
        // a flat map of small strings should not produce face/char records
        val root = MpValue.Map(linkedMapOf(
            MpValue.Str("a") to MpValue.Str("hello"),
            MpValue.Str("b") to MpValue.Str("world"),
            MpValue.Str("c") to MpValue.Bool(true),
        ))
        val st = SaveAnalyzer.analyze(MessagePack.serialize(root))
        assertNull(st.bestFace)
        assertNull(st.bestChar)
        assertTrue(st.records.isEmpty())
    }
}

private fun java.io.ByteArrayOutputStream.writeLe32(v: Int) {
    write(v and 0xFF); write((v shr 8) and 0xFF); write((v shr 16) and 0xFF); write((v shr 24) and 0xFF)
}
