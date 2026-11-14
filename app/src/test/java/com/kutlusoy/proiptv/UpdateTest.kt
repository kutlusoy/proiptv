package com.kutlusoy.proiptv

import com.kutlusoy.proiptv.update.Checksums
import com.kutlusoy.proiptv.update.ReleaseParser
import com.kutlusoy.proiptv.update.UpdateDownloader
import com.kutlusoy.proiptv.update.UpdateException
import com.kutlusoy.proiptv.update.UpdateNotifyPolicy
import com.kutlusoy.proiptv.update.Versions
import java.io.File
import java.security.MessageDigest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class UpdateTest {
    private val latest = """{"tag_name":"v0.2.1","draft":false,"prerelease":false,"assets":[
        {"name":"ProIPTV-0.2.1.apk","browser_download_url":"https://example.org/a.apk","size":1234},
        {"name":"ProIPTV-0.2.1.aab","browser_download_url":"https://example.org/a.aab","size":99},
        {"name":"SHA256SUMS.txt","browser_download_url":"https://example.org/sums","size":10}]}"""

    @Test
    fun readsTheLatestRelease() {
        val r = ReleaseParser.parseLatest(latest)!!
        assertEquals("0.2.1", r.version)
        assertEquals(2_001L, r.versionCode)
        assertEquals("ProIPTV-0.2.1.apk", r.apkName)
        assertEquals(1234L, r.apkSize)
        assertEquals("https://example.org/sums", r.checksumsUrl)
    }

    @Test
    fun ignoresPreReleasesDraftsAndGarbage() {
        assertNull(ReleaseParser.parseLatest(latest.replace("\"prerelease\":false", "\"prerelease\":true")))
        assertNull(ReleaseParser.parseLatest(latest.replace("\"draft\":false", "\"draft\":true")))
        assertNull(ReleaseParser.parseLatest("not json"))
        assertNull(ReleaseParser.parseLatest("""{"tag_name":"nightly"}"""))
    }

    @Test
    fun picksTheNewestTestRelease() {
        val list = """[
            {"tag_name":"test-9","draft":false,"prerelease":true,"assets":[{"name":"ProIPTV-0.0.1-test9-abc.apk","browser_download_url":"https://e/9.apk","size":1}]},
            {"tag_name":"test-12","draft":false,"prerelease":true,"assets":[{"name":"ProIPTV-0.0.1-test12-def.apk","browser_download_url":"https://e/12.apk","size":1},{"name":"SHA256SUMS.txt","browser_download_url":"https://e/s","size":1}]},
            {"tag_name":"v0.0.1","draft":false,"prerelease":false,"assets":[]},
            {"tag_name":"test-30","draft":true,"prerelease":true,"assets":[]}]"""
        val r = ReleaseParser.parseNewestTest(list)!!
        assertEquals(12, r.build)
        assertEquals("test 12", r.version)
        assertEquals("https://e/12.apk", r.apkUrl)
        assertNull(ReleaseParser.parseNewestTest("[]"))
    }

    @Test
    fun versionCodesFollowTheScheme() {
        assertEquals(1L, Versions.code("0.0.1"))
        assertEquals(1_002_003L, Versions.code("1.2.3"))
        assertEquals(1_000_000L, Versions.code("1.0.0-rc1"))
        assertNull(Versions.code("1.0"))
        assertNull(Versions.code("v1.0.0"))
    }

    @Test
    fun findsTheChecksumOfTheFile() {
        val hash = "a".repeat(64)
        val text = "$hash  ./ProIPTV-0.2.1.apk\n${"b".repeat(64)}  ./ProIPTV-0.2.1.aab\n"
        assertEquals(hash, Checksums.find(text, "ProIPTV-0.2.1.apk"))
        assertNull(Checksums.find(text, "other.apk"))
        assertNull(Checksums.find("short  ./x.apk", "x.apk"))
    }

    @Test
    fun oneNotificationPerDayAtMost() {
        assertTrue(UpdateNotifyPolicy.shouldNotify(-1, 20_000))
        assertFalse(UpdateNotifyPolicy.shouldNotify(20_000, 20_000))
        assertTrue(UpdateNotifyPolicy.shouldNotify(20_000, 20_001))
    }

    @Test
    fun theDownloaderRefusesInsecureAddressesAndWrongChecksums() {
        val server = MockWebServer()
        val payload = ByteArray(200_000) { (it % 200).toByte() }
        server.enqueue(MockResponse().setBody(Buffer().write(payload)))
        server.enqueue(MockResponse().setBody(Buffer().write(payload)))
        server.start()
        try {
            val dir = File.createTempFile("upd", "").also { it.delete(); it.mkdirs() }
            val target = File(dir, "x.apk")
            val sha = MessageDigest.getInstance("SHA-256").digest(payload).joinToString("") { "%02x".format(it) }
            val downloader = UpdateDownloader(OkHttpClient(), requireHttps = false)
            downloader.download(server.url("/x").toString(), target, sha, 10_000_000)
            assertTrue(target.readBytes().contentEquals(payload))
            target.delete()
            try {
                downloader.download(server.url("/x").toString(), target, "0".repeat(64), 10_000_000)
                fail("wrong checksum must be refused")
            } catch (e: UpdateException) {
                assertEquals(UpdateException.Code.CHECKSUM_MISMATCH, e.code)
            }
            assertFalse(target.exists())
            assertFalse(File(dir, "x.apk.part").exists())
            try {
                UpdateDownloader(OkHttpClient()).fetchText("http://example.org/x")
                fail("plain http must be refused")
            } catch (e: UpdateException) {
                assertEquals(UpdateException.Code.INSECURE_URL, e.code)
            }
            assertNotNull(downloader)
        } finally {
            server.shutdown()
        }
    }
}
