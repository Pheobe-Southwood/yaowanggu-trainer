package com.yaowanggu.trainer.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLogTest {

    @Test
    fun ringBufferCapsAt2000() {
        repeat(2500) { AppLog.i("line$it") }
        val snap = AppLog.snapshot()
        assertEquals(2000, snap.size)
        assertTrue(snap.last().contains("line2499"))
        assertTrue(snap.first().contains("line500"))
        assertTrue(AppLog.snapshotText().contains("line2499"))
    }
}
