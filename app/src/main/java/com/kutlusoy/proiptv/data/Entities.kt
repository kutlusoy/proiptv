package com.kutlusoy.proiptv.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

object Kind {
    const val LIVE = 0
    const val MOVIE = 1
    const val SERIES = 2
    const val EPISODE = 3
}

object AccountType {
    const val XTREAM = "xtream"
    const val M3U = "m3u"
}

@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String,
    val url: String,
    val username: String = "",
    val password: String = "",
    val epgUrl: String? = null,
    val userAgent: String? = null,
    val liveFormat: String = "auto",
    val enabled: Boolean = true,
    val useVod: Boolean = true,
    val useSeries: Boolean = true,
    val ord: Int = 0,
    val status: String? = null,
    val expiry: Long? = null,
    val maxConnections: Int? = null,
    val activeConnections: Int? = null,
    val trial: Boolean = false,
    val outputFormats: String? = null,
    val lastSync: Long = 0,
    val lastError: String? = null,
    val syncGen: Int = 0,
    val epgGen: Int = 0,
    val epgSync: Long = 0,
    val epgEtag: String? = null,
    val epgModified: String? = null,
    val autoEpgUrl: String? = null,
)

@Entity(tableName = "categories", indices = [Index("accountId", "kind")])
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val kind: Int,
    val remoteId: String,
    val name: String,
    val ord: Int,
)

@Entity(
    tableName = "items",
    indices = [
        Index(value = ["accountId", "kind", "remoteId"], unique = true),
        Index(value = ["accountId", "kind", "categoryRemoteId"]),
        Index(value = ["kind", "accountId", "ord"]),
    ],
)
data class Item(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val kind: Int,
    val remoteId: String,
    val name: String,
    val categoryRemoteId: String = "",
    val logo: String? = null,
    val epgId: String? = null,
    val url: String? = null,
    val ext: String? = null,
    val archiveDays: Int = 0,
    val catchupSource: String? = null,
    val rating: String? = null,
    val plot: String? = null,
    val ord: Int = 0,
    val gen: Int = 0,
    val country: String = "",
    val year: Int = 0,
)

@Entity(tableName = "programs", indices = [Index("accountId", "epgId", "start")])
data class Program(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val epgId: String,
    val start: Long,
    val end: Long,
    val title: String,
    val description: String?,
    val gen: Int,
)

@Entity(tableName = "favorite_lists")
data class FavoriteList(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val ord: Int = 0,
    val kind: Int = 0,
)

@Entity(
    tableName = "favorites",
    primaryKeys = ["listId", "accountId", "kind", "remoteId"],
    indices = [Index("listId", "sortKey")],
)
data class Favorite(
    val listId: Long,
    val accountId: Long,
    val kind: Int,
    val remoteId: String,
    val name: String,
    val sortKey: Double,
)

@Entity(tableName = "progress", primaryKeys = ["accountId", "kind", "remoteId"])
data class Progress(
    val accountId: Long,
    val kind: Int,
    val remoteId: String,
    val positionMs: Long,
    val durationMs: Long,
    val updated: Long,
    val watched: Boolean = false,
)

data class FavoriteRow(
    val listId: Long,
    val accountId: Long,
    val kind: Int,
    val remoteId: String,
    val name: String,
    val sortKey: Double,
    val logo: String?,
    val epgId: String?,
    val itemId: Long?,
)

data class FavoriteKey(val accountId: Long, val kind: Int, val remoteId: String)

object RecStatus {
    const val RECORDING = 0
    const val DONE = 1
    const val INTERRUPTED = 2
    const val SCHEDULED = 3
    const val MISSED = 4
}

@Entity(tableName = "recordings")
data class Recording(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val title: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val plannedEnd: Long? = null,
    val path: String,
    val bytes: Long = 0,
    val status: Int = RecStatus.RECORDING,
    val plannedStart: Long? = null,
    val channelRemoteId: String = "",
    val note: String? = null,
)

object DlStatus {
    const val QUEUED = 0
    const val RUNNING = 1
    const val DONE = 2
    const val FAILED = 3
}

@Entity(tableName = "downloads", indices = [Index(value = ["accountId", "kind", "remoteId"], unique = true)])
data class Download(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val kind: Int,
    val remoteId: String,
    val title: String,
    val logo: String? = null,
    val ext: String = "mp4",
    val seriesRemoteId: String = "",
    val seriesName: String = "",
    val season: Int = 0,
    val episode: Int = 0,
    val bytes: Long = 0,
    val total: Long = 0,
    val status: Int = DlStatus.QUEUED,
    val error: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

data class GuideRow(
    val title: String,
    val start: Long,
    val end: Long,
    val itemId: Long,
    val channel: String,
    val logo: String?,
    val accountId: Long,
    val ord: Int,
    val category: String,
)
