//
// Created by kinit on 7/10/22.
//

#ifndef TDJNI_LOGIMPL_H
#define TDJNI_LOGIMPL_H

#include "../utils/log/Log.h"

class LogImpl {
private:
    // no instance
    LogImpl() = default;

public:
    static Log::LogHandler getLogHandler();
};

#endif //TDJNI_LOGIMPL_H
