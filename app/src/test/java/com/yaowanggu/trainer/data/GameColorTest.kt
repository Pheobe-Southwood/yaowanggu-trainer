package com.yaowanggu.trainer.data

import org.junit.Assert.assertEquals
import org.junit.Test

class GameColorTest {

    @Test
    fun hueIsMirrored() {
        // 社区色表样本：游戏色相 → 标准色相
        assertEquals(347f, GameColor.mirrorHue(13), 0.001f)   // 鲜红
        assertEquals(338f, GameColor.mirrorHue(22), 0.001f)   // 粉
        assertEquals(308f, GameColor.mirrorHue(52), 0.001f)   // 品红
        assertEquals(288f, GameColor.mirrorHue(72), 0.001f)   // 淡紫
        assertEquals(232f, GameColor.mirrorHue(128), 0.001f)  // 深藏青
        assertEquals(214f, GameColor.mirrorHue(146), 0.001f)  // 蓝
        assertEquals(124f, GameColor.mirrorHue(236), 0.001f)  // 绿
        assertEquals(29f, GameColor.mirrorHue(331), 0.001f)   // 亮黄
        assertEquals(0f, GameColor.mirrorHue(360), 0.001f)    // 棕
        assertEquals(0f, GameColor.mirrorHue(0), 0.001f)
    }

    @Test
    fun satValueScaleAndClamp() {
        assertEquals(1f, GameColor.scale01(200), 0.001f)
        assertEquals(1f, GameColor.scale01(400), 0.001f) // 上限钳制
        assertEquals(0.25f, GameColor.scale01(50), 0.001f)
        assertEquals(0.735f, GameColor.scale01(147), 0.001f)
        assertEquals(0f, GameColor.scale01(0), 0.001f)
        assertEquals(0f, GameColor.scale01(-5), 0.001f)
    }

    @Test
    fun stdHsvCombines() {
        val hsv = GameColor.stdHsv(146, 200, 200) // 色表：蓝
        assertEquals(214f, hsv[0], 0.001f)
        assertEquals(1f, hsv[1], 0.001f)
        assertEquals(1f, hsv[2], 0.001f)
    }

    @Test
    fun skinUsesBaseHue() {
        val hsv = GameColor.skinStdHsv(78, 118)
        assertEquals(GameColor.SKIN_BASE_HUE, hsv[0], 0.001f)
        assertEquals(0.39f, hsv[1], 0.001f)
        assertEquals(0.59f, hsv[2], 0.001f)
    }
}
