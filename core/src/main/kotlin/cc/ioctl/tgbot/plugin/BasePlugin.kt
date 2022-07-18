package cc.ioctl.tgbot.plugin

import cc.ioctl.tdlib.RobotServer

abstract class BasePlugin : IPlugin {

    override val server: RobotServer get() = RobotServer.instance

    override var isEnabled: Boolean = false

    override fun onLoad() {
        // no-op
    }

}
