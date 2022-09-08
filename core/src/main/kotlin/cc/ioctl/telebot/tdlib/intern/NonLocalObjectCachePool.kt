package cc.ioctl.telebot.tdlib.intern

import cc.ioctl.telebot.tdlib.RobotServer
import cc.ioctl.telebot.tdlib.obj.Channel
import cc.ioctl.telebot.tdlib.obj.Group
import cc.ioctl.telebot.tdlib.obj.PrivateChatSession
import cc.ioctl.telebot.tdlib.obj.User

class NonLocalObjectCachePool internal constructor(
    val server: RobotServer
) {

    private val mLock = Object()
    private val MAX_CACHE_SIZE = 3000

    private val mLruUserCache = object : LinkedHashMap<Long, User>(16) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, User>?): Boolean {
            return size > MAX_CACHE_SIZE
        }
    }

    private val mLruGroupCache = LinkedHashMap<Long, Group>(16)
    private val mLruChannelCache = LinkedHashMap<Long, Channel>(16)
    private val mLruPrivateChatCache = LinkedHashMap<Long, PrivateChatSession>(16)

    fun getOrCreateUser(userId: Long): User {
        synchronized(mLock) {
            return mLruUserCache[userId] ?: User(server, userId).also {
                mLruUserCache[userId] = it
            }
        }
    }

    fun findCachedUser(uid: Long): User? {
        if (uid == 0L) {
            return null
        }
        synchronized(mLock) {
            return mLruUserCache[uid]
        }
    }


    fun getOrCreateGroup(groupId: Long): Group {
        synchronized(mLock) {
            return mLruGroupCache[groupId] ?: Group(server, groupId).also {
                mLruGroupCache[groupId] = it
            }
        }
    }

    fun getOrCreateChannel(channelId: Long): Channel {
        synchronized(mLock) {
            return mLruChannelCache[channelId] ?: Channel(server, channelId).also {
                mLruChannelCache[channelId] = it
            }
        }
    }

    fun getOrCreatePrivateChat(chatId: Long, userId: Long): PrivateChatSession {
        synchronized(mLock) {
            return mLruPrivateChatCache[chatId] ?: PrivateChatSession(server, userId).also {
                mLruPrivateChatCache[chatId] = it
            }
        }
    }

    fun getCachedPrivateChat(chatId: Long): PrivateChatSession? {
        synchronized(mLock) {
            return mLruPrivateChatCache[chatId]
        }
    }

    fun findCachedGroup(uid: Long): Group? {
        if (uid == 0L) {
            return null
        }
        synchronized(mLock) {
            return mLruGroupCache[uid]
        }
    }

    fun findCachedChannel(uid: Long): Channel? {
        if (uid == 0L) {
            return null
        }
        synchronized(mLock) {
            return mLruChannelCache[uid]
        }
    }

    fun findCachedUserWithUserName(username: String): User? {
        if (username.isEmpty()) {
            return null
        }
        synchronized(mLock) {
            for (entry in mLruUserCache.entries) {
                if (entry.value.username == username) {
                    return entry.value
                }
            }
        }
        return null
    }

}
