package com.yaowanggu.trainer.data.export

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

class DiagExporterTest {

    @Test
    fun zipRoundTrip() {
        val entries = linkedMapOf(
            "diagnostics.txt" to "hello".toByteArray(),
            "applog.txt" to "line1\nline2".toByteArray(),
            "saves/nfile0.save" to byteArrayOf(1, 2, 3),
        )
        val zip = DiagExporter.buildZip(entries)
        val names = mutableListOf<String>()
        ZipInputStream(ByteArrayInputStream(zip)).use { z ->
            var e = z.nextEntry
            while (e != null) {
                names += e.name
                assertArrayEquals(entries[e.name], z.readBytes())
                z.closeEntry()
                e = z.nextEntry
            }
        }
        assertEquals(entries.keys.toList(), names)
    }

    @Test
    fun emptyZipIsValid() {
        val zip = DiagExporter.buildZip(emptyMap())
        ZipInputStream(ByteArrayInputStream(zip)).use { z ->
            assertEquals(null, z.nextEntry)
        }
    }
}
