package cc.ioctl.telebot.plugin

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
