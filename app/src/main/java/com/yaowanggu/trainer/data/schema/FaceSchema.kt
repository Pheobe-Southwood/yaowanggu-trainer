package com.yaowanggu.trainer.data.schema

/**
 * 捏脸/外观字段表（20 项）。
 * 来源：社区「外观字段表」（IMG_20250320_095412 教程截图），与 GG 内存实测顺序一致：
 * ID → 脸型 → 眉毛 → 眼睛 → 嘴巴 → 鼻子 → 前发 → 后发 → 性格 → 痣 →
 * 肤色饱和 → 肤色明暗 → 发色色相 → 发色饱和 → 发色明暗 →
 * 瞳色色相 → 瞳色饱和 → 瞳色明暗 → 幼前发 → 幼后发
 *
 * 编号即存档中的字段序号（1 起）。颜色为 HSV：色相 0–360，饱和/明暗 0–400。
 */
data class FaceField(
    val index: Int,          // 1..20 存档序号
    val name: String,        // 中文名
    val max: Int,            // 经验最大值（含）
    val kind: Kind,
    val enumLabels: Map<Int, String> = emptyMap(),
) {
    enum class Kind { ID, OPTION, TRAIT, MOLE, HUE, SAT_VAL }
}

object FaceSchema {
    val fields: List<FaceField> = listOf(
        FaceField(1, "ID", 999999, FaceField.Kind.ID),
        FaceField(2, "脸型", 12, FaceField.Kind.OPTION),
        FaceField(3, "眉毛", 12, FaceField.Kind.OPTION),
        FaceField(4, "眼睛", 12, FaceField.Kind.OPTION),
        FaceField(5, "嘴巴", 12, FaceField.Kind.OPTION),
        FaceField(6, "鼻子", 8, FaceField.Kind.OPTION),
        FaceField(7, "前发", 12, FaceField.Kind.OPTION),
        FaceField(8, "后发", 12, FaceField.Kind.OPTION),
        FaceField(9, "性格", 9, FaceField.Kind.TRAIT),
        FaceField(10, "痣", 3, FaceField.Kind.MOLE),
        FaceField(11, "肤色·饱和", 400, FaceField.Kind.SAT_VAL),
        FaceField(12, "肤色·明暗", 400, FaceField.Kind.SAT_VAL),
        FaceField(13, "发色·色相", 360, FaceField.Kind.HUE),
        FaceField(14, "发色·饱和", 400, FaceField.Kind.SAT_VAL),
        FaceField(15, "发色·明暗", 400, FaceField.Kind.SAT_VAL),
        FaceField(16, "瞳色·色相", 360, FaceField.Kind.HUE),
        FaceField(17, "瞳色·饱和", 400, FaceField.Kind.SAT_VAL),
        FaceField(18, "瞳色·明暗", 400, FaceField.Kind.SAT_VAL),
        FaceField(19, "幼前发", 12, FaceField.Kind.OPTION),
        FaceField(20, "幼后发", 12, FaceField.Kind.OPTION),
    )

    val count: Int get() = fields.size

    fun byIndex(i: Int): FaceField? = fields.firstOrNull { it.index == i }

    /** 色相字段分组（用于 UI 取色） */
    val hairHueIndex = 13
    val hairSatIndex = 14
    val hairValIndex = 15
    val eyeHueIndex = 16
    val eyeSatIndex = 17
    val eyeValIndex = 18
    val skinSatIndex = 11
    val skinValIndex = 12
}
