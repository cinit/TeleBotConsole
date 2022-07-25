package cc.ioctl.telebot.plugin

import cc.ioctl.telebot.tdlib.RobotServer
import cc.ioctl.telebot.tdlib.obj.Bot

abstract class PluginBase : IPlugin {

    override val server: RobotServer get() = RobotServer.instance

    override var isEnabled: Boolean = false

    override fun onLoad() {
        // no-op
    }

    override fun onEnable() {
        // no-op
    }

    override fun onServerStart() {
        // no-op
    }

    override fun onLoginFinish(bots: Map<Long, Bot>) {
        // no-op
    }

    override fun onServerStop(lock: ShutdownLock) {
        // no-op
    }

    override fun onDisable() {
        // no-op
    }
}
