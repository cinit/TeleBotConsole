package cc.ioctl.telebot.tdlib.obj

import cc.ioctl.telebot.tdlib.RobotServer
import cc.ioctl.telebot.tdlib.tlrpc.RemoteApiException
import java.io.IOException

class Group internal constructor(
    val server: RobotServer, val groupId: Long
) : ChatSession, IKnowability, IAffinity {

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

    private val cachedAdminIds = mutableSetOf<Long>()
    private val cachedNonAdminIds = mutableSetOf<Long>()
    private var cachedCreatorId = 0L

    @Throws(IOException::class, RemoteApiException::class)
    suspend fun isMemberHasAdminRight(bot: Bot, userId: Long, invalidate: Boolean = false): Boolean {
        if (!invalidate) {
            if (cachedAdminIds.contains(userId)) {
                return true
            }
            if (cachedNonAdminIds.contains(userId)) {
                return false
            }
            if (cachedCreatorId == userId) {
                return true
            }
        }
        val chatMember = bot.getGroupMember(groupId, userId)
        val status = chatMember.getAsJsonObject("status")["@type"].asString
        when (status) {
            "chatMemberStatusCreator" -> {
                cachedCreatorId = userId
                return true
            }
            "chatMemberStatusAdministrator" -> {
                cachedAdminIds.add(userId)
                cachedNonAdminIds.remove(userId)
                return true
            }
            "chatMemberStatusMember",
            "chatMemberStatusRestricted" -> {
                cachedNonAdminIds.add(userId)
                cachedAdminIds.remove(userId)
                return false
            }
            "chatMemberStatusBanned",
            "chatMemberStatusLeft" -> {
                cachedNonAdminIds.add(userId)
                return false
            }
            else -> {
                throw IllegalStateException("Unknown status: $status")
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
