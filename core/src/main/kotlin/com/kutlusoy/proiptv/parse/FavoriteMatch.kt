package com.kutlusoy.proiptv.parse

/** A channel of an account as the provider lists it now. */
data class MatchCandidate(val remoteId: String, val name: String, val epgId: String?)

/**
 * Decides which channel of an account a favorite belongs to after the list was updated. The candidates are the channels of that one account and
 * kind, so a favorite never moves to another account. The id alone is not trusted, because providers give ids to other channels.
 */
class FavoriteMatch(candidates: List<MatchCandidate>) {
    private val byId = candidates.associateBy { it.remoteId }
    private val byName = HashMap<String, MatchCandidate>().also { m -> candidates.forEach { m.putIfAbsent(StableId.normalize(it.name), it) } }
    private val byEpg = candidates.filter { !it.epgId.isNullOrBlank() }.groupBy { it.epgId!!.lowercase() }

    /**
     * The channel the favorite points to now, or null when there is none (the favorite is then removed). In this order: the same id with the same
     * name, or with the same tvg-id (the channel was renamed); a channel with the same name; a channel with the same tvg-id.
     */
    fun resolve(id: String, name: String, epgId: String?): MatchCandidate? {
        val epg = epgId?.takeIf { it.isNotBlank() }?.lowercase()
        val current = byId[id]
        if (current != null) {
            if (StableId.sameName(current.name, name)) return current
            if (epg != null && current.epgId?.lowercase() == epg) return current
        }
        byName[StableId.normalize(name)]?.let { return it }
        if (epg != null) byEpg[epg]?.let { same -> return same.firstOrNull { StableId.sameName(it.name, name) } ?: same.first() }
        return null
    }
}
