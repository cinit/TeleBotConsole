package cc.ioctl.telebot.tdlib.obj

class UnknownChat(
    override val chatId: Long
) : ChatSession {
    override val name: String = "UnknownChat-$chatId"

    override val isContentProtected: Boolean = false
}
