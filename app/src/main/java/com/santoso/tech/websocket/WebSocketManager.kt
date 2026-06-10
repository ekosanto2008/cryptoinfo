package com.santoso.tech.websocket

import com.santoso.tech.data.model.CandleData
import com.santoso.tech.data.model.Ticker
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import okhttp3.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class WsRequest(
    val op: String,
    val args: List<WsArg>
)

@Serializable
data class WsArg(
    val channel: String,
    val instId: String? = null,
    val instType: String? = null
)

// Maps timeframe UI label → OKX WS candle channel name
fun timeframeToChannel(bar: String): String = when (bar) {
    "15m" -> "candle15m"
    "1H"  -> "candle1H"
    "4H"  -> "candle4H"
    "1W"  -> "candle1W"
    else  -> "candle1D"   // default "1D"
}

@Singleton
class WebSocketManager @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json
) {
    private var webSocket: WebSocket? = null
    private var isConnected = false

    // Track current subscriptions for reconnect
    private val subscribedTickerIds = mutableSetOf<String>()
    private val subscribedCandles = mutableSetOf<Pair<String, String>>() // (instId, bar)

    // Ticker real-time flow
    private val _tickerFlow = MutableSharedFlow<Ticker>(
        replay = 0,
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val tickerFlow: SharedFlow<Ticker> = _tickerFlow.asSharedFlow()

    // Candle real-time flow: Pair<instId, CandleData>
    private val _candleFlow = MutableSharedFlow<Pair<String, CandleData>>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val candleFlow: SharedFlow<Pair<String, CandleData>> = _candleFlow.asSharedFlow()

    private val wsListener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            isConnected = true
            // Re-subscribe all tracked subscriptions on reconnect
            resubscribeAll()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            try {
                val root = json.parseToJsonElement(text).jsonObject
                val channel = root["arg"]?.jsonObject?.get("channel")?.jsonPrimitive?.content ?: ""
                val instId  = root["arg"]?.jsonObject?.get("instId")?.jsonPrimitive?.content ?: ""
                val dataArr = root["data"]?.jsonArray ?: return

                when {
                    // --- ticker update ---
                    channel == "tickers" -> {
                        dataArr.forEach { element ->
                            val ticker = json.decodeFromJsonElement<Ticker>(element)
                            _tickerFlow.tryEmit(ticker)
                        }
                    }
                    // --- candle update (channel starts with "candle") ---
                    channel.startsWith("candle") -> {
                        dataArr.forEach { element ->
                            val row = element.jsonArray
                            if (row.size >= 5) {
                                val candle = CandleData(
                                    timestamp = row[0].jsonPrimitive.content.toLongOrNull() ?: 0L,
                                    open      = row[1].jsonPrimitive.content.toFloatOrNull() ?: 0f,
                                    high      = row[2].jsonPrimitive.content.toFloatOrNull() ?: 0f,
                                    low       = row[3].jsonPrimitive.content.toFloatOrNull() ?: 0f,
                                    close     = row[4].jsonPrimitive.content.toFloatOrNull() ?: 0f,
                                    volume    = if (row.size > 5) row[5].jsonPrimitive.content.toFloatOrNull() ?: 0f else 0f
                                )
                                _candleFlow.tryEmit(Pair(instId, candle))
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Silently ignore parse errors
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            isConnected = false
            // Auto-reconnect after 3 seconds
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
        if (webSocket != null) return   // already connected or connecting
        val request = Request.Builder()
            .url("wss://ws.okx.com:8443/ws/v5/public")
            .build()
        webSocket = client.newWebSocket(request, wsListener)
    }

    private fun reconnect() {
        webSocket?.cancel()
        webSocket = null
        isConnected = false
        connect()
    }

    /**
     * Subscribe to ticker updates for a list of instIds.
     * Batches into groups of 50 to stay within OKX limits.
     */
    fun subscribeTickers(instIds: List<String>) {
        subscribedTickerIds.addAll(instIds)
        if (!isConnected) return
        sendTickerSubscriptions(instIds, "subscribe")
    }

    fun unsubscribeTickers(instIds: List<String>) {
        subscribedTickerIds.removeAll(instIds.toSet())
        if (!isConnected) return
        sendTickerSubscriptions(instIds, "unsubscribe")
    }

    /**
     * Replace all current ticker subscriptions with a new set of instIds.
     * Efficiently unsubscribes old ones and subscribes new ones.
     */
    fun replaceTickerSubscriptions(newInstIds: List<String>) {
        val oldIds = subscribedTickerIds.toSet()
        val newSet = newInstIds.toSet()
        val toUnsub = oldIds - newSet
        val toSub = newSet - oldIds

        if (toUnsub.isNotEmpty()) {
            subscribedTickerIds.removeAll(toUnsub)
            if (isConnected) sendTickerSubscriptions(toUnsub.toList(), "unsubscribe")
        }
        if (toSub.isNotEmpty()) {
            subscribedTickerIds.addAll(toSub)
            if (isConnected) sendTickerSubscriptions(toSub.toList(), "subscribe")
        }
    }

    private fun sendTickerSubscriptions(instIds: List<String>, op: String) {
        // Batch into groups of 50
        instIds.chunked(50).forEach { batch ->
            val args = batch.map { WsArg(channel = "tickers", instId = it) }
            val msg = json.encodeToString(WsRequest(op = op, args = args))
            webSocket?.send(msg)
        }
    }

    fun subscribeCandle(instId: String, bar: String) {
        subscribedCandles.add(Pair(instId, bar))
        if (!isConnected) return
        val channel = timeframeToChannel(bar)
        val args = listOf(WsArg(channel = channel, instId = instId))
        webSocket?.send(json.encodeToString(WsRequest(op = "subscribe", args = args)))
    }

    fun unsubscribeCandle(instId: String, bar: String) {
        subscribedCandles.remove(Pair(instId, bar))
        if (!isConnected) return
        val channel = timeframeToChannel(bar)
        val args = listOf(WsArg(channel = channel, instId = instId))
        webSocket?.send(json.encodeToString(WsRequest(op = "unsubscribe", args = args)))
    }

    private fun resubscribeAll() {
        // Re-subscribe tickers
        if (subscribedTickerIds.isNotEmpty()) {
            sendTickerSubscriptions(subscribedTickerIds.toList(), "subscribe")
        }
        // Re-subscribe candles
        subscribedCandles.forEach { (instId, bar) ->
            val channel = timeframeToChannel(bar)
            val args = listOf(WsArg(channel = channel, instId = instId))
            webSocket?.send(json.encodeToString(WsRequest(op = "subscribe", args = args)))
        }
    }

    fun disconnect() {
        webSocket?.close(1000, "Normal closure")
        webSocket = null
        isConnected = false
    }
}
