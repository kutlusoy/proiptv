package com.kutlusoy.proiptv.data

object SortKeys {
    const val STEP = 1000.0
    private const val MIN_GAP = 1e-6

    fun between(prev: Double?, next: Double?): Double? = when {
        prev == null && next == null -> STEP
        prev == null -> next!! - STEP
        next == null -> prev + STEP
        next - prev < MIN_GAP -> null
        else -> (prev + next) / 2
    }

    fun evenly(count: Int): List<Double> = List(count) { (it + 1) * STEP }
}
