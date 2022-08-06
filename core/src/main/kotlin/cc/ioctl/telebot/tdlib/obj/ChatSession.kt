package cc.ioctl.telebot.tdlib.obj

interface ChatSession {

    val name: String

    val chatId: Long

    val isContentProtected: Boolean

}
