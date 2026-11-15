package com.kutlusoy.proiptv.engine

/** One recording of an account: which channel and from when to when (milliseconds). */
data class RecordingSlot(val channel: String, val start: Long, val end: Long)

object RecordingSlots {
    /**
     * Whether a new recording fits next to the others when the account allows [max] connections at the same time.
     * Recordings of the same channel share one connection. A recording that is planned at the moment of the new one counts, too.
     */
    fun fits(max: Int, new: RecordingSlot, others: List<RecordingSlot>): Boolean {
        val relevant = others.filter { it.start < new.end && it.end > new.start }
        val moments = (relevant.map { it.start } + new.start).filter { it >= new.start && it < new.end }.distinct()
        for (t in moments) {
            val channels = HashSet<String>()
            channels += new.channel
            for (o in relevant) if (o.start <= t && o.end > t) channels += o.channel
            if (channels.size > max) return false
        }
        return true
    }
}
