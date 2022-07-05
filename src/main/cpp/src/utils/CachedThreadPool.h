//
// Created by kinit on 2022-02-18.
//

#ifndef NEOGROUPCAPTCHABOT_CACHEDTHREADPOOL_H
#define NEOGROUPCAPTCHABOT_CACHEDTHREADPOOL_H

#include <functional>
#include <memory>

namespace utils {

class CachedThreadPool {
public:
    CachedThreadPool() = delete;

    /**
     * @brief Construct a new CachedThreadPool object
     * @param corePoolSize the minimum number of threads to keep in the pool
     * @param maxPoolSize the maximum number of threads to keep in the pool
     * @param keepAliveTime the time, in milliseconds, each thread should be kept alive
     * @param queueSize the maximum number of tasks to allow to wait in the queue
     */
    explicit CachedThreadPool(int corePoolSize, int maxPoolSize, int keepAliveTime = 60000, int queueSize = 64);

    ~CachedThreadPool();

    CachedThreadPool(const CachedThreadPool &) = delete;

    CachedThreadPool &operator=(const CachedThreadPool &) = delete;

    void execute(std::function<void()> task);

    void execute(std::unique_ptr<std::function<void()>> task);

    void shutdown();

    void shutdownNow();

    void awaitTermination(int timeout);

    [[nodiscard]] bool isShutdown() const;

    [[nodiscard]] bool isTerminated() const;

private:
    class Impl;

    Impl *impl = nullptr;
};

}

#endif //NEOGROUPCAPTCHABOT_CACHEDTHREADPOOL_H
