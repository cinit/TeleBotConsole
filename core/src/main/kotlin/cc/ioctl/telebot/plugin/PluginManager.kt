package cc.ioctl.telebot.plugin

import cc.ioctl.telebot.tdlib.RobotServer
import cc.ioctl.telebot.tdlib.obj.Bot
import cc.ioctl.telebot.util.Log
import com.moandjiezana.toml.Toml
import java.io.File
import java.io.IOError

object PluginManager {

    private const val TAG = "PluginManager"

    data class PluginInfo(
        var name: String,
        var mainClass: String,
        var path: File?
    )

    private val mLock = Any()
    private val mRegisteredPlugins = HashMap<String, PluginInfo>()
    private val mLoadedPlugins = HashMap<String, IPlugin>()

    @JvmStatic
    fun registerBundledPlugin(name: String, mainClass: String) {
        Log.d(TAG, "registerBundledPlugin: $name, $mainClass")
        synchronized(mLock) { mRegisteredPlugins.put(name, PluginInfo(name, mainClass, null)) }
    }

    fun registerPluginsFromConfig(config: Toml) {
        val allowUnpackedPlugins = config.getBoolean("plugin.allow_unpacked_plugins", false)
        val types: Array<String> = if (allowUnpackedPlugins) {
            arrayOf("bundled", "jar", "unpacked")
        } else {
            arrayOf("bundled", "jar")
        }
        synchronized(mLock) {
            types.forEach { type ->
                config.getTables("plugin.$type")?.forEach { item ->
                    val pluginInfo = parsePlugin(type, item)
                    if (pluginInfo != null) {
                        mRegisteredPlugins[pluginInfo.name] = pluginInfo
                    }
                }
            }
        }
    }

    private fun parsePlugin(type: String, item: Toml): PluginInfo? {
        try {
            val enabled = item.getBoolean("enabled", true)
            if (!enabled) {
                return null
            }
            when (type) {
                "bundled" -> {
                    val name = item.getString("name")
                    val entry = item.getString("entry")
                    return PluginInfo(name, entry, null)
                }
                else -> {
                    Log.e(TAG, "parsePlugin: unknown type $type")
                    return null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unable to parse plugin", e)
            return null
        }
    }

    internal fun loadRegisterPlugins() {
        synchronized(mLock) {
            mRegisteredPlugins.forEach {
                Log.i(TAG, "Loading plugin: ${it.key}")
                val plugin = PluginLoader.loadPluginLocked(this, it.value)
                if (plugin == null) {
                    Log.e(TAG, "Failed to load plugin ${it.key}")
                } else {
                    mLoadedPlugins[it.key] = plugin
                }
            }
        }
    }

    internal fun enableLoadedPlugins() {
        synchronized(mLock) {
            mLoadedPlugins.forEach {
                val plugin = it.value
                Log.i(TAG, "Enabling plugin ${it.key}")
                try {
                    plugin.isEnabled = true
                    plugin.onEnable()
                } catch (e: Throwable) {
                    if (e is Exception || e is LinkageError || e is IOError || e.javaClass.name.startsWith("kotlin.")) {
                        Log.e(TAG, "Failed to enable plugin ${it.key}", e)
                        plugin.isEnabled = false
                    } else {
                        throw e
                    }
                }
            }
        }
    }

    internal fun callPluginsServerStarted() {
        synchronized(mLock) {
            mLoadedPlugins.forEach {
                val plugin = it.value
                Log.i(TAG, "call plugin onServerStart ${it.key}")
                try {
                    plugin.onServerStart()
                } catch (e: Throwable) {
                    if (e is Exception || e is LinkageError || e is IOError || e.javaClass.name.startsWith("kotlin.")) {
                        Log.e(TAG, "Failed to call plugin onServerStart ${it.key}", e)
                    } else {
                        throw e
                    }
                }
            }
        }
    }

    internal fun callPluginsLoginFinished() {
        val bots = HashMap<Long, Bot>(5)
        RobotServer.instance.allAuthenticatedBots.forEach {
            val uid = it.userId
            if (uid > 0) {
                bots[uid] = it
            }
        }
        synchronized(mLock) {
            mLoadedPlugins.forEach {
                val plugin = it.value
                Log.i(TAG, "call plugin onServerStart ${it.key}")
                try {
                    plugin.onLoginFinish(bots)
                } catch (e: Throwable) {
                    if (e is Exception || e is LinkageError || e is IOError || e.javaClass.name.startsWith("kotlin.")) {
                        Log.e(TAG, "Failed to call plugin onLoginFinish ${it.key}", e)
                    } else {
                        throw e
                    }
                }
            }
        }
    }


}
