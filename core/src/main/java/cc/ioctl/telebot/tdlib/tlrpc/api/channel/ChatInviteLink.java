package cc.ioctl.telebot.tdlib.tlrpc.api.channel;

import cc.ioctl.telebot.tdlib.tlrpc.BaseTlRpcJsonObject;
import cc.ioctl.telebot.tdlib.tlrpc.TlRpcField;
import org.jetbrains.annotations.Nullable;

public class ChatInviteLink extends BaseTlRpcJsonObject {

    @TlRpcField("@type")
    public static final String TYPE = "chatInviteLink";

    @TlRpcField("invite_link")
    public String inviteLink;

    @Nullable
    @TlRpcField("name")
    public String name;

    @TlRpcField("creator_user_id")
    public long creatorUserId;

    @TlRpcField("date")
    public long creationTimeSeconds;

    @TlRpcField("edit_date")
    public long editTimeSeconds;

    @TlRpcField("member_limit")
    public int memberLimit;

    @TlRpcField("pending_join_request_count")
    public int pendingJoinRequestCount;

    @TlRpcField("creates_join_request")
    public boolean isApprovalRequired;

    @TlRpcField("is_primary")
    public boolean isPrimary;

    @TlRpcField("is_revoked")
    public boolean isRevoked;

}
