package com.kutlusoy.proiptv.data

import com.kutlusoy.proiptv.parse.FavoriteMatch
import com.kutlusoy.proiptv.parse.MatchCandidate

/** What a favorite pointed to before an update: the name and the tvg-id of the channel. */
data class FavoriteSnapshot(val name: String, val epgId: String?)

/**
 * After a successful update of an account: a favorite has to point to the channel it was made for. Only the channels of the same account are
 * considered. When the provider has removed the entry, or has given its id to another channel, the favorite follows the channel with the same name
 * or tvg-id (see [FavoriteMatch]), or is removed. Kinds that came back empty are not touched, a failed or partial update must not empty the lists.
 */
class FavoriteReconciler(private val db: AppDatabase) {
    /** Call before the update: the name and tvg-id the favorites of the account have now. */
    suspend fun snapshot(accountId: Long): Map<String, FavoriteSnapshot> =
        db.favorites().ofAccount(accountId).mapNotNull { f ->
            db.items().find(accountId, f.kind, f.remoteId)?.let { "${f.kind}|${f.remoteId}" to FavoriteSnapshot(it.name, it.epgId) }
        }.toMap()

    suspend fun run(accountId: Long, before: Map<String, FavoriteSnapshot> = emptyMap()) {
        val favorites = db.favorites().ofAccount(accountId)
        // channels of switched off categories are not in the lists, their favorites stay for the day the category is switched on again
        val hasHidden = db.items().hiddenIds(accountId, Kind.LIVE).isNotEmpty()
        for (kind in favorites.map { it.kind }.distinct()) {
            if (db.items().countOf(accountId, kind) == 0) continue
            val match = FavoriteMatch(db.items().ofKind(accountId, kind).map { MatchCandidate(it.remoteId, it.name, it.epgId) })
            for (f in favorites.filter { it.kind == kind }) {
                val old = before["$kind|${f.remoteId}"]
                val found = match.resolve(f.remoteId, f.name, old?.epgId)
                if (found == null && kind == Kind.LIVE && hasHidden) continue
                if (found?.remoteId == f.remoteId) {
                    // the channel was renamed: the favorite takes the new name
                    if (found.name != f.name) db.favorites().add(f.copy(name = found.name))
                    continue
                }
                db.favorites().remove(f.listId, accountId, kind, f.remoteId)
                if (found != null && f.listId !in db.favorites().listsOf(accountId, kind, found.remoteId)) {
                    db.favorites().add(Favorite(f.listId, accountId, kind, found.remoteId, found.name, f.sortKey))
                }
            }
        }
        if (db.items().countOf(accountId, Kind.LIVE) > 0) db.history().pruneMissing(accountId)
    }
}
