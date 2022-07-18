package cc.ioctl.tdlib.obj

import cc.ioctl.tdlib.RobotServer

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

    override var isKnown: Boolean = false
        internal set


    override var affinityUserId: Long = 0L
        internal set

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
