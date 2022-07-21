package cc.ioctl.telebot.tdlib.obj

interface Sender {

    /**
     * The user identifier of the sender.
     */
    val userId: Long

    val name: String

    /**
     * The username of the account, without the @ prefix, may be null if the user does not have one.
     */
    val username: String?

    val isUser: Boolean

    val isKnown: Boolean

    val isAnonymous: Boolean

    val isAnonymousChannel: Boolean

    val isAnonymousAdmin: Boolean

}
