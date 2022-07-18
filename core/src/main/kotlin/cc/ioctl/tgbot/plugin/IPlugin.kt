package cc.ioctl.tgbot.plugin

import cc.ioctl.tdlib.RobotServer

interface IPlugin {

    val server: RobotServer

    var isEnabled: Boolean

    fun onLoad()

    fun onEnable()

}
