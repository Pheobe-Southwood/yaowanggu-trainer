package com.yaowanggu.trainer.data

/**
 * 游戏内颜色 → 标准 HSV 的映射（2026-09 社区色表 color.jpg 13 组标注样本实证）：
 *
 * - 色相：游戏值与标准色相**镜像**，std = (360 − game) mod 360。
 *   样本佐证：13→鲜红(347°)、22→粉(338°)、52→品红(308°)、72→淡紫(288°)、
 *   128→深藏青(232°)、146→蓝(214°)、236→绿(124°)、331→亮黄(29°)、360→棕(0°)。
 * - 饱和/明暗：0..200 → 0..1 截断（200 即满；400 为上限钳制）。
 *   佐证：13;200;147 = 饱和鲜红、128;54;50 = 深藏青、222;0;400 = 白。
 *
 * 纯函数，便于单测；UI 侧再用 android.graphics.Color.HSVToColor 转 ARGB。
 */
object GameColor {

    /** 发色/瞳色：游戏 (hue, sat, value) → 标准 HSV float[3]（h 0-360, s/v 0-1）。 */
    fun stdHsv(hue: Long, sat: Long, value: Long): FloatArray = floatArrayOf(
        mirrorHue(hue),
        scale01(sat),
        scale01(value),
    )

    /** 肤色：存档中只有饱和/明暗两个字段，色相为游戏内固定基色（约 25° 暖肤）。 */
    fun skinStdHsv(sat: Long, value: Long): FloatArray = floatArrayOf(
        SKIN_BASE_HUE,
        scale01(sat),
        scale01(value),
    )

    const val SKIN_BASE_HUE: Float = 25f

    fun mirrorHue(hue: Long): Float {
        val h = ((hue % 360) + 360) % 360
        return ((360 - h) % 360).toFloat()
    }

    fun scale01(x: Long): Float = (x / 200f).coerceIn(0f, 1f)
}
