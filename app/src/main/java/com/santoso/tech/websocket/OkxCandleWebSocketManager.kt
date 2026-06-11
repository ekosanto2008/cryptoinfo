package com.santoso.tech.websocket

import com.santoso.tech.data.model.ChartCandle
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import okhttp3.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OkxCandleWebSocketManager @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json
) {
    private var webSocket: WebSocket? = null
    private var isConnected = false

    private val subscribedCandles = mutableSetOf<Pair<String, String>>() // (instId, bar)

    private val _candleFlow = MutableSharedFlow<ChartCandle>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val candleFlow: SharedFlow<ChartCandle> = _candleFlow.asSharedFlow()

    private val wsListener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            isConnected = true
            resubscribeAll()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            try {
                val root = json.parseToJsonElement(text).jsonObject
                val channel = root["arg"]?.jsonObject?.get("channel")?.jsonPrimitive?.content ?: ""
                val dataArr = root["data"]?.jsonArray ?: return

                if (channel.startsWith("candle")) {
                    dataArr.forEach { element ->
                        val row = element.jsonArray
                        if (row.size >= 5) {
                            val timeMs = row[0].jsonPrimitive.content.toLongOrNull() ?: 0L
                            val candle = ChartCandle(
                                time = timeMs / 1000, // TradingView expects seconds for default timestamps
                                open = row[1].jsonPrimitive.content.toDoubleOrNull() ?: 0.0,
                                high = row[2].jsonPrimitive.content.toDoubleOrNull() ?: 0.0,
                                low = row[3].jsonPrimitive.content.toDoubleOrNull() ?: 0.0,
                                close = row[4].jsonPrimitive.content.toDoubleOrNull() ?: 0.0
                            )
                            _candleFlow.tryEmit(candle)
                        }
                    }
                }
            } catch (e: Exception) {
                // Silently ignore parse errors
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            isConnected = false
            try {
                Thread.sleep(3000)
                reconnect()
            } catch (_: Exception) {}
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            isConnected = false
        }
    }

    fun connect() {
        if (webSocket != null) return
        val request = Request.Builder()
            .url("wss://ws.okx.com:8443/ws/v5/business")
            .build()
        webSocket = client.newWebSocket(request, wsListener)
    }

    fun reconnect() {
        webSocket?.cancel()
        webSocket = null
        isConnected = false
        connect()
    }

    fun subscribe(instId: String, bar: String) {
        subscribedCandles.add(Pair(instId, bar))
        if (!isConnected) return
        val channel = timeframeToChannel(bar)
        val args = listOf(WsArg(channel = channel, instId = instId))
        webSocket?.send(json.encodeToString(WsRequest(op = "subscribe", args = args)))
    }

    fun unsubscribe(instId: String, bar: String) {
        subscribedCandles.remove(Pair(instId, bar))
        if (!isConnected) return
        val channel = timeframeToChannel(bar)
        val args = listOf(WsArg(channel = channel, instId = instId))
        webSocket?.send(json.encodeToString(WsRequest(op = "unsubscribe", args = args)))
    }

    private fun resubscribeAll() {
        subscribedCandles.forEach { (instId, bar) ->
            val channel = timeframeToChannel(bar)
            val args = listOf(WsArg(channel = channel, instId = instId))
            webSocket?.send(json.encodeToString(WsRequest(op = "subscribe", args = args)))
        }
    }

    fun close() {
        webSocket?.close(1000, "Normal closure")
        webSocket = null
        isConnected = false
    }
}
