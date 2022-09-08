package cc.ioctl.telebot.tdlib.tlrpc.api.channel;

import cc.ioctl.telebot.tdlib.tlrpc.BaseTlRpcJsonObject;
import cc.ioctl.telebot.tdlib.tlrpc.TlRpcField;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class MemberStatus extends BaseTlRpcJsonObject {

    private MemberStatus() {
    }

    public static class Administrator extends MemberStatus {

        @TlRpcField("@type")
        public static final String TYPE = "chatMemberStatusAdministrator";

        @Nullable
        @TlRpcField(value = "custom_title", ifEmptyStringNull = true)
        public String customTitle;

        @TlRpcField("can_be_edited")
        public boolean canBeEdited;

        @TlRpcField("can_manage_chat")
        public boolean canManageChat;

        @TlRpcField("can_change_info")
        public boolean canChangeInfo;

        // channel only
        @TlRpcField(value = "can_post_messages", optional = true)
        public boolean canPostMessages = false;

        // channel only
        @TlRpcField("can_edit_messages")
        public boolean canEditMessages;

        @TlRpcField("can_delete_messages")
        public boolean canDeleteMessages;

        @TlRpcField("can_invite_users")
        public boolean canInviteUsers;

        @TlRpcField("can_restrict_members")
        public boolean canRestrictMembers;

        @TlRpcField(value = "can_pin_messages", optional = true)
        public boolean canPinMessages = false;

        @TlRpcField("can_promote_members")
        public boolean canPromoteMembers;

        @TlRpcField("can_manage_video_chats")
        public boolean canManageVideoChats;

        @TlRpcField(value = "is_anonymous", optional = true)
        public boolean isAnonymous = false;

    }

    public static class Creator extends MemberStatus {

        @TlRpcField("@type")
        public static final String TYPE = "chatMemberStatusCreator";

        @Nullable
        @TlRpcField(value = "custom_title", ifEmptyStringNull = true)
        public String customTitle;

        @TlRpcField(value = "is_anonymous", optional = true)
        public boolean isAnonymous = false;

        @TlRpcField("is_member")
        public boolean isMember;

    }


    public static class Left extends MemberStatus {

        @TlRpcField("@type")
        public static final String TYPE = "chatMemberStatusLeft";

    }

    public static class Member extends MemberStatus {

        @TlRpcField("@type")
        public static final String TYPE = "chatMemberStatusMember";

    }

    public static class Restricted extends MemberStatus {

        @TlRpcField("@type")
        public static final String TYPE = "chatMemberStatusRestricted";

        @TlRpcField("restricted_until_date")
        public long restrictedUntilDate;

        @TlRpcField("permissions")
        public ChatPermissions permissions;

    }

    public static class Banned extends MemberStatus {

        @TlRpcField("@type")
        public static final String TYPE = "chatMemberStatusBanned";

        @TlRpcField("banned_until_date")
        public long bannedUntilDate;

    }

    @NotNull
    public static MemberStatus fromJsonObject(@NotNull JsonObject jsonObj) {
        String type = jsonObj.get("@type").getAsString();
        if (type == null) {
            throw new IllegalArgumentException("type is null");
        }
        switch (type) {
            case Administrator.TYPE:
                return new Administrator();
            case Creator.TYPE:
                return new Creator();
            case Left.TYPE:
                return new Left();
            case Member.TYPE:
                return new Member();
            case Restricted.TYPE:
                return new Restricted();
            case Banned.TYPE:
                return new Banned();
            default:
                throw new IllegalArgumentException("Unknown type: " + type);
        }
    }

}
