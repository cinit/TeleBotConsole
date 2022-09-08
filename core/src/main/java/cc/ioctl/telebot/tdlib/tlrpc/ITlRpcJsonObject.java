package cc.ioctl.telebot.tdlib.tlrpc;

import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;

public interface ITlRpcJsonObject {

    @NotNull
    JsonObject toJsonObject();

}
