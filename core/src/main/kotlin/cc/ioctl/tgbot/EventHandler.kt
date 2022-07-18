package cc.ioctl.tgbot

import cc.ioctl.tdlib.obj.Bot
import com.google.gson.JsonObject

object EventHandler {

    interface MessageListenerV1 {
        fun onReceiveMessage(bot: Bot, chatId: Long, senderId: Long, message: JsonObject): Boolean
        fun onDeleteMessages(bot: Bot, chatId: Long, msgIds: List<Long>): Boolean
    }

}
