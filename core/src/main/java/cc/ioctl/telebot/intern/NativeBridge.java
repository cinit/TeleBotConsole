package cc.ioctl.telebot.intern;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class NativeBridge {

    private NativeBridge() {
        throw new AssertionError("This class is not meant to be instantiated");
    }

    private static final Object sLock = new Object();

    public static native void nativeInit(@NotNull String workingDirPath);

    @Nullable
    private static native String nativeTDLibPollEventUnlocked(int timeout);

    @NotNull
    public static native String nativeTDLibExecuteSynchronized(@NotNull String request);

    public static native void nativeTDLibExecuteAsync(int tdClientIndex, @NotNull String request);

    public static native int nativeTDLibCreateClient();

    /**
     * Poll for an event from the TDLib client.
     *
     * @param timeout the timeout in milliseconds, 0 for no wait.
     * @return the event, or null if no event was available.
     */
    @Nullable
    public static String nativeTDLibPollEvent(int timeout) {
        if (timeout < 0) {
            timeout = 0;
        }
        synchronized (sLock) {
            return nativeTDLibPollEventUnlocked(timeout);
        }
    }
}
