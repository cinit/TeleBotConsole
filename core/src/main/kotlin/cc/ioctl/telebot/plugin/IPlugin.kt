package cc.ioctl.telebot.plugin

import cc.ioctl.telebot.tdlib.RobotServer

interface IPlugin {

    val server: RobotServer

    var isEnabled: Boolean

    fun onLoad()

    fun onEnable()

}
