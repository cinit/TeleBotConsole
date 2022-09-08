package cc.ioctl.telebot.tdlib.tlrpc.api.channel;

import cc.ioctl.telebot.tdlib.tlrpc.BaseTlRpcJsonObject;
import cc.ioctl.telebot.tdlib.tlrpc.TlRpcField;

public class ChatPermissions extends BaseTlRpcJsonObject {

    @TlRpcField("@type")
    public static final String TYPE = "chatPermissions";

    @TlRpcField("can_send_messages")
    public boolean canSendMessages;

    @TlRpcField("can_send_media_messages")
    public boolean canSendMediaMessages;

    @TlRpcField("can_send_polls")
    public boolean canSendPolls;

    @TlRpcField("can_send_other_messages")
    public boolean canSendOtherMessages;

    @TlRpcField("can_add_web_page_previews")
    public boolean canAddWebPagePreviews;

    @TlRpcField("can_change_info")
    public boolean canChangeInfo;

    @TlRpcField("can_invite_users")
    public boolean canInviteUsers;

    @TlRpcField("can_pin_messages")
    public boolean canPinMessages;

}
