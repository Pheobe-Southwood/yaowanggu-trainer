package com.yaowanggu.trainer.data.export

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 把诊断包条目打包成 zip 字节流。
 * 条目由 TrainerViewModel 组装（存档原始字节 / 解压 inner / diagnostics.txt / applog.txt）。
 */
object DiagExporter {

    fun buildZip(entries: Map<String, ByteArray>): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { z ->
            entries.forEach { (name, data) ->
                z.putNextEntry(ZipEntry(name))
                z.write(data)
                z.closeEntry()
            }
        }
        return bos.toByteArray()
    }
}
