package cc.ioctl.tdlib.tlrpc.api.msg;

import cc.ioctl.tdlib.tlrpc.TlRpcField;
import cc.ioctl.tdlib.tlrpc.TlRpcJsonObject;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;

public class FormattedText extends TlRpcJsonObject {

    private static final TextEntity[] EMPTY_TEXT_ENTITY_ARRAY = new TextEntity[0];

    public static class TextEntity extends TlRpcJsonObject {
        @TlRpcField("@type")
        public static final String TYPE = "textEntity";
        @TlRpcField("offset")
        public int offset;
        @TlRpcField("length")
        public int length;
        @TlRpcField("type")
        public JsonObject entityType;

        public TextEntity(int offset, int length, JsonObject entityType) {
            this.offset = offset;
            this.length = length;
            this.entityType = entityType;
        }

        public TextEntity() {
        }
    }

    @TlRpcField("@type")
    public static final String TYPE = "formattedText";

    @TlRpcField("text")
    public String text;

    @TlRpcField("entities")
    public TextEntity[] entities;

    public FormattedText(@NotNull String text, @NotNull TextEntity[] entities) {
        this.text = text;
        this.entities = entities;
    }

    public FormattedText() {
    }

    public static FormattedText forPlainText(@NotNull String text) {
        return new FormattedText(text, EMPTY_TEXT_ENTITY_ARRAY);
    }
}
