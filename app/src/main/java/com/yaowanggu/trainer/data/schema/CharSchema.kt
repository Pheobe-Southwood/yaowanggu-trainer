package com.yaowanggu.trainer.data.schema

/**
 * 角色主数据字段表（39 项）。
 * 来源：社区「角色字段表」（IMG_20250320_095412 教程截图），
 * 与 GG 教程内存实测偏移一致（寿元=+0x00，随后出生月/日，性别在 +0x10）。
 */
data class CharField(
    val index: Int,
    val name: String,
    val max: Int,
    val enumLabels: Map<Int, String> = emptyMap(),
    val note: String = "",
)

object CharSchema {
    val fields: List<CharField> = listOf(
        CharField(1, "寿元", 1_000_000),
        CharField(2, "出生月", 12),
        CharField(3, "出生日", 31),
        CharField(4, "未知④", Int.MAX_VALUE, note = "社区表格标注未知"),
        CharField(5, "性别", 4, enumLabels = mapOf(1 to "男孩", 2 to "女孩", 3 to "男人", 4 to "女人"),
            note = "内存实测 3=男 4=女；社区表记 男3女2，以实测为准"),
        CharField(6, "所在地", 999),
        CharField(7, "门派", 999),
        CharField(8, "职位", 999, enumLabels = mapOf(1 to "凌霄宗首席弟子", 2 to "药王谷弟子")),
        CharField(9, "妖族身份(兽耳)", 11, note = "改此值可加兽耳，代码见参考图"),
        CharField(10, "未知⑩", Int.MAX_VALUE),
        CharField(11, "父亲", 99999),
        CharField(12, "母亲", 99999),
        CharField(13, "师尊", 99999),
        CharField(14, "道侣", 99999),
        CharField(15, "未知⑮", Int.MAX_VALUE),
        CharField(16, "未知⑯", Int.MAX_VALUE),
        CharField(17, "未知⑰", Int.MAX_VALUE),
        CharField(18, "境界", 8, enumLabels = mapOf(1 to "炼气", 2 to "筑基", 3 to "金丹", 4 to "元婴", 5 to "化神", 6 to "炼虚", 7 to "合体", 8 to "飞升")),
        CharField(19, "未知⑲", Int.MAX_VALUE),
        CharField(20, "渡劫死亡率", 100),
        CharField(21, "灵根总纲", 2, enumLabels = mapOf(0 to "变异天灵根", 1 to "天灵根", 2 to "变异灵根", 3 to "杂灵根")),
        CharField(22, "金灵根", 1, enumLabels = mapOf(0 to "无", 1 to "有")),
        CharField(23, "水灵根", 1, enumLabels = mapOf(0 to "无", 1 to "有")),
        CharField(24, "木灵根", 1, enumLabels = mapOf(0 to "无", 1 to "有")),
        CharField(25, "火灵根", 1, enumLabels = mapOf(0 to "无", 1 to "有")),
        CharField(26, "土灵根", 1, enumLabels = mapOf(0 to "无", 1 to "有")),
        CharField(27, "冰灵根", 1, enumLabels = mapOf(0 to "无", 1 to "有")),
        CharField(28, "风灵根", 1, enumLabels = mapOf(0 to "无", 1 to "有")),
        CharField(29, "雷灵根", 1, enumLabels = mapOf(0 to "无", 1 to "有")),
        CharField(30, "灵气", 99_999_999),
        CharField(31, "武力", 99_999_999),
        CharField(32, "状态", 1, enumLabels = mapOf(0 to "正常", 1 to "移动中")),
        CharField(33, "突破率", 100),
        CharField(34, "丹药毒", 100),
        CharField(35, "未知㉟", Int.MAX_VALUE),
        CharField(36, "未知㊱", Int.MAX_VALUE),
        CharField(37, "下界劫身", 1, enumLabels = mapOf(0 to "无", 1 to "有")),
        CharField(38, "上界元身", 1, enumLabels = mapOf(0 to "无", 1 to "有")),
        CharField(39, "武力值上限", 99_999_999),
    )

    val count: Int get() = fields.size
    fun byIndex(i: Int): CharField? = fields.firstOrNull { it.index == i }
}
