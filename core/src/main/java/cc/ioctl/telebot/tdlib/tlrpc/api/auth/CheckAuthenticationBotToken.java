package cc.ioctl.telebot.tdlib.tlrpc.api.auth;

import cc.ioctl.telebot.tdlib.tlrpc.TlRpcField;
import cc.ioctl.telebot.tdlib.tlrpc.TlRpcJsonObject;

public class CheckAuthenticationBotToken extends TlRpcJsonObject {

    @TlRpcField("@type")
    public static final String TYPE = "checkAuthenticationBotToken";

    @TlRpcField("token")
    public String botToken;

    public CheckAuthenticationBotToken() {
        super();
    }

    public CheckAuthenticationBotToken(String botToken) {
        super();
        this.botToken = botToken;
    }

}
