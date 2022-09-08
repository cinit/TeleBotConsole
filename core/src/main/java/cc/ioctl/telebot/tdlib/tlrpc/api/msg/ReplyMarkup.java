package cc.ioctl.telebot.tdlib.tlrpc.api.msg;

import cc.ioctl.telebot.tdlib.tlrpc.TlRpcField;
import cc.ioctl.telebot.tdlib.tlrpc.BaseTlRpcJsonObject;
import org.jetbrains.annotations.NotNull;

public abstract class ReplyMarkup extends BaseTlRpcJsonObject {

    private ReplyMarkup() {
    }

    public static class ForceReply extends ReplyMarkup {
        @TlRpcField("@type")
        public static final String TYPE = "replyMarkupForceReply";
        @TlRpcField("is_personal")
        public boolean isPersonal;
        @TlRpcField("input_field_placeholder")
        public String inputFieldPlaceholder;

        public ForceReply() {
        }

        public ForceReply(boolean isPersonal, String inputFieldPlaceholder) {
            this.isPersonal = isPersonal;
            this.inputFieldPlaceholder = inputFieldPlaceholder;
        }
    }

    public static class InlineKeyboard extends ReplyMarkup {

        public static class Button extends BaseTlRpcJsonObject {
            public abstract static class Type extends BaseTlRpcJsonObject {
                public static class Callback extends Type {
                    @TlRpcField("@type")
                    public static final String TYPE = "inlineKeyboardButtonTypeCallback";
                    /**
                     * The base64-encoded data of the callback button.
                     */
                    @TlRpcField("data")
                    public String data;

                    public Callback() {
                    }

                    public Callback(@NotNull String data) {
                        this.data = data;
                    }
                }

                public static class CallbackWithPassword extends Type {
                    @TlRpcField("@type")
                    public static final String TYPE = "inlineKeyboardButtonTypeCallbackWithPassword";
                    /**
                     * The base64-encoded data of the callback button.
                     */
                    @TlRpcField("data")
                    public String data;

                    public CallbackWithPassword() {
                    }

                    public CallbackWithPassword(@NotNull String data) {
                        this.data = data;
                    }
                }

                public static class Url extends Type {
                    @TlRpcField("@type")
                    public static final String TYPE = "inlineKeyboardButtonTypeUrl";
                    @TlRpcField("url")
                    public String url;

                    public Url() {
                    }

                    public Url(String url) {
                        this.url = url;
                    }
                }

                public static class User extends Type {
                    @TlRpcField("@type")
                    public static final String TYPE = "inlineKeyboardButtonTypeUser";
                    @TlRpcField("user")
                    public int userId;

                    public User() {
                    }

                    public User(int userId) {
                        this.userId = userId;
                    }
                }

                public static class SwitchInline extends Type {
                    @TlRpcField("@type")
                    public static final String TYPE = "inlineKeyboardButtonTypeSwitchInline";
                    @TlRpcField("query")
                    public String query;
                    @TlRpcField("in_current_chat")
                    public boolean inCurrentChat;

                    public SwitchInline() {
                    }

                    public SwitchInline(String query, boolean inCurrentChat) {
                        this.query = query;
                        this.inCurrentChat = inCurrentChat;
                    }
                }
            }

            @TlRpcField("@type")
            public static final String TYPE = "inlineKeyboardButton";
            @TlRpcField("text")
            public String text;
            @TlRpcField("type")
            public Type type;

            public Button() {
            }

            public Button(String text, Type type) {
                this.text = text;
                this.type = type;
            }
        }

        @TlRpcField("@type")
        public static final String TYPE = "replyMarkupInlineKeyboard";

        @TlRpcField("rows")
        public Button[][] rows;

        public InlineKeyboard() {
        }

        public InlineKeyboard(Button[][] rows) {
            this.rows = rows;
        }
    }

    public static class RemoveKeyboard extends ReplyMarkup {
        @TlRpcField("@type")
        public static final String TYPE = "replyMarkupRemoveKeyboard";
        @TlRpcField("is_personal")
        public boolean isPersonal;

        public RemoveKeyboard() {
        }

        public RemoveKeyboard(boolean isPersonal) {
            this.isPersonal = isPersonal;
        }
    }

}
