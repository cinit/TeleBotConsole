package cc.ioctl.startup;

import cc.ioctl.cli.Console;
import cc.ioctl.intern.NativeBridge;
import cc.ioctl.tdlib.RobotServer;
import cc.ioctl.tgbot.startup.ServerInit;
import cc.ioctl.util.*;
import com.tencent.mmkv.MMKV;
import com.tencent.mmkv.MMKVLogLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class StartupRoutine {

    private static final String TAG = "StartupRoutine";

    private StartupRoutine() {
        throw new AssertionError("This class is not meant to be instantiated");
    }

    static void startup(@NotNull File workingDir,
                        @Nullable String nativeLibPathOverride,
                        @Nullable String socketProxy) {
        // set working directory
        System.out.println("Working directory: " + workingDir.getAbsolutePath());
        System.setProperty("user.dir", workingDir.getAbsolutePath());
        // check file.encoding is UTF-8
        if (Charset.defaultCharset() != StandardCharsets.UTF_8) {
            System.out.println("ERROR: file.encoding is not UTF-8");
            try {
                System.setProperty("file.encoding", "UTF-8");
                Field charset = Charset.class.getDeclaredField("defaultCharset");
                charset.setAccessible(true);
                charset.set(null, null);
            } catch (ReflectiveOperationException ignored) {
            }
            if (Charset.defaultCharset() != StandardCharsets.UTF_8) {
                System.out.println("ERROR: failed to set file.encoding to UTF-8");
                System.out.println("Please add '-Dfile.encoding=UTF-8' to your JVM arguments for the bot to work properly.");
                System.exit(1);
            }
        }
        if (!new File("/proc/self/exe").exists()) {
            System.err.println("/proc/self/exe does not exist");
            System.err.println("Only Linux-based systems are supported");
            System.exit(1);
            return;
        }
        if (!TextUtils.isEmpty(nativeLibPathOverride)) {
            System.out.println("Loading overridden native library: " + nativeLibPathOverride);
            System.load(nativeLibPathOverride);
        } else {
            System.err.println("No native library path found");
            System.err.println("Use --native-lib=<path> to specify the path");
            System.err.println("The native library is too big to be embedded in the jar");
            System.exit(1);
            int arch = NativeUtils.getCurrentProcessArch();
            String abiName = NativeUtils.archIntToAndroidLibArch(arch);
            String resPath = "lib/" + abiName + "/libtdjni.so";
            InputStream is = StartupRoutine.class.getClassLoader().getResourceAsStream(resPath);
            if (is == null) {
                System.err.println("Could not find native library in assets: " + resPath);
                System.exit(1);
                return;
            }
            File nativeLibDir = new File(workingDir, "lib" + File.separator + abiName);
            if (!nativeLibDir.exists() && !nativeLibDir.mkdirs()) {
                System.err.println("Could not create native library directory: " + nativeLibDir.getAbsolutePath());
                System.exit(1);
                return;
            }
            byte[] data = null;
            try {
                data = IoUtils.readFully(is);
            } catch (IOException e) {
                IoUtils.unsafeThrow(e);
            }
            assert data != null;
            File nativeLib = new File(nativeLibDir, "libtdjni.so");
            if (nativeLib.exists() && nativeLib.length() == data.length) {
                System.out.println("Native library already exists: " + nativeLib.getAbsolutePath());
            } else {
                System.out.println("Writing native library to: " + nativeLib.getAbsolutePath());
                try {
                    IoUtils.writeFile(nativeLib, data);
                } catch (IOException e) {
                    IoUtils.unsafeThrow(e);
                }
            }
            System.load(nativeLib.getAbsolutePath());
        }
        System.out.println("Process ID: " + OsUtils.getPid());
        NativeBridge.nativeInit(workingDir.getAbsolutePath());
        // initialize mmkv
        File mmkvDir = new File(workingDir, "mmkv");
        File mmkvCacheDir = new File(mmkvDir, ".tmp");
        if (!mmkvDir.exists() && !mmkvDir.mkdirs()) {
            throw new RuntimeException("Could not create mmkv directory: " + mmkvDir.getAbsolutePath());
        }
        if (!mmkvCacheDir.exists() && !mmkvCacheDir.mkdirs()) {
            throw new RuntimeException("Could not create mmkv cache directory: " + mmkvCacheDir.getAbsolutePath());
        }
        NativeBridge.nativeInit(workingDir.getAbsolutePath());
        MMKV.initialize(false, mmkvDir.getAbsolutePath(), mmkvCacheDir.getAbsolutePath(), MMKVLogLevel.LevelInfo);
        // initialize logger
        Console console = Console.getInstance();
        Log.setLogHandler(console);
        Log.d("StartupRoutine", "StartupRoutine started");
        // initialize robot server
        RobotServer server = RobotServer.createInstance(workingDir);
        ServerInit.runServer(server, socketProxy);
    }
}
