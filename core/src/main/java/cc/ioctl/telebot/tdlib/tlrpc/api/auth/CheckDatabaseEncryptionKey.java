package cc.ioctl.telebot.tdlib.tlrpc.api.auth;

import cc.ioctl.telebot.tdlib.tlrpc.TlRpcField;
import cc.ioctl.telebot.tdlib.tlrpc.TlRpcJsonObject;
import org.jetbrains.annotations.NotNull;

public class CheckDatabaseEncryptionKey extends TlRpcJsonObject {

    @TlRpcField("@type")
    public static final String TYPE = "checkDatabaseEncryptionKey";

    @TlRpcField("encryption_key")
    public String encryptionKey;

    public CheckDatabaseEncryptionKey() {
        super();
    }

    public CheckDatabaseEncryptionKey(@NotNull String encryptionKey) {
        super();
        this.encryptionKey = encryptionKey;
    }

}
