package cc.ioctl.telebot.plugin

import cc.ioctl.telebot.tdlib.RobotServer

abstract class PluginBase : IPlugin {

    override val server: RobotServer get() = RobotServer.instance

    override var isEnabled: Boolean = false

    override fun onLoad() {
        // no-op
    }

}
