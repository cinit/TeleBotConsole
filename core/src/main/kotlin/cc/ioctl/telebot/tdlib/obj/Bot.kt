package cc.ioctl.telebot.tdlib.obj

import cc.ioctl.telebot.EventHandler
import cc.ioctl.telebot.TransactionDispatcher
import cc.ioctl.telebot.tdlib.RobotServer
import cc.ioctl.telebot.tdlib.tlrpc.RemoteApiException
import cc.ioctl.telebot.tdlib.tlrpc.TlRpcJsonObject
import cc.ioctl.telebot.tdlib.tlrpc.api.InputFile
import cc.ioctl.telebot.tdlib.tlrpc.api.auth.SetTdlibParameters
import cc.ioctl.telebot.tdlib.tlrpc.api.msg.FormattedText
import cc.ioctl.telebot.tdlib.tlrpc.api.msg.Message
import cc.ioctl.telebot.tdlib.tlrpc.api.msg.ReplyMarkup
import cc.ioctl.telebot.util.Condition
import cc.ioctl.telebot.util.IoUtils
import cc.ioctl.telebot.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import org.jetbrains.skija.Image
import java.io.File
import java.io.IOException
import kotlin.time.Duration.Companion.milliseconds

class Bot internal constructor(
    val server: RobotServer,
    val clientIndex: Int,
    designator: String
) : Account() {

    companion object {
        private const val TAG = "Bot"
        const val CHAT_ID_NEGATIVE_NOTATION = -1000000000000L

        @JvmStatic
        fun chatIdToGroupId(chatId: Long): Long {
            if (chatId > CHAT_ID_NEGATIVE_NOTATION) {
                throw IllegalArgumentException("chatId $chatId is not a group chat id")
            }
            return -chatId + CHAT_ID_NEGATIVE_NOTATION
        }

        @JvmStatic
        fun groupIdToChatId(groupId: Long): Long {
            if (groupId <= 0) {
                throw IllegalArgumentException("groupId $groupId is not a group chat id")
            }
            return -groupId + CHAT_ID_NEGATIVE_NOTATION
        }

        @JvmStatic
        fun isTrivialPrivateChat(chatId: Long): Boolean {
            return chatId > 0
        }

        @JvmStatic
        fun isTrivialUserSender(senderId: Long): Boolean {
            return senderId > 0
        }

        @JvmStatic
        fun isAnonymousSender(senderId: Long): Boolean {
            return senderId < CHAT_ID_NEGATIVE_NOTATION
        }
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

    private val mDataBaseDir = File(server.tdlibDir, designator)

    private val mOnRecvMsgListeners = HashSet<EventHandler.MessageListenerV1>(1)
    private val mOnGroupEventListeners = HashSet<EventHandler.GroupPermissionListenerV1>(1)
    private val mGroupMemberJoinRequestListenerV1 = HashSet<EventHandler.GroupMemberJoinRequestListenerV1>(1)
    private val mCallbackQueryListeners = HashSet<EventHandler.CallbackQueryListenerV1>(1)

    init {
        IoUtils.mkdirsOrThrow(mDataBaseDir)
    }

    internal data class TransientMessageHolder(
        val chatId: Long,
        val oldMsgId: Long,
        val oldMessage: Message,
        val time: Long,
        val lock: Mutex,
        @Volatile var newMessage: Message? = null,
        @Volatile var errorMsg: String? = null
    )

    private val mTransientMessageLock = Any()

    // key is "chatId_oldMsgId", guarded by mTransientMessageLock
    private val mTransientMessages = HashMap<String, TransientMessageHolder>(1)

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
            "updateMessageContent" -> {
                handleUpdateMessageContent(event)
            }
            "updateFile" -> {
                handleUpdateFile(event)
            }
            "updateMessageEdited" -> {
                handleUpdateMessageEdited(event)
            }
            "updateBasicGroup",
            "updateSupergroup" -> {
                handleUpdateTypedGroup(event, type)
            }
            "updateNewChat" -> {
                handleUpdateNewChat(event)
            }
            "updateChatTitle" -> {
                handleUpdateChatTitle(event)
            }
            "updateChatPermissions" -> {
                handleUpdateChatPermissions(event)
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
            "updateMessageSendFailed" -> {
                handleUpdateMessageSendFailed(event)
            }
            "updateMessageIsPinned" -> {
                handleUpdateMessageIsPinned(event)
            }
            "updateNewCallbackQuery" -> {
                handleUpdateNewCallbackQuery(event)
            }
            "updateChatMember" -> {
                handleUpdateChatMember(event)
            }
            "updateNewChatJoinRequest" -> {
                handleUpdateNewChatJoinRequest(event)
            }
            "updateChatHasProtectedContent" -> {
                handleUpdateChatHasProtectedContent(event)
            }
            "updateChatPhoto" -> {
                handleUpdateChatPhoto(event)
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
        when (type) {
            "updateSupergroup" -> {
                val supergroup = JsonParser.parseString(event).asJsonObject.getAsJsonObject("supergroup")
                val uid = supergroup.get("id").asLong
                val username = supergroup.get("username").asString
                val group = server.getOrNewGroup(uid, this)
                group.isSuperGroup = true
                group.username = username.ifEmpty { null }
                return true
            }
            "updateBasicGroup" -> {
                val basicGroup = JsonParser.parseString(event).asJsonObject.getAsJsonObject("basic_group")
                val uid = basicGroup.get("id").asLong
                Log.d(TAG, "handleUpdateTypedGroup: basicGroup: $basicGroup")
                return true
            }
            else -> {
                Log.e(TAG, "handleUpdateTypedGroup: unexpected type: $type, event: $event")
                return false
            }
        }
    }

    private fun handleUpdateChatTitle(event: String): Boolean {
        val event = JsonParser.parseString(event).asJsonObject
        val chatId = event.get("chat_id").asLong
        val title = event.get("title").asString
        if (chatId < CHAT_ID_NEGATIVE_NOTATION) {
            val gid = chatIdToGroupId(chatId)
            val cachedGroup = server.getCachedGroupWithGroupId(gid)
            if (cachedGroup != null) {
                cachedGroup.name = title
            }
        }
        return true
    }

    private fun handleUpdateChatPhoto(event: String): Boolean {
        val event = JsonParser.parseString(event).asJsonObject
        val chatId = event.get("chat_id").asLong
        Log.d(TAG, "handleUpdateChatPhoto: chatId: $chatId")
        return true
    }

    private fun handleUpdateChatPermissions(event: String): Boolean {
        val obj = JsonParser.parseString(event).asJsonObject
        val chatId = obj.get("chat_id").asLong
        val permissions = obj.get("permissions").asJsonObject
        Log.d(TAG, "handleUpdateChatPermissions: chatId: $chatId, permissions: $permissions")
        for (listener in synchronized(mListenerLock) { mOnGroupEventListeners.toList() }) {
            listener.onChatPermissionsChanged(this, chatId, permissions)
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
        updateUserInfo(userObj)
        return true
    }

    private fun updateUserInfo(userObj: JsonObject): User {
        TlRpcJsonObject.checkTypeNonNull(userObj, "user")
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
        user.isKnown = true
        if (uid > 0 && uid == this.userId) {
            this.firstName = firstName
            this.lastName = lastName.ifEmpty { null }
            this.phoneNumber = phoneNumber.ifEmpty { null }
            this.username = username.ifEmpty { null }
        }
        // check whether uid match local bot
        server.getBotWithUserId(uid)?.let { that ->
            that.firstName = firstName
            that.lastName = lastName.ifEmpty { null }
            that.phoneNumber = phoneNumber.ifEmpty { null }
            that.username = username.ifEmpty { null }
        }
        return user
    }

    private fun handleUpdateDeleteMessages(event: String): Boolean {
        val deleteMessages = JsonParser.parseString(event).asJsonObject
        val chatId = deleteMessages.get("chat_id").asLong
        val messageIds = deleteMessages.get("message_ids").asJsonArray.map { it.asLong }
        val isFromCache = deleteMessages.get("from_cache")?.asBoolean ?: false
        if (isFromCache) {
            // ignore non permanent message deletions
            return true
        }
        // TDLib docs say that some messages being sent can be irrecoverably deleted,
        // in which case updateDeleteMessages will be received instead of updateMessageSendFailed.
        synchronized(mTransientMessageLock) {
            for (msgId in messageIds) {
                val key = "${chatId}_${msgId}"
                val holder = mTransientMessages[key]
                if (holder != null && holder.lock.isLocked) {
                    holder.newMessage = holder.oldMessage
                    holder.errorMsg = "message has been deleted"
                    holder.lock.unlock()
                }
            }
        }
        for (listener in synchronized(mListenerLock) { mOnRecvMsgListeners.toList() }) {
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
        for (listener in synchronized(mListenerLock) { mCallbackQueryListeners.toList() }) {
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
        val msg = Message.fromJsonObject(update.getAsJsonObject("message"))
        val msgId = msg.id
        val senderId = msg.senderId
        val chatId = msg.chatId
        val oldMsgId = update.get("old_message_id").asLong
        var hasOwner = false
        synchronized(mTransientMessageLock) {
            val key = "${chatId}_${oldMsgId}"
            val holder = mTransientMessages[key]
            if (holder != null) {
                holder.newMessage = msg
                val lock = holder.lock
                hasOwner = true
                if (lock.isLocked) {
                    lock.unlock()
                } else {
                    Log.e(TAG, "handleUpdateMessageSendSucceeded: mutex is not locked")
                }
            }
        }
        if (!hasOwner) {
            val logMsg = "handleUpdateMessageSendSucceeded but no owner: " +
                    "chatId=$chatId, msgId=$msgId, oldMsgId=$oldMsgId, senderId=$senderId"
            Log.d(TAG, logMsg)
        }
        return true
    }

    private fun handleUpdateMessageSendFailed(event: String): Boolean {
        val update = JsonParser.parseString(event).asJsonObject
        val msg = Message.fromJsonObject(update.getAsJsonObject("message"))
        val msgId = msg.id
        val senderId = msg.senderId
        val chatId = msg.chatId
        val oldMsgId = update.get("old_message_id").asLong
        val errorMsg = update.get("error_message").asString
        synchronized(mTransientMessageLock) {
            val key = "${chatId}_${oldMsgId}"
            val holder = mTransientMessages[key]
            if (holder != null) {
                holder.newMessage = msg
                holder.errorMsg = errorMsg
                val lock = holder.lock
                if (lock.isLocked) {
                    lock.unlock()
                } else {
                    Log.e(TAG, "handleUpdateMessageSendFailed: mutex is not locked")
                }
            }
        }
        val logMsg = "handleUpdateMessageSendFailed: " +
                "chatId=$chatId, msgId=$msgId, oldMsgId=$oldMsgId, senderId=$senderId"
        Log.w(TAG, logMsg)
        return true
    }

    private fun handleUpdateNewMessage(event: String): Boolean {
        val msg = Message.fromJsonObject(JsonParser.parseString(event).asJsonObject.getAsJsonObject("message"))
        val msgId = msg.id
        val chatId = msg.chatId
        val isOutgoing = msg.isOutgoing
        if (isOutgoing) {
            return true
        }
        val senderId = getSenderId(msg.senderId)
        // call listeners
        for (listener in synchronized(mListenerLock) { mOnRecvMsgListeners.toList() }) {
            listener.onReceiveMessage(this, chatId, senderId, msg)
        }
        return true
    }

    private fun handleUpdateMessageContent(event: String): Boolean {
        val obj = JsonParser.parseString(event).asJsonObject
        val chatId = obj.get("chat_id").asLong
        val msgId = obj.get("message_id").asLong
        val content = obj.getAsJsonObject("new_content")
        // call listeners
        for (listener in synchronized(mListenerLock) { mOnRecvMsgListeners.toList() }) {
            listener.onUpdateMessageContent(this, chatId, msgId, content)
        }
        return true
    }

    private fun handleUpdateFile(event: String): Boolean {
        // Log.v(TAG, "handleUpdateFile: $event")
        return true
    }

    private fun handleUpdateChatHasProtectedContent(event: String): Boolean {
        val obj = JsonParser.parseString(event).asJsonObject
        TlRpcJsonObject.checkTypeNonNull(obj, "updateChatHasProtectedContent")
        val chatId = obj.get("chat_id").asLong
        val hasProtectedContent = obj.get("has_protected_content").asBoolean
        if (chatId < CHAT_ID_NEGATIVE_NOTATION) {
            val groupId = chatIdToGroupId(chatId)
            val group = server.getCachedGroupWithGroupId(groupId)
            if (group != null) {
                group.isContentProtected = hasProtectedContent
            }
        } else {
            Log.e(TAG, "handleUpdateChatHasProtectedContent: chatId=$chatId is not a group")
        }
        return true
    }

    private fun handleUpdateMessageEdited(event: String): Boolean {
        val obj = JsonParser.parseString(event).asJsonObject
        val chatId = obj.get("chat_id").asLong
        val msgId = obj.get("message_id").asLong
        val editDate = obj.get("edit_date").asInt
        for (listener in synchronized(mListenerLock) { mOnRecvMsgListeners.toList() }) {
            listener.onMessageEdited(this, chatId, msgId, editDate)
        }
        return true
    }

    private fun handleUpdateMessageIsPinned(event: String): Boolean {
        val obj = JsonParser.parseString(event).asJsonObject
        val chatId = obj.get("chat_id").asLong
        val msgId = obj.get("message_id").asLong
        val isPinned = obj.get("is_pinned").asBoolean
        for (listener in synchronized(mListenerLock) { mOnRecvMsgListeners.toList() }) {
            listener.onMessagePinned(this, chatId, msgId, isPinned)
        }
        return true
    }

    private fun handleUpdateChatMember(event: String): Boolean {
        val obj = JsonParser.parseString(event).asJsonObject
        val chatId = obj.get("chat_id").asLong
        val userId = obj.get("actor_user_id").asLong
        for (listener in synchronized(mListenerLock) { mOnGroupEventListeners.toList() }) {
            listener.onMemberStatusChanged(this, chatId, userId, obj)
        }
        return true
    }

    private fun handleUpdateNewChatJoinRequest(event: String): Boolean {
        val obj = JsonParser.parseString(event).asJsonObject
        val chatId = obj.get("chat_id").asLong
        val userId = obj.get("request").asJsonObject.get("user_id").asLong
        for (listener in synchronized(mListenerLock) { mGroupMemberJoinRequestListenerV1.toList() }) {
            listener.onMemberJoinRequest(this, chatId, userId, obj)
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
        updateChatInfo(chat)
        return true
    }

    private fun updateChatInfo(chat: JsonObject): JsonObject {
        TlRpcJsonObject.checkTypeNonNull(chat, "chat")
        val chatId = chat.get("id").asLong
        val name = chat.get("title").asString
        val typeImpl = chat["type"].asJsonObject
        when (val chatType = typeImpl.get("@type").asString) {
            "chatTypeSupergroup",
            "chatTypeBasicGroup" -> {
                updateGroupFromChat(chat)
            }
            "chatTypePrivate" -> {
                val uid = typeImpl["user_id"].asLong
                val user = server.getOrNewUser(uid, this)
                user.defaultChatId = chatId
            }
            else -> {
                Log.e(TAG, "handleUpdateNewChat: unexpected chat type: $chatType, chatId=$chatId")
            }
        }
        return chat
    }

    private fun updateGroupFromChat(chatObj: JsonObject): Group {
        TlRpcJsonObject.checkTypeNonNull(chatObj, "chat")
        val chatType = chatObj.get("type").asJsonObject.get("@type").asString
        val chatId = chatObj.get("id").asLong
        val name = chatObj.get("title").asString
        val typeImpl = chatObj["type"].asJsonObject
        when (chatType) {
            "chatTypeSupergroup" -> {
                val gid = typeImpl["supergroup_id"].asLong
                if (chatId != -gid + CHAT_ID_NEGATIVE_NOTATION) {
                    throw AssertionError("chatId=$chatId, gid=$gid")
                }
                val group = server.getOrNewGroup(gid, this)
                group.isSuperGroup = true
                group.name = name
                val photo = chatObj.getAsJsonObject("photo")
                if (photo != null) {
                    group.photo = RemoteFile.fromJsonObject(photo["small"].asJsonObject)
                }
                return group
            }
            "chatTypeBasicGroup" -> {
                val gid = typeImpl["basic_group_id"].asLong
                if (chatId != -gid + CHAT_ID_NEGATIVE_NOTATION) {
                    throw AssertionError("chatId=$chatId, gid=$gid")
                }
                val group = server.getOrNewGroup(gid, this)
                group.isSuperGroup = false
                group.name = name
                // photo is unknown yet
                return group
            }
            else -> {
                throw IllegalArgumentException("updateGroupFromChat: unexpected chat type: $chatType, event: $chatObj")
            }
        }
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
        // Log.v(TAG, "handleUpdateOption: $event")
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
        val param = SetTdlibParameters.Parameter(server.defaultTDLibParametersTemplate)
        param.databaseDirectory = mDataBaseDir.absolutePath
        val request = JsonObject()
        request.addProperty("@type", "setTdlibParameters")
        request.add("parameters", param.toJsonObject())
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

    fun registerGroupEventListener(listener: EventHandler.GroupPermissionListenerV1) {
        synchronized(mListenerLock) {
            mOnGroupEventListeners.add(listener)
        }
    }

    fun unregisterGroupEventListener(listener: EventHandler.GroupPermissionListenerV1) {
        synchronized(mListenerLock) {
            mOnGroupEventListeners.remove(listener)
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

    fun registerGroupMemberJoinRequestListenerV1(listener: EventHandler.GroupMemberJoinRequestListenerV1) {
        synchronized(mListenerLock) {
            mGroupMemberJoinRequestListenerV1.add(listener)
        }
    }

    fun unregisterGroupMemberJoinRequestListenerV1(listener: EventHandler.GroupMemberJoinRequestListenerV1) {
        synchronized(mListenerLock) {
            mGroupMemberJoinRequestListenerV1.remove(listener)
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
        val request = JsonObject().apply {
            addProperty("@type", "sendMessage")
            addProperty("chat_id", chatId)
            add("input_message_content", inputMessageContent)
            add("reply_markup", replyMarkup?.toJsonObject())
            addProperty("message_thread_id", msgThreadId)
            addProperty("reply_to_message_id", replyMsgId)
            add("options", options)
        }
        val until = System.currentTimeMillis() + server.defaultTimeout
        val result = executeRequest(request.toString(), server.defaultTimeout)
            ?: throw IOException("Timeout executing sendMessage")
        val obj = JsonParser.parseString(result).asJsonObject
        TlRpcJsonObject.throwRemoteApiExceptionIfError(obj)
        val oldMsg: Message
        try {
            oldMsg = Message.fromJsonObject(obj)
        } catch (e: ReflectiveOperationException) {
            throw IOException("failed to parse result: $obj", e)
        }
        val oldMsgId = oldMsg.id
        val key = "${chatId}_${oldMsgId}"
        val owner = Mutex(true)
        val holder = TransientMessageHolder(
            chatId, oldMsgId, oldMsg, System.currentTimeMillis(), lock = owner
        )
        synchronized(mTransientMessageLock) {
            mTransientMessages[key] = holder
        }
        while (holder.newMessage == null) {
            val now = System.currentTimeMillis()
            val remain = until - now
            if (remain <= 0) {
                break
            } else {
                try {
                    withTimeout(remain) {
                        owner.lock()
                    }
                } catch (e: TimeoutCancellationException) {
                    break
                }
            }
        }
        synchronized(mTransientMessageLock) {
            if (mTransientMessages.remove(key) != holder) {
                throw AssertionError("mTransientMessages remove check failed")
            }
        }
        val newMessage = holder.newMessage
        if (newMessage == null) {
            throw IOException("Timeout waiting for updateMessageSendSuccess")
        }
        if (holder.errorMsg != null) {
            throw RemoteApiException(holder.errorMsg)
        }
        return newMessage
    }

    @Throws(RemoteApiException::class, IOException::class)
    suspend fun sendMessageForText(
        chatId: Long, text: String,
        replyMarkup: ReplyMarkup? = null,
        disableWebPreview: Boolean = false,
        msgThreadId: Long = 0, replyMsgId: Long = 0
    ): Message {
        return sendMessageForText(
            chatId, FormattedText.forPlainText(text), replyMarkup, disableWebPreview,
            msgThreadId = msgThreadId, replyMsgId = replyMsgId
        )
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

    @Throws(RemoteApiException::class, IOException::class)
    suspend fun resolveMessage(chatId: Long, msgId: Long, invalidate: Boolean = false): Message {
        val request = JsonObject().apply {
            addProperty("@type", "getMessage")
            addProperty("chat_id", chatId)
            addProperty("message_id", msgId)
        }
        val result = executeRequest(request.toString(), server.defaultTimeout)
        if (result == null) {
            throw IOException("Timeout executing getMessage request")
        } else {
            val obj = JsonParser.parseString(result).asJsonObject
            TlRpcJsonObject.throwRemoteApiExceptionIfError(obj)
            return Message.fromJsonObject(obj)
        }
    }

    @Throws(RemoteApiException::class, IOException::class)
    suspend fun resolveUser(userId: Long, invalidate: Boolean = false): User {
        if (userId <= 0) {
            throw IllegalArgumentException("userId $userId is not valid")
        }
        if (!invalidate) {
            val cached = server.getCachedUserWithUserId(userId)
            if (cached != null && cached.isKnown) {
                return cached
            }
        }
        val request = JsonObject().apply {
            addProperty("@type", "getUser")
            addProperty("user_id", userId)
        }
        val result = executeRequest(request.toString(), server.defaultTimeout)
        if (result == null) {
            throw IOException("Timeout executing getUser request")
        } else {
            val obj = JsonParser.parseString(result).asJsonObject
            TlRpcJsonObject.throwRemoteApiExceptionIfError(obj)
            return updateUserInfo(obj)
        }
    }

    @Throws(RemoteApiException::class, IOException::class)
    suspend fun resolveGroup(groupId: Long, invalidate: Boolean = false): Group {
        if (groupId <= 0) {
            throw IllegalArgumentException("groupId $groupId is not valid")
        }
        if (!invalidate) {
            val cached = server.getCachedGroupWithGroupId(groupId)
            if (cached != null && cached.isKnown) {
                return cached
            }
        }
        // currently, we don't know whether it is a basic group or supergroup
        val request1 = JsonObject().apply {
            addProperty("@type", "getChat")
            val chatId = -groupId + CHAT_ID_NEGATIVE_NOTATION
            addProperty("chat_id", chatId)
        }
        val result1 = executeRequest(request1.toString(), server.defaultTimeout)
            ?: throw IOException("Timeout executing getChat request")
        val chatObj = JsonParser.parseString(result1).asJsonObject
        TlRpcJsonObject.throwRemoteApiExceptionIfError(chatObj)
        TlRpcJsonObject.checkTypeNonNull(chatObj, "chat")
        return when (val chatType = chatObj.get("type").asJsonObject.get("@type").asString) {
            "chatTypeBasicGroup" -> {
                updateGroupFromChat(chatObj)
            }
            "chatTypeSupergroup" -> {
                updateGroupFromChat(chatObj)
            }
            else -> throw IllegalArgumentException("Unknown chat type: $chatType")
        }
    }

    @Throws(RemoteApiException::class, IOException::class)
    suspend fun resolveChat(chatId: Long, invalidate: Boolean = false): JsonObject {
        if (chatId == 0L) {
            throw IllegalArgumentException("chatId $chatId is not valid")
        }
        val request1 = JsonObject().apply {
            addProperty("@type", "getChat")
            addProperty("chat_id", chatId)
        }
        val result1 = executeRequest(request1.toString(), server.defaultTimeout)
            ?: throw IOException("Timeout executing getChat request")
        val chatObj = JsonParser.parseString(result1).asJsonObject
        TlRpcJsonObject.throwRemoteApiExceptionIfError(chatObj)
        TlRpcJsonObject.checkTypeNonNull(chatObj, "chat")
        return chatObj
    }

    @Throws(RemoteApiException::class, IOException::class)
    suspend fun getGroupMember(groupId: Long, userId: Long): JsonObject {
        if (groupId <= 0) {
            throw IllegalArgumentException("groupId $groupId is not valid")
        }
        if (userId <= 0) {
            throw IllegalArgumentException("userId $userId is not valid")
        }
        val request1 = JsonObject().apply {
            addProperty("@type", "getChatMember")
            addProperty("chat_id", groupIdToChatId(groupId))
            add("member_id", JsonObject().apply {
                addProperty("@type", "messageSenderUser")
                addProperty("user_id", userId)
            })
        }
        val result1 = executeRequest(request1.toString(), server.defaultTimeout)
            ?: throw IOException("Timeout executing getChatMember request")
        val chatObj = JsonParser.parseString(result1).asJsonObject
        TlRpcJsonObject.throwRemoteApiExceptionIfError(chatObj)
        TlRpcJsonObject.checkTypeNonNull(chatObj, "chatMember")
        return chatObj
    }

    @Throws(RemoteApiException::class, IOException::class)
    suspend fun processChatJoinRequest(chatId: Long, userId: Long, approve: Boolean) {
        if (chatId > CHAT_ID_NEGATIVE_NOTATION) {
            throw IllegalArgumentException("chatId $chatId is not a valid group chat id")
        }
        if (userId <= 0) {
            throw IllegalArgumentException("userId $userId is not a valid user id")
        }
        val request = JsonObject().apply {
            addProperty("@type", "processChatJoinRequest")
            addProperty("chat_id", chatId)
            addProperty("user_id", userId)
            addProperty("approve", approve)
        }
        val result = executeRequest(request.toString(), server.defaultTimeout)
        if (result == null) {
            throw IOException("Timeout executing processChatJoinRequest request")
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
