package cc.ioctl.util;

import org.jetbrains.annotations.Nullable;

public class TextUtils {

    private TextUtils() {
        throw new AssertionError("This class is not meant to be instantiated");
    }

    public static boolean isEmpty(@Nullable String text) {
        return text == null || text.isEmpty();
    }
}
