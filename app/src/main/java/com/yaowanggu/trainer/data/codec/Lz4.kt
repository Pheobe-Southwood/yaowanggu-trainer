package com.yaowanggu.trainer.data.codec

/**
 * 纯 Kotlin 的 LZ4 **block 格式**解码器（非 frame 格式）。
 * MessagePack-CSharp 的 Lz4Block / Lz4BlockArray 压缩载荷即为此格式。
 * 只需要解码：写回存档时我们输出未压缩的 plain msgpack（官方反序列化兼容）。
 */
class Lz4Exception(message: String) : Exception(message)

object Lz4 {

    /**
     * 解码一个 LZ4 block。
     * @param input 压缩字节
     * @param outputLength 期望的解压长度（MessagePack ext99 头部提供），用于边界校验
     */
    fun decompressBlock(input: ByteArray, outputLength: Int): ByteArray {
        if (outputLength < 0) throw Lz4Exception("negative output length")
        val out = ByteArray(outputLength)
        val ilen = input.size
        var ip = 0
        var op = 0

        while (ip < ilen) {
            val token = input[ip].toInt() and 0xFF
            ip++

            // ---- literals ----
            var litLen = token shr 4
            if (litLen == 15) {
                while (true) {
                    if (ip >= ilen) throw Lz4Exception("literal length extension overrun")
                    val b = input[ip].toInt() and 0xFF
                    ip++
                    litLen += b
                    if (litLen > outputLength) throw Lz4Exception("literal length exceeds output size")
                    if (b != 255) break
                }
            }
            if (op + litLen > outputLength) throw Lz4Exception("literal copy out of bounds")
            if (ip + litLen > ilen) throw Lz4Exception("literal source overrun")
            if (litLen > 0) {
                System.arraycopy(input, ip, out, op, litLen)
                ip += litLen
                op += litLen
            }

            // 最后一个 sequence 只有 literals
            if (ip >= ilen) break

            // ---- match ----
            if (ip + 2 > ilen) throw Lz4Exception("missing match offset")
            val offset = (input[ip].toInt() and 0xFF) or ((input[ip + 1].toInt() and 0xFF) shl 8)
            ip += 2
            if (offset <= 0 || offset > op) throw Lz4Exception("invalid offset $offset at op=$op")

            var matchLen = token and 0x0F
            if (matchLen == 15) {
                while (true) {
                    if (ip >= ilen) throw Lz4Exception("match length extension overrun")
                    val b = input[ip].toInt() and 0xFF
                    ip++
                    matchLen += b
                    if (matchLen > outputLength) throw Lz4Exception("match length exceeds output size")
                    if (b != 255) break
                }
            }
            matchLen += 4
            if (op + matchLen > outputLength) throw Lz4Exception("match copy out of bounds")

            var src = op - offset
            repeat(matchLen) {
                out[op] = out[src]
                op++
                src++
            }
        }

        if (op != outputLength) throw Lz4Exception("decoded size $op != expected $outputLength")
        return out
    }

    /**
     * 把数据打包成「纯字面量」的合法 LZ4 block（单 sequence，无 match 部分）。
     * 用于 Lz4Block 容器写回：解码端（含本文件 decompressBlock 与 MessagePack-CSharp）
     * 在输入耗尽时结束，末尾无 match 是合法块。
     */
    fun compressLiteralBlock(data: ByteArray): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val n = data.size
        if (n >= 15) {
            out.write(0xF0) // litLen=15 + 扩展链, matchLen=0
            var rem = n - 15
            while (rem >= 255) { out.write(255); rem -= 255 }
            out.write(rem)
        } else {
            out.write(n shl 4)
        }
        out.write(data)
        return out.toByteArray()
    }
}
