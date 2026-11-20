package com.kutlusoy.proiptv.data

import android.app.Application
import android.os.Environment
import android.os.storage.StorageManager
import com.kutlusoy.proiptv.Prefs
import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
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

    /**
     * The folder for backups that stays when the app is uninstalled: ProIPTV/Sicherungen on the drive (not inside the app folder). Android lets an app write there
     * only on older versions (with the storage permission), so every use has to be ready for a failure.
     */
    private fun openFolder(volume: StorageVolume): File = File(volume.dir.path.substringBefore("/Android/"), "ProIPTV/Sicherungen")

    private fun preferredVolumes(): List<StorageVolume> {
        val all = volumes()
        val chosen = chosenRoot()?.let { dir -> all.firstOrNull { dir.path.startsWith(it.dir.path) } }
        return listOfNotNull(chosen) + (all.filter { !it.primary && it != chosen }) + all.filter { it.primary && it != chosen }
    }

    /** Writes a backup into the open folder of the first drive that allows it. Returns the file or null when no drive allows it. */
    fun writeOpenBackup(name: String, text: String): File? {
        for (volume in preferredVolumes()) {
            val file = runCatching {
                val dir = openFolder(volume).also { it.mkdirs() }
                File(dir, name).also { it.writeText(text, Charsets.UTF_8) }
            }.getOrNull()
            if (file != null && file.isFile) return file
        }
        return null
    }

    /** The newest backup file that can be found: in the open folders of the drives, else in the folders of the app. */
    fun newestBackup(): File? {
        val open = volumes().flatMap { v -> runCatching { openFolder(v).listFiles { f -> f.isFile && f.name.endsWith(".json") }?.toList().orEmpty() }.getOrDefault(emptyList()) }
        return (open + backupFiles()).maxByOrNull { it.lastModified() }
    }

    /** Where the open backups of the preferred drive are, for a message. */
    fun openFolderPath(): String? = preferredVolumes().firstOrNull()?.let { openFolder(it).path }

    /** The backup files in the folders of all connected volumes, the newest first (for devices that have no app to choose a file). */
    fun backupFiles(): List<File> =
        (volumes().map { File(it.dir, "backups") } + backups()).distinct()
            .flatMap { it.listFiles { f -> f.isFile && f.name.endsWith(".json") }?.toList().orEmpty() }
            .sortedByDescending { it.lastModified() }

    /** The folder the user chose with the file manager, null when none was chosen or it is not there (any more). */
    fun treeRoot(): DocumentFile? =
        prefs.storageTree?.let { runCatching { DocumentFile.fromTreeUri(app, Uri.parse(it)) }.getOrNull() }?.takeIf { it.exists() && it.canWrite() }

    /** True when a folder was chosen but cannot be used at the moment (a card that is not there, a permission that was taken away). */
    fun treeMissing(): Boolean = prefs.storageTree != null && treeRoot() == null

    /** A readable name of the chosen folder for the settings. */
    fun treeLabel(): String? = prefs.storageTree?.let { raw ->
        val doc = runCatching { DocumentFile.fromTreeUri(app, Uri.parse(raw)) }.getOrNull()
        (doc?.name ?: Uri.parse(raw).lastPathSegment)?.substringAfterLast(':')?.ifBlank { null }
    }

    private fun mimeOf(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "mp4", "m4v" -> "video/mp4"
        "mkv" -> "video/x-matroska"
        "ts" -> "video/mp2t"
        "avi" -> "video/x-msvideo"
        "webm" -> "video/webm"
        "json" -> "application/json"
        else -> "application/octet-stream"
    }

    /** Finds or creates the folders of [relative] (without the last part, which is the file name) inside the chosen folder and returns the folder. */
    private fun folderFor(relative: String): Pair<DocumentFile, String>? {
        var dir = treeRoot() ?: return null
        val parts = relative.split('/').filter { it.isNotEmpty() }
        if (parts.isEmpty()) return null
        for (name in parts.dropLast(1)) {
            dir = dir.findFile(name)?.takeIf { it.isDirectory } ?: dir.createDirectory(name) ?: return null
        }
        return dir to parts.last()
    }

    private fun freeName(dir: DocumentFile, name: String): String {
        if (dir.findFile(name) == null) return name
        val base = name.substringBeforeLast('.')
        val ext = name.substringAfterLast('.', "")
        var i = 2
        while (dir.findFile(if (ext.isEmpty()) "$base ($i)" else "$base ($i).$ext") != null) i++
        return if (ext.isEmpty()) "$base ($i)" else "$base ($i).$ext"
    }

    /** Moves a finished file into the chosen folder (the path [relative] inside it) and returns its content address; null when that did not work (the file stays where it is). */
    fun moveToTree(source: File, relative: String): String? {
        if (!source.isFile) return null
        val (dir, name) = folderFor(relative) ?: return null
        val target = runCatching { dir.createFile(mimeOf(name), freeName(dir, name)) }.getOrNull() ?: return null
        return try {
            app.contentResolver.openOutputStream(target.uri, "w")!!.use { out -> source.inputStream().use { it.copyTo(out, 256 * 1024) } }
            if (target.length() != source.length()) {
                target.delete()
                null
            } else {
                source.delete()
                target.uri.toString()
            }
        } catch (e: Exception) {
            runCatching { target.delete() }
            null
        }
    }

    /** Writes a text file (a backup) into the chosen folder; returns a readable place or null. */
    fun writeTextToTree(relative: String, text: String): String? {
        val (dir, name) = folderFor(relative) ?: return null
        val target = runCatching { dir.createFile(mimeOf(name), freeName(dir, name)) }.getOrNull() ?: return null
        return try {
            app.contentResolver.openOutputStream(target.uri, "w")!!.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            (treeLabel() ?: "") + "/" + relative
        } catch (e: Exception) {
            runCatching { target.delete() }
            null
        }
    }
}

/** A finished file, in the folder of the app (a path) or in the folder the user chose (a content address). */
object StoredFile {
    fun isContent(path: String?): Boolean = path?.startsWith("content://") == true

    fun uri(path: String): Uri = if (isContent(path)) Uri.parse(path) else Uri.fromFile(File(path))

    fun exists(context: Context, path: String): Boolean =
        if (isContent(path)) runCatching { DocumentFile.fromSingleUri(context, Uri.parse(path))?.exists() == true }.getOrDefault(false) else File(path).exists()

    fun delete(context: Context, path: String) {
        if (isContent(path)) runCatching { DocumentFile.fromSingleUri(context, Uri.parse(path))?.delete() } else runCatching { File(path).delete() }
    }
}
