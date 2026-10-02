package com.yaowanggu.trainer.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonQueryTest {

    @Test
    fun emptyQueryMatchesAll() {
        assertTrue(PersonQuery.matches(353, null, 1176000, ""))
        assertTrue(PersonQuery.matches(353, "弘元", 1176000, "   "))
    }

    @Test
    fun aliasMatchIsCaseInsensitiveSubstring() {
        assertTrue(PersonQuery.matches(353, "弘元", 1176000, "元"))
        assertTrue(PersonQuery.matches(10, "Yu", 510, "yu"))
        assertFalse(PersonQuery.matches(10, "于至行", 510, "党"))
    }

    @Test
    fun idSubstringMatch() {
        assertTrue(PersonQuery.matches(353, null, 1176000, "35"))
        assertTrue(PersonQuery.matches(10, null, 510, "10"))
        assertFalse(PersonQuery.matches(10, null, 510, "99"))
    }

    @Test
    fun powerSubstringMatch() {
        assertTrue(PersonQuery.matches(3, null, 175100, "175100"))
        assertTrue(PersonQuery.matches(3, null, 175100, "751"))
        assertFalse(PersonQuery.matches(3, null, 175100, "175101"))
        assertTrue(PersonQuery.matches(8, null, null, "8")) // 无武力值仍可按 ID 命中
    }
}
