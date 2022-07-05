package cc.ioctl.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class Log {

    private Log() {
        throw new AssertionError("This class is not meant to be instantiated");
    }

    public interface LogHandler {
        void onLogMessage(int level, @NotNull String tag, @NotNull String message, @Nullable Throwable throwable);
    }

    private static LogHandler sLogHandler = null;

    public static final int VERBOSE = 2;

    public static final int DEBUG = 3;

    public static final int INFO = 4;

    public static final int WARN = 5;

    public static final int ERROR = 6;

    public static final int ASSERT = 7;

    public static void log(int level, @NotNull String tag, @NotNull String message, @Nullable Throwable throwable) {
        LogHandler h = sLogHandler;
        if (h != null) {
            h.onLogMessage(level, tag, message, throwable);
        }
    }

    public static void setLogHandler(@Nullable LogHandler logHandler) {
        sLogHandler = logHandler;
    }

    @Nullable
    public static LogHandler getLogHandler() {
        return sLogHandler;
    }

    public static void v(@NotNull String tag, @NotNull String message) {
        log(VERBOSE, tag, message, null);
    }

    public static void v(@NotNull String tag, @NotNull String message, @Nullable Throwable throwable) {
        log(VERBOSE, tag, message, throwable);
    }

    public static void d(@NotNull String tag, @NotNull String message) {
        log(DEBUG, tag, message, null);
    }

    public static void d(@NotNull String tag, @NotNull String message, @Nullable Throwable throwable) {
        log(DEBUG, tag, message, throwable);
    }

    public static void i(@NotNull String tag, @NotNull String message) {
        log(INFO, tag, message, null);
    }

    public static void i(@NotNull String tag, @NotNull String message, @Nullable Throwable throwable) {
        log(INFO, tag, message, throwable);
    }

    public static void w(@NotNull String tag, @NotNull String message) {
        log(WARN, tag, message, null);
    }

    public static void w(@NotNull String tag, @NotNull String message, @Nullable Throwable throwable) {
        log(WARN, tag, message, throwable);
    }

    public static void e(@NotNull String tag, @NotNull String message) {
        log(ERROR, tag, message, null);
    }

    public static void e(@NotNull String tag, @NotNull String message, @Nullable Throwable throwable) {
        log(ERROR, tag, message, throwable);
    }
}
