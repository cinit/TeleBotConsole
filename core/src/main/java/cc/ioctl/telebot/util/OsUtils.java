package cc.ioctl.telebot.util;

public class OsUtils {

    private OsUtils() {
        throw new AssertionError("This class is not meant to be instantiated");
    }

    public static native int getPid();

}
