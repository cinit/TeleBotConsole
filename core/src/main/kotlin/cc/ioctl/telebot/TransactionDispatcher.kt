package cc.ioctl.telebot

import cc.ioctl.telebot.tdlib.RobotServer
import cc.ioctl.telebot.tdlib.obj.Bot
import cc.ioctl.telebot.tdlib.tlrpc.BaseTlRpcJsonObject
import cc.ioctl.telebot.util.Log
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.util.concurrent.ConcurrentHashMap

object TransactionDispatcher {

    private const val TAG = "TransactionDispatcher"

    private val mEventConsumerMap = ConcurrentHashMap<String, TransactionCallbackV1>(10)

    interface TransactionCallbackV1 {
        fun onEvent(event: JsonObject, bot: Bot?, type: String): Boolean
    }

    @JvmStatic
    suspend fun dispatchTDLibEvent(server: RobotServer, eventJsonString: String) {
        val event = JsonParser.parseString(eventJsonString).asJsonObject
        val type = BaseTlRpcJsonObject.getType(event)
        if (type == null) {
            Log.e(TAG, "handleTDLibEvent: type is null, event: $event")
            return
        }
        val clientIndex = BaseTlRpcJsonObject.getClientId(event)
        val extra = BaseTlRpcJsonObject.getExtra(event)
        val bot: Bot? = server.getBotWithTDLibClientIndex(clientIndex)
        if (extra != null) {
            val consumed = mEventConsumerMap.remove(extra)?.onEvent(event, bot, type) ?: false
            if (consumed) {
                return
            }
        }
        if (bot != null) {
            if (bot.onReceiveTDLibEvent(event, type)) {
                return
            } else {
                Log.w(TAG, "handleTDLibEvent: bot.onReceiveTDLibEvent return false, event: $event")
            }
        } else {
            Log.e(TAG, "handleTDLibEvent: bot is null, event: $event")
        }
        Log.w(TAG, "handleTDLibEvent: event not handled, event: $event")
    }

    @JvmStatic
    fun waitForSingleEvent(extra: String, callback: TransactionCallbackV1) {
        val old = mEventConsumerMap.put(extra, callback)
        if (old != null) {
            Log.e(TAG, "waitForSingleEvent: old callback is not null, extra: $extra")
            Log.e(TAG, "This is a bug, please report to the author")
        }
    }
}
