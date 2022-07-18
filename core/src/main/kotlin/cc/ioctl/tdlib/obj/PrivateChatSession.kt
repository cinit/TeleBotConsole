package cc.ioctl.tdlib.obj

import cc.ioctl.tdlib.RobotServer

/**
 * An unencrypted private chat with a user.
 */
class PrivateChatSession internal constructor(
    private val server: RobotServer,
    override val chatId: Long,
    val userId: Long,
) : ChatSession {

    val user: User get() = server.getOrNewUser(userId)

    override val name: String get() = user.name

}
