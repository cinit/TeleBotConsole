package cc.ioctl.telebot

import cc.ioctl.telebot.tdlib.obj.Bot
import cc.ioctl.telebot.tdlib.tlrpc.api.msg.Message
import com.google.gson.JsonObject

object EventHandler {

    interface MessageListenerV1 {
        fun onReceiveMessage(bot: Bot, chatId: Long, senderId: Long, message: Message): Boolean
        fun onDeleteMessages(bot: Bot, chatId: Long, msgIds: List<Long>): Boolean
        fun onUpdateMessageContent(bot: Bot, chatId: Long, msgId: Long, content: JsonObject): Boolean
        fun onMessageEdited(bot: Bot, chatId: Long, msgId: Long, editDate: Int): Boolean
        fun onMessagePinned(bot: Bot, chatId: Long, msgId: Long, isPinned: Boolean): Boolean {
            return false
        }
    }

    interface GroupPermissionListenerV1 {
        /**
         * @param bot the bot that received the event
         * @param chatId the chat id of the group, negative number starting with '-100'
         * @param userId the anchor user id of the event
         * @param event the updateChatMember object
         */
        fun onMemberStatusChanged(bot: Bot, chatId: Long, userId: Long, event: JsonObject): Boolean

        /**
         * @param bot the bot that received the event
         * @param chatId the chat id of the group, negative number starting with '-100'
         * @param perm the chatPermissions object
         */
        fun onChatPermissionsChanged(bot: Bot, chatId: Long, permissions: JsonObject): Boolean
    }

    interface GroupMemberJoinRequestListenerV1 {
        /**
         * @param bot the bot that received the event
         * @param chatId the chat id of the group, negative number starting with '-100'
         * @param userId the user id requesting to join the group
         * @param event the updateNewChatJoinRequest object
         */
        fun onMemberJoinRequest(bot: Bot, chatId: Long, userId: Long, event: JsonObject): Boolean
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
