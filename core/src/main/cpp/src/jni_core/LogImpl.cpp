//
// Created by kinit on 7/10/22.
//
#include <malloc.h>
#include <cstring>
#include <ctime>
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
    time_t timesec = tv.tv_sec;
    const auto *tm = localtime(&timesec);
    int month = tm->tm_mon + 1;
    int day = tm->tm_mday;
    int hour = tm->tm_hour;
    int min = tm->tm_min;
    int sec = tm->tm_sec;
    int usec = int(tv.tv_usec);
    const char *tagString;
    const char *colorStart;
    const char *colorEnd;
    if (LoggerOutputImpl_mEnableVt100) {
        switch (level) {
            case Log::Level::VERBOSE: {
                tagString = "[VERBOSE]";
                colorStart = VT100_COLOR_GREY;
                colorEnd = VT100_COLOR_NORMAL;
                break;
            }
            case Log::Level::DEBUG: {
                tagString = "[ DEBUG ]";
                colorStart = VT100_COLOR_GREEN;
                colorEnd = VT100_COLOR_NORMAL;
                break;
            }
            case Log::Level::INFO: {
                tagString = "[ INFO  ]";
                colorStart = VT100_COLOR_BLUE;
                colorEnd = VT100_COLOR_NORMAL;
                break;
            }
            case Log::Level::WARN: {
                tagString = "[ WARN  ]";
                colorStart = VT100_COLOR_YELLOW;
                colorEnd = VT100_COLOR_NORMAL;
                break;
            }
            case Log::Level::ERROR: {
                tagString = "[ ERROR ]";
                colorStart = VT100_COLOR_RED;
                colorEnd = VT100_COLOR_NORMAL;
                break;
            }
            case Log::Level::ASSERT: {
                tagString = "[ FATAL ]";
                colorStart = VT100_COLOR_RED;
                colorEnd = VT100_COLOR_NORMAL;
                break;
            }
            default: {
                tagString = "[UNKNOWN]";
                colorStart = "";
                colorEnd = "";
                break;
            }
        }
    } else {
        colorStart = "";
        colorEnd = "";
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
    snprintf(buf, len, "%s%02d-%02d %02d:%02d:%02d.%06d %s %s%s %s\n", colorStart, month, day, hour, min, sec, usec, tagString, tag, colorEnd, msg);
    // write to the fifo
    cli::Console &console = cli::Console::getInstance();
    console.printLine(buf);
    free(buf);
}

Log::LogHandler LogImpl::getLogHandler() {
    return &defaultLogHandler;
}
