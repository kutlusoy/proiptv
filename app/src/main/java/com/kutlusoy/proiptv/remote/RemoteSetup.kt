package com.kutlusoy.proiptv.remote

import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** What the phone sends. Passwords stay in memory, they are encrypted when the account is saved. */
@Serializable
data class RemoteAccount(
    val type: String = "xtream",
    val name: String = "",
    val url: String = "",
    val username: String = "",
    val password: String = "",
    val userAgent: String = "",
    val epgUrl: String = "",
)

@Serializable
private data class Answer(val ok: Boolean, val error: String? = null)

/**
 * The pages and the interface of the setup from the phone, free of Android classes so it can be tested.
 * Every request needs the one-time token from the QR code.
 */
class RemoteRouter(
    private val token: String,
    private val page: () -> ByteArray,
    private val onAccount: suspend (RemoteAccount) -> String?,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun route(request: HttpRequest): ByteArray {
        val provided = request.query["t"] ?: request.headers["x-token"]
        if (!RemoteHttp.tokenMatches(token, provided)) {
            return RemoteHttp.response(403, "text/plain; charset=utf-8", "Invalid link. Scan the QR code on the TV again.".toByteArray())
        }
        return when {
            request.method == "GET" && request.path == "/" -> RemoteHttp.response(200, "text/html; charset=utf-8", page())
            request.method == "POST" && request.path == "/api/account" -> account(request)
            else -> answer(404, Answer(false, "not_found"))
        }
    }

    fun badRequest(message: String): ByteArray = answer(400, Answer(false, message))

    private suspend fun account(request: HttpRequest): ByteArray {
        val draft = try {
            json.decodeFromString<RemoteAccount>(String(request.body, Charsets.UTF_8))
        } catch (e: Exception) {
            return answer(400, Answer(false, "invalid_data"))
        }
        val problem = validate(draft)
        if (problem != null) return answer(400, Answer(false, problem))
        val failure = onAccount(draft)
        return if (failure == null) answer(200, Answer(true)) else answer(200, Answer(false, failure))
    }

    private fun validate(a: RemoteAccount): String? = when {
        a.type != "xtream" && a.type != "m3u" -> "invalid_type"
        a.name.isBlank() || a.name.length > 100 -> "name_missing"
        a.url.isBlank() || a.url.length > 2000 -> "url_missing"
        a.type == "xtream" && a.username.isBlank() && !a.url.contains("username=") -> "username_missing"
        a.password.length > 500 || a.username.length > 200 || a.userAgent.length > 300 || a.epgUrl.length > 2000 -> "too_long"
        else -> null
    }

    private fun answer(status: Int, body: Answer): ByteArray =
        RemoteHttp.response(status, "application/json; charset=utf-8", json.encodeToString(body).toByteArray(Charsets.UTF_8))
}

/** Serves the setup page for the phone. It only runs while the setup dialog is open. */
class RemoteSetupServer(private val router: RemoteRouter) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var serverSocket: ServerSocket? = null

    /** Starts listening on the first free port and returns it. */
    fun start(ports: IntRange = PORTS): Int {
        var socket: ServerSocket? = null
        for (candidate in ports) {
            try {
                socket = ServerSocket(candidate)
                break
            } catch (e: IOException) {
                continue
            }
        }
        val server = socket ?: throw IOException("No free port")
        serverSocket = server
        scope.launch {
            while (isActive) {
                val client = try {
                    server.accept()
                } catch (e: IOException) {
                    break
                }
                launch { handle(client) }
            }
        }
        return server.localPort
    }

    fun stop() {
        runCatching { serverSocket?.close() }
        scope.cancel()
    }

    private suspend fun handle(client: Socket) {
        client.use { socket ->
            socket.soTimeout = SOCKET_TIMEOUT_MS
            val reply = try {
                val request = RemoteHttp.readRequest(socket.getInputStream().buffered())
                if (request == null) router.badRequest("bad_request") else router.route(request)
            } catch (e: IOException) {
                router.badRequest("bad_request")
            } catch (e: Exception) {
                router.badRequest(e.message ?: "error")
            }
            try {
                socket.getOutputStream().apply { write(reply); flush() }
            } catch (e: IOException) {
                // the phone closed the connection
            }
        }
    }

    companion object {
        val PORTS = 8080..8099
        private const val SOCKET_TIMEOUT_MS = 15_000
    }
}
