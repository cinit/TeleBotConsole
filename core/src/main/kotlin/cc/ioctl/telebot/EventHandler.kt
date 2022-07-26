package cc.ioctl.telebot

import cc.ioctl.telebot.tdlib.obj.Bot
import com.google.gson.JsonObject

object EventHandler {

    interface MessageListenerV1 {
        fun onReceiveMessage(bot: Bot, chatId: Long, senderId: Long, message: JsonObject): Boolean
        fun onDeleteMessages(bot: Bot, chatId: Long, msgIds: List<Long>): Boolean
        fun onUpdateMessageContent(bot: Bot, chatId: Long, msgId: Long, content: JsonObject): Boolean
        fun onMessageEdited(bot: Bot, chatId: Long, msgId: Long, editDate: Int): Boolean
        fun onMessagePinned(bot: Bot, chatId: Long, msgId: Long, isPinned: Boolean): Boolean {
            return false
        }
    }

    interface CallbackQueryListenerV1 {
        fun onCallbackQuery(
            bot: Bot,
            query: JsonObject,
            queryId: String,
            chatId: Long,
            senderId: Long,
            msgId: Long
        ): Boolean
    }

}
