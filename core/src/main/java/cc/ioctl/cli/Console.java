package cc.ioctl.cli;

import cc.ioctl.util.Log;
import cc.ioctl.util.TextUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class Console implements Log.LogHandler {
    private static Console sInstance = null;

    private static class ConsoleInfo {

        public boolean isColorEnabled = false;
        public boolean isVt100 = false;
        public int height = 0;
        public int width = 0;

        public ConsoleInfo() {
        }

        public ConsoleInfo(boolean isColorEnabled, boolean isVt100, int width, int height) {
            this.isColorEnabled = isColorEnabled;
            this.isVt100 = isVt100;
            this.width = width;
            this.height = height;
        }
    }

    private final ConsoleInfo mConsoleInfo;
    private String mStatusText = "";
    private String mTitle = "";

    @NotNull
    public static synchronized Console getInstance() {
        if (sInstance == null) {
            sInstance = new Console();
        }
        return sInstance;
    }

    private Console() {
        mConsoleInfo = nGetConsoleInfo();
    }

    private native ConsoleInfo nGetConsoleInfo();

    private native void nUpdateStatusText(@Nullable String text);

    private native void nUpdateTitleText(@Nullable String title);

    private native void nPrintLine(@NotNull String text);

    private native void nLogMessage(int level, @NotNull String tag, @NotNull String message, @Nullable String details);

    @NotNull
    private native String nPromptInputText(@Nullable String instruction, @Nullable String prompt,
                                           @Nullable String defaultValue, boolean echo);

    public boolean isVt100() {
        return mConsoleInfo.isVt100;
    }

    public boolean isColorEnabled() {
        return mConsoleInfo.isColorEnabled;
    }

    public int getHeight() {
        return mConsoleInfo.height;
    }

    public int getWidth() {
        return mConsoleInfo.width;
    }

    /**
     * Prints a line of text to the console.
     * For logging purposes, use {@link cc.ioctl.util.Log} instead.
     *
     * @param text The text to print.
     */
    public void printLine(@NotNull String text) {
        Objects.requireNonNull(text, "text");
        nPrintLine(text);
    }

    public void setStatusText(@Nullable String text) {
        if (text == null) {
            text = "";
        }
        mStatusText = text;
        nUpdateStatusText(text);
    }

    public String getStatusText() {
        return mStatusText;
    }

    public void setTitle(@Nullable String text) {
        if (text == null) {
            text = "";
        }
        mTitle = text;
        nUpdateTitleText(text);
    }

    public String getTitle() {
        return mTitle;
    }

    @NotNull
    public String promptInputText(@Nullable String instruction, @Nullable String prompt,
                                  @Nullable String defaultValue, boolean echo) {
        if (TextUtils.isEmpty(instruction)) {
            instruction = null;
        }
        if (TextUtils.isEmpty(prompt)) {
            prompt = null;
        }
        if (TextUtils.isEmpty(defaultValue)) {
            defaultValue = null;
        }
        return nPromptInputText(instruction, prompt, defaultValue, echo);
    }

    @Override
    public void onLogMessage(int level, @NotNull String tag, @NotNull String message, @Nullable Throwable throwable) {
        Objects.requireNonNull(tag, "tag");
        Objects.requireNonNull(message, "message");
        String detail = null;
        if (throwable != null) {
            detail = Log.getStackTraceString(throwable);
        }
        nLogMessage(level, tag, message, detail);
    }
}
