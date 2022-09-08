package cc.ioctl.telebot.tdlib.tlrpc.api.channel;

import cc.ioctl.telebot.tdlib.obj.SessionInfo;
import cc.ioctl.telebot.tdlib.tlrpc.BaseTlRpcJsonObject;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ChannelMemberStatusEvent {

    public long groupId;

    /**
     * Positive number for user, negative number for channel or group, no '100'.
     */
    public long userId;

    public long actorUserId;

    public long timestampSeconds;

    @Nullable
    public String inviteLink;

    public long oldInviterUserId;

    public long oldJoinDateSeconds;

    public long newInviterUserId;

    public long newJoinDateSeconds;

    @Nullable
    public MemberStatus oldStatus;

    public MemberStatus newStatus;

    private ChannelMemberStatusEvent() {
    }

    public static ChannelMemberStatusEvent fromJsonObject(@NotNull JsonObject obj) throws ReflectiveOperationException {
        ChannelMemberStatusEvent result = new ChannelMemberStatusEvent();
        BaseTlRpcJsonObject.checkTypeNonNull(obj, "updateChatMember");
        long chatId = obj.get("chat_id").getAsLong();
        if (chatId >= 0) {
            throw new IllegalArgumentException("bad chat_id: " + chatId + ", not channel");
        }
        result.groupId = SessionInfo.chatIdToGroupId(chatId);
        result.actorUserId = obj.get("actor_user_id").getAsLong();
        result.timestampSeconds = obj.get("date").getAsLong();
        if (obj.has("invite_link")) {
            result.inviteLink = obj.get("invite_link").getAsJsonObject().get("invite_link").getAsString();
        }
        JsonElement oldMemberOrNull = obj.get("old_chat_member");
        JsonObject newMember = obj.get("new_chat_member").getAsJsonObject();
        JsonObject memberIdObj = newMember.get("member_id").getAsJsonObject();
        String memberType = memberIdObj.get("@type").getAsString();
        if ("messageSenderUser".equals(memberType)) {
            result.userId = memberIdObj.get("user_id").getAsLong();
        } else if ("messageSenderChat".equals(memberType)) {
            long senderChatId = memberIdObj.get("chat_id").getAsLong();
            long senderChannelId = SessionInfo.chatIdToGroupId(senderChatId);
            result.userId = -senderChannelId;
        } else {
            throw new IllegalArgumentException("bad member type: " + memberType);
        }
        if (oldMemberOrNull != null) {
            JsonObject oldMember = oldMemberOrNull.getAsJsonObject();
            result.oldInviterUserId = oldMember.get("inviter_user_id").getAsLong();
            result.oldJoinDateSeconds = oldMember.get("joined_chat_date").getAsLong();
            result.oldStatus = MemberStatus.fromJsonObject(oldMember.get("status").getAsJsonObject());
        }
        result.newInviterUserId = newMember.get("inviter_user_id").getAsLong();
        result.newJoinDateSeconds = newMember.get("joined_chat_date").getAsLong();
        result.newStatus = MemberStatus.fromJsonObject(newMember.get("status").getAsJsonObject());
        return result;
    }
}
