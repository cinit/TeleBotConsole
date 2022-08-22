package cc.ioctl.telebot.tdlib.obj

import cc.ioctl.telebot.tdlib.RobotServer
import cc.ioctl.telebot.tdlib.tlrpc.RemoteApiException
import com.google.gson.JsonObject
import java.io.IOException

class Channel internal constructor(
    val server: RobotServer, val channelId: Long
) : ChatSession, IKnowability, IAffinity {

    init {
        check(channelId > 0) { "invalid channel id: $channelId" }
    }

    data class ChannelAdminPermissionSet(
        val canChangeInfo: Boolean,
        val canPostMessages: Boolean,
        val canEditMessages: Boolean,
        val canDeleteMessages: Boolean,
        val canInviteUsers: Boolean,
        val canRestrictMembers: Boolean,
        val canPromoteMembers: Boolean,
        val canManageVideoChats: Boolean,
    ) {
        companion object {
            fun fromJsonObject(obj: JsonObject): ChannelAdminPermissionSet {
                when (obj["@type"].asString) {
                    "chatMemberStatusAdministrator" -> {
                        val rights = obj["rights"].asJsonObject
                        return ChannelAdminPermissionSet(
                            canChangeInfo = rights.get("can_change_info").asBoolean,
                            canPostMessages = rights.get("can_post_messages").asBoolean,
                            canEditMessages = rights.get("can_edit_messages").asBoolean,
                            canDeleteMessages = rights.get("can_delete_messages").asBoolean,
                            canInviteUsers = rights.get("can_invite_users").asBoolean,
                            canRestrictMembers = rights.get("can_restrict_members").asBoolean,
                            canPromoteMembers = rights.get("can_promote_members").asBoolean,
                            canManageVideoChats = rights.get("can_manage_video_chats").asBoolean,
                        )
                    }
                    "chatMemberStatusCreator" -> {
                        return ChannelAdminPermissionSet(
                            canChangeInfo = true,
                            canPostMessages = true,
                            canEditMessages = true,
                            canDeleteMessages = true,
                            canInviteUsers = true,
                            canRestrictMembers = true,
                            canPromoteMembers = true,
                            canManageVideoChats = true,
                        )
                    }
                    else -> {
                        error("invalid channel member status: ${obj["@type"].asString}")
                    }
                }
            }
        }
    }

    override var name: String = channelId.toString()
        internal set

    var username: String? = null
        internal set

    override var chatId: Long = -(1000000000000L + channelId)
        internal set

    var photo: RemoteFile? = null
        internal set

    override var isContentProtected: Boolean = false
        internal set

    override var isKnown: Boolean = false
        internal set

    override var affinityUserId: Long = 0L
        internal set

    private val cachedSelfPermissionSet = HashMap<Long, ChannelAdminPermissionSet?>(1)

    @Throws(RemoteApiException::class, IOException::class)
    suspend fun getSelfPermissionSet(bot: Bot, invalidate: Boolean = false): ChannelAdminPermissionSet? {
        val userId = bot.userId
        if (!invalidate && cachedSelfPermissionSet.contains(userId)) {
            return cachedSelfPermissionSet[userId]
        }
        val chatMember = bot.getGroupMember(channelId, userId)
        val chatMemberStatus = chatMember["status"].asJsonObject
        return updateChatMemberPermissionStatus(userId, chatMemberStatus)
    }

    internal fun updateChatMemberPermissionStatus(
        userId: Long,
        chatMemberStatus: JsonObject
    ): ChannelAdminPermissionSet? {
        when (val statusType = chatMemberStatus["@type"].asString) {
            "chatMemberStatusAdministrator",
            "chatMemberStatusCreator" -> {
                val p = ChannelAdminPermissionSet.fromJsonObject(chatMemberStatus)
                cachedSelfPermissionSet[userId] = p
                return p
            }
            "chatMemberStatusMember",
            "chatMemberStatusLeft",
            "chatMemberStatusBanned" -> {
                cachedSelfPermissionSet[userId] = null
                return null
            }
            else -> {
                error("invalid channel chatMemberStatus type: $statusType")
            }
        }
    }

    @Throws(RemoteApiException::class, IOException::class)
    suspend fun canPostMessages(bot: Bot): Boolean {
        return getSelfPermissionSet(bot)?.canPostMessages == true
    }

    @Throws(RemoteApiException::class, IOException::class)
    suspend fun canEditMessages(bot: Bot): Boolean {
        return getSelfPermissionSet(bot)?.canEditMessages == true
    }

    @Throws(RemoteApiException::class, IOException::class)
    suspend fun canDeleteMessages(bot: Bot): Boolean {
        return getSelfPermissionSet(bot)?.canDeleteMessages == true
    }

    override fun hashCode(): Int {
        return channelId.toInt()
    }

    override fun equals(other: Any?): Boolean {
        if (other is Channel) {
            return channelId == other.channelId
        }
        return false
    }

    override fun toString(): String {
        return "Channel(channelId=$channelId, name=$name, username=$username)"
    }
}
