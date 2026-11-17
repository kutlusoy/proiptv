package com.kutlusoy.proiptv.data

import com.kutlusoy.proiptv.parse.StableId

/**
 * After a successful update of an account: a favorite has to point to the channel it was made for. When the provider has removed the entry, or
 * has given its id to another channel (the name is not the same any more), the favorite follows the channel of the same name, or is removed.
 * Kinds that came back empty are not touched, a failed or partial update must not empty the lists.
 */
class FavoriteReconciler(private val db: AppDatabase) {
    suspend fun run(accountId: Long) {
        val favorites = db.favorites().ofAccount(accountId)
        for (kind in favorites.map { it.kind }.distinct()) {
            if (db.items().countOf(accountId, kind) == 0) continue
            val byId = db.items().ofKind(accountId, kind).associateBy { it.remoteId }
            val byName = byId.values.groupBy { StableId.normalize(it.name) }
            for (f in favorites.filter { it.kind == kind }) {
                val current = byId[f.remoteId]
                if (current != null && StableId.sameName(current.name, f.name)) continue
                val other = byName[StableId.normalize(f.name)]?.firstOrNull()
                db.favorites().remove(f.listId, accountId, kind, f.remoteId)
                if (other != null && f.listId !in db.favorites().listsOf(accountId, kind, other.remoteId)) {
                    db.favorites().add(Favorite(f.listId, accountId, kind, other.remoteId, other.name, f.sortKey))
                }
            }
        }
        if (db.items().countOf(accountId, Kind.LIVE) > 0) db.history().pruneMissing(accountId)
    }
}
