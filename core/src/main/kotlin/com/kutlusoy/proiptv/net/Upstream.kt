package com.kutlusoy.proiptv.net

import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Dispatcher
import okhttp3.Response

/**
 * The connection to a live channel of the provider. It is cut for real when it is no longer needed: closing the stream cancels the call (the
 * socket is closed at once, also while a read is waiting), and cancelling the coroutine while the connection is still being made cancels it too.
 * Providers with one connection per account depend on that.
 */
object Upstream {
    private val liveClient by lazy {
        // an own dispatcher: a long running stream must not wait for a free slot behind other streams
        Http.client.newBuilder()
            .readTimeout(20, TimeUnit.SECONDS)
            .dispatcher(Dispatcher().apply { maxRequests = 64; maxRequestsPerHost = 64 })
            .build()
    }

    fun opener(url: String, userAgent: String?): suspend () -> InputStream = {
        val call = liveClient.newCall(Http.request(url, userAgent))
        val response = call.await()
        if (!response.isSuccessful) {
            val code = response.code
            response.close()
            throw HttpException(code, "HTTP $code")
        }
        CancelOnClose(call, response.body!!.byteStream())
    }

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                if (continuation.isCancelled) response.close() else continuation.resume(response)
            }

            override fun onFailure(call: Call, e: IOException) {
                if (!continuation.isCancelled) continuation.resumeWithException(e)
            }
        })
    }

    private class CancelOnClose(private val call: Call, input: InputStream) : FilterInputStream(input) {
        override fun close() {
            call.cancel()
            runCatching { super.close() }
        }
    }
}
