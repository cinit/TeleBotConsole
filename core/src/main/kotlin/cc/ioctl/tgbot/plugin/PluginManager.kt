package cc.ioctl.tgbot.plugin

import cc.ioctl.util.Log
import java.io.IOError

object PluginManager {

    private const val TAG = "PluginManager"

    data class PluginInfo(
        var name: String,
        var mainClass: String
    )

    private val mLock = Any()
    private val mRegisteredPlugins = HashMap<String, PluginInfo>()
    private val mLoadedPlugins = HashMap<String, IPlugin>()

    @JvmStatic
    fun registerPlugin(name: String, mainClass: String) {
        Log.d(TAG, "registerPlugin: $name, $mainClass")
        synchronized(mLock) { mRegisteredPlugins.put(name, PluginInfo(name, mainClass)) }
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
                    if (e is Exception || e is LinkageError || e is IOError) {
                        Log.e(TAG, "Failed to enable plugin ${it.key}", e)
                        plugin.isEnabled = false
                    } else {
                        throw e
                    }
                }
            }
        }
    }

}
