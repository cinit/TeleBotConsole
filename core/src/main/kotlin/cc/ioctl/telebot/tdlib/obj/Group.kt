package cc.ioctl.telebot.tdlib.obj

import cc.ioctl.telebot.tdlib.RobotServer
import cc.ioctl.telebot.tdlib.tlrpc.RemoteApiException
import cc.ioctl.telebot.tdlib.tlrpc.TlRpcJsonObject
import com.google.gson.JsonObject
import java.io.IOException

class Group internal constructor(
    val server: RobotServer, val groupId: Long
) : ChatSession, IKnowability, IAffinity {

    init {
        check(groupId > 0) { "invalid groupId: $groupId" }
    }

    data class GroupMemberPermissionSet(
        val canSendMessages: Boolean,
        val canSendMediaMessages: Boolean,
        val canSendPolls: Boolean,
        val canSendOtherMessages: Boolean,
        val canAddWebPagePreviews: Boolean,
        val canChangeInfo: Boolean,
        val canInviteUsers: Boolean,
        val canPinMessages: Boolean
    ) {
        companion object {
            fun fromJsonObject(obj: JsonObject): GroupMemberPermissionSet {
                TlRpcJsonObject.checkTypeNonNull(obj, "chatPermissions")
                return GroupMemberPermissionSet(
                    obj.get("can_send_messages").asBoolean,
                    obj.get("can_send_media_messages").asBoolean,
                    obj.get("can_send_polls").asBoolean,
                    obj.get("can_send_other_messages").asBoolean,
                    obj.get("can_add_web_page_previews").asBoolean,
                    obj.get("can_change_info").asBoolean,
                    obj.get("can_invite_users").asBoolean,
                    obj.get("can_pin_messages").asBoolean
                )
            }
        }
    }

    data class GroupAdminPermissionSet(
        val uid: Long,
        val adminTitle: String?,
        val canChangeInfo: Boolean,
        val canDeleteMessages: Boolean,
        val canInviteUsers: Boolean,
        val canRestrictMembers: Boolean,
        val canPinMessages: Boolean,
        val canPromoteMembers: Boolean,
        val canManageVideoChats: Boolean,
        val isAnonymous: Boolean
    ) {
        companion object {
            fun fromJsonObject(userId: Long, obj: JsonObject): GroupAdminPermissionSet {
                when (obj["@type"].asString) {
                    "chatMemberStatusAdministrator" -> {
                        val rights = obj["rights"].asJsonObject
                        return GroupAdminPermissionSet(
                            uid = userId,
                            adminTitle = obj.get("custom_title")?.asString?.ifEmpty { null },
                            canChangeInfo = rights.get("can_change_info").asBoolean,
                            canDeleteMessages = rights.get("can_delete_messages").asBoolean,
                            canInviteUsers = rights.get("can_invite_users").asBoolean,
                            canRestrictMembers = rights.get("can_restrict_members").asBoolean,
                            canPinMessages = rights.get("can_pin_messages").asBoolean,
                            canPromoteMembers = rights.get("can_promote_members").asBoolean,
                            canManageVideoChats = rights.get("can_manage_video_chats").asBoolean,
                            isAnonymous = rights.get("is_anonymous").asBoolean
                        )
                    }
                    "chatMemberStatusCreator" -> {
                        return GroupAdminPermissionSet(
                            uid = userId,
                            adminTitle = obj.get("custom_title")?.asString?.ifEmpty { null },
                            canChangeInfo = true,
                            canDeleteMessages = true,
                            canInviteUsers = true,
                            canRestrictMembers = true,
                            canPinMessages = true,
                            canPromoteMembers = true,
                            canManageVideoChats = true,
                            isAnonymous = obj.get("is_anonymous").asBoolean
                        )
                    }
                    else -> {
                        throw IllegalStateException("unknown chatMemberStatus type: ${obj["@type"].asString}")
                    }
                }
            }
        }
    }

    override var name: String = groupId.toString()
        internal set

    var username: String? = null
        internal set

    var isSuperGroup: Boolean = false
        internal set

    var isBroadcastGroup: Boolean = false
        internal set

    override var chatId: Long = -(1000000000000L + groupId)
        internal set

    var photo: RemoteFile? = null
        internal set

    override var isContentProtected: Boolean = false
        internal set

    override var isKnown: Boolean = false
        internal set


    override var affinityUserId: Long = 0L
        internal set

    private var cachedMemberDefaultPermissions: GroupMemberPermissionSet? = null
    private val cachedAdminPermissions = HashMap<Long, GroupAdminPermissionSet>()
    private val cachedNonAdminMemberIds = mutableSetOf<Long>()
    private val cachedNonMemberIds = mutableSetOf<Long>()
    private var cachedCreator: GroupAdminPermissionSet? = null

    @Throws(IOException::class, RemoteApiException::class)
    suspend fun isMemberAdministrative(bot: Bot, userId: Long, invalidate: Boolean = false): Boolean {
        return getAdminPermissionSet(bot, userId, invalidate) != null
    }

    /**
     * Get the admin permission set for the given user.
     * @param bot the actor bot
     * @param userId the user id
     * @param invalidate if true, the cached permission set will be invalidated
     * @return the admin permission set for the given user, or null if the user is not an admin
     */
    @Throws(IOException::class, RemoteApiException::class)
    suspend fun getAdminPermissionSet(bot: Bot, userId: Long, invalidate: Boolean = false): GroupAdminPermissionSet? {
        if (!invalidate) {
            cachedAdminPermissions[userId]?.let {
                return it
            }
            if (cachedNonAdminMemberIds.contains(userId) || cachedNonMemberIds.contains(userId)) {
                return null
            }
            if (cachedCreator?.uid == userId) {
                return cachedCreator
            }
        }
        val chatMember = bot.getGroupMember(groupId, userId)
        val chatMemberStatus = chatMember["status"].asJsonObject
        return updateChatMemberPermissionStatus(userId, chatMemberStatus).second
    }

    @Throws(IOException::class, RemoteApiException::class)
    suspend fun isGroupMember(bot: Bot, userId: Long, invalidate: Boolean = false): Boolean {
        if (!invalidate) {
            if (cachedCreator?.uid == userId
                || cachedAdminPermissions.contains(userId)
                || cachedNonAdminMemberIds.contains(userId)
            ) {
                return true
            }
            if (cachedNonMemberIds.contains(userId)) {
                return false
            }
        }
        val chatMember = bot.getGroupMember(groupId, userId)
        val chatMemberStatus = chatMember["status"].asJsonObject
        return updateChatMemberPermissionStatus(userId, chatMemberStatus).first
    }

    internal fun updateChatMemberPermissionStatus(userId: Long, chatMemberStatus: JsonObject)
            : Pair<Boolean, GroupAdminPermissionSet?> {
        when (val statusType = chatMemberStatus["@type"].asString) {
            "chatMemberStatusCreator" -> {
                val p = GroupAdminPermissionSet.fromJsonObject(userId, chatMemberStatus)
                cachedCreator = p
                return Pair(true, p)
            }
            "chatMemberStatusAdministrator" -> {
                val p = GroupAdminPermissionSet.fromJsonObject(userId, chatMemberStatus)
                cachedAdminPermissions[userId] = p
                cachedNonAdminMemberIds.remove(userId)
                cachedNonMemberIds.remove(userId)
                return Pair(true, p)
            }
            "chatMemberStatusMember",
            "chatMemberStatusRestricted" -> {
                cachedNonMemberIds.remove(userId)
                cachedNonAdminMemberIds.add(userId)
                cachedAdminPermissions.remove(userId)
                return Pair(true, null)
            }
            "chatMemberStatusBanned",
            "chatMemberStatusLeft" -> {
                cachedAdminPermissions.remove(userId)
                cachedNonAdminMemberIds.remove(userId)
                cachedNonMemberIds.add(userId)
                return Pair(false, null)
            }
            else -> {
                throw IllegalStateException("Unknown status: $statusType")
            }
        }
    }

    override fun hashCode(): Int {
        return groupId.toInt()
    }

    override fun equals(other: Any?): Boolean {
        if (other is Group) {
            return groupId == other.groupId
        }
        return false
    }

    override fun toString(): String {
        return "Group(groupId=$groupId, name=$name, username=$username, isSuperGroup=$isSuperGroup, isKnown=$isKnown, affinityUserId=$affinityUserId)"
    }
}
