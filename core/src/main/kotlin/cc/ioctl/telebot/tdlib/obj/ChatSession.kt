package cc.ioctl.telebot.tdlib.obj

interface ChatSession : ISessionDescriptor {

    val name: String

    override val sessionInfo: SessionInfo

    val isContentProtected: Boolean

}
