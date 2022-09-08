package cc.ioctl.telebot.tdlib.obj

import cc.ioctl.telebot.tdlib.RobotServer

/**
 * An unencrypted private chat with a user.
 */
class PrivateChatSession internal constructor(
    private val server: RobotServer,
    val userId: Long,
) : ChatSession {

    val user: User get() = server.getOrNewUser(userId)

    override val sessionInfo = SessionInfo(userId, 0)

    override val name: String get() = user.name

    override val isContentProtected: Boolean = false

}
