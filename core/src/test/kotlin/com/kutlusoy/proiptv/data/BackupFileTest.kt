package com.kutlusoy.proiptv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BackupFileTest {
    private val data = BackupData(
        accounts = listOf(BackupAccount("Home", "m3u", "http://h/list.m3u", "bob", "s3cret")),
        lists = listOf(BackupList("Favorites", listOf(BackupFavorite(BackupCodec.accountKey("m3u", "http://h/list.m3u", "bob"), 0, "7", "ORF 1")))),
        timeshift = false, bufferMb = 256,
    )

    @Test
    fun withoutPasswordNoAccountPasswordIsWritten() {
        val text = BackupCodec.encode(data, null)
        assertTrue(!text.contains("s3cret"))
        val back = BackupCodec.decode(text, null)
        assertNull(back.accounts[0].password)
        assertEquals("ORF 1", back.lists[0].entries[0].name)
        assertEquals(256, back.bufferMb)
    }

    @Test
    fun withPasswordItIsEncryptedAndComesBack() {
        val text = BackupCodec.encode(data, "pw")
        assertTrue(BackupCodec.isEncrypted(text))
        assertTrue(!text.contains("s3cret"))
        assertEquals("s3cret", BackupCodec.decode(text, "pw").accounts[0].password)
        try {
            BackupCodec.decode(text, "other")
            fail("expected a wrong password")
        } catch (e: WrongPassword) {
        }
    }

    @Test
    fun switchedOffCategoriesAreKeptAndOldBackupsStillLoad() {
        val account = BackupAccount("Tengri", "xtream", "http://t", "u", null, hiddenLive = listOf(BackupHidden("12", "FR SPORT"), BackupHidden("Movies")))
        val back = BackupCodec.decode(BackupCodec.encode(data.copy(accounts = listOf(account)), null), null)
        assertEquals(listOf(BackupHidden("12", "FR SPORT"), BackupHidden("Movies", "Movies")), back.accounts[0].hiddenLive)
        val old = """{"format":1,"app":"proiptv","created":1,"encrypted":false,"data":{"accounts":[{"name":"A","type":"m3u","url":"u"}],"lists":[]}}"""
        assertTrue(BackupCodec.decode(old, null).accounts[0].hiddenLive.isEmpty())
    }
}
