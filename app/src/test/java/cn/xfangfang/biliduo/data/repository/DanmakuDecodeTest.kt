package cn.xfangfang.biliduo.data.repository

import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.GZIPOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class DanmakuDecodeTest {

    private val xml = """<?xml version="1.0" encoding="UTF-8"?><i><d p="1,1,25,16777215">hi</d></i>"""
        .toByteArray(Charsets.UTF_8)

    @Test
    fun `plain body wins over a deflate header`() {
        // Observed on Bilibili's CDN: small dm/list.so pools come back as
        // plain XML while the header still says Content-Encoding: deflate.
        assertArrayEquals(xml, decodeDanmakuBody(xml, "deflate"))
    }

    @Test
    fun `plain body with leading whitespace and bom`() {
        val body = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
            "\n  ".toByteArray() + xml
        assertArrayEquals(body, decodeDanmakuBody(body, "deflate"))
    }

    @Test
    fun `raw deflate body is inflated`() {
        val compressed = deflate(xml, nowrap = true)
        val decoded = decodeDanmakuBody(compressed, "deflate")
        assertEquals(String(xml, Charsets.UTF_8), String(decoded, Charsets.UTF_8))
    }

    @Test
    fun `zlib deflate body is inflated`() {
        val compressed = deflate(xml, nowrap = false)
        val decoded = decodeDanmakuBody(compressed, "deflate")
        assertEquals(String(xml, Charsets.UTF_8), String(decoded, Charsets.UTF_8))
    }

    @Test
    fun `gzip body is inflated even without a header`() {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(xml) }
        val decoded = decodeDanmakuBody(out.toByteArray(), "")
        assertEquals(String(xml, Charsets.UTF_8), String(decoded, Charsets.UTF_8))
    }

    private fun deflate(data: ByteArray, nowrap: Boolean): ByteArray {
        val deflater = Deflater(Deflater.DEFAULT_COMPRESSION, nowrap)
        return try {
            deflater.setInput(data)
            deflater.finish()
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(1024)
            while (!deflater.finished()) {
                val count = deflater.deflate(buffer)
                out.write(buffer, 0, count)
            }
            out.toByteArray()
        } finally {
            deflater.end()
        }
    }
}
