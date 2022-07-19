package cc.ioctl.tgbot.startup

import cc.ioctl.tdlib.RobotServer
import cc.ioctl.tgbot.plugin.PluginManager
import cc.ioctl.util.IoUtils
import cc.ioctl.util.Log
import com.moandjiezana.toml.Toml
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.system.exitProcess

object ServerInit {

    private const val TAG = "ServerInit"

    @JvmStatic
    fun runServer(server: RobotServer, socketProxy: String?) {
        Log.i(TAG, "proxy: $socketProxy")
        server.proxy = socketProxy
        // read config
        val configDir = File(server.baseDir, "config")
        IoUtils.mkdirsOrThrow(configDir)
        val serverConfigFile = File(configDir, "server.toml")
        val botsConfigFile = File(configDir, "bots.toml")
        val pluginsConfigFile = File(configDir, "plugins.toml")
        extractFileIfNotExists(serverConfigFile, "config")
        extractFileIfNotExists(botsConfigFile, "config")
        extractFileIfNotExists(pluginsConfigFile, "config")
        val serverConfig = Toml().read(serverConfigFile.readText())
        val botsConfig = Toml().read(botsConfigFile.readText())
        val pluginsConfig = Toml().read(pluginsConfigFile.readText())
        // configure TDLib
        val apiId: Int = serverConfig.getLong("server.api_id").toInt()
            .verifyConfigOrFatal("server.api_id", botsConfigFile, "api_id must be positive") { it > 0 }
        val apiHash: String = serverConfig.getString("server.api_hash")
            .verifyConfigOrFatal(
                "server.api_hash", botsConfigFile,
                "api_hash must be 32 hexadecimal characters"
            ) { it.matches(Regex("^[0-9a-f]{32}$")) }
        val useTestDC: Boolean = serverConfig.getBoolean("server.use_test_dc")
        val standBotTokens = ArrayList<String>(2)
        val userBotPhones = ArrayList<String>(1)
        botsConfig.getTables("accounts.bot")?.forEach { cfg ->
            val token = (cfg.getString("bot_token"))
                .verifyConfigOrFatal(
                    "accounts.bot.bot_token", botsConfigFile,
                    "bot_token is invalid or missing, requires '^[0-9]+:[a-zA-Z0-9_]+$'"
                ) { it?.matches(Regex("^[0-9]+:[a-zA-Z0-9_]+$")) ?: false }
            standBotTokens.add(token!!)
        }
        botsConfig.getTables("accounts.user")?.forEach { cfg ->
            val phone = (cfg.getString("phone"))
                .verifyConfigOrFatal(
                    "accounts.user_phone", botsConfigFile,
                    "phone is invalid or missing, requires '^\\+[0-9]{1,3} [0-9]+\$'"
                ) { it?.matches(Regex("^\\+[0-9]{1,3} [0-9]+$")) ?: false }
            userBotPhones.add(phone!!)
        }
        if (standBotTokens.isEmpty() && userBotPhones.isEmpty()) {
            Log.e(TAG, "No bots configured, there is nothing to do")
            Log.e(TAG, "Please add bots to bots.toml")
            Log.e(TAG, "Shutting down...")
            exitProcess(1)
        }
        server.start(apiId, apiHash, useTestDC)
        // register plugins
        PluginManager.registerPluginsFromConfig(pluginsConfig)
        // load plugins
        PluginManager.loadRegisterPlugins()
        for (phoneNumber in userBotPhones) {
            Log.e(TAG, "User bot phone NOT IMPLEMENTED")
            throw java.lang.UnsupportedOperationException("User bot phone NOT IMPLEMENTED")
            // TODO: 2022-07-17 implement user bot interactive login
        }
        // login bots
        runBlocking {
            for (botToken in standBotTokens) {
                val bot = server.createNewBot()
                val uid = botToken.split(":")[0].toLong()
                Log.i(TAG, "Logging in bot $uid with token.")
                bot.loginWithBotTokenSuspended(botToken)
            }
            Log.i(TAG, "Login success")
        }
        // enable plugins
        PluginManager.enableLoadedPlugins()
        handleConsoleCommand()
    }


    private fun handleConsoleCommand() {
        Log.e(TAG, "TODO")
        Thread.sleep(10000)
    }

    private fun extractFileIfNotExists(file: File, assetsBaseDir: String) {
        if (!file.exists()) {
            (ServerInit.javaClass.classLoader.getResourceAsStream("$assetsBaseDir/${file.name}")
                ?: throw IllegalStateException("file not found in assets: $assetsBaseDir/${file.name}")).use {
                val data = IoUtils.readFully(it)
                IoUtils.writeFile(file, data)
            }
        }
    }

    private inline fun <reified T> T.verifyConfigOrFatal(
        name: String,
        file: File,
        abortMsg: String,
        verifier: ((T) -> Boolean)
    ): T {
        if (!verifier(this)) {
            Log.e(TAG, "Configuration semantic error in item '$name': $abortMsg")
            Log.e(TAG, "Please check your config file: ${file.absolutePath}")
            throw IllegalArgumentException("configuration error: $abortMsg")
        }
        return this
    }
}
