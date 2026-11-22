package cn.xfangfang.biliduo.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateManifestParserTest {

    private fun parse(raw: String): UpdateInfo =
        UpdateManifestParser.parse(raw) ?: error("manifest should parse")

    private val sha = "a".repeat(64)

    private fun manifest(
        versionCode: Long = 60,
        versionName: String = "0.2.0-rc59",
        downloads: String =
            """[
                {"name":"cn","url":"https://bili-duo.sourden.xyz/releases/rc59.apk"},
                {"name":"github","url":"https://github.com/0sour/bili-duo/releases/download/v0.2.0-rc59/rc59.apk"}
            ]""",
        size: Long = 12_345_678L,
        sha256: String = sha,
        extra: String = "",
    ): String = """
        {
          "versionCode": $versionCode,
          "versionName": "$versionName",
          "tag": "v$versionName",
          "downloads": $downloads,
          "size": $size,
          "sha256": "$sha256",
          "notes": "1. 番剧索引\n2. 修复若干问题",
          "publishedAt": "2026-11-22T10:00:00Z",
          "minSdk": 24,
          "channel": "beta"$extra
        }
    """.trimIndent()

    @Test
    fun parses_full_manifest() {
        val info = parse(manifest())
        assertEquals(60L, info.versionCode)
        assertEquals("0.2.0-rc59", info.versionName)
        assertEquals("v0.2.0-rc59", info.tag)
        assertEquals(2, info.downloads.size)
        assertEquals(12_345_678L, info.size)
        assertEquals(sha, info.sha256)
        assertEquals(24, info.minSdk)
        assertEquals("beta", info.channel)
        assertTrue(info.notes.contains("番剧索引"))
    }

    @Test
    fun compares_by_version_code_not_release_name() {
        val info = parse(manifest(versionCode = 60))
        assertTrue(info.isNewerThan(59))
        assertFalse(info.isNewerThan(60))
        assertFalse(info.isNewerThan(61))
    }

    @Test
    fun cn_uses_named_source_and_falls_back_to_first_url() {
        val named = parse(manifest())
        assertEquals(
            "https://bili-duo.sourden.xyz/releases/rc59.apk",
            named.downloadUrl(UpdateSource.CN)
        )
        assertEquals(
            "https://github.com/0sour/bili-duo/releases/download/v0.2.0-rc59/rc59.apk",
            named.downloadUrl(UpdateSource.GITHUB)
        )

        val onlyFirst = parse(
            manifest(
                downloads = """[{"name":"mirror","url":"https://example.com/a.apk"}]"""
            )
        )
        assertEquals("https://example.com/a.apk", onlyFirst.downloadUrl(UpdateSource.CN))
        assertNull(onlyFirst.downloadUrl(UpdateSource.GITHUB))

        val githubOnly = parse(
            manifest(
                downloads =
                    """[{"name":"github","url":"https://github.com/a/b/releases/a.apk"}]"""
            )
        )
        assertNull(githubOnly.downloadUrl(UpdateSource.CN))
        assertEquals(
            "https://github.com/a/b/releases/a.apk",
            githubOnly.downloadUrl(UpdateSource.GITHUB)
        )
    }

    @Test
    fun accepts_legacy_single_apk_url() {
        val raw = """
            {
              "versionCode": 60,
              "versionName": "0.2.0-rc59",
              "tag": "v0.2.0-rc59",
              "apkUrl": "https://example.com/bili-duo.apk",
              "size": 100,
              "sha256": "$sha"
            }
        """.trimIndent()
        val info = parse(raw)
        assertEquals("https://example.com/bili-duo.apk", info.downloadUrl(UpdateSource.CN))
        assertEquals("v0.2.0-rc59", info.tag)
    }

    @Test
    fun rejects_invalid_manifests() {
        assertNull(UpdateManifestParser.parse("not json"))
        assertNull(UpdateManifestParser.parse(manifest(versionCode = 0)))
        assertNull(
            UpdateManifestParser.parse(
                """{"versionCode":60,"downloads":[{"name":"cn","url":"https://example.com/a.apk"}],"size":100,"sha256":"$sha"}"""
            )
        )
        assertNull(UpdateManifestParser.parse(manifest(size = 0)))
        assertNull(UpdateManifestParser.parse(manifest(sha256 = "abc")))
        assertNull(
            UpdateManifestParser.parse(
                manifest(sha256 = "G".repeat(64))
            )
        )
        assertNull(
            UpdateManifestParser.parse(
                manifest(downloads = """[{"name":"cn","url":"http://example.com/a.apk"}]""")
            )
        )
        assertNull(UpdateManifestParser.parse(manifest(downloads = "[]")))
    }

    @Test
    fun missing_optional_fields_do_not_crash() {
        val raw = """
            {
              "versionCode": 60,
              "versionName": "0.2.0-rc59",
              "downloads": [{"name":"cn","url":"https://example.com/a.apk"}],
              "size": 100,
              "sha256": "$sha"
            }
        """.trimIndent()
        val info = parse(raw)
        assertEquals("0.2.0-rc59", info.tag)
        assertEquals("", info.notes)
        assertNull(info.minSdk)
    }
}
