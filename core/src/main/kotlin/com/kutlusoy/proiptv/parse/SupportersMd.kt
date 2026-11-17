package com.kutlusoy.proiptv.parse

data class SupporterSection(val title: String, val lines: List<String>)

/** The small Markdown file of the people who helped: `## Heading` starts a section, every bullet or text line below it is one line of the section. */
object SupportersMd {
    fun parse(text: String): List<SupporterSection> {
        val sections = ArrayList<SupporterSection>()
        var title: String? = null
        val lines = ArrayList<String>()
        fun close() {
            if (title != null || lines.isNotEmpty()) sections += SupporterSection(title.orEmpty(), lines.toList())
            lines.clear()
        }
        for (raw in text.lines()) {
            val line = raw.trim()
            when {
                line.isEmpty() || (line.startsWith("<!--") && line.endsWith("-->")) -> Unit
                line.startsWith("#") -> { close(); title = line.trimStart('#').trim() }
                line.startsWith("* ") || line.startsWith("- ") -> lines += line.substring(2).trim()
                else -> lines += line
            }
        }
        close()
        return sections.filter { it.lines.isNotEmpty() }
    }
}
