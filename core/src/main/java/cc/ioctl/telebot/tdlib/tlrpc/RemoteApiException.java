package cc.ioctl.telebot.tdlib.tlrpc;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RemoteApiException extends Exception {

    private final int code;
    private final String message;

    public RemoteApiException(int code, @NotNull String message, @Nullable Throwable cause) {
        super(message, cause);
        this.code = code;
        this.message = message;
    }

    public RemoteApiException(int code, @NotNull String message) {
        this(code, message, null);
    }

    @Override
    public String getMessage() {
        return message;
    }

    public int getCode() {
        return code;
    }

    @Override
    public String toString() {
        return getClass().getName() + ": " + code + ": " + message;
    }
}
