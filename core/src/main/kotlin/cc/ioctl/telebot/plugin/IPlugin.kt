package cc.ioctl.telebot.plugin

import cc.ioctl.telebot.tdlib.RobotServer
import cc.ioctl.telebot.tdlib.obj.Bot

interface IPlugin {

    val server: RobotServer

    var isEnabled: Boolean

    /**
     * Called when the plugin is loaded.
     * Note that at this time the server may not be initialized yet.
     */
    fun onLoad()

    /**
     * Called when the plugin is enabled.
     * This is called after the server is initialized, but before TDLib network is started.
     */
    fun onEnable()

    /**
     * Called before the TDLib network is started.
     */
    fun onServerStart()

    /**
     * Called after the bots in config file are logged in.
     */
    fun onLoginFinish(bots: Map<Long, Bot>)

    /**
     * Called when the plugin is going to shut down.
     * You need to save your data etc. here.
     * This method is not meant to be used for time-consuming tasks.
     * If you need to do that, use the [lock] parameter to delay the shutdown.
     * After the server is stopped, you can no longer make TDLib calls.
     * @param lock A lock that you can use to delay the shutdown.
     */
    fun onServerStop(lock: ShutdownLock)

    /**
     * Called when the plugin is disabled.
     */
    fun onDisable()

}
