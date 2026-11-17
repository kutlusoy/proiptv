package com.kutlusoy.proiptv.parse

import java.security.MessageDigest

/** Ids of playlist entries and the comparison of names that decides whether a favorite still points to the channel it was made for. */
object StableId {
    /** The id of an M3U entry: the same address always gives the same id, whatever the position in the list is. */
    fun of(text: String): String =
        MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }.take(16)

    fun normalize(name: String): String = name.decodeEntities().trim().replace(Regex("\\s+"), " ").lowercase()

    fun sameName(a: String, b: String): Boolean = normalize(a) == normalize(b)
}
