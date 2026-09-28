package com.yaowanggu.trainer.data

import com.yaowanggu.trainer.data.msgpack.MpValue
import com.yaowanggu.trainer.data.schema.FaceSchema

/**
 * 结构化角色定位器（依据 2026-09 真机诊断包实证）：
 *
 * 外观模块（nfile30）= msgpack 根数组，含 347 条变长 int 记录（每角色一条）；
 * 每条记录尾部附近有一个 20 字段五官窗口，且 窗口[0] == 记录索引+1（角色 ID），347/347 验证通过。
 * 五官窗口之前是角色前缀数据（含疑似 灵气/武力 等大数）。
 *
 * 定位规则（按优先级）：
 * 1. ID 锚定：从记录尾部向前找 window[0] == recordIndex+1 且字段全部落在 FaceSchema 严格范围内的窗口；
 * 2. 严格范围兜底：window[0] >= 1、痣<=3、性格<=9、鼻<=8、选项<=12、色相<=360、饱和/明暗<=400、
 *    非全零、发色或瞳色色相 > 0。
 * 只有当多数记录都命中时才认定为「角色模块」，避免把普通 int 数组误判为角色记录。
 */
object PersonFinder {

    data class PersonRecord(
        val recordIndex: Int,
        val charId: Long,
        /** 五官窗口位置（path=[Index(recordIndex)], start=窗口偏移），可直接用于 TreeField 编辑 */
        val loc: SeqLocation,
        val faceValues: List<Long?>,
        /** 记录中最大的两个 int（疑似灵气/武力），用于与游戏内数值对照确认玩家角色 */
        val hints: List<Long>,
        val recordLen: Int,
    )

    fun findPersons(tree: MpValue?): List<PersonRecord> {
        val root = tree as? MpValue.Arr ?: return emptyList()
        val eligible = root.items.count { (it as? MpValue.Arr)?.items?.size ?: 0 >= FaceSchema.count }
        if (eligible < 4) return emptyList()

        val out = ArrayList<PersonRecord>()
        var anchored = 0
        root.items.forEachIndexed { i, item ->
            val rec = item as? MpValue.Arr ?: return@forEachIndexed
            val vals = rec.items.map { it.asIntOrNull() }
            if (vals.size < FaceSchema.count) return@forEachIndexed

            var start = -1
            var isAnchored = false
            // 1) ID 锚定（从尾部向前，真脸在记录尾部）
            for (s in vals.size - FaceSchema.count downTo 0) {
                val w = vals.subList(s, s + FaceSchema.count)
                if (w[0] == (i + 1).toLong() && faceStrict(w)) { start = s; isAnchored = true; break }
            }
            // 2) 严格范围兜底
            if (start < 0) {
                for (s in vals.size - FaceSchema.count downTo 0) {
                    val w = vals.subList(s, s + FaceSchema.count)
                    if (faceStrict(w)) { start = s; break }
                }
            }
            if (start < 0) return@forEachIndexed
            if (isAnchored) anchored++

            val w = vals.subList(start, start + FaceSchema.count).toList()
            val hints = vals.filterNotNull().filter { it > 1000 }.sortedDescending().take(2)
            out.add(
                PersonRecord(
                    recordIndex = i,
                    charId = w[0] ?: (i + 1).toLong(),
                    loc = SeqLocation(listOf(PathStep.Index(i)), start),
                    faceValues = w,
                    hints = hints,
                    recordLen = vals.size,
                )
            )
        }

        // 多数命中才认定为角色模块；锚定比例过低说明 ID 规则不符，仅保留严格命中也不可信 → 要求锚定为主
        if (out.size * 2 < eligible) return emptyList()
        if (out.size >= 8 && anchored * 2 < out.size) return emptyList()
        return out
    }

    /** FaceSchema 严格范围 + 非退化校验。 */
    private fun faceStrict(w: List<Long?>): Boolean {
        if (w.size < FaceSchema.count) return false
        val limits = intArrayOf(
            999_999,          // 1 ID
            12, 12, 12, 12,   // 脸型 眉毛 眼睛 嘴巴
            8,                // 鼻子
            12, 12,           // 前发 后发
            9,                // 性格
            3,                // 痣
            400, 400,         // 肤色 饱和/明暗
            360, 400, 400,    // 发色 色相/饱和/明暗
            360, 400, 400,    // 瞳色 色相/饱和/明暗
            12, 12,           // 幼前发 幼后发
        )
        for (i in 0 until FaceSchema.count) {
            val x = w[i] ?: return false
            if (x < 0 || x > limits[i]) return false
        }
        val id = w[0] ?: return false
        if (id < 1) return false                       // 角色 ID 从 1 起
        if (w.subList(1, FaceSchema.count).all { it == 0L }) return false // 拒绝全零退化窗口
        if ((w[12] ?: 0) == 0L && (w[15] ?: 0) == 0L) return false        // 发色/瞳色色相至少一个非零
        return true
    }
}
