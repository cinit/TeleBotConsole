//
// Created by kinit on 2022-02-18.
//

#include <thread>
#include <chrono>
#include "CachedThreadPool.h"

#include "SyncUtils.h"

namespace utils {

static std::unique_ptr<CachedThreadPool> sThreadPool = nullptr;

void async(std::function<void()> func) {
    if (!func) {
        return;
    }
    if (!sThreadPool) {
        sThreadPool = std::make_unique<CachedThreadPool>(4, 16);
    }
    sThreadPool->execute(std::move(func));
}

void Thread::sleep(int ms) {
    std::this_thread::sleep_for(std::chrono::milliseconds(ms));
}

uint64_t getCurrentTimeMillis() {
    return std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::system_clock::now().time_since_epoch()).count();
}

}
