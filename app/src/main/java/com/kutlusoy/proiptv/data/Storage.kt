package com.kutlusoy.proiptv.data

import android.app.Application
import android.os.Environment
import android.os.storage.StorageManager
import com.kutlusoy.proiptv.Prefs
import java.io.File

/** A place where the app can write: the internal storage, a memory card or a USB drive. [dir] is the folder of the app on it (no permission needed). */
data class StorageVolume(val dir: File, val label: String, val primary: Boolean, val freeBytes: Long)

/**
 * Where downloads, recordings and backups are kept. The standard places stay as they were until the user chooses another volume (an external drive) in the
 * settings; when that drive is not connected, the standard places are used.
 */
class Storage(private val app: Application, private val prefs: Prefs) {
    /** The volumes that are connected now. */
    fun volumes(): List<StorageVolume> {
        val manager = app.getSystemService(StorageManager::class.java)
        return app.getExternalFilesDirs(null).filterNotNull().filter { Environment.getExternalStorageState(it) == Environment.MEDIA_MOUNTED }.map { dir ->
            val volume = runCatching { manager?.getStorageVolume(dir) }.getOrNull()
            val primary = volume?.isPrimary ?: !Environment.isExternalStorageRemovable(dir)
            StorageVolume(dir, volume?.getDescription(app) ?: dir.path, primary, dir.usableSpace)
        }
    }

    /** The folder that was chosen, null at the standard place or when the drive is not connected. */
    fun chosenRoot(): File? = prefs.storageRoot?.let { File(it) }?.takeIf { it.isDirectory || runCatching { it.mkdirs() }.getOrDefault(false) }?.takeIf { dir -> volumes().any { dir.path.startsWith(it.dir.path) } }

    /** True when a place was chosen but is not there at the moment. */
    fun chosenMissing(): Boolean = prefs.storageRoot != null && chosenRoot() == null

    private fun defaultRoot(): File = app.getExternalFilesDir(null) ?: app.filesDir

    fun downloads(): File = File(chosenRoot() ?: defaultRoot(), "downloads")

    fun recordings(): File = chosenRoot()?.let { File(it, "recordings") } ?: app.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: File(app.filesDir, "recordings")

    fun backups(): File = File(chosenRoot() ?: defaultRoot(), "backups")

    /** The backup files in the folders of all connected volumes, the newest first (for devices that have no app to choose a file). */
    fun backupFiles(): List<File> =
        (volumes().map { File(it.dir, "backups") } + backups()).distinct()
            .flatMap { it.listFiles { f -> f.isFile && f.name.endsWith(".json") }?.toList().orEmpty() }
            .sortedByDescending { it.lastModified() }
}
