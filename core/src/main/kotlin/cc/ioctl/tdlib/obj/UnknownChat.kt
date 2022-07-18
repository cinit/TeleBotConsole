package cc.ioctl.tdlib.obj

class UnknownChat(
    override val chatId: Long
) : ChatSession {
    override val name: String = "UnknownChat-$chatId"
}
