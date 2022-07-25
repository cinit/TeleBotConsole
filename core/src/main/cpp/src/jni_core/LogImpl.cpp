//
// Created by kinit on 7/10/22.
//
#include <malloc.h>
#include <cstring>

#include <sys/time.h>

#include "LogImpl.h"
#include "Console.h"

#define VT100_COLOR_NORMAL "\x1B[0m"
#define VT100_COLOR_RED "\x1B[31m"
#define VT100_COLOR_GREEN "\x1B[32m"
#define VT100_COLOR_YELLOW "\x1B[33m"
#define VT100_COLOR_BLUE "\x1B[34m"
#define VT100_COLOR_GREY "\x1B[37m"

int LoggerOutputImpl_mEnableVt100 = 1;

void defaultLogHandler(Log::Level level, const char *tag, const char *msg) {
    size_t len = strlen(msg) + strlen(tag) + 64;
    char *buf = (char *) malloc(len);
    if (buf == nullptr) {
        return;
    }
    timeval tv = {};
    gettimeofday(&tv, nullptr);
    int month = int(int64_t(tv.tv_sec) / int64_t(60 * 60 * 24 * 30));
    int day = int(int64_t(tv.tv_sec) / int64_t(60 * 60 * 24));
    int hour = int(int64_t(tv.tv_sec) / int64_t(60 * 60));
    int min = int(int64_t(tv.tv_sec) / int64_t(60));
    int sec = int(int64_t(tv.tv_sec) % int64_t(60));
    int usec = int(tv.tv_usec);
    const char *tagString = nullptr;
    if (LoggerOutputImpl_mEnableVt100) {
        switch (level) {
            case Log::Level::VERBOSE: {
                tagString = VT100_COLOR_GREY "[VERBOSE]" VT100_COLOR_NORMAL;
                break;
            }
            case Log::Level::DEBUG: {
                tagString = "[ DEBUG ]";
                break;
            }
            case Log::Level::INFO: {
                tagString = VT100_COLOR_GREEN "[ INFO  ]" VT100_COLOR_NORMAL;
                break;
            }
            case Log::Level::WARN: {
                tagString = VT100_COLOR_YELLOW "[ WARN  ]" VT100_COLOR_NORMAL;
                break;
            }
            case Log::Level::ERROR: {
                tagString = VT100_COLOR_RED "[ ERROR ]" VT100_COLOR_NORMAL;
                break;
            }
            case Log::Level::ASSERT: {
                tagString = VT100_COLOR_RED "[ FATAL ]" VT100_COLOR_NORMAL;
                break;
            }
            default: {
                tagString = VT100_COLOR_RED "[UNKNOWN]" VT100_COLOR_NORMAL;
                break;
            }
        }
    } else {
        switch (level) {
            case Log::Level::VERBOSE: {
                tagString = "[VERBOSE]";
                break;
            }
            case Log::Level::DEBUG: {
                tagString = "[ DEBUG ]";
                break;
            }
            case Log::Level::INFO: {
                tagString = "[ INFO  ]";
                break;
            }
            case Log::Level::WARN: {
                tagString = "[ WARN  ]";
                break;
            }
            case Log::Level::ERROR: {
                tagString = "[ ERROR ]";
                break;
            }
            case Log::Level::ASSERT: {
                tagString = "[ FATAL ]";
                break;
            }
            default: {
                tagString = "[UNKNOWN]";
                break;
            }
        }
    }
    // assemble the log message
    // MM-DD HH:MM:SS LEVEL TAG MSG
    snprintf(buf, len, "%02d-%02d %02d:%02d:%02d.%06d %s %s %s\n", month, day, hour, min, sec, usec, tagString, tag, msg);
    // write to the fifo
    cli::Console &console = cli::Console::getInstance();
    console.printLine(buf);
    free(buf);
}

Log::LogHandler LogImpl::getLogHandler() {
    return &defaultLogHandler;
}
