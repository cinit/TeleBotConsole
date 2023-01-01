package cc.ioctl.telebot.tdlib.obj

import cc.ioctl.telebot.EventHandler
import cc.ioctl.telebot.TransactionDispatcher
import cc.ioctl.telebot.tdlib.RobotServer
import cc.ioctl.telebot.tdlib.obj.SessionInfo.Companion.CHAT_ID_NEGATIVE_NOTATION
import cc.ioctl.telebot.tdlib.obj.SessionInfo.Companion.chatIdToGroupId
import cc.ioctl.telebot.tdlib.obj.SessionInfo.Companion.groupIdToChatId
import cc.ioctl.telebot.tdlib.tlrpc.BaseTlRpcJsonObject
import cc.ioctl.telebot.tdlib.tlrpc.RemoteApiException
import cc.ioctl.telebot.tdlib.tlrpc.api.InputFile
import cc.ioctl.telebot.tdlib.tlrpc.api.auth.SetTdlibParameters
import cc.ioctl.telebot.tdlib.tlrpc.api.channel.ChannelMemberStatusEvent
import cc.ioctl.telebot.tdlib.tlrpc.api.channel.ChatJoinRequest
import cc.ioctl.telebot.tdlib.tlrpc.api.channel.ChatPermissions
import cc.ioctl.telebot.tdlib.tlrpc.api.msg.FormattedText
import cc.ioctl.telebot.tdlib.tlrpc.api.msg.Message
import cc.ioctl.telebot.tdlib.tlrpc.api.msg.ReplyMarkup
import cc.ioctl.telebot.tdlib.tlrpc.api.query.CallbackQuery
import cc.ioctl.telebot.util.Condition
import cc.ioctl.telebot.util.IoUtils
import cc.ioctl.telebot.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.jetbrains.skija.Image
import java.io.File
import java.io.IOException
import kotlin.time.Duration.Companion.milliseconds

class Bot internal constructor(
    val server: RobotServer, val clientIndex: Int, designator: String
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

    private val mDataBaseDir = File(server.tdlibDir, designator)

    private val mOnRecvMsgListeners = HashSet<EventHandler.MessageListenerV1>(1)
    private val mOnGroupEventListeners = HashSet<EventHandler.GroupPermissionListenerV2>(1)
    private val mGroupMemberJoinRequestListenerV1 = HashSet<EventHandler.GroupMemberJoinRequestListenerV2>(1)
    private val mCallbackQueryListeners = HashSet<EventHandler.CallbackQueryListenerV2>(1)

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
        @Volatile var errorCode: Int = 0,
        @Volatile var errorMsg: String? = null
    )

    private val mTransientMessageLock = Any()

    // key is "chatId_oldMsgId", guarded by mTransientMessageLock
    private val mTransientMessages = HashMap<String, TransientMessageHolder>(1)

    private var mDefaultLogOnlyErrorHandler = object : TransactionDispatcher.TransactionCallbackV1 {
        override fun onEvent(event: JsonObject, bot: Bot?, type: String): Boolean {
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
        UNINITIALIZED, WAIT_TOKEN, WAIT_CODE, WAIT_PASSWORD, WAIT_RESPONSE, AUTHORIZED, INVALID_CREDENTIALS, INVALID_STATE, CLOSED
    }

    suspend fun onReceiveTDLibEventBlocking(event: JsonObject, type: String): Boolean {
        return runBlocking {
            withContext(Dispatchers.Default) {
                return@withContext handleTDLibEvent(event, type)
            }
        }
    }

    suspend fun onReceiveTDLibEvent(event: JsonObject, type: String): Boolean {
        return handleTDLibEvent(event, type)
    }

    private suspend fun handleTDLibEvent(event: JsonObject, type: String): Boolean {
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
            "updateBasicGroup", "updateSupergroup" -> {
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
            "updateSelectedBackground", "updateFileDownloads",
            "updateChatThemes", "updateDiceEmojis", "updateDefaultReactionType",
            "updateAnimationSearchParameters", "updateRecentStickers", "updateReactions", "updateChatPosition" -> {
                // ignore
                true
            }
            else -> {
                Log.e(TAG, "onReceiveTDLibEvent: unexpected type: $type, event: $event")
                false
            }
        }
    }

    private fun handleUpdateTypedGroup(event: JsonObject, type: String): Boolean {
        when (type) {
            "updateSupergroup" -> {
                val supergroup = event.getAsJsonObject("supergroup")
                val uid = supergroup.get("id").asLong
                //val username = supergroup.get("username").asString
                val group = server.getOrNewGroup(uid, this)
                group.isSuperGroup = true
                //group.username = username.ifEmpty { null }
                return true
            }
            "updateBasicGroup" -> {
                val basicGroup = event.getAsJsonObject("basic_group")
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

    private fun handleUpdateChatTitle(event: JsonObject): Boolean {
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

    private fun handleUpdateChatPhoto(event: JsonObject): Boolean {
        val event = event
        val chatId = event.get("chat_id").asLong
        Log.d(TAG, "handleUpdateChatPhoto: chatId: $chatId")
        return true
    }

    private fun handleUpdateChatPermissions(obj: JsonObject): Boolean {
        val chatId = obj.get("chat_id").asLong
        val permissions = obj.get("permissions").asJsonObject
        check(chatId < 0) { "handleUpdateChatMember: chatId=$chatId is not a group" }
        val gid = if (chatId < CHAT_ID_NEGATIVE_NOTATION) chatIdToGroupId(chatId) else -chatId
        val perm = ChatPermissions.fromJsonObject(ChatPermissions::class.java, permissions)
        Log.d(TAG, "handleUpdateChatPermissions: gid: $gid, permissions: $permissions")
        for (listener in synchronized(mListenerLock) { mOnGroupEventListeners.toList() }) {
            listener.onGroupDefaultPermissionsChanged(this, gid, perm)
        }
        return true
    }

    private fun handleUpdateConnectionState(event: JsonObject): Boolean {
        val state = event.get("state").asJsonObject.get("@type").asString
        Log.d(TAG, "handleUpdateConnectionState: $state")
        return true
    }

    private fun handleUpdateUser(event: JsonObject): Boolean {
        val userObj = event.getAsJsonObject("user")
        updateUserInfo(userObj)
        return true
    }

    private fun updateUserInfo(userObj: JsonObject): User {
        BaseTlRpcJsonObject.checkTypeNonNull(userObj, "user")
        val uid = userObj.get("id").asLong
        val firstName = userObj.get("first_name").asString
        val lastName = userObj.get("last_name").asString
        val username = userObj["usernames"].asJsonObject["active_usernames"].asJsonArray.firstOrNull()?.asString
        val phoneNumber = userObj.get("phone_number").asString
        val languageCode = userObj.get("language_code").asString
        val user = server.getOrNewUser(uid, this)
        user.firstName = firstName
        user.lastName = lastName.ifEmpty { null }
        user.username = username?.ifEmpty { null }
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
            this.username = username?.ifEmpty { null }
        }
        // check whether uid match local bot
        server.getBotWithUserId(uid)?.let { that ->
            that.firstName = firstName
            that.lastName = lastName.ifEmpty { null }
            that.phoneNumber = phoneNumber.ifEmpty { null }
            that.username = username?.ifEmpty { null }
        }
        return user
    }

    private fun handleUpdateDeleteMessages(event: JsonObject): Boolean {
        val deleteMessages = event
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
                    holder.errorCode = 500
                    holder.errorMsg = "message has been deleted"
                    holder.lock.unlock()
                }
            }
        }
        val si = SessionInfo.forTDLibChatId(chatId)
        for (listener in synchronized(mListenerLock) { mOnRecvMsgListeners.toList() }) {
            listener.onDeleteMessages(this, si, messageIds)
        }
        return true
    }

    private fun handleUpdateNewCallbackQuery(event: JsonObject): Boolean {
        val callbackQuery = event
        val chatId = callbackQuery.get("chat_id").asLong
        val senderId = callbackQuery.get("sender_user_id").asLong
        val queryId = callbackQuery.get("id").asString
        val msgId = callbackQuery.get("message_id").asLong
        // call listeners
        var handled = false
        val query = CallbackQuery.fromJsonObject(callbackQuery)
        for (listener in synchronized(mListenerLock) { mCallbackQueryListeners.toList() }) {
            if (listener.onCallbackQuery(this, query)) {
                handled = true
            }
        }
        if (!handled) {
            Log.w(TAG, "A callback query $queryId in chat $chatId, sender $senderId was not handled")
        }
        return true
    }

    private fun handleUpdateMessageSendSucceeded(event: JsonObject): Boolean {
        val update = event
        val msg = Message.fromJsonObject(update.getAsJsonObject("message"))
        val msgId = msg.id
        val senderId = msg.senderId
        val si = msg.sessionInfo
        val oldMsgId = update.get("old_message_id").asLong
        var hasOwner = false
        synchronized(mTransientMessageLock) {
            val key = "${si.toTDLibChatId()}_${oldMsgId}"
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
            val logMsg =
                "handleUpdateMessageSendSucceeded but no owner: " + "$si, msgId=$msgId, oldMsgId=$oldMsgId, senderId=$senderId"
            Log.d(TAG, logMsg)
        }
        return true
    }

    private fun handleUpdateMessageSendFailed(event: JsonObject): Boolean {
        val update = event
        val msg = Message.fromJsonObject(update.getAsJsonObject("message"))
        val msgId = msg.id
        val si = msg.sessionInfo
        val senderId = msg.senderId
        val oldMsgId = update.get("old_message_id").asLong
        val errorMsg = update.get("error_message").asString
        val errorCode = update.get("error_code").asInt
        synchronized(mTransientMessageLock) {
            val key = "${si.toTDLibChatId()}_${oldMsgId}"
            val holder = mTransientMessages[key]
            if (holder != null) {
                holder.newMessage = msg
                holder.errorCode = errorCode
                holder.errorMsg = errorMsg
                val lock = holder.lock
                if (lock.isLocked) {
                    lock.unlock()
                } else {
                    Log.e(TAG, "handleUpdateMessageSendFailed: mutex is not locked")
                }
            }
        }
        val logMsg = "handleUpdateMessageSendFailed: " + "$si, msgId=$msgId, oldMsgId=$oldMsgId, senderId=$senderId"
        Log.w(TAG, logMsg)
        return true
    }

    private fun handleUpdateNewMessage(event: JsonObject): Boolean {
        val msg = Message.fromJsonObject(event.getAsJsonObject("message"))
        val msgId = msg.id
        val si = msg.sessionInfo
        val isOutgoing = msg.isOutgoing
        if (isOutgoing) {
            return true
        }
        val senderId = getSenderId(msg.senderId)
        // call listeners
        for (listener in synchronized(mListenerLock) { mOnRecvMsgListeners.toList() }) {
            listener.onReceiveMessage(this, si, senderId, msg)
        }
        return true
    }

    private fun handleUpdateMessageContent(event: JsonObject): Boolean {
        val obj = event
        val chatId = obj.get("chat_id").asLong
        val msgId = obj.get("message_id").asLong
        val content = obj.getAsJsonObject("new_content")
        val si = SessionInfo.forTDLibChatId(chatId)
        // call listeners
        for (listener in synchronized(mListenerLock) { mOnRecvMsgListeners.toList() }) {
            listener.onUpdateMessageContent(this, si, msgId, content)
        }
        return true
    }

    private fun handleUpdateFile(event: JsonObject): Boolean {
        // Log.v(TAG, "handleUpdateFile: $event")
        return true
    }

    private fun handleUpdateChatHasProtectedContent(event: JsonObject): Boolean {
        val obj = event
        BaseTlRpcJsonObject.checkTypeNonNull(obj, "updateChatHasProtectedContent")
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

    private fun handleUpdateMessageEdited(event: JsonObject): Boolean {
        val obj = event
        val chatId = obj.get("chat_id").asLong
        val msgId = obj.get("message_id").asLong
        val editDate = obj.get("edit_date").asInt
        val si = SessionInfo.forTDLibChatId(chatId)
        for (listener in synchronized(mListenerLock) { mOnRecvMsgListeners.toList() }) {
            listener.onMessageEdited(this, si, msgId, editDate)
        }
        return true
    }

    private fun handleUpdateMessageIsPinned(event: JsonObject): Boolean {
        val obj = event
        val chatId = obj.get("chat_id").asLong
        val msgId = obj.get("message_id").asLong
        val isPinned = obj.get("is_pinned").asBoolean
        val si = SessionInfo.forTDLibChatId(chatId)
        for (listener in synchronized(mListenerLock) { mOnRecvMsgListeners.toList() }) {
            listener.onMessagePinned(this, si, msgId, isPinned)
        }
        return true
    }

    private fun handleUpdateChatMember(obj: JsonObject): Boolean {
        val chatId = obj.get("chat_id").asLong
        if (chatId < 0) {
            val groupId = SessionInfo.chatIdToGroupId(chatId)
            val memberObj = obj["new_chat_member"].asJsonObject
            val userId = when (val memberType = memberObj["member_id"].asJsonObject["@type"].asString) {
                "messageSenderUser" -> {
                    memberObj["member_id"].asJsonObject["user_id"].asLong
                }
                "messageSenderChat" -> {
                    -SessionInfo.chatIdToChannelId(memberObj["member_id"].asJsonObject["chat_id"].asLong)
                }
                else -> {
                    error("unknown member type $memberType")
                }
            }
            check(userId != 0L) { "handleUpdateChatMember: userId=$userId is invalid" }
            check(groupId > 0) { "handleUpdateChatMember: groupId=$groupId is not a group" }
            val newStatus = obj["new_chat_member"].asJsonObject["status"].asJsonObject
            server.getCachedGroupWithGroupId(groupId)?.updateChatMemberPermissionStatus(userId, newStatus)
            server.getCachedChannelWithChannelId(groupId)?.updateChatMemberPermissionStatus(userId, newStatus)
            val event = ChannelMemberStatusEvent.fromJsonObject(obj)
            for (listener in synchronized(mListenerLock) { mOnGroupEventListeners.toList() }) {
                listener.onMemberStatusChanged(this, groupId, userId, event)
            }
            return true
        } else {
            val userId = chatId
            Log.i(TAG, obj.toString())
            return true
        }
    }

    private fun handleUpdateNewChatJoinRequest(event: JsonObject): Boolean {
        val chatId = event.get("chat_id").asLong
        val userId = event.get("request").asJsonObject.get("user_id").asLong
        val groupId = SessionInfo.chatIdToGroupId(chatId)
        val request = ChatJoinRequest.fromJsonObject(ChatJoinRequest::class.java, event.get("request").asJsonObject)
        for (listener in synchronized(mListenerLock) { mGroupMemberJoinRequestListenerV1.toList() }) {
            listener.onMemberJoinRequest(this, groupId, userId, request)
        }
        return true
    }

    private fun getSenderId(obj: JsonObject): Long {
        return when (val type = obj.get("@type").asString) {
            "messageSenderUser" -> {
                obj.get("user_id").asLong
            }
            "messageSenderChat" -> {
                -SessionInfo.chatIdToChannelId(obj.get("chat_id").asLong)
            }
            else -> {
                error("unexpected sender type: $type")
            }
        }
    }

    private fun handleUpdateNewChat(event: JsonObject): Boolean {
        val chat = event.getAsJsonObject("chat")
        updateChatInfo(chat)
        return true
    }

    private fun updateChatInfo(chat: JsonObject): JsonObject {
        BaseTlRpcJsonObject.checkTypeNonNull(chat, "chat")
        val chatId = chat.get("id").asLong
        val name = chat.get("title").asString
        val typeImpl = chat["type"].asJsonObject
        when (val chatType = typeImpl.get("@type").asString) {
            "chatTypeSupergroup", "chatTypeBasicGroup" -> {
                val isChannel = chatType == "chatTypeSupergroup" && typeImpl.get("is_channel").asBoolean
                if (isChannel) {
                    updateChannelFromChat(chat)
                } else {
                    updateGroupFromChat(chat)
                }
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
        BaseTlRpcJsonObject.checkTypeNonNull(chatObj, "chat")
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
                check(!typeImpl["is_channel"].asBoolean) { "updateChannelFromChat: supergroup is a channel" }
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
                error("updateGroupFromChat: unexpected chat type: $chatType, event: $chatObj")
            }
        }
    }

    private fun updateChannelFromChat(chatObj: JsonObject): Channel {
        BaseTlRpcJsonObject.checkTypeNonNull(chatObj, "chat")
        val chatId = chatObj.get("id").asLong
        val name = chatObj.get("title").asString
        val typeImpl = chatObj["type"].asJsonObject
        // channel should be a supergroup
        BaseTlRpcJsonObject.checkTypeNonNull(typeImpl, "chatTypeSupergroup")
        check(typeImpl["is_channel"].asBoolean == true) { "updateChannelFromChat: supergroup is not a channel" }
        val gid = typeImpl["supergroup_id"].asLong
        if (chatId != -gid + CHAT_ID_NEGATIVE_NOTATION) {
            throw AssertionError("chatId=$chatId, gid=$gid")
        }
        val channel = server.getOrNewChannel(gid, this)
        channel.name = name
        val photo = chatObj.getAsJsonObject("photo")
        if (photo != null) {
            channel.photo = RemoteFile.fromJsonObject(photo["small"].asJsonObject)
        }
        return channel
    }

    private fun handleUpdateUserStatus(event: JsonObject): Boolean {
        Log.i(TAG, "handleUpdateUserStatus: $event")
        return true
    }

    private suspend fun handleUpdateAuthorizationState(event: JsonObject): Boolean {
        val state: JsonObject = event.getAsJsonObject("authorization_state")
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
                        Log.d(TAG, "try auth with bot token")
                        val request = JsonObject()
                        request.addProperty("@type", "checkAuthenticationBotToken")
                        request.addProperty("token", mAuthBotToken)
                        executeRawRequestAsync(request.toString()) { result, _, type ->
                            if (type == "error") {
                                Log.e(TAG, "checkAuthenticationBotToken: error: $result")
                                mAuthState = AuthState.INVALID_CREDENTIALS;
                                notifyAuthorizationResult(false, result["message"].asString)
                            } else if (type == "ok") {
                                Log.d(TAG, "checkAuthenticationBotToken: ok: $result")
                                mAuthState = AuthState.AUTHORIZED
                                notifyAuthorizationResult(true, null)
                            } else {
                                Log.e(TAG, "Unexpected result checking authentication bot token: $result")
                                mAuthState = AuthState.INVALID_CREDENTIALS
                                notifyAuthorizationResult(
                                    false, "Unexpected result checking authentication bot token: $result"
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
                                notifyAuthorizationResult(false, result["message"].asString)
                            } else if (type == "ok") {
                                Log.d(TAG, "setAuthenticationPhoneNumber: ok: $result")
                                mAuthState = AuthState.WAIT_CODE
                            } else {
                                Log.e(TAG, "Unexpected result setting authentication phone number: $result")
                                mAuthState = AuthState.INVALID_CREDENTIALS
                                notifyAuthorizationResult(
                                    false, "Unexpected result setting authentication phone number: $result"
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

    private fun handleUpdateOption(event: JsonObject): Boolean {
        val name = event.get("name").asString
        val value = event.get("value").asJsonObject
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
        val request = param.toJsonObject()
        request.addProperty("@type", "setTdlibParameters")
        executeRequestWaitExpectSuccess(request.toString(), 1000)
    }

    suspend fun loginWithBotTokenSuspended(token: String): Long {
        require(token.isNotEmpty()) { "Token is empty" }
        check(mAuthState != AuthState.AUTHORIZED) { "Bot is already authorized, uid: $userId" }
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
                            val uid = result.get("value").asLong
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

    private fun executeRawRequestAsync(request: JsonObject, callback: (JsonObject, Bot?, String) -> Boolean): String {
        return executeRawRequestAsync(request.toString(), callback)
    }

    private fun executeRawRequestAsync(request: String, callback: (JsonObject, Bot?, String) -> Boolean): String {
        return server.executeRawRequestAsync(request, this, object : TransactionDispatcher.TransactionCallbackV1 {
            override fun onEvent(event: JsonObject, bot: Bot?, type: String): Boolean {
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

    fun registerGroupEventListener(listener: EventHandler.GroupPermissionListenerV2) {
        synchronized(mListenerLock) {
            mOnGroupEventListeners.add(listener)
        }
    }

    fun unregisterGroupEventListener(listener: EventHandler.GroupPermissionListenerV2) {
        synchronized(mListenerLock) {
            mOnGroupEventListeners.remove(listener)
        }
    }

    fun registerCallbackQueryListener(listener: EventHandler.CallbackQueryListenerV2) {
        synchronized(mListenerLock) {
            mCallbackQueryListeners.add(listener)
        }
    }

    fun unregisterCallbackQueryListener(listener: EventHandler.CallbackQueryListenerV2) {
        synchronized(mListenerLock) {
            mCallbackQueryListeners.remove(listener)
        }
    }

    fun registerGroupMemberJoinRequestListenerV1(listener: EventHandler.GroupMemberJoinRequestListenerV2) {
        synchronized(mListenerLock) {
            mGroupMemberJoinRequestListenerV1.add(listener)
        }
    }

    fun unregisterGroupMemberJoinRequestListenerV1(listener: EventHandler.GroupMemberJoinRequestListenerV2) {
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
        val type = result.asJsonObject.get("@type").asString
        if (type != "ok") {
            Log.e(TAG, "executeRequestWait: error: $result")
            return false
        }
        return true
    }

    @Throws(RemoteApiException::class, IOException::class)
    suspend fun sendMessageRawEx(
        si: SessionInfo,
        inputMessageContent: JsonObject,
        replyMarkup: ReplyMarkup? = null,
        msgThreadId: Long = 0,
        replyMsgId: Long = 0,
        options: JsonObject? = null
    ): Message {
        val chatId = si.toTDLibChatId()
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
        val obj = executeRequest(request.toString(), server.defaultTimeout)
            ?: throw IOException("Timeout executing sendMessage")
        BaseTlRpcJsonObject.throwRemoteApiExceptionIfError(obj)
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
        val newMessage = holder.newMessage ?: throw IOException("Timeout waiting for updateMessageSendSuccess")
        if (holder.errorMsg != null) {
            throw RemoteApiException(holder.errorCode, holder.errorMsg!!)
        }
        return newMessage
    }

    @Throws(RemoteApiException::class, IOException::class)
    suspend fun sendMessageForText(
        si: SessionInfo,
        text: String,
        replyMarkup: ReplyMarkup? = null,
        disableWebPreview: Boolean = false,
        msgThreadId: Long = 0,
        replyMsgId: Long = 0
    ): Message {
        return sendMessageForText(
            si,
            FormattedText.forPlainText(text),
            replyMarkup,
            disableWebPreview,
            msgThreadId = msgThreadId,
            replyMsgId = replyMsgId
        )
    }

    @Throws(RemoteApiException::class, IOException::class)
    suspend fun sendMessageForText(
        si: SessionInfo,
        textMsg: FormattedText,
        replyMarkup: ReplyMarkup? = null,
        disableWebPreview: Boolean = true,
        msgThreadId: Long = 0,
        replyMsgId: Long = 0,
        options: JsonObject? = null
    ): Message {
        val msgObj = JsonObject().apply {
            addProperty("@type", "inputMessageText")
            add("text", textMsg.toJsonObject())
            addProperty("disable_web_page_preview", disableWebPreview)
            addProperty("clear_draft", false)
        }
        return sendMessageRawEx(si, msgObj, replyMarkup, msgThreadId, replyMsgId, options)
    }

    @Throws(RemoteApiException::class, IOException::class)
    suspend fun sendMessageForPhoto(
        si: SessionInfo,
        file: File,
        caption: String,
        replyMarkup: ReplyMarkup? = null,
        ttl: Int = 0,
        msgThreadId: Long = 0,
        replyMsgId: Long = 0,
        options: JsonObject? = null
    ): Message {
        return sendMessageForPhoto(
            si, file, FormattedText.forPlainText(caption), replyMarkup, ttl, msgThreadId, replyMsgId, options
        )
    }

    @Throws(RemoteApiException::class, IOException::class)
    suspend fun sendMessageForPhoto(
        si: SessionInfo,
        file: File,
        caption: FormattedText?,
        replyMarkup: ReplyMarkup? = null,
        ttl: Int = 0,
        msgThreadId: Long = 0,
        replyMsgId: Long = 0,
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
        return sendMessageRawEx(si, msgObj, replyMarkup, msgThreadId, replyMsgId, options)
    }

    @Throws(RemoteApiException::class, IOException::class)
    suspend fun editMessageCaption(
        si: SessionInfo, msgId: Long, caption: FormattedText, replyMarkup: ReplyMarkup? = null
    ): JsonObject {
        JsonObject().apply {
            addProperty("@type", "editMessageCaption")
            addProperty("chat_id", si.toTDLibChatId())
            addProperty("message_id", msgId)
            add("caption", caption.toJsonObject())
            add("reply_markup", replyMarkup?.toJsonObject())
        }.let {
            val obj = executeRequest(it.toString(), server.defaultTimeout)
            if (obj == null) {
                throw IOException("Timeout")
            } else {
                BaseTlRpcJsonObject.throwRemoteApiExceptionIfError(obj)
                return obj
            }
        }
    }

    @Throws(RemoteApiException::class, IOException::class)
    suspend fun deleteMessages(
        si: SessionInfo, msgIds: Array<Long>
    ): JsonObject {
        require(msgIds.isNotEmpty()) { "msgIds is empty" }
        JsonObject().apply {
            addProperty("@type", "deleteMessages")
            addProperty("chat_id", si.toTDLibChatId())
            add("message_ids", JsonArray().apply {
                msgIds.forEach { add(it) }
            })
            addProperty("revoke", true)
        }.let {
            val obj = executeRequest(it.toString(), server.defaultTimeout)
            if (obj == null) {
                throw IOException("Timeout")
            } else {
                BaseTlRpcJsonObject.throwRemoteApiExceptionIfError(obj)
                return obj
            }
        }
    }

    @Throws(RemoteApiException::class, IOException::class)
    suspend fun deleteMessages(
        si: SessionInfo, msgIds: List<Long>
    ): JsonObject {
        return deleteMessages(si, msgIds.toTypedArray())
    }

    @Throws(RemoteApiException::class, IOException::class)
    suspend fun deleteMessage(
        si: SessionInfo, msgId: Long
    ): JsonObject {
        return deleteMessages(si, arrayOf(msgId))
    }

    @Throws(RemoteApiException::class, IOException::class)
    suspend fun editMessageTextEx(
        si: SessionInfo, msgId: Long, inputMessageContent: JsonObject, replyMarkup: ReplyMarkup? = null
    ): JsonObject {
        BaseTlRpcJsonObject.checkTypeNonNull(inputMessageContent, "inputMessageText")
        JsonObject().apply {
            addProperty("@type", "editMessageText")
            addProperty("chat_id", si.toTDLibChatId())
            addProperty("message_id", msgId)
            add("input_message_content", inputMessageContent)
            add("reply_markup", replyMarkup?.toJsonObject())
        }.let {
            val obj = executeRequest(it.toString(), server.defaultTimeout)
            if (obj == null) {
                throw IOException("Timeout")
            } else {
                BaseTlRpcJsonObject.throwRemoteApiExceptionIfError(obj)
                return obj
            }
        }
    }

    @Throws(RemoteApiException::class, IOException::class)
    suspend fun editMessageText(
        si: SessionInfo,
        msgId: Long,
        formattedText: FormattedText,
        replyMarkup: ReplyMarkup? = null,
        disableWebPreview: Boolean = true
    ): JsonObject {
        return editMessageTextEx(
            si, msgId, JsonObject().apply {
                addProperty("@type", "inputMessageText")
                add("text", formattedText.toJsonObject())
                addProperty("disable_web_page_preview", disableWebPreview)
                addProperty("clear_draft", false)
            }, replyMarkup
        )
    }

    @Throws(RemoteApiException::class, IOException::class)
    suspend fun answerCallbackQuery(
        queryId: Long, text: String?, showAlert: Boolean, url: String? = null, cacheTime: Int = 0
    ) {
        val request = JsonObject().apply {
            addProperty("@type", "answerCallbackQuery")
            addProperty("callback_query_id", queryId)
            addProperty("text", text)
            addProperty("show_alert", showAlert)
            addProperty("url", url)
            addProperty("cache_time", cacheTime)
        }
        val obj = executeRequest(request.toString(), server.defaultTimeout)
        if (obj == null) {
            throw IOException("Timeout")
        } else {
            BaseTlRpcJsonObject.throwRemoteApiExceptionIfError(obj)
        }
    }

    @Throws(RemoteApiException::class, IOException::class)
    suspend fun getMessage(si: SessionInfo, msgId: Long, invalidate: Boolean = false): Message {
        val request = JsonObject().apply {
            addProperty("@type", "getMessage")
            addProperty("chat_id", si.toTDLibChatId())
            addProperty("message_id", msgId)
        }
        val obj = executeRequest(request.toString(), server.defaultTimeout)
        if (obj == null) {
            throw IOException("Timeout executing getMessage request")
        } else {
            BaseTlRpcJsonObject.throwRemoteApiExceptionIfError(obj)
            return Message.fromJsonObject(obj)
        }
    }

    @Throws(RemoteApiException::class, IOException::class)
    suspend fun getUser(userId: Long, invalidate: Boolean = false): User {
        require(userId > 0) { "userId $userId is not valid" }
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
        val obj = executeRequest(request.toString(), server.defaultTimeout)
        if (obj == null) {
            throw IOException("Timeout executing getUser request")
        } else {
            BaseTlRpcJsonObject.throwRemoteApiExceptionIfError(obj)
            return updateUserInfo(obj)
        }
    }

    @Throws(RemoteApiException::class, IOException::class)
    suspend fun getGroup(groupId: Long, invalidate: Boolean = false): Group {
        require(groupId > 0) { "groupId $groupId is not valid" }
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
        val chatObj = executeRequest(request1.toString(), server.defaultTimeout)
            ?: throw IOException("Timeout executing getChat request")
        BaseTlRpcJsonObject.throwRemoteApiExceptionIfError(chatObj)
        BaseTlRpcJsonObject.checkTypeNonNull(chatObj, "chat")
        return when (val chatType = chatObj.get("type").asJsonObject.get("@type").asString) {
            "chatTypeBasicGroup" -> {
                updateGroupFromChat(chatObj)
            }
            "chatTypeSupergroup" -> {
                updateGroupFromChat(chatObj)
            }
            else -> error("Unknown chat type: $chatType")
        }
    }

    @Throws(RemoteApiException::class, IOException::class)
    suspend fun getChannel(channelId: Long, invalidate: Boolean = false): Channel {
        require(channelId > 0) { "channelId $channelId is not valid" }
        if (!invalidate) {
            val cached = server.getCachedChannelWithChannelId(channelId)
            if (cached != null && cached.isKnown) {
                return cached
            }
        }
        val request = JsonObject().apply {
            addProperty("@type", "getChat")
            addProperty("chat_id", -channelId + CHAT_ID_NEGATIVE_NOTATION)
        }
        val obj = executeRequest(request.toString(), server.defaultTimeout)
            ?: throw IOException("Timeout executing getChat request")
        BaseTlRpcJsonObject.throwRemoteApiExceptionIfError(obj)
        BaseTlRpcJsonObject.checkTypeNonNull(obj, "chat")
        return updateChannelFromChat(obj)
    }

    @Throws(RemoteApiException::class, IOException::class)
    suspend fun getChat(chatId: Long, invalidate: Boolean = false): JsonObject {
        require(chatId != 0L) { "chatId $chatId is not valid" }
        val request1 = JsonObject().apply {
            addProperty("@type", "getChat")
            addProperty("chat_id", chatId)
        }
        val chatObj = executeRequest(request1.toString(), server.defaultTimeout)
            ?: throw IOException("Timeout executing getChat request")
        BaseTlRpcJsonObject.throwRemoteApiExceptionIfError(chatObj)
        BaseTlRpcJsonObject.checkTypeNonNull(chatObj, "chat")
        return chatObj
    }

    @Throws(RemoteApiException::class, IOException::class)
    suspend fun getGroupMember(groupId: Long, userId: Long): JsonObject {
        require(groupId > 0) { "groupId $groupId is not valid" }
        require(userId > 0) { "userId $userId is not valid" }
        val request1 = JsonObject().apply {
            addProperty("@type", "getChatMember")
            addProperty("chat_id", groupIdToChatId(groupId))
            add("member_id", JsonObject().apply {
                addProperty("@type", "messageSenderUser")
                addProperty("user_id", userId)
            })
        }
        val chatObj = executeRequest(request1.toString(), server.defaultTimeout)
            ?: throw IOException("Timeout executing getChatMember request")
        BaseTlRpcJsonObject.throwRemoteApiExceptionIfError(chatObj)
        BaseTlRpcJsonObject.checkTypeNonNull(chatObj, "chatMember")
        return chatObj
    }

    @Throws(RemoteApiException::class, IOException::class)
    suspend fun processChatJoinRequest(groupId: Long, userId: Long, approve: Boolean) {
        require(groupId > 0) { "chatId $groupId is not a valid group id" }
        require(userId > 0) { "userId $userId is not a valid user id" }
        val request = JsonObject().apply {
            addProperty("@type", "processChatJoinRequest")
            addProperty("chat_id", SessionInfo.groupIdToChatId(groupId))
            addProperty("user_id", userId)
            addProperty("approve", approve)
        }
        val obj = executeRequest(request.toString(), server.defaultTimeout)
        if (obj == null) {
            throw IOException("Timeout executing processChatJoinRequest request")
        } else {
            BaseTlRpcJsonObject.throwRemoteApiExceptionIfError(obj)
        }
    }

    private suspend fun executeRequest(request: String, timeout: Int): JsonObject? {
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
