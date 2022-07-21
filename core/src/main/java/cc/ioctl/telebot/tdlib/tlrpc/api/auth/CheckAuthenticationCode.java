package cc.ioctl.telebot.tdlib.tlrpc.api.auth;

import cc.ioctl.telebot.tdlib.tlrpc.TlRpcField;
import cc.ioctl.telebot.tdlib.tlrpc.TlRpcJsonObject;

public class CheckAuthenticationCode extends TlRpcJsonObject {

    @TlRpcField("@type")
    public static final String TYPE = "checkAuthenticationCode";

    @TlRpcField("code")
    public String code;

    public CheckAuthenticationCode() {
        super();
    }

    public CheckAuthenticationCode(String code) {
        super();
        this.code = code;
    }

}
