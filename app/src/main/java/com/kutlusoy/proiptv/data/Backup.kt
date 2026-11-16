package com.kutlusoy.proiptv.data

import kotlinx.coroutines.flow.first

data class RestoreResult(val accounts: Int, val favorites: Int)

class BackupManager(
    private val repo: Repository,
    private val getSettings: () -> Pair<Boolean, Int>,
    private val setSettings: (Boolean, Int) -> Unit,
    private val decryptPassword: (Account) -> String,
) {
    suspend fun export(password: String?): String {
        val db = repo.db
        val accounts = db.accounts().list()
        val keys = accounts.associate { it.id to BackupCodec.accountKey(it.type, it.url, it.username) }
        val includePasswords = !password.isNullOrEmpty()
        val lists = db.favorites().lists().first().map { list ->
            BackupList(
                list.name,
                db.favorites().entries(list.id).mapNotNull { f ->
                    keys[f.accountId]?.let { BackupFavorite(it, f.kind, f.remoteId, f.name) }
                },
            )
        }
        val (timeshift, bufferMb) = getSettings()
        val data = BackupData(
            accounts = accounts.map {
                BackupAccount(
                    it.name, it.type, it.url, it.username, if (includePasswords) decryptPassword(it) else null,
                    it.epgUrl, it.userAgent, it.liveFormat, it.enabled, it.useVod, it.useSeries,
                )
            },
            lists = lists, timeshift = timeshift, bufferMb = bufferMb,
        )
        return BackupCodec.encode(data, password)
    }

    suspend fun restore(text: String, password: String?): RestoreResult {
        val data = BackupCodec.decode(text, password)
        val db = repo.db
        val existing = db.accounts().list().associateBy { BackupCodec.accountKey(it.type, it.url, it.username) }.toMutableMap()
        val ids = existing.mapValues { it.value.id }.toMutableMap()
        var added = 0
        for (a in data.accounts) {
            val key = BackupCodec.accountKey(a.type, a.url, a.username)
            if (key in ids) continue
            val id = repo.saveAccount(
                Account(
                    name = a.name, type = a.type, url = a.url, username = a.username, epgUrl = a.epgUrl, userAgent = a.userAgent,
                    liveFormat = a.liveFormat, enabled = a.enabled, useVod = a.useVod, useSeries = a.useSeries,
                ),
                a.password.orEmpty(),
            )
            ids[key] = id
            added++
        }
        var favorites = 0
        val lists = db.favorites().lists().first()
        val byArea = lists.toMutableList()
        for (l in data.lists) {
            for (e in l.entries) {
                val accountId = ids[e.account] ?: continue
                val area = if (e.kind in 0..2) e.kind else Kind.LIVE
                val listId = byArea.firstOrNull { it.name == l.name && it.kind == area }?.id
                    ?: repo.createList(l.name, area).also { id -> byArea += FavoriteList(id = id, name = l.name, kind = area) }
                val known = db.favorites().entries(listId).map { Triple(it.accountId, it.kind, it.remoteId) }.toSet()
                if (Triple(accountId, e.kind, e.remoteId) in known) continue
                repo.setFavorite(listId, accountId, e.kind, e.remoteId, e.name, true)
                favorites++
            }
        }
        setSettings(data.timeshift, data.bufferMb)
        return RestoreResult(added, favorites)
    }
}
