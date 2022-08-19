package cc.ioctl.telebot.tdlib.tlrpc.api.msg

import cc.ioctl.telebot.tdlib.tlrpc.api.msg.ReplyMarkup.InlineKeyboard
import cc.ioctl.telebot.util.Base64

fun inlineKeyboardCallbackButton(text: String, data: ByteArray): InlineKeyboard.Button {
    return InlineKeyboard.Button(text, InlineKeyboard.Button.Type.Callback(Base64.encodeToString(data, Base64.NO_WRAP)))
}

fun inlineKeyboardCallbackButton(text: String, dataForString: String): InlineKeyboard.Button {
    return inlineKeyboardCallbackButton(text, dataForString.toByteArray(Charsets.UTF_8))
}
