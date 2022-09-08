package cc.ioctl.telebot.tdlib.tlrpc.api.channel;

import cc.ioctl.telebot.tdlib.tlrpc.BaseTlRpcJsonObject;
import cc.ioctl.telebot.tdlib.tlrpc.TlRpcField;
import org.jetbrains.annotations.Nullable;

public class ChatJoinRequest extends BaseTlRpcJsonObject {

    @TlRpcField("@type")
    public static final String TYPE = "chatJoinRequest";

    @TlRpcField("user_id")
    public long userId;

    @TlRpcField("date")
    public long timestampSeconds;

    @Nullable
    @TlRpcField(value = "bio", ifEmptyStringNull = true, optional = true)
    public String bio;

}
