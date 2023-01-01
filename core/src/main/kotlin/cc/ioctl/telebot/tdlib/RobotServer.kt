package cc.ioctl.telebot.tdlib

import cc.ioctl.telebot.TransactionDispatcher
import cc.ioctl.telebot.intern.NativeBridge
import cc.ioctl.telebot.intern.TDLibPollThread
import cc.ioctl.telebot.tdlib.intern.NonLocalObjectCachePool
import cc.ioctl.telebot.tdlib.obj.Bot
import cc.ioctl.telebot.tdlib.obj.Channel
import cc.ioctl.telebot.tdlib.obj.Group
import cc.ioctl.telebot.tdlib.obj.PrivateChatSession
import cc.ioctl.telebot.tdlib.obj.User
import cc.ioctl.telebot.tdlib.tlrpc.BaseTlRpcJsonObject
import cc.ioctl.telebot.tdlib.tlrpc.api.auth.SetTdlibParameters
import cc.ioctl.telebot.util.IoUtils
import cc.ioctl.telebot.util.Log
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeout
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

class RobotServer private constructor(val baseDir: File) {

    var proxy: String? = null
    val isRunning: Boolean get() = !mShuttingDown
    val serverConfigDir = File(baseDir, "config")
    val pluginsDir = File(baseDir, "plugins")
    val tdlibDir = File(baseDir, "tdlib")
    var defaultTimeout: Int = 30 * 1000
    val cachedObjectPool: NonLocalObjectCachePool = NonLocalObjectCachePool(this)
    val executor: ExecutorService = Executors.newCachedThreadPool()

    private val mLock = Any()
    private lateinit var mPollThread: Thread
    private lateinit var mServerParametersTemplate: SetTdlibParameters.Parameter
    val defaultTDLibParametersTemplate: SetTdlibParameters.Parameter get() = mServerParametersTemplate

    private var mShuttingDown = false
    private val mSequence: AtomicLong = AtomicLong(1)
    private val mLocalBots: HashMap<Int, Bot> = HashMap(1)
    private val mLocalBotUidMaps: HashMap<Long, Int> = HashMap(1)
    var exceptionHandler: ExceptionHandler? = null

    fun start(apiId: Int, apiHash: String, useTestDC: Boolean) {
        require(apiId > 0) { "apiId must be greater than 0" }
        require(apiHash.matches(Regex("[a-f0-9]{32}"))) { "apiHash must be a 40-character lowercase hex string" }
        IoUtils.mkdirsOrThrow(pluginsDir)
        IoUtils.mkdirsOrThrow(tdlibDir)
        synchronized(mLock) {
            if (::mPollThread.isInitialized) {
                error("RobotServer is already started")
            }
            // set TDLib log verbosity level to WARN
            NativeBridge.nativeTDLibExecuteSynchronized(JsonObject().apply {
                addProperty("@type", "setLogVerbosityLevel")
                addProperty("new_verbosity_level", 2)
            }.toString()).also { result ->
                if (BaseTlRpcJsonObject.getType(result) == "ok") {
                    Log.i("RobotServer", "TDLib log verbosity level set to WARNING")
                } else {
                    Log.e("RobotServer", "Failed to set TDLib log verbosity level to WARNING, $result")
                }
            }
            mServerParametersTemplate = SetTdlibParameters.Parameter(
                File(tdlibDir, "no_such_dir").absolutePath,
                false, false,
                apiId, apiHash,
                "en", "Server", "1.0",
                true, useTestDC
            )
            mPollThread = TDLibPollThread(this@RobotServer).also { it.start() }
        }
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            exceptionHandler?.onException(e, t)
        }
    }

    fun onReceiveTDLibEvent(resp: String) {
        if (resp.isEmpty()) {
            return
        }
        // FIXME: 2022-07-18 there maybe something wrong with the ExecutorService and coroutine
        executor.execute {
            runBlocking {
                try {
                    TransactionDispatcher.dispatchTDLibEvent(this@RobotServer, resp)
                } catch (e: Exception) {
                    exceptionHandler?.onException(e, Thread.currentThread())
                    Log.e(TAG, "onReceiveTDLibEvent error for $resp", e)
                }
            }
        }
    }

    fun getBotWithTDLibClientIndex(index: Int): Bot? {
        if (index < 0) {
            return null
        }
        synchronized(mLock) {
            return mLocalBots[index]
        }
    }

    fun getBotWithUserId(userId: Long): Bot? {
        if (userId <= 0L) {
            return null
        }
        synchronized(mLock) {
            return mLocalBots[mLocalBotUidMaps[userId] ?: return null]
        }
    }

    val allBots: List<Bot>
        get() {
            val bots = ArrayList<Bot>()
            synchronized(mLock) {
                mLocalBots.values.forEach { bots.add(it) }
            }
            return bots
        }

    val allAuthenticatedBots: List<Bot>
        get() {
            synchronized(mLock) {
                return mLocalBots.values.filter { it.isAuthenticated }
            }
        }

    fun onBotAuthenticationStatusChanged(bot: Bot) {
        synchronized(mLock) {
            val userId = bot.userId
            val isAuthenticated = bot.isAuthenticated && userId > 0L
            if (isAuthenticated) {
                mLocalBotUidMaps[userId] = bot.clientIndex
            } else {
                // remove the bot from the cache
                mLocalBotUidMaps.remove(userId)
            }
        }
    }

    fun getCachedUserWithUserId(uid: Long): User? {
        require(uid > 0L) { "uid must be greater than 0" }
        return cachedObjectPool.findCachedUser(uid)
    }

    fun getCachedUserWithUserName(username: String): User? {
        return cachedObjectPool.findCachedUserWithUserName(username)
    }

    fun getOrNewUser(userId: Long, bot: Bot? = null): User {
        require(userId > 0) { "userId must be greater than 0" }
        val user = cachedObjectPool.getOrCreateUser(userId)
        user.affinityUserId = bot?.userId ?: 0
        return user
    }

    fun getOrNewGroup(groupId: Long, bot: Bot? = null): Group {
        require(groupId > 0) { "groupId must be greater than 0" }
        val group = cachedObjectPool.getOrCreateGroup(groupId)
        group.affinityUserId = bot?.userId ?: 0
        return group
    }

    fun getOrNewChannel(channelId: Long, bot: Bot? = null): Channel {
        require(channelId > 0) { "channelId must be greater than 0" }
        val channel = cachedObjectPool.getOrCreateChannel(channelId)
        channel.affinityUserId = bot?.userId ?: 0
        return channel
    }

    fun getCachedPrivateChatSessionWithChatId(chatId: Long): PrivateChatSession? {
        require(chatId > 0) { "chatId must be greater than 0" }
        // try to find in cache first
        return cachedObjectPool.getCachedPrivateChat(chatId)
    }

    fun getCachedGroupWithGroupId(groupId: Long): Group? {
        require(groupId > 0) { "groupId must be greater than 0" }
        return cachedObjectPool.findCachedGroup(groupId)
    }

    fun getCachedChannelWithChannelId(channelId: Long): Channel? {
        require(channelId > 0) { "channelId must be greater than 0" }
        return cachedObjectPool.findCachedChannel(channelId)
    }

    fun nextSequence(): Long {
        return mSequence.getAndIncrement()
    }

    fun createNewBot(designator: String): Bot {
        require(designator.isNotEmpty()) { "designator must not be empty" }
        require(designator.matches(Regex("[a-zA-Z0-9_]+"))) { "designator must be a-zA-Z0-9_" }
        synchronized(mLock) {
            val index = NativeBridge.nativeTDLibCreateClient()
            if (index < 0) {
                error("Failed to create new bot: returned index $index")
            }
            val bot = Bot(this, index, designator)
            mLocalBots[index] = bot
            // new created bot is not yet authorized, so we don't need to add it to the uid map
            return bot
        }
    }

    /**
     * Execute a request and return the request id.
     */
    fun executeRawRequestAsync(
        request: String,
        bot: Bot,
        callback: TransactionDispatcher.TransactionCallbackV1
    ): String {
        val extra: String
        val requestToSend: String
        val req = JsonParser.parseString(request).asJsonObject
        require(req.has("@type")) { "request must have @type" }
        if (!req.has("@extra")) {
            extra = "req_" + nextSequence()
            req.addProperty("@extra", extra)
            requestToSend = req.toString()
        } else {
            extra = req.get("@extra").asString
            requestToSend = request
        }
        TransactionDispatcher.waitForSingleEvent(extra, callback)
        NativeBridge.nativeTDLibExecuteAsync(bot.clientIndex, requestToSend)
        return extra
    }

    fun executeRequestBlocking(request: String, bot: Bot, timeout: Int): JsonObject? {
        val extra: String
        val req = JsonParser.parseString(request).asJsonObject
        require(req.has("@type")) { "request must have @type" }
        val requestToSend: String
        if (!req.has("@extra")) {
            extra = "req_" + nextSequence()
            req.addProperty("@extra", extra)
            requestToSend = req.toString()
        } else {
            extra = req.get("@extra").asString
            requestToSend = request
        }
        val result: Array<JsonObject?> = arrayOfNulls(1)
        val owner = Object()
        TransactionDispatcher.waitForSingleEvent(extra, object : TransactionDispatcher.TransactionCallbackV1 {
            override fun onEvent(event: JsonObject, bot: Bot?, type: String): Boolean {
                return if (BaseTlRpcJsonObject.getExtra(event) == extra) {
                    synchronized(owner) {
                        result[0] = event
                        owner.notifyAll()
                    }
                    true
                } else {
                    Log.e(TAG, "unexpected event: $event")
                    false
                }
            }
        })
        synchronized(owner) {
            NativeBridge.nativeTDLibExecuteAsync(bot.clientIndex, requestToSend)
            val start: Long = System.currentTimeMillis()
            val end = start + timeout
            while (result[0] == null) {
                val now = System.currentTimeMillis()
                val remain = end - now
                if (remain <= 0) {
                    break
                } else {
                    owner.wait(remain)
                }
            }
        }
        return result[0]
    }

    suspend fun executeRequestSuspended(request: String, bot: Bot, timeout: Int): JsonObject? {
        val extra: String
        val req = JsonParser.parseString(request).asJsonObject
        require(req.has("@type")) { "request must have @type" }
        val requestToSend: String
        if (!req.has("@extra")) {
            extra = "req_" + nextSequence()
            req.addProperty("@extra", extra)
            requestToSend = req.toString()
        } else {
            extra = req.get("@extra").asString
            requestToSend = request
        }
        val mutex = Mutex(true)
        val result: Array<JsonObject?> = arrayOfNulls(1)
        TransactionDispatcher.waitForSingleEvent(extra, object : TransactionDispatcher.TransactionCallbackV1 {
            override fun onEvent(event: JsonObject, bot: Bot?, type: String): Boolean {
                return if (BaseTlRpcJsonObject.getExtra(event) == extra) {
                    result[0] = event
                    mutex.unlock()
                    true
                } else {
                    Log.e(TAG, "unexpected event: $event")
                    false
                }
            }
        })
        NativeBridge.nativeTDLibExecuteAsync(bot.clientIndex, requestToSend)
        val start: Long = System.currentTimeMillis()
        val end = start + timeout
        while (result[0] == null) {
            val now = System.currentTimeMillis()
            val remain = end - now
            if (remain <= 0) {
                break
            } else {
                try {
                    withTimeout(remain) {
                        mutex.lock()
                    }
                } catch (e: TimeoutCancellationException) {
                    break
                }
            }
        }
        return result[0]
    }

    companion object {
        @Volatile
        private var sInstance: RobotServer? = null

        private val TAG = RobotServer::class.java.simpleName

        @JvmStatic
        @Synchronized
        fun createInstance(workingDir: File): RobotServer {
            check(workingDir.isAbsolute) { "workingDir must be an absolute path" }
            return if (sInstance == null) {
                val server = RobotServer(workingDir)
                sInstance = server
                server
            } else {
                error("RobotServer is already created")
            }
        }

        val instance: RobotServer
            get() = sInstance ?: error("RobotServer is not initialized")
    }

    interface ExceptionHandler {
        fun onException(e: Throwable, t: Thread)
    }

}
