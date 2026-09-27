package com.yaowanggu.trainer.data.codec

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class Lz4Test {

    @Test
    fun literalsOnlyShort() {
        // token 0x50 = litLen 5, matchLen 0；"Hello"
        val input = byteArrayOf(0x50, 72, 101, 108, 108, 111)
        assertArrayEquals("Hello".toByteArray(), Lz4.decompressBlock(input, 5))
    }

    @Test
    fun literalsOnly15Extension() {
        val data = ByteArray(20) { (0x41 + it).toByte() } // 'A'..'T'
        val input = ByteArray(2 + 20)
        input[0] = 0xF0.toByte()
        input[1] = 5 // 15 + 5 = 20
        System.arraycopy(data, 0, input, 2, 20)
        assertArrayEquals(data, Lz4.decompressBlock(input, 20))
    }

    @Test
    fun matchBackReference() {
        // literals "abcd" + match offset=4 len=4 → "abcdabcd" + 末尾空 literal token
        val input = byteArrayOf(0x40, 0x61, 0x62, 0x63, 0x64, 0x04, 0x00, 0x00)
        assertArrayEquals("abcdabcd".toByteArray(), Lz4.decompressBlock(input, 8))
    }

    @Test
    fun overlappingMatch() {
        // literals "abc" + match offset=3 len=4 → "abcabca"
        val input = byteArrayOf(0x30, 0x61, 0x62, 0x63, 0x03, 0x00)
        assertArrayEquals("abcabca".toByteArray(), Lz4.decompressBlock(input, 7))
    }

    @Test(expected = Lz4Exception::class)
    fun corruptOffsetThrows() {
        val input = byteArrayOf(0x40, 0x61, 0x62, 0x63, 0x64, 0x09, 0x00)
        Lz4.decompressBlock(input, 8)
    }

    @Test(expected = Lz4Exception::class)
    fun sizeMismatchThrows() {
        val input = byteArrayOf(0x50, 72, 101, 108, 108, 111)
        Lz4.decompressBlock(input, 6)
    }
}
