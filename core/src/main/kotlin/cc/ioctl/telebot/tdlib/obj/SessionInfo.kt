package cc.ioctl.telebot.tdlib.obj

/**
 * Chat session info
 * @param id Session identifier
 * @param type 0 for private chat with a user, 1 for group/channels
 */
data class SessionInfo(
    val id: Long, val type: Int
) : ISessionDescriptor {

    init {
        check(type in 0..1) { "invalid type: $type" }
        check(id != 0L) { "invalid id: $id" }
    }

    override val sessionInfo = this

    val isTrivialPrivateChat: Boolean = (type == 0)

    val isGroupOrChannel: Boolean = (type == 1)

    companion object {

        @JvmStatic
        fun forUser(userId: Long): SessionInfo {
            return SessionInfo(userId, 0)
        }

        @JvmStatic
        fun forGroup(groupId: Long): SessionInfo {
            return SessionInfo(groupId, 1)
        }

        @JvmStatic
        fun forChannel(channelId: Long): SessionInfo {
            return SessionInfo(channelId, 1)
        }

        @JvmStatic
        fun forTDLibChatId(chatId: Long): SessionInfo {
            return when {
                isChannelChatId(chatId) -> forChannel(chatIdToChannelId(chatId))
                chatId < 0 -> forGroup(-chatId)
                else -> forUser(chatId)
            }
        }

        const val CHAT_ID_NEGATIVE_NOTATION = -1000000000000L

        @JvmStatic
        fun chatIdToGroupId(chatId: Long): Long {
            require(chatId < CHAT_ID_NEGATIVE_NOTATION) { "chatId $chatId is not a group chat id" }
            return -chatId + CHAT_ID_NEGATIVE_NOTATION
        }

        @JvmStatic
        fun groupIdToChatId(groupId: Long): Long {
            require(groupId > 0) { "groupId $groupId is not a group chat id" }
            return if (groupId < 1000000000L) {
                -groupId
            } else {
                -groupId + CHAT_ID_NEGATIVE_NOTATION
            }
        }

        @JvmStatic
        fun isTrivialPrivateChat(chatId: Long): Boolean {
            return chatId > 0
        }

        @JvmStatic
        fun isTrivialUserSender(senderId: Long): Boolean {
            return senderId > 0
        }

        @JvmStatic
        fun isAnonymousSender(senderId: Long): Boolean {
            return senderId < CHAT_ID_NEGATIVE_NOTATION
        }

        @JvmStatic
        fun isChannelChatId(chatId: Long): Boolean {
            return chatId < CHAT_ID_NEGATIVE_NOTATION
        }

        @JvmStatic
        fun chatIdToChannelId(chatId: Long): Long {
            require(chatId < CHAT_ID_NEGATIVE_NOTATION) { "chatId $chatId is not a channel chat id" }
            return -chatId + CHAT_ID_NEGATIVE_NOTATION
        }

        @JvmStatic
        fun channelIdToChatId(channelId: Long): Long {
            require(channelId > 0) { "channelId $channelId is not a channel chat id" }
            return -channelId + CHAT_ID_NEGATIVE_NOTATION
        }

    }

    fun toTDLibChatId(): Long {
        return when (type) {
            0 -> id
            1 -> channelIdToChatId(id)
            else -> error("invalid type: $type")
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SessionInfo

        if (id != other.id) return false
        if (type != other.type) return false

        return true
    }

    override fun hashCode(): Int {
        return 31 * type + id.hashCode()
    }

    override fun toString(): String {
        return "SessionInfo(id=$id, type=$type)"
    }
}
