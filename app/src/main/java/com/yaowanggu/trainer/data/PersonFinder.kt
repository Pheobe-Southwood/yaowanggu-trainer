package com.yaowanggu.trainer.data

import com.yaowanggu.trainer.data.msgpack.MpValue
import com.yaowanggu.trainer.data.schema.FaceSchema

/**
 * 结构化角色定位器（依据 2026-09/10 真机诊断包实证）：
 *
 * 外观模块（nfile30）= msgpack 根数组，含 300+ 条变长 int 记录（每角色一条）；
 * 每条记录尾部附近有一个 20 字段五官窗口，且 窗口[0] == 记录索引+1（角色 ID）。
 * 五官窗口之前是角色前缀数据（含灵气/武力等数值）。
 *
 * 定位规则（按优先级）：
 * 1. ID 锚定：从记录尾部向前找 window[0] == recordIndex+1 且落在五官合理容错范围内的窗口；
 * 2. 范围兜底：window[0] >= 1、部件<=30、颜色<=1000、非全零退化窗口。
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
        /** 整条记录的全部 int 值（供 CharMap 按偏移读取属性字段） */
        val recordValues: List<Long?> = emptyList(),
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
                    recordValues = vals,
                )
            )
        }

        // 多数命中才认定为角色模块；锚定比例过低说明 ID 规则不符，仅保留严格命中也不可信 → 要求锚定为主
        if (out.size * 2 < eligible) return emptyList()
        if (out.size >= 8 && anchored * 2 < out.size) return emptyList()
        return out
    }

    /** FaceSchema 范围容错 + 非退化校验。 */
    private fun faceStrict(w: List<Long?>): Boolean {
        if (w.size < FaceSchema.count) return false
        val limits = intArrayOf(
            999_999,          // 1 ID
            30, 30, 30, 30,   // 脸型 眉毛 眼睛 嘴巴
            30,               // 鼻子
            30, 30,           // 前发 后发
            30,               // 性格
            30,               // 痣
            1000, 1000,       // 肤色 饱和/明暗
            1000, 1000, 1000, // 发色 色相/饱和/明暗（允许历史越界值如 440）
            1000, 1000, 1000, // 瞳色 色相/饱和/明暗
            30, 30,           // 幼前发 幼后发
        )
        for (i in 0 until FaceSchema.count) {
            val x = w[i] ?: return false
            if (x < 0 || x > limits[i]) return false
        }
        val id = w[0] ?: return false
        if (id < 1) return false                       // 角色 ID 从 1 起
        if (w.subList(1, FaceSchema.count).all { it == 0L }) return false // 拒绝全零退化窗口
        return true
    }
}
