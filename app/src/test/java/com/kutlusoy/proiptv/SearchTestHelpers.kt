package com.kutlusoy.proiptv

import androidx.paging.PagingSource
import com.kutlusoy.proiptv.data.GuideRow
import com.kutlusoy.proiptv.data.Item
import com.kutlusoy.proiptv.data.ItemDao
import com.kutlusoy.proiptv.data.ProgramDao
import com.kutlusoy.proiptv.parse.SearchMatch
import kotlinx.coroutines.flow.Flow

/** The queries take the search as four words. These keep the tests readable: they take the search as one text. */
suspend fun ItemDao.count(kind: Int, ids: List<Long>, cat: String?, q: String, country: String?, year: Int?, flags: Int): Int {
    val (a, b, c, d) = SearchMatch.four(q)
    return count(kind, ids, cat, a, b, c, d, country, year, flags)
}

suspend fun ItemDao.at(kind: Int, ids: List<Long>, cat: String?, q: String, country: String?, year: Int?, flags: Int, offset: Int): Item? {
    val (a, b, c, d) = SearchMatch.four(q)
    return at(kind, ids, cat, a, b, c, d, country, year, flags, offset)
}

fun ItemDao.paging(kind: Int, ids: List<Long>, cat: String?, q: String, country: String?, year: Int?, flags: Int): PagingSource<Int, Item> {
    val (a, b, c, d) = SearchMatch.four(q)
    return paging(kind, ids, cat, a, b, c, d, country, year, flags, null)
}

fun ProgramDao.guide(ids: List<Long>, cat: String?, from: Long, to: Long, q: String): Flow<List<GuideRow>> {
    val (a, b, c, d) = SearchMatch.four(q)
    return guide(ids, cat, from, to, a, b, c, d)
}
