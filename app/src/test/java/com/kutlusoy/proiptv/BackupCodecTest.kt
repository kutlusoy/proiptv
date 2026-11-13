package com.kutlusoy.proiptv

import com.kutlusoy.proiptv.data.BackupAccount
import com.kutlusoy.proiptv.data.BackupCodec
import com.kutlusoy.proiptv.data.BackupData
import com.kutlusoy.proiptv.data.BackupFavorite
import com.kutlusoy.proiptv.data.BackupList
import com.kutlusoy.proiptv.data.InvalidBackup
import com.kutlusoy.proiptv.data.WrongPassword
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {
    private val data = BackupData(
        accounts = listOf(BackupAccount("Home", "xtream", "http://h:80", "bob", "s3cret", epgUrl = "http://g/x.xml", liveFormat = "ts", useSeries = false)),
        lists = listOf(BackupList("Sport", listOf(BackupFavorite("xtream|http://h:80|bob", 0, "10", "One"), BackupFavorite("xtream|http://h:80|bob", 0, "12", "Three")))),
        timeshift = false,
        bufferMb = 512,
    )

    @Test
    fun plainBackupHasNoPasswords() {
        val text = BackupCodec.encode(data, null)
        assertFalse(text.contains("s3cret"))
        assertFalse(BackupCodec.isEncrypted(text))
        val back = BackupCodec.decode(text, null)
        assertNull(back.accounts[0].password)
        assertEquals("bob", back.accounts[0].username)
        assertEquals("ts", back.accounts[0].liveFormat)
        assertEquals(listOf("10", "12"), back.lists[0].entries.map { it.remoteId })
        assertEquals(512, back.bufferMb)
        assertFalse(back.timeshift)
    }

    @Test
    fun protectedBackupKeepsPasswordsEncrypted() {
        val text = BackupCodec.encode(data, "open sesame")
        assertFalse(text.contains("s3cret"))
        assertFalse(text.contains("bob"))
        assertTrue(BackupCodec.isEncrypted(text))
        val back = BackupCodec.decode(text, "open sesame")
        assertEquals("s3cret", back.accounts[0].password)
        assertEquals(data.lists, back.lists)
    }

    @Test
    fun wrongOrMissingPasswordIsRejected() {
        val text = BackupCodec.encode(data, "right")
        for (pw in listOf("wrong", "", null)) {
            try {
                BackupCodec.decode(text, pw)
                throw AssertionError("accepted $pw")
            } catch (e: WrongPassword) {
            }
        }
    }

    @Test
    fun tamperedOrForeignFilesAreInvalid() {
        val text = BackupCodec.encode(data, "right")
        val tampered = text.replace(Regex("\"cipher\": \"([0-9a-f])"), "\"cipher\": \"0")
        try {
            BackupCodec.decode(tampered, "right")
            throw AssertionError("tampered file accepted")
        } catch (e: WrongPassword) {
        }
        for (bad in listOf("", "not json", "{}", """{"format":1,"app":"other","created":1,"encrypted":false}""")) {
            try {
                BackupCodec.decode(bad, null)
                throw AssertionError("accepted $bad")
            } catch (e: InvalidBackup) {
            }
        }
    }
}
