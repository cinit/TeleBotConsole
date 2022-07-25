package cc.ioctl.telebot.tdlib.obj

import cc.ioctl.telebot.tdlib.RobotServer
import cc.ioctl.telebot.tdlib.tlrpc.RemoteApiException
import cc.ioctl.telebot.tdlib.tlrpc.TlRpcJsonObject
import cc.ioctl.telebot.tdlib.tlrpc.api.InputFile
import cc.ioctl.telebot.tdlib.tlrpc.api.msg.FormattedText
import cc.ioctl.telebot.tdlib.tlrpc.api.msg.ReplyMarkup
import cc.ioctl.telebot.EventHandler
import cc.ioctl.telebot.TransactionDispatcher
import cc.ioctl.telebot.tdlib.tlrpc.api.msg.Message
import cc.ioctl.telebot.util.Condition
import cc.ioctl.telebot.util.IoUtils
import cc.ioctl.telebot.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import org.jetbrains.skija.Image
import java.io.File
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
    private val mCallbackQueryListeners = HashSet<EventHandler.CallbackQueryListenerV1>(1)

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
            "updateMessageSendSucceeded" -> {
                handleUpdateMessageSendSucceeded(event)
            }
            "updateNewCallbackQuery" -> {
                handleUpdateNewCallbackQuery(event)
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

    private fun handleUpdateNewCallbackQuery(event: String): Boolean {
        val callbackQuery = JsonParser.parseString(event).asJsonObject
        val chatId = callbackQuery.get("chat_id").asLong
        val senderId = callbackQuery.get("sender_user_id").asLong
        val queryId = callbackQuery.get("id").asString
        val msgId = callbackQuery.get("message_id").asLong
        // call listeners
        var handled = false
        for (listener in mCallbackQueryListeners) {
            if (listener.onCallbackQuery(this, callbackQuery, queryId, chatId, senderId, msgId)) {
                handled = true
            }
        }
        if (!handled) {
            Log.w(TAG, "A callback query $queryId in chat $chatId, sender $senderId was not handled")
        }
        return true
    }

    private fun handleUpdateMessageSendSucceeded(event: String): Boolean {
        val update = JsonParser.parseString(event).asJsonObject
        val msg = update.getAsJsonObject("message")
        val msgId = msg.get("id").asLong
        val senderId = getSenderId(msg.getAsJsonObject("sender_id"))
        val chatId = msg.get("chat_id").asLong
        val oldMsgId = update.get("old_message_id").asLong
        val logMsg = "handleUpdateMessageSendSucceeded: " +
                "chatId=$chatId, msgId=$msgId, oldMsgId=$oldMsgId, senderId=$senderId"
        Log.d(TAG, logMsg)
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
        val senderId = getSenderId(obj["sender_id"].asJsonObject)
        // call listeners
        for (listener in mOnRecvMsgListeners) {
            listener.onReceiveMessage(this, chatId, senderId, obj)
        }
        return true
    }

    private fun getSenderId(obj: JsonObject): Long {
        return when (val type = obj.get("@type").asString) {
            "messageSenderUser" -> {
                obj.get("user_id").asLong
            }
            "messageSenderChat" -> {
                obj.get("chat_id").asLong
            }
            else -> {
                throw IllegalArgumentException("unexpected sender type: $type")
            }
        }
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

    fun registerCallbackQueryListener(listener: EventHandler.CallbackQueryListenerV1) {
        synchronized(mListenerLock) {
            mCallbackQueryListeners.add(listener)
        }
    }

    fun unregisterCallbackQueryListener(listener: EventHandler.CallbackQueryListenerV1) {
        synchronized(mListenerLock) {
            mCallbackQueryListeners.remove(listener)
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
    suspend fun sendMessageRawEx(
        chatId: Long, inputMessageContent: JsonObject,
        replyMarkup: ReplyMarkup? = null,
        msgThreadId: Long = 0, replyMsgId: Long = 0,
        options: JsonObject? = null
    ): Message {
        JsonObject().apply {
            addProperty("@type", "sendMessage")
            addProperty("chat_id", chatId)
            add("input_message_content", inputMessageContent)
            add("reply_markup", replyMarkup?.toJsonObject())
            addProperty("message_thread_id", msgThreadId)
            addProperty("reply_to_message_id", replyMsgId)
            add("options", options)
        }.let {
            val result = executeRequest(it.toString(), server.defaultTimeout)
            if (result == null) {
                throw IOException("Timeout")
            } else {
                val obj = JsonParser.parseString(result).asJsonObject
                TlRpcJsonObject.throwRemoteApiExceptionIfError(obj)
                try {
                    val msg = Message.fromJsonObject(obj)
                    Log.d(TAG, "sendMessageRaw: oldMsgId: ${msg.id}")
                    return msg
                } catch (e: ReflectiveOperationException) {
                    throw IOException("failed to parse result: $obj", e)
                }
            }
        }
    }

    @Throws(RemoteApiException::class, IOException::class)
    suspend fun sendMessageForText(
        chatId: Long, text: String,
        replyMarkup: ReplyMarkup? = null,
        disableWebPreview: Boolean = false
    ): Message {
        return sendMessageForText(chatId, FormattedText.forPlainText(text), replyMarkup, disableWebPreview)
    }

    @Throws(RemoteApiException::class, IOException::class)
    suspend fun sendMessageForText(
        chatId: Long, textMsg: FormattedText,
        replyMarkup: ReplyMarkup? = null,
        disableWebPreview: Boolean = true,
        msgThreadId: Long = 0, replyMsgId: Long = 0,
        options: JsonObject? = null
    ): Message {
        val msgObj = JsonObject().apply {
            addProperty("@type", "inputMessageText")
            add("text", textMsg.toJsonObject())
            addProperty("disable_web_page_preview", disableWebPreview)
            addProperty("clear_draft", false)
        }
        return sendMessageRawEx(chatId, msgObj, replyMarkup, msgThreadId, replyMsgId, options)
    }

    @Throws(RemoteApiException::class, IOException::class)
    suspend fun sendMessageForPhoto(
        chatId: Long, file: File, caption: String,
        replyMarkup: ReplyMarkup? = null, ttl: Int = 0,
        msgThreadId: Long = 0, replyMsgId: Long = 0,
        options: JsonObject? = null
    ): Message {
        return sendMessageForPhoto(
            chatId, file, FormattedText.forPlainText(caption), replyMarkup,
            ttl, msgThreadId, replyMsgId, options
        )
    }

    @Throws(RemoteApiException::class, IOException::class)
    suspend fun sendMessageForPhoto(
        chatId: Long, file: File, caption: FormattedText?,
        replyMarkup: ReplyMarkup? = null, ttl: Int = 0,
        msgThreadId: Long = 0, replyMsgId: Long = 0,
        options: JsonObject? = null
    ): Message {
        val inputFile = InputFile.fromLocalFileToJsonObject(file)
        val imgHeight: Int
        val imgWidth: Int
        // get image size
        withContext(Dispatchers.IO) {
            val bytes = IoUtils.readFully(file.inputStream())
            Image.makeFromEncoded(bytes).use { img ->
                imgHeight = img.height
                imgWidth = img.width
            }
        }
        val msgObj = JsonObject().apply {
            addProperty("@type", "inputMessagePhoto")
            add("photo", inputFile)
            addProperty("width", imgWidth)
            addProperty("height", imgHeight)
            add("caption", caption?.toJsonObject())
            addProperty("ttl", ttl)
        }
        return sendMessageRawEx(chatId, msgObj, replyMarkup, msgThreadId, replyMsgId, options)
    }

    @Throws(RemoteApiException::class, IOException::class)
    suspend fun editMessageCaption(
        chatId: Long, msgId: Long,
        caption: FormattedText,
        replyMarkup: ReplyMarkup? = null
    ): JsonObject {
        JsonObject().apply {
            addProperty("@type", "editMessageCaption")
            addProperty("chat_id", chatId)
            addProperty("message_id", msgId)
            add("caption", caption.toJsonObject())
            add("reply_markup", replyMarkup?.toJsonObject())
        }.let {
            val result = executeRequest(it.toString(), server.defaultTimeout)
            if (result == null) {
                throw IOException("Timeout")
            } else {
                val obj = JsonParser.parseString(result).asJsonObject
                TlRpcJsonObject.throwRemoteApiExceptionIfError(obj)
                return obj
            }
        }
    }

    @Throws(RemoteApiException::class, IOException::class)
    suspend fun deleteMessages(
        chatId: Long, msgIds: Array<Long>
    ): JsonObject {
        if (msgIds.isEmpty()) {
            throw IllegalArgumentException("msgIds is empty")
        }
        JsonObject().apply {
            addProperty("@type", "deleteMessages")
            addProperty("chat_id", chatId)
            add("message_ids", JsonArray().apply {
                msgIds.forEach { add(it) }
            })
            addProperty("revoke", true)
        }.let {
            val result = executeRequest(it.toString(), server.defaultTimeout)
            if (result == null) {
                throw IOException("Timeout")
            } else {
                val obj = JsonParser.parseString(result).asJsonObject
                TlRpcJsonObject.throwRemoteApiExceptionIfError(obj)
                return obj
            }
        }
    }

    @Throws(RemoteApiException::class, IOException::class)
    suspend fun deleteMessages(
        chatId: Long, msgIds: List<Long>
    ): JsonObject {
        return deleteMessages(chatId, msgIds.toTypedArray())
    }

    @Throws(RemoteApiException::class, IOException::class)
    suspend fun deleteMessage(
        chatId: Long, msgId: Long
    ): JsonObject {
        return deleteMessages(chatId, arrayOf(msgId))
    }

    @Throws(RemoteApiException::class, IOException::class)
    suspend fun editMessageTextEx(
        chatId: Long, msgId: Long,
        inputMessageContent: JsonObject,
        replyMarkup: ReplyMarkup? = null
    ): JsonObject {
        TlRpcJsonObject.checkTypeNonNull(inputMessageContent, "inputMessageText")
        JsonObject().apply {
            addProperty("@type", "editMessageText")
            addProperty("chat_id", chatId)
            addProperty("message_id", msgId)
            add("input_message_content", inputMessageContent)
            add("reply_markup", replyMarkup?.toJsonObject())
        }.let {
            val result = executeRequest(it.toString(), server.defaultTimeout)
            if (result == null) {
                throw IOException("Timeout")
            } else {
                val obj = JsonParser.parseString(result).asJsonObject
                TlRpcJsonObject.throwRemoteApiExceptionIfError(obj)
                return obj
            }
        }
    }

    @Throws(RemoteApiException::class, IOException::class)
    suspend fun editMessageText(
        chatId: Long, msgId: Long,
        formattedText: FormattedText,
        replyMarkup: ReplyMarkup? = null,
        disableWebPreview: Boolean = true
    ): JsonObject {
        return editMessageTextEx(
            chatId, msgId,
            JsonObject().apply {
                addProperty("@type", "inputMessageText")
                add("text", formattedText.toJsonObject())
                addProperty("disable_web_page_preview", disableWebPreview)
                addProperty("clear_draft", false)
            },
            replyMarkup
        )
    }

    @Throws(RemoteApiException::class, IOException::class)
    suspend fun answerCallbackQuery(
        queryId: String, text: String?, showAlert: Boolean,
        url: String? = null, cacheTime: Int = 0
    ) {
        val request = JsonObject().apply {
            addProperty("@type", "answerCallbackQuery")
            addProperty("callback_query_id", queryId)
            addProperty("text", text)
            addProperty("show_alert", showAlert)
            addProperty("url", url)
            addProperty("cache_time", cacheTime)
        }
        val result = executeRequest(request.toString(), server.defaultTimeout)
        if (result == null) {
            throw IOException("Timeout")
        } else {
            val obj = JsonParser.parseString(result).asJsonObject
            TlRpcJsonObject.throwRemoteApiExceptionIfError(obj)
        }
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
