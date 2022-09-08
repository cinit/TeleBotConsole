package cc.ioctl.telebot.tdlib.tlrpc.api.query;

import cc.ioctl.telebot.tdlib.obj.SessionInfo;
import cc.ioctl.telebot.tdlib.tlrpc.BaseTlRpcJsonObject;
import cc.ioctl.telebot.util.Base64;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class CallbackQuery {

    public final long queryId;

    public final long senderUserId;

    public final SessionInfo sessionInfo;

    public final long messageId;

    public final long chatInstance;

    @Nullable
    private final String mGameShortName;

    @Nullable
    private final byte[] mPayload;

    @Nullable
    private final String mPassword;

    public CallbackQuery(long queryId, long senderUserId, SessionInfo sessionInfo, long messageId, long chatInstance,
                         @Nullable String gameShortName, @Nullable byte[] payload, @Nullable String password) {
        this.queryId = queryId;
        this.senderUserId = senderUserId;
        this.sessionInfo = sessionInfo;
        this.messageId = messageId;
        this.chatInstance = chatInstance;
        mGameShortName = gameShortName;
        mPayload = payload;
        mPassword = password;
    }

    @NotNull
    public String getPassword() {
        if (mPassword == null) {
            throw new IllegalStateException("payload type is not password");
        }
        return mPassword;
    }

    @NotNull
    public byte[] getPayloadData() {
        if (mPayload == null) {
            throw new IllegalStateException("payload type is not data");
        }
        return mPayload;
    }

    @NotNull
    public byte[] getPayloadDataAsString(@NotNull Charset charset) {
        if (mPayload == null) {
            throw new IllegalStateException("payload type is not data");
        }
        return new String(mPayload, charset).getBytes();
    }

    @NotNull
    public byte[] getPayloadDataAsString() {
        return getPayloadDataAsString(StandardCharsets.UTF_8);
    }

    @NotNull
    public String getGameShortName() {
        if (mGameShortName == null) {
            throw new IllegalStateException("payload type is not game");
        }
        return mGameShortName;
    }

    public static CallbackQuery fromJsonObject(@NotNull JsonObject obj) {
        BaseTlRpcJsonObject.checkTypeNonNull(obj, "updateNewCallbackQuery");
        long queryId = obj.get("id").getAsLong();
        long senderUserId = obj.get("sender_user_id").getAsLong();
        SessionInfo sessionInfo = SessionInfo.forTDLibChatId(obj.get("chat_id").getAsLong());
        long messageId = obj.get("message_id").getAsLong();
        long chatInstance = obj.get("chat_instance").getAsLong();
        String payloadType = obj.get("payload").getAsJsonObject().get("@type").getAsString();
        switch (payloadType) {
            case "callbackQueryPayloadData":
                return new CallbackQuery(queryId, senderUserId, sessionInfo, messageId, chatInstance, null,
                        Base64.decode(obj.get("payload").getAsJsonObject().get("data").getAsString(), Base64.DEFAULT), null);
            case "callbackQueryPayloadGame":
                return new CallbackQuery(queryId, senderUserId, sessionInfo, messageId, chatInstance,
                        obj.get("payload").getAsJsonObject().get("game_short_name").getAsString(), null, null);
            case "callbackQueryPayloadPassword":
                return new CallbackQuery(queryId, senderUserId, sessionInfo, messageId, chatInstance, null,
                        Base64.decode(obj.get("payload").getAsJsonObject().get("data").getAsString(), Base64.DEFAULT),
                        obj.get("payload").getAsJsonObject().get("password").getAsString());
            default:
                throw new IllegalArgumentException("unknown payload type: " + payloadType);
        }
    }

}
