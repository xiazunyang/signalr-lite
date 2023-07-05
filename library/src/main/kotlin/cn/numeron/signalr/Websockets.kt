package cn.numeron.signalr

import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import okhttp3.*
import okio.ByteString

interface WebSocketEvent {

    object Open : WebSocketEvent

    class TextMessage(val text: String) : WebSocketEvent

    class BytesMessage(val bytes: ByteString) : WebSocketEvent

    class Closing(val code: Int, reason: String) : WebSocketEvent

    class Closed(val code: Int, reason: String) : WebSocketEvent

}

fun OkHttpClient.newWebSocketAsFlow(
    request: Request,
    messageFlow: Flow<String>,
    logger: SignalRLogger? = null
): Flow<WebSocketEvent> {
    return callbackFlow {
        var sendJob: Job? = null
        val listener = object : WebSocketListener() {
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                trySendBlocking(WebSocketEvent.Closed(code, reason))
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                trySendBlocking(WebSocketEvent.Closing(code, reason))
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                throw t
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                trySendBlocking(WebSocketEvent.TextMessage(text)).onSuccess {
                    logger?.log("<<< $text")
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                trySendBlocking(WebSocketEvent.BytesMessage(bytes))
            }

            override fun onOpen(webSocket: WebSocket, response: Response) {
                trySendBlocking(WebSocketEvent.Open)
                sendJob = launch {
                    messageFlow.collect {
                        logger?.log(">>> $it")
                        webSocket.send(it)
                    }
                }
            }
        }
        val webSocket = newWebSocket(request, listener)

        awaitClose {
            webSocket.cancel()
            sendJob?.cancel()
        }
    }
}