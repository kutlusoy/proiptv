package com.kutlusoy.proiptv.net

import java.io.InputStream
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object Upstream {
    private val liveClient by lazy { Http.client.newBuilder().readTimeout(20, TimeUnit.SECONDS).build() }

    fun opener(url: String, userAgent: String?): suspend () -> InputStream = {
        withContext(Dispatchers.IO) {
            val response = liveClient.newCall(Http.request(url, userAgent)).execute()
            if (!response.isSuccessful) {
                val code = response.code
                response.close()
                throw HttpException(code, "HTTP $code")
            }
            response.body!!.byteStream()
        }
    }
}
