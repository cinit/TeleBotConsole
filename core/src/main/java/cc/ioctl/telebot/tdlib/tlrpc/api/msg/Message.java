package cc.ioctl.telebot.tdlib.tlrpc.api.msg;

import cc.ioctl.telebot.tdlib.obj.SessionInfo;
import cc.ioctl.telebot.tdlib.tlrpc.BaseTlRpcJsonObject;
import cc.ioctl.telebot.tdlib.tlrpc.TlRpcField;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;

public class Message extends BaseTlRpcJsonObject {

    @TlRpcField("@type")
    public static final String TYPE = "message";

    @TlRpcField("id")
    public long id;

    public long serverMsgId;

    @TlRpcField("sender_id")
    public JsonObject senderId;

    public long senderUserId;

    public SessionInfo sessionInfo;

    @TlRpcField(value = "sending_state", optional = true)
    public JsonObject sendingState;

    @TlRpcField(value = "scheduling_state", optional = true)
    public JsonObject schedulingState;

    @TlRpcField("is_outgoing")
    public boolean isOutgoing;

    @TlRpcField("is_pinned")
    public boolean isPinned;

    @TlRpcField("can_be_edited")
    public boolean canBeEdited;

    @TlRpcField("can_be_forwarded")
    public boolean canBeForwarded;

    @TlRpcField("can_be_saved")
    public boolean canBeSaved;

    @TlRpcField("can_be_deleted_only_for_self")
    public boolean canBeDeletedOnlyForSelf;

    @TlRpcField("can_be_deleted_for_all_users")
    public boolean canBeDeletedForAllUsers;

    @TlRpcField("can_get_statistics")
    public boolean canGetStatistics;

    @TlRpcField("can_get_message_thread")
    public boolean canGetMessageThread;

    @TlRpcField("can_get_viewers")
    public boolean canGetViewers;

    @TlRpcField("can_get_media_timestamp_links")
    public boolean canGetMediaTimestampLinks;

    @TlRpcField("has_timestamped_media")
    public boolean hasTimestampedMedia;

    @TlRpcField("is_channel_post")
    public boolean isChannelPost;

    @TlRpcField("contains_unread_mention")
    public boolean containsUnreadMention;

    /**
     * Point in time (Unix timestamp) when the message was sent.
     */
    @TlRpcField("date")
    public int date;

    /**
     * Point in time (Unix timestamp) when the message was last edited.
     */
    @TlRpcField("edit_date")
    public int editDate;

    @TlRpcField(value = "forward_info", optional = true)
    public JsonObject forwardInfo;

    @TlRpcField(value = "interaction_info", optional = true)
    public JsonObject interactionInfo;

    @TlRpcField("reply_in_chat_id")
    public long replyInChatId;

    @TlRpcField("reply_to_message_id")
    public long replyToMessageId;

    @TlRpcField("message_thread_id")
    public long messageThreadId;

    @TlRpcField("ttl")
    public int ttl;

    @TlRpcField("ttl_expires_in")
    public int ttlExpiresIn;

    @TlRpcField("via_bot_user_id")
    public long viaBotUserId;

    @TlRpcField("author_signature")
    public String authorSignature;

    @TlRpcField("media_album_id")
    public long mediaAlbumId;

    @TlRpcField(value = "restriction_reason", optional = true)
    public String restrictionReason;

    @TlRpcField("content")
    public JsonObject content;

    @TlRpcField(value = "reply_markup", optional = true)
    public JsonObject replyMarkup;

    public Message() {
    }

    public static Message fromJsonObject(@NotNull JsonObject obj) throws ReflectiveOperationException {
        Message message = BaseTlRpcJsonObject.fromJsonObject(Message.class, obj);
        // update several field
        if ((message.id & (1 << 20 - 1)) == 0) {
            message.serverMsgId = message.id >> 20;
        } else {
            message.serverMsgId = 0;
        }
        long chatId = obj.get("chat_id").getAsLong();
        message.sessionInfo = SessionInfo.forTDLibChatId(chatId);
        // update senderUserId
        String senderType = message.senderId.get("@type").getAsString();
        switch (senderType) {
            case "messageSenderUser":
                message.senderUserId = message.senderId.get("user_id").getAsLong();
                break;
            case "messageSenderChat":
                message.senderUserId = -SessionInfo.chatIdToChannelId(message.senderId.get("chat_id").getAsLong());
                break;
            default:
                throw new IllegalArgumentException("Unknown sender type: " + senderType);
        }
        return message;
    }

    @Override
    @NotNull
    public JsonObject toJsonObject() {
        throw new UnsupportedOperationException("not implemented");
    }
}
