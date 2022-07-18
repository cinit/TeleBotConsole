package cc.ioctl.tgbot

import cc.ioctl.tdlib.RobotServer
import cc.ioctl.tdlib.obj.Bot
import cc.ioctl.tdlib.tlrpc.TlRpcJsonObject
import cc.ioctl.util.Log
import java.util.concurrent.ConcurrentHashMap

object TransactionDispatcher {

    private const val TAG = "TransactionDispatcher"

    private val mEventConsumerMap = ConcurrentHashMap<String, TransactionCallbackV1>(10)

    interface TransactionCallbackV1 {
        fun onEvent(event: String, bot: Bot?, type: String): Boolean
    }

    @JvmStatic
    suspend fun dispatchTDLibEvent(server: RobotServer, event: String) {
        // Log.d(TAG, "handleTDLibEvent: $event")
        val type = TlRpcJsonObject.getType(event)
        if (type == null) {
            Log.e(TAG, "handleTDLibEvent: type is null, event: $event")
            return
        }
        val clientIndex = TlRpcJsonObject.getClientId(event)
        val extra = TlRpcJsonObject.getExtra(event)
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
