// SPDX-License-Identifier: MIT
//
// Created by kinit on 2021-10-05.
//

#ifndef RPCPROTOCOL_LOG_H
#define RPCPROTOCOL_LOG_H

static_assert(sizeof(char) == 1, "char is not 1 byte");
static_assert(sizeof(char16_t) == 2, "char16_t is not 2 bytes");

class Log {
public:
    enum class Level {
        UNKNOWN = 0,
        VERBOSE = 2,
        DEBUG = 3,
        INFO = 4,
        WARN = 5,
        ERROR = 6,
        ASSERT = 7
    };
    using LogHandler = void (*)(Level level, const char *tag, const char *msg);
private:
    static volatile LogHandler mHandler;
public:
    static void format(Level level, const char *tag, const char *fmt, ...) __attribute__ ((__format__ (__printf__, 3, 4)));

    static void logBuffer(Level level, const char *tag, const char *msg) {
        LogHandler h = mHandler;
        if (h == nullptr) {
            return;
        }
        h(level, tag, msg);
    }

    static inline LogHandler getLogHandler() noexcept {
        return mHandler;
    }

    static inline void setLogHandler(LogHandler h) noexcept {
        mHandler = h;
    }

    static const char *levelToString(Level level) noexcept;
};

#define LOGE(...)  Log::format(Log::Level::ERROR, LOG_TAG, __VA_ARGS__)
#define LOGW(...)  Log::format(Log::Level::WARN, LOG_TAG, __VA_ARGS__)
#define LOGI(...)  Log::format(Log::Level::INFO, LOG_TAG, __VA_ARGS__)
#define LOGD(...)  Log::format(Log::Level::DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGV(...)  Log::format(Log::Level::VERBOSE, LOG_TAG, __VA_ARGS__)

#endif //RPCPROTOCOL_LOG_H
