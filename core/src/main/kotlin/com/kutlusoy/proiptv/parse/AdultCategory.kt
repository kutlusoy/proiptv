package com.kutlusoy.proiptv.parse

/** Providers do not always mark adult titles. A category whose name starts with XXX ("XXX", "xxx | Movies", "[XXX] Live") counts as adult content too. */
object AdultCategory {
    fun matches(name: String?): Boolean = name != null && name.trimStart { !it.isLetterOrDigit() }.startsWith("XXX", ignoreCase = true)
}
