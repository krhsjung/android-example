package kr.hs.jung.example.utils.webrtc

import io.getstream.log.taggedLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString

class SignalingClient(url: String, private val listener: WebSocketListener? = null) {
    private val logger by taggedLogger("SignalingClient")

    private val signalingScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val client = OkHttpClient()
    private val webSocket = client.newWebSocket(
        Request.Builder().url(url).build(),
        SignalingWebSocketListener()
    )

    // session flow to send information about the session state to the subscribers
    private val _messageFlow = MutableSharedFlow<String>()
    val messageFlow: SharedFlow<String> = _messageFlow

    fun sendMessage(message: String) {
        logger.d { "[Message(String)] $message" }
        webSocket.send(message)
    }

    fun sendMessage(message: ByteString) {
        logger.d { "[Message(ByteString)] $message" }
        webSocket.send(message)
    }

    fun dispose() {
        webSocket.cancel()
    }

    private inner class SignalingWebSocketListener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            logger.d { "connected" }
            listener?.onOpen(webSocket, response)
        }
        override fun onMessage(webSocket: WebSocket, text: String) {
            logger.d { "message : $text" }
            listener?.onMessage(webSocket, text)
            signalingScope.launch {
                _messageFlow.emit(text)
            }
        }
        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            logger.d { "message : $bytes" }
            listener?.onMessage(webSocket, bytes)
        }
        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            logger.d { "connection closing reason $reason" }
            listener?.onClosing(webSocket, code, reason)
        }
        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            logger.d { "connection close reason $reason" }
            listener?.onClosed(webSocket, code, reason)
        }
        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            logger.d { "failed reason $t" }
            listener?.onFailure(webSocket, t, response)
        }
    }
}
