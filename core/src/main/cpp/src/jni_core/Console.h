//
// Created by kinit on 7/6/22.
//

#ifndef NEOAUTH2TGBOT_CONSOLE_H
#define NEOAUTH2TGBOT_CONSOLE_H

#include <string_view>
#include <string>
#include <mutex>

namespace cli {

class Console {
private:
    Console();

    ~Console() = default;

public:
    // no copy nor move
    Console(const Console &) = delete;

    Console &operator=(const Console &) = delete;

    Console(Console &&) = delete;

    Console &operator=(Console &&) = delete;

    static Console &getInstance();

    void printLine(std::string_view msg);

    void updateStatusText(std::string_view msg);

private:
    std::string mStatusText;
    std::mutex mOutputMutex;
    int mReadOnlyTransientLineCount = 0; // 0 means no transient line, input buffer doesn't count
    int mInputFd = -1;
    int mOutputFd = -1;
};

} // cli

#endif //NEOAUTH2TGBOT_CONSOLE_H
