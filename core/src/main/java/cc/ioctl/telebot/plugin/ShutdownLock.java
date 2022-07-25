package cc.ioctl.telebot.plugin;

import org.jetbrains.annotations.NotNull;

public interface ShutdownLock {

    /**
     * Acquire the shutdown lock.
     *
     * @param plugin      The plugin that is requesting hanging up the shutdown.
     * @param maxWaitTime The maximum time in milliseconds to wait for the shutdown lock, must be greater than 0.
     */
    void acquire(@NotNull IPlugin plugin, int maxWaitTime);

    /**
     * Release the shutdown lock.
     *
     * @param plugin The plugin that is releasing the shutdown lock.
     */
    void release(@NotNull IPlugin plugin);

}
