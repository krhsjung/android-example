package kr.hs.jung.example.utils

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString

enum class WebSocketClientStatus {
    DISCONNECTED,
    DISCONNECTING,
    CONNECTING,
    CONNECTED
}

class WebSocketClient(private val url: String, private val listener: WebSocketListener? = null) {
    private val TAG = "WebSocketClient"

    private lateinit var webSocket : WebSocket

    private val client = OkHttpClient()
    private var clientStatus = WebSocketClientStatus.DISCONNECTED

    fun connect(): WebSocketClientStatus {
        if (clientStatus == WebSocketClientStatus.DISCONNECTED) {
            Log.i(TAG, "connect url : $url")
            val request = Request
                .Builder()
                .url(url)
                .build()
            webSocket = client.newWebSocket(request, SignalingWebSocketListener())
            clientStatus = WebSocketClientStatus.CONNECTING
        } else {
            Log.i(TAG, "WebSocket is connecting or connected")
        }
        return clientStatus
    }

    fun sendMessage(message: String) {
        webSocket.send(message)
    }

    fun sendMessage(message: ByteString) {
        webSocket.send(message)
    }

    fun disconnect() {
        webSocket.close(1000, "User calls websocket disconnect")
    }

    private inner class SignalingWebSocketListener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.i(TAG, "connected")
            clientStatus = WebSocketClientStatus.CONNECTED
            listener?.onOpen(webSocket, response)
        }
        override fun onMessage(webSocket: WebSocket, text: String) {
            Log.i(TAG, "message : $text")
            listener?.onMessage(webSocket, text)
        }
        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            Log.i(TAG, "message : $bytes")
            listener?.onMessage(webSocket, bytes)
        }
        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            Log.i(TAG, "connection closing reason $reason")
            clientStatus = WebSocketClientStatus.DISCONNECTING
            listener?.onClosing(webSocket, code, reason)
        }
        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.i(TAG, "connection close reason $reason")
            clientStatus = WebSocketClientStatus.DISCONNECTED
            listener?.onClosed(webSocket, code, reason)
        }
        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.i(TAG, "failed reason $t")
            listener?.onFailure(webSocket, t, response)
        }
    }
}
