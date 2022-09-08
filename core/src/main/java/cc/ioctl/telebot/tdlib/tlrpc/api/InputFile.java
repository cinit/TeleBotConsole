package cc.ioctl.telebot.tdlib.tlrpc.api;

import cc.ioctl.telebot.tdlib.tlrpc.BaseTlRpcJsonObject;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Objects;

public abstract class InputFile extends BaseTlRpcJsonObject {

    @NotNull
    public static JsonObject fromLocalFileToJsonObject(@NotNull File file) {
        Objects.requireNonNull(file, "file must not be null");
        if (!file.exists() || !file.isFile()) {
            throw new IllegalArgumentException("File does not exist or is not a file: " + file.getAbsolutePath());
        }
        if (!file.canRead()) {
            throw new IllegalArgumentException("File is not readable: " + file.getAbsolutePath());
        }
        JsonObject result = new JsonObject();
        result.addProperty("@type", "inputFileLocal");
        result.addProperty("path", file.getAbsolutePath());
        return result;
    }
}
