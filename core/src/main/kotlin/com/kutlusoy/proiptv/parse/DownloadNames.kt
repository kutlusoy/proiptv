package com.kutlusoy.proiptv.parse

/**
 * Where a download is stored, relative to the folder of the downloads. A movie is one file, `DE-Name-2023.mp4`. The episodes of a series go into
 * a folder per series and one per season: `DE-Name-2021/Season 01/DE-Name-2021-S01E05-Title.mp4`. The language and the year come from the title
 * as the provider writes it ("DE | Name (2021)"), parts that are not there are left out.
 */
object DownloadNames {
    private fun head(raw: String): Triple<String, String, String> {
        val text = raw.decodeEntities().trim()
        val info = TitleParser.parse(text)
        return Triple(TitleParser.country(text), RecordingNames.clean(info.title), if (info.year > 0) info.year.toString() else "")
    }

    private fun join(vararg parts: String) = parts.filter { it.isNotEmpty() }.joinToString("-").ifEmpty { "Download" }

    fun movie(title: String, ext: String): String {
        val (language, name, year) = head(title)
        return join(language, name, year) + "." + ext
    }

    /** [seasonLabel] gives the name of the season folder in the language of the app. */
    fun episode(seriesName: String, season: Int, episode: Int, episodeTitle: String, ext: String, seasonLabel: (Int) -> String): String {
        val (language, name, year) = head(seriesName.ifBlank { episodeTitle })
        val series = join(language, name, year)
        val number = (if (season > 0) "S%02d".format(season) else "") + (if (episode > 0) "E%02d".format(episode) else "")
        val title = RecordingNames.clean(TitleParser.parse(episodeTitle).title).takeUnless { it.equals(name, ignoreCase = true) }.orEmpty()
        val folder = RecordingNames.clean(seasonLabel(season.coerceAtLeast(0)))
        return series + "/" + folder + "/" + join(language, name, year, number, title) + "." + ext
    }
}
