//
// Created by kinit on 2021-10-05.
//

#include "Log.h"

#include <cstdarg>
#include <cstdio>
#include <cstring>
#include <malloc.h>

#include "../TextUtils.h"

volatile Log::LogHandler Log::mHandler = nullptr;

const char *Log::levelToString(Log::Level level) noexcept {
    switch (level) {
        case Level::UNKNOWN:
            return "UNKNOWN";
        case Level::VERBOSE:
            return "VERBOSE";
        case Level::DEBUG:
            return "DEBUG";
        case Level::INFO:
            return "INFO";
        case Level::WARN:
            return "WARN";
        case Level::ERROR:
            return "ERROR";
        default:
            return "UNKNOWN";
    }
}

void Log::format(Log::Level level, const char *tag, const char *fmt, ...) {
    va_list varg1;
    va_list varg2;
    LogHandler h = mHandler;
    if (h == nullptr || fmt == nullptr) {
        return;
    }
    va_start(varg1, fmt);
    va_copy(varg2, varg1);
    int size = vsnprintf(nullptr, 0, fmt, varg1) + 4;
    va_end(varg1);
    if (size <= 0) {
        return;
    }
    void *buffer = malloc(size);
    if (buffer == nullptr) {
        return;
    }
    va_start(varg2, fmt);
    vsnprintf((char *) buffer, size, fmt, varg2);
    va_end(varg2);
    h(level, tag, static_cast<const char *>(buffer));
    free(buffer);
}
