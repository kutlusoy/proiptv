package com.kutlusoy.proiptv.desktop

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class MissingFilesTest {
    private fun tempDir() = File.createTempFile("proiptv", "").also { it.delete(); it.mkdirs() }

    private fun rec(path: String, status: Int = RecStatus.DONE) =
        RecordingDto(0, "a", "Channel", 1000, 2000, null, path, 10, status, "5")

    @Test
    fun aRecordingWhoseFileWasDeletedDisappears() {
        val dir = tempDir()
        val store = RecordingStore(tempDir())
        val kept = File(dir, "kept.ts").also { it.writeText("x") }
        store.add(rec(kept.absolutePath))
        store.add(rec(File(dir, "gone.ts").absolutePath))
        store.removeMissing()
        assertEquals(listOf(kept.absolutePath), store.all().map { it.path })
    }

    @Test
    fun nothingIsRemovedWhileTheFolderIsNotThere() {
        val store = RecordingStore(tempDir())
        store.add(rec(File(tempDir().also { it.deleteRecursively() }, "x.ts").absolutePath))
        store.removeMissing()
        assertEquals(1, store.all().size)
    }

    @Test
    fun plannedAndRunningRecordingsAreNotTouched() {
        val dir = tempDir()
        val store = RecordingStore(tempDir())
        store.add(rec("", RecStatus.SCHEDULED))
        store.add(rec(File(dir, "running.ts").absolutePath, RecStatus.RECORDING))
        store.removeMissing()
        assertEquals(2, store.all().size)
    }
}
