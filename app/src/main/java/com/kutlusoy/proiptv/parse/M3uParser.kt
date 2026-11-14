package com.kutlusoy.proiptv.parse

import java.io.BufferedReader

data class M3uEntry(
    val name: String,
    val url: String,
    val group: String,
    val tvgId: String?,
    val logo: String?,
    val number: Int?,
    val catchup: String?,
    val catchupSource: String?,
    val catchupDays: Int,
)

data class M3uHeader(val epgUrl: String?)

object M3uParser {
    private val BOM = Char(0xFEFF).toString()
    private val attr = Regex("""([\w-]+)="([^"]*)"""")

    fun parse(reader: BufferedReader, onHeader: (M3uHeader) -> Unit = {}): Sequence<M3uEntry> = sequence {
        var pending: M3uEntry? = null
        var first = true
        while (true) {
            val raw = reader.readLine() ?: break
            val line = raw.trim().removePrefix(BOM)
            if (line.isEmpty()) continue
            if (first) {
                first = false
                if (line.startsWith("#EXTM3U")) {
                    val attrs = attributes(line)
                    onHeader(M3uHeader(attrs["url-tvg"] ?: attrs["x-tvg-url"]))
                    continue
                }
            }
            when {
                line.startsWith("#EXTINF") -> pending = extinf(line)
                line.startsWith("#") -> {}
                else -> {
                    val entry = pending
                    if (entry != null) yield(entry.copy(url = line))
                    pending = null
                }
            }
        }
    }

    private fun attributes(line: String): Map<String, String> =
        attr.findAll(line).associate { it.groupValues[1].lowercase() to it.groupValues[2] }

    private fun extinf(line: String): M3uEntry {
        val attrs = attributes(line)
        var inQuote = false
        var comma = -1
        for (i in line.indices) {
            val c = line[i]
            if (c == '"') inQuote = !inQuote
            else if (c == ',' && !inQuote) {
                comma = i
                break
            }
        }
        val title = if (comma >= 0) line.substring(comma + 1).trim() else ""
        val name = title.ifEmpty { attrs["tvg-name"] ?: "" }
        return M3uEntry(
            name = name.decodeEntities(),
            url = "",
            group = attrs["group-title"]?.trim().orEmpty().decodeEntities(),
            tvgId = attrs["tvg-id"]?.takeIf { it.isNotBlank() },
            logo = attrs["tvg-logo"]?.takeIf { it.isNotBlank() },
            number = attrs["tvg-chno"]?.toIntOrNull(),
            catchup = attrs["catchup"]?.takeIf { it.isNotBlank() },
            catchupSource = attrs["catchup-source"]?.takeIf { it.isNotBlank() },
            catchupDays = attrs["catchup-days"]?.toIntOrNull() ?: 0,
        )
    }
}
