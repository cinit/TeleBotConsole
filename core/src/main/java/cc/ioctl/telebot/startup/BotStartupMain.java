package cc.ioctl.telebot.startup;

import cc.ioctl.telebot.util.TextUtils;

import java.io.File;
import java.util.HashMap;

public class BotStartupMain {

    private BotStartupMain() {
        throw new AssertionError("This class is not meant to be instantiated");
    }

    public static void main(String[] args) {
        if (args.length == 0 || "--help".equalsIgnoreCase(args[0])) {
            printUsage();
            return;
        }
        HashMap<String, String> options = new HashMap<>(4);
        for (String arg : args) {
            if (arg.startsWith("-")) {
                int index = arg.indexOf('=');
                if (index == -1) {
                    options.put(arg, null);
                } else {
                    options.put(arg.substring(0, index), arg.substring(index + 1));
                }
            } else {
                System.out.println("Invalid option: " + arg);
                System.out.println("See --help for usage");
                System.exit(1);
                return;
            }
        }
        String workingDirPath = options.remove("--dir");
        String proxy = options.remove("--proxy");
        String nativeLibPath = options.remove("--native-lib");
        if (!options.isEmpty()) {
            System.out.println("Invalid option: " + options.keySet().iterator().next());
            System.out.println("See --help for usage");
            System.exit(1);
            return;
        }
        if (TextUtils.isEmpty(workingDirPath)) {
            System.out.println("--dir is required");
            System.out.println("See --help for usage");
            System.exit(1);
            return;
        }
        File workingDir = new File(workingDirPath).getAbsoluteFile();
        if (!workingDir.exists() || !workingDir.isDirectory()) {
            System.out.println("Working directory is not a directory or does not exist: " + workingDirPath);
            System.exit(1);
            return;
        }
        if (!workingDir.canWrite()) {
            System.out.println("Working directory is not writable: " + workingDirPath);
            System.exit(1);
            return;
        }
        StartupRoutine.startup(workingDir, nativeLibPath, proxy);
    }

    private static void printUsage() {
        System.out.println("Usage: java -jar <jar file> [OPTIONS...]");
        System.out.println("Mandatory options:");
        System.out.println("--dir=<working directory>");
        System.out.println("    The working directory of the bot, must be a writable existing directory.");
        System.out.println("    Bot configuration files and SESSION TOKENS will be stored in this directory.");
        System.out.println("    MUST BE A SECURE DIRECTORY, OTHERWISE THE SESSION TOKENS WILL BE ACCESSIBLE TO OTHERS.");
        System.out.println("Supported options:");
        System.out.println("--help");
        System.out.println("    Prints this help message and exits.");
        System.out.println("--native-lib=<path>");
        System.out.println("    Path to the native library to use instead of the default one, for debugging purposes.");
        System.out.println("--proxy=socks5://<host>:<port>");
        System.out.println("    Use a proxy to connect to the Telegram DC. The proxy must be a SOCKS5 proxy.");
    }
}
