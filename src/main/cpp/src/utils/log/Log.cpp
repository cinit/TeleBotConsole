//
// Created by kinit on 2021-10-05.
//

#include "Log.h"

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
