package cc.ioctl.telebot.tdlib.obj

import cc.ioctl.telebot.tdlib.RobotServer

/**
 * A non-local user (i.e. a user or bot that is not yours).
 * Note that the constructor of this class is internal and is not meant to be used by clients.
 * Do not modify fields in this class directly unless you know what you are doing.
 * Do not allocate instances directly. Use [cc.ioctl.tdlib.intern.NonLocalObjectCachePool.getOrCreateUser] instead.
 */
class User internal constructor(
    val server: RobotServer,
    override val userId: Long
) : Account(), ChatSession, Sender, IKnowability, IAffinity {

    override val sessionInfo = SessionInfo(userId, 0)

    override var isKnown: Boolean = false
        internal set

    override var username: String? = null
        internal set

    override var firstName: String = userId.toString()
        internal set

    override var lastName: String? = null
        internal set

    override var phoneNumber: String? = null
        internal set

    var languageCode: String? = null
        internal set

    override var bio: String? = null
        internal set

    var photo: RemoteFile? = null
        internal set

    override var isBot: Boolean = false
        internal set

    override var isDeletedAccount: Boolean = false
        internal set

    override var affinityUserId: Long = 0L
        internal set

    var defaultChatId: Long = 0L
        internal set

    override val isUser: Boolean = true

    override val isContentProtected = false

    override fun hashCode(): Int {
        return userId.toInt()
    }

    override fun toString(): String {
        val u = username
        return if (u != null) {
            "$name (@$u, $userId)"
        } else {
            "$name ($userId)"
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as User
        if (userId != other.userId) return false
        return true
    }

}
