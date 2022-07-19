package cc.ioctl.tdlib.obj

import cc.ioctl.tdlib.RobotServer
import cc.ioctl.tdlib.tlrpc.RemoteApiException
import cc.ioctl.tdlib.tlrpc.TlRpcJsonObject
import cc.ioctl.tdlib.tlrpc.api.msg.ReplyMarkup
import cc.ioctl.tgbot.EventHandler
import cc.ioctl.tgbot.TransactionDispatcher
import cc.ioctl.util.Condition
import cc.ioctl.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.time.Duration.Companion.milliseconds

class Bot internal constructor(
    val server: RobotServer,
    val clientIndex: Int
) : Account() {

    companion object {
        private const val TAG = "Bot"
    }

    override var userId: Long = 0L

    override var username: String? = null

    override var firstName: String = "<unknown>"

    override var lastName: String? = null

    override var phoneNumber: String? = null

    override var bio: String? = null

    override val isUser: Boolean = true

    override val isKnown: Boolean get() = isAuthenticated

    override var isBot: Boolean = false

    override val isDeletedAccount: Boolean = false

    private val mListenerLock = Any()

    private val mOnRecvMsgListeners = HashSet<EventHandler.MessageListenerV1>(1)

    private var mDefaultLogOnlyErrorHandler = object : TransactionDispatcher.TransactionCallbackV1 {
        override fun onEvent(event: String, bot: Bot?, type: String): Boolean {
            if (bot != this@Bot) {
                Log.e(TAG, "onEvent: bot!=this@Bot, expected: $bot, actual: ${this@Bot}")
                return false
            } else {
                return when (type) {
                    "ok" -> {
                        true
                    }
                    "error" -> {
                        Log.e(TAG, "onEvent: error: $event")
                        true
                    }
                    else -> {
                        Log.e(TAG, "onEvent: unexpected type: $type, event: $event")
                        false
                    }
                }
            }
        }
    }

    val isAuthenticated: Boolean
        get() = userId != 0L && mAuthState == AuthState.AUTHORIZED

    private var mAuthState: AuthState = AuthState.UNINITIALIZED
    private var mAuthBotToken: String? = null
    private var mAuthUserPhone: String? = null
    private val mAuthStateCondition = Condition(Mutex(true))
    private val mAuthStateLastErrorMsg: String? = null

    private enum class AuthState {
        UNINITIALIZED,
        WAIT_TOKEN,
        WAIT_CODE,
        WAIT_PASSWORD,
        WAIT_RESPONSE,
        AUTHORIZED,
        INVALID_CREDENTIALS,
        INVALID_STATE,
        CLOSED
    }

    suspend fun onReceiveTDLibEventBlocking(event: String, type: String): Boolean {
        return runBlocking {
            withContext(Dispatchers.Default) {
                return@withContext handleTDLibEvent(event, type)
            }
        }
    }

    suspend fun onReceiveTDLibEvent(event: String, type: String): Boolean {
        return handleTDLibEvent(event, type)
    }

    private suspend fun handleTDLibEvent(event: String, type: String): Boolean {
        return when (type) {
            "updateAuthorizationState" -> {
                handleUpdateAuthorizationState(event)
            }
            "updateOption" -> {
                handleUpdateOption(event)
            }
            "updateConnectionState" -> {
                handleUpdateConnectionState(event)
            }
            "updateUser" -> {
                handleUpdateUser(event)
            }
            "updateNewMessage" -> {
                handleUpdateNewMessage(event)
            }
            "updateGroup",
            "updateSupergroup" -> {
                handleUpdateTypedGroup(event, type)
            }
            "updateNewChat" -> {
                handleUpdateNewChat(event)
            }
            "updateUserStatus" -> {
                handleUpdateUserStatus(event)
            }
            "updateDeleteMessages" -> {
                handleUpdateDeleteMessages(event)
            }
            "updateSelectedBackground",
            "updateFileDownloads",
            "updateChatThemes",
            "updateDiceEmojis",
            "updateAnimationSearchParameters",
            "updateRecentStickers",
            "updateReactions",
            "updateChatPosition" -> {
                // ignore
                true
            }
            else -> {
                Log.e(TAG, "onReceiveTDLibEvent: unexpected type: $type, event: $event")
                false
            }
        }
    }

    private fun handleUpdateTypedGroup(event: String, type: String): Boolean {
        if (type != "updateGroup" && type != "updateSupergroup") {
            Log.e(TAG, "handleUpdateTypedGroup: unexpected type: $type, event: $event")
            return false
        }
        val isSupergroup = type == "updateSupergroup"
        if (isSupergroup) {
            val supergroup = JsonParser.parseString(event).asJsonObject.getAsJsonObject("supergroup")
            val uid = supergroup.get("id").asLong
            val username = supergroup.get("username").asString
            val group = server.getOrNewGroup(uid, this)
            group.isSuperGroup = true
            group.username = username.ifEmpty { null }
        } else {
            Log.e(TAG, "non-supergroup group: $event")
        }
        return true
    }

    private fun handleUpdateConnectionState(event: String): Boolean {
        val state = JsonParser.parseString(event).asJsonObject.get("state").asJsonObject.get("@type").asString
        Log.d(TAG, "handleUpdateConnectionState: $state")
        return true
    }

    private fun handleUpdateUser(event: String): Boolean {
        val userObj = JsonParser.parseString(event).asJsonObject.getAsJsonObject("user")
        val uid = userObj.get("id").asLong
        val firstName = userObj.get("first_name").asString
        val lastName = userObj.get("last_name").asString
        val username = userObj.get("username").asString
        val phoneNumber = userObj.get("phone_number").asString
        val languageCode = userObj.get("language_code").asString
        val user = server.getOrNewUser(uid, this)
        user.firstName = firstName
        user.lastName = lastName.ifEmpty { null }
        user.username = username.ifEmpty { null }
        if (phoneNumber.isNotEmpty()) {
            user.phoneNumber = null
        }
        if (languageCode.isNotEmpty()) {
            user.languageCode = languageCode
        }
        if (uid > 0 && uid == this.userId) {
            this.firstName = firstName
            this.lastName = lastName.ifEmpty { null }
            this.phoneNumber = phoneNumber.ifEmpty { null }
            this.username = username.ifEmpty { null }
        }
        return true
    }

    private fun handleUpdateDeleteMessages(event: String): Boolean {
        val deleteMessages = JsonParser.parseString(event).asJsonObject
        val chatId = deleteMessages.get("chat_id").asLong
        val messageIds = deleteMessages.get("message_ids").asJsonArray.map { it.asLong }
        for (listener in mOnRecvMsgListeners) {
            listener.onDeleteMessages(this, chatId, messageIds)
        }
        return true
    }

    private fun handleUpdateNewMessage(event: String): Boolean {
        val obj = JsonParser.parseString(event).asJsonObject.getAsJsonObject("message")
        val msgId = obj.get("id").asLong
        val chatId = obj.get("chat_id").asLong
        val isOutgoing = obj.get("is_outgoing").asBoolean
        if (isOutgoing) {
            return true
        }
        val senderImpl = obj["sender_id"].asJsonObject
        val senderType = senderImpl.get("@type").asString
        val senderId: Long
        when (senderType) {
            "messageSenderUser" -> {
                senderId = senderImpl.get("user_id").asLong
            }
            "messageSenderChat" -> {
                senderId = senderImpl.get("chat_id").asLong
            }
            else -> {
                Log.e(TAG, "handleUpdateNewMessage: unexpected sender type: $senderType")
                return false
            }
        }
        // call listeners
        for (listener in mOnRecvMsgListeners) {
            listener.onReceiveMessage(this, chatId, senderId, obj)
        }
        return true
    }

    private fun handleUpdateNewChat(event: String): Boolean {
        val chat = JsonParser.parseString(event).asJsonObject.getAsJsonObject("chat")
        val chatId = chat.get("id").asLong
        val name = chat.get("title").asString
        val typeImpl = chat["type"].asJsonObject
        when (val chatType = typeImpl.get("@type").asString) {
            "chatTypeSupergroup" -> {
                val gid = typeImpl["supergroup_id"].asLong
                val group = server.getOrNewGroup(gid, this)
                group.isSuperGroup = true
                group.name = name
                val photo = chat.getAsJsonObject("photo")
                if (photo != null) {
                    group.photo = RemoteFile.fromJsonObject(photo["small"].asJsonObject)
                }
            }
            "chatTypePrivate" -> {
                val uid = typeImpl["user_id"].asLong
                val user = server.getOrNewUser(uid, this)
                user.defaultChatId = chatId
            }
            else -> {
                Log.e(TAG, "handleUpdateNewChat: unexpected chat type: $chatType, event: $event")
            }
        }
        return true
    }

    private fun handleUpdateUserStatus(event: String): Boolean {
        Log.i(TAG, "handleUpdateUserStatus: $event")
        return true
    }

    private suspend fun handleUpdateAuthorizationState(event: String): Boolean {
        val state: JsonObject = JsonParser.parseString(event).asJsonObject.getAsJsonObject("authorization_state")
        val authState = state.get("@type").asString
        Log.d(TAG, "handleUpdateAuthorizationState: $authState")
        when (authState) {
            "authorizationStateWaitTdlibParameters" -> {
                sendTdLibParameters()
                return true
            }
            "authorizationStateWaitEncryptionKey" -> {
                sendDatabaseEncryptionKey()
                return true
            }
            "authorizationStateWaitPhoneNumber" -> {
                // check if we already have bot token
                if (mAuthState == AuthState.UNINITIALIZED) {
                    // if we have access token, try to use it
                    if (!mAuthBotToken.isNullOrBlank()) {
                        // send bot token
                        Log.d(TAG, "try auth with bot token");
                        val request = JsonObject()
                        request.addProperty("@type", "checkAuthenticationBotToken")
                        request.addProperty("token", mAuthBotToken)
                        executeRawRequestAsync(request.toString()) { result, _, type ->
                            if (type == "error") {
                                Log.e(TAG, "checkAuthenticationBotToken: error: $result")
                                mAuthState = AuthState.INVALID_CREDENTIALS;
                                notifyAuthorizationResult(false, result)
                            } else if (type == "ok") {
                                Log.d(TAG, "checkAuthenticationBotToken: ok: $result")
                                mAuthState = AuthState.AUTHORIZED
                                notifyAuthorizationResult(true, null)
                            } else {
                                Log.e(TAG, "Unexpected result checking authentication bot token: $result")
                                mAuthState = AuthState.INVALID_CREDENTIALS
                                notifyAuthorizationResult(
                                    false,
                                    "Unexpected result checking authentication bot token: $result"
                                )
                            }
                            return@executeRawRequestAsync true
                        }
                        mAuthBotToken = null
                        mAuthState = AuthState.WAIT_RESPONSE
                        return true
                    } else if (!mAuthUserPhone.isNullOrBlank()) {
                        // send phone number
                        Log.d(TAG, "try auth with phone number");
                        val authSettings = JsonObject().apply {
                            addProperty("allow_flash_call", false)
                            addProperty("allow_missed_call", false)
                            addProperty("is_current_phone_number", false)
                            addProperty("allow_sms_retriever_api", false)
                            add("authentication_tokens", JsonArray())
                        }
                        val request = JsonObject().apply {
                            addProperty("@type", "setAuthenticationPhoneNumber")
                            addProperty("phone_number", mAuthUserPhone)
                            add("settings", authSettings)
                        }
                        executeRawRequestAsync(request.toString()) { result, _, type ->
                            if (type == "error") {
                                Log.e(TAG, "setAuthenticationPhoneNumber: error: $result")
                                mAuthState = AuthState.INVALID_CREDENTIALS;
                                notifyAuthorizationResult(false, result)
                            } else if (type == "ok") {
                                Log.d(TAG, "setAuthenticationPhoneNumber: ok: $result")
                                mAuthState = AuthState.WAIT_CODE
                                notifyAuthorizationResult(true, null)
                            } else {
                                Log.e(TAG, "Unexpected result setting authentication phone number: $result")
                                mAuthState = AuthState.INVALID_CREDENTIALS
                                notifyAuthorizationResult(
                                    false,
                                    "Unexpected result setting authentication phone number: $result"
                                )
                            }
                            return@executeRawRequestAsync true
                        }
                        mAuthBotToken = null
                        mAuthState = AuthState.WAIT_RESPONSE
                    }
                    return true;
                } else {
                    mAuthState = AuthState.WAIT_TOKEN
                    return true;
                }
            }
            "authorizationStateReady" -> {
                mAuthState = AuthState.AUTHORIZED
                Log.d(TAG, "authorizationStateReady, client index: $clientIndex")
                notifyAuthorizationResult(true, null)
                return true
            }
            else -> {
                Log.e(TAG, "Unknown authorization state: ${state.get("@type").asString}")
                return false
            }
        }
    }

    private suspend fun sendDatabaseEncryptionKey() {
        // no encryption key not supported yet
        val request = JsonObject()
        request.addProperty("@type", "checkDatabaseEncryptionKey")
        request.addProperty("password", "")
        executeRequestWaitExpectSuccess(request.toString(), 1000)
    }

    private fun handleUpdateOption(event: String): Boolean {
        val name = JsonParser.parseString(event).asJsonObject.get("name").asString
        val value = JsonParser.parseString(event).asJsonObject.get("value").asJsonObject
        Log.d(TAG, "handleUpdateOption: $event")
        when (name) {
            "my_id" -> {
                userId = value.get("value").asLong
            }
        }
        return true
    }

    private fun notifyAuthorizationResult(isSuccess: Boolean, errorMsg: String?) {
        Log.d(TAG, "notifyAuthorizationResult: $isSuccess, $errorMsg")
        server.onBotAuthenticationStatusChanged(this)
        mAuthStateCondition.signalAll()
    }

    private suspend fun sendTdLibParameters() {
        val param = server.defaultTDLibParameters.toJsonObject()
        val request = JsonObject()
        request.addProperty("@type", "setTdlibParameters")
        request.add("parameters", param)
        executeRequestWaitExpectSuccess(request.toString(), 1000)
    }

    suspend fun loginWithBotTokenSuspended(token: String): Long {
        if (token.isEmpty()) {
            throw IllegalArgumentException("Token is empty")
        }
        if (mAuthState == AuthState.AUTHORIZED) {
            throw IllegalStateException("Bot is already authorized, uid: $userId")
        }
        Log.d(TAG, "loginWithBotToken, mAuthState: $mAuthState, clientId: $clientIndex")
        mAuthBotToken = token;
        if (mAuthState == AuthState.WAIT_TOKEN || mAuthState == AuthState.INVALID_CREDENTIALS) {
            Log.d(TAG, "try auth with bot token")
            val request = JsonObject().apply {
                addProperty("@type", "checkAuthenticationBotToken")
                addProperty("token", token)
            }
            executeRawRequestAsyncExpectSuccess(request)
            mAuthState = AuthState.WAIT_RESPONSE
        } else {
            if (clientIndex == 1 && mAuthState == AuthState.UNINITIALIZED) {
                // send a request to start procedure
                executeRawRequestAsync(JsonObject().apply {
                    addProperty("@type", "getOption")
                    addProperty("name", "my_id")
                }.toString()) { result, _, type ->
                    return@executeRawRequestAsync when (type) {
                        "optionValueInteger" -> {
                            val uid = JsonParser.parseString(result).asJsonObject.get("value").asLong
                            if (uid > 0) {
                                userId = uid
                                Log.i(TAG, "loginWithBotToken: user id: $userId")
                            }
                            true
                        }
                        else -> {
                            Log.e(TAG, "Unexpected result getting my_id: $result")
                            true
                        }
                    }
                }
            }
        }
        // wait for response
        val ret = mAuthStateCondition.await(server.defaultTimeout.toLong().milliseconds)
        Log.d(TAG, "await result: $ret")
        return if (mAuthState == AuthState.AUTHORIZED) userId else 0L
    }

    private fun executeRawRequestAsyncExpectSuccess(request: JsonObject) {
        executeRawRequestAsyncExpectSuccess(request.toString())
    }

    private fun executeRawRequestAsyncExpectSuccess(request: String) {
        server.executeRawRequestAsync(request, this, mDefaultLogOnlyErrorHandler)
    }

    private fun executeRawRequestAsync(request: String, callback: TransactionDispatcher.TransactionCallbackV1): String {
        return server.executeRawRequestAsync(request, this, callback)
    }

    private fun executeRawRequestAsync(request: JsonObject, callback: (String, Bot?, String) -> Boolean): String {
        return executeRawRequestAsync(request.toString(), callback)
    }

    private fun executeRawRequestAsync(request: String, callback: (String, Bot?, String) -> Boolean): String {
        return server.executeRawRequestAsync(request, this, object : TransactionDispatcher.TransactionCallbackV1 {
            override fun onEvent(event: String, bot: Bot?, type: String): Boolean {
                return callback(event, bot, type)
            }
        })
    }

    fun registerOnReceiveMessageListener(listener: EventHandler.MessageListenerV1) {
        synchronized(mListenerLock) {
            mOnRecvMsgListeners.add(listener)
        }
    }

    fun unregisterOnReceiveMessageListener(listener: EventHandler.MessageListenerV1) {
        synchronized(mListenerLock) {
            mOnRecvMsgListeners.remove(listener)
        }
    }

    private suspend fun executeRequestWaitExpectSuccess(request: String, timeout: Int): Boolean {
        val result = executeRequest(request, timeout)
        if (result == null) {
            Log.e(TAG, "executeRequestWait: timeout, request: $request")
            return false
        }
        val type = JsonParser.parseString(result).asJsonObject.get("@type").asString
        if (type != "ok") {
            Log.e(TAG, "executeRequestWait: error: $result")
            return false
        }
        return true
    }

    @Throws(RemoteApiException::class, IOException::class)
    suspend fun sendMessageRaw(
        chatId: Long, inputMessageContent: JsonObject,
        replyMarkup: ReplyMarkup? = null
    ) {
        JsonObject().apply {
            addProperty("@type", "sendMessage")
            addProperty("chat_id", chatId)
            add("input_message_content", inputMessageContent)
            add("reply_markup", replyMarkup?.toJsonObject())
        }.let {
            val result = executeRequest(it.toString(), server.defaultTimeout)
            if (result == null) {
                throw IOException("Timeout")
            } else {
                TlRpcJsonObject.throwRemoteApiExceptionIfError(result)
            }
        }
    }

    @Throws(RemoteApiException::class, IOException::class)
    suspend fun sendMessageForText(
        chatId: Long, plainText: String,
        replyMarkup: ReplyMarkup? = null,
        disableWebPreview: Boolean = false
    ) {
        JsonObject().apply {
            addProperty("@type", "sendMessage")
            addProperty("chat_id", chatId)
            add("input_message_content", JsonObject().apply {
                addProperty("@type", "inputMessageText")
                add("text", JsonObject().apply {
                    addProperty("@type", "formattedText")
                    addProperty("text", plainText)
                    add("entities", JsonArray())
                })
                addProperty("disable_web_page_preview", disableWebPreview)
                addProperty("clear_draft", false)
            })
            add("reply_markup", replyMarkup?.toJsonObject())
        }.let {
            val result = executeRequest(it.toString(), server.defaultTimeout)
            if (result == null) {
                throw IOException("Timeout")
            } else {
                TlRpcJsonObject.throwRemoteApiExceptionIfError(result)
            }
        }
    }

    suspend fun executeSendMessageEx(
        chatId: Long, msgThreadId: Long, replyMsgId: Long,
        options: JsonObject?, replyMarkup: JsonObject?,
        message: JsonObject, timeout: Int
    ): JsonObject? {
        TlRpcJsonObject.checkTypeNullable(options, "messageSendOptions")
        TlRpcJsonObject.checkTypeNullable(replyMarkup, "ReplyMarkup")
        val request = JsonObject().apply {
            addProperty("@type", "sendMessage")
            addProperty("chat_id", chatId)
            addProperty("message_thread_id", msgThreadId)
            addProperty("reply_to_message_id", replyMsgId)
            add("options", options)
            add("reply_markup", replyMarkup)
            add("input_message_content", message)
        }
        val resp = executeRequest(request.toString(), timeout)
        return if (resp == null) null else JsonParser.parseString(resp).asJsonObject
    }

    private suspend fun executeRequest(request: String, timeout: Int): String? {
        return server.executeRequestSuspended(request, this, timeout)
    }

    override fun hashCode(): Int {
        return clientIndex
    }

    override fun toString(): String {
        val u = username
        return if (u != null) {
            "$name (@$u, $userId)"
        } else {
            "$name ($userId)"
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Bot
        if (clientIndex != other.clientIndex) return false
        return true
    }
}
