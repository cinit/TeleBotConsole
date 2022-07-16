//
// Created by kinit on 7/6/22.
//

#include "Console.h"

#include <unistd.h>

namespace cli {

Console &Console::getInstance() {
    static Console instance;
    return instance;
}

void Console::printLine(std::string_view msg) {
    if (msg.empty()) {
        return;
    }
    std::scoped_lock<std::mutex> lock(mOutputMutex);
    if (mOutputFd == -1) {
        return;
    }
    write(mOutputFd, msg.data(), msg.size());
    if (msg.back() != '\n') {
        write(mOutputFd, "\n", 1);
    }
    fsync(mOutputFd);
}

void Console::updateStatusText(std::string_view msg) {
    mStatusText = msg;
}

Console::Console() {
    mInputFd = 0;
    mOutputFd = 1;
}

} // cli
