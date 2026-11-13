package com.kutlusoy.proiptv.sync

object GuidePolicy {
    const val MAX_AGE_SECS = 6 * 3600L
    const val MIN_RETRY_SECS = 3600L
    const val MIN_COVERAGE_SECS = 12 * 3600L

    fun isDue(now: Long, lastSync: Long, maxEnd: Long?): Boolean {
        if (lastSync <= 0) return true
        val age = now - lastSync
        if (age >= MAX_AGE_SECS) return true
        val coverage = (maxEnd ?: 0L) - now
        return coverage < MIN_COVERAGE_SECS && age >= MIN_RETRY_SECS
    }
}
