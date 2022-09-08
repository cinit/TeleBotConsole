package cc.ioctl.telebot

import cc.ioctl.telebot.tdlib.obj.Bot
import cc.ioctl.telebot.tdlib.obj.SessionInfo
import cc.ioctl.telebot.tdlib.tlrpc.api.channel.ChannelMemberStatusEvent
import cc.ioctl.telebot.tdlib.tlrpc.api.channel.ChatJoinRequest
import cc.ioctl.telebot.tdlib.tlrpc.api.channel.ChatPermissions
import cc.ioctl.telebot.tdlib.tlrpc.api.msg.Message
import cc.ioctl.telebot.tdlib.tlrpc.api.query.CallbackQuery
import com.google.gson.JsonObject

object EventHandler {

    interface MessageListenerV1 {
        fun onReceiveMessage(bot: Bot, si: SessionInfo, senderId: Long, message: Message): Boolean
        fun onDeleteMessages(bot: Bot, si: SessionInfo, msgIds: List<Long>): Boolean
        fun onUpdateMessageContent(bot: Bot, si: SessionInfo, msgId: Long, content: JsonObject): Boolean
        fun onMessageEdited(bot: Bot, si: SessionInfo, msgId: Long, editDate: Int): Boolean
        fun onMessagePinned(bot: Bot, si: SessionInfo, msgId: Long, isPinned: Boolean): Boolean {
            return false
        }
    }

    interface GroupPermissionListenerV2 {
        /**
         * @param bot the bot that received the event
         * @param groupId the group id
         * @param userId the user id of the event, greater than 0 for user, starting with '-100' for anonymous channel
         * @param event the event
         */
        fun onMemberStatusChanged(bot: Bot, groupId: Long, userId: Long, event: ChannelMemberStatusEvent): Boolean

        /**
         * @param bot the bot that received the event
         * @param groupId the group id
         * @param perm the new chat permissions
         */
        fun onGroupDefaultPermissionsChanged(bot: Bot, groupId: Long, permissions: ChatPermissions): Boolean

    }

    interface GroupMemberJoinRequestListenerV2 {
        /**
         * @param bot the bot that received the event
         * @param groupId the chat id of the group/channel, always positive
         * @param userId the user id requesting to join the group
         * @param request the join request
         */
        fun onMemberJoinRequest(bot: Bot, groupId: Long, userId: Long, request: ChatJoinRequest): Boolean
    }

    interface CallbackQueryListenerV2 {
        fun onCallbackQuery(
            bot: Bot,
            query: CallbackQuery
        ): Boolean
    }

}
