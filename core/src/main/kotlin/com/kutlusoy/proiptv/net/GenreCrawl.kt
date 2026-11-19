package com.kutlusoy.proiptv.net

import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/**
 * Providers give the genre of a movie only in its details, not in the list. This walks through the movies that have none, one request after the other
 * with a pause, and hands every answer on. It stops at once when the provider starts to refuse (too many requests, no access), so an account is not put at risk.
 */
object GenreCrawl {
    /** @return how many movies were asked; [fetch] returns the genre text or null when the movie has none. */
    suspend fun run(
        ids: List<String>,
        pauseMs: Long,
        fetch: suspend (String) -> String?,
        store: suspend (id: String, genre: String?) -> Unit,
        onProgress: (done: Int) -> Unit = {},
    ): Int {
        var done = 0
        for (id in ids) {
            coroutineContext.ensureActive()
            try {
                store(id, fetch(id))
            } catch (e: HttpException) {
                // the provider says no: no more requests (a missing movie is the only answer that is not a refusal)
                if (e.code != 404) return done
                store(id, null)
            } catch (e: java.io.IOException) {
                // no network at the moment: try again at the next start
                return done
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                store(id, null)
            }
            done++
            onProgress(done)
            delay(pauseMs)
        }
        return done
    }
}
