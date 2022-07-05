//
// Created by kinit on 2022-02-18.
//

#include <pthread.h>
#include <atomic>
#include <mutex>
#include <map>
#include <queue>
#include <condition_variable>

#include "CachedThreadPool.h"

namespace utils {

class CachedThreadPool::Impl {
public:
    Impl() = delete;

    explicit Impl(int corePoolSize, int maxPoolSize, int keepAliveTime, int queueSize);

    ~Impl();

    Impl(const Impl &) = delete;

    Impl &operator=(const Impl &) = delete;

    void execute(std::unique_ptr<std::function<void()>> task);

    void shutdown();

    void shutdownNow();

    [[nodiscard]] size_t currentWorkerCount() const;

    [[nodiscard]] bool isShutdown() const;

    [[nodiscard]] bool isTerminated() const;

    void awaitTermination(int timeout);

    void startWorker(std::unique_ptr<std::function<void()>> firstTask, bool isCore);

private:
    class Worker;

    int mCorePoolSize;
    int mMaxPoolSize;
    int mKeepAliveTime;
    int mMaxQueueSize;
    std::atomic_bool mIsShutdown = false;
    std::atomic_bool mIsTerminated = false;
    std::mutex mWorkerLock;
    std::mutex mQueueLock;
    std::map<pthread_t, std::unique_ptr<Worker>> mWorkers;
    std::queue<std::unique_ptr<std::function<void()>>> mTaskQueue;
    std::condition_variable mQueueCondition;
    std::mutex mTerminationLock;
    std::condition_variable mTerminationCondition;

    /**
     * Get a task from the queue. If the queue is empty, block until a task is available.
     * This method will return nullptr if the pool is shutdown.
     * @param timeout the timeout in milliseconds, -1 for infinite, 0 for no wait
     * @return a task if the thread pool is still running, nullptr if the pool is shutdown.
     */
    std::unique_ptr<std::function<void()>> getTask(int timeout);

    void onWorkerExit(Worker *worker) noexcept;
};

CachedThreadPool::CachedThreadPool(int corePoolSize, int maxPoolSize, int keepAliveTime, int queueSize)
        : impl(new CachedThreadPool::Impl(corePoolSize, maxPoolSize, keepAliveTime, queueSize)) {}

CachedThreadPool::~CachedThreadPool() {
    impl->shutdown();
    impl->awaitTermination(-1);
    delete impl;
    impl = nullptr;
}

void CachedThreadPool::execute(std::function<void()> task) {
    impl->execute(std::make_unique<std::function<void()>>(std::move(task)));
}

void CachedThreadPool::execute(std::unique_ptr<std::function<void()>> task) {
    impl->execute(std::move(task));
}

void CachedThreadPool::shutdown() {
    impl->shutdown();
}

void CachedThreadPool::shutdownNow() {
    impl->shutdownNow();
}

void CachedThreadPool::awaitTermination(int timeout) {
    impl->awaitTermination(timeout);
}

bool CachedThreadPool::isShutdown() const {
    return impl->isShutdown();
}

bool CachedThreadPool::isTerminated() const {
    return impl->isTerminated();
}

// Worker

class CachedThreadPool::Impl::Worker {
public:
    explicit Worker(CachedThreadPool::Impl *pool, bool isCore, std::unique_ptr<std::function<void()>> firstTask);

    ~Worker() = default;

    Worker(const Worker &) = delete;

    Worker &operator=(const Worker &) = delete;

private:
    CachedThreadPool::Impl *mPool;
    // to be set latter
    pthread_t mThread = 0;
    bool mIsCoreWorker = false;
    // possible nullptr
    std::unique_ptr<std::function<void()>> mFirstTask;

    /**
     * Called before the thread is terminated.
     * You should no longer use the worker pointer after this method is called.
     */
    void onTerminate() noexcept;

    static void run(Worker *worker);

    friend CachedThreadPool::Impl;
};

CachedThreadPool::Impl::Worker::Worker(CachedThreadPool::Impl *pool, bool isCore,
                                       std::unique_ptr<std::function<void()>> firstTask)
        : mPool(pool), mIsCoreWorker(isCore), mFirstTask(std::move(firstTask)) {}

void CachedThreadPool::Impl::Worker::run(Worker *worker) {
    std::unique_ptr<std::function<void()>> task;
    if (worker->mFirstTask) {
        task = std::move(worker->mFirstTask);
        worker->mFirstTask.reset();
    }
    CachedThreadPool::Impl *pool = worker->mPool;
    try {
        while (true) {
            if (!task) {
                // get a task from the queue
                bool allowQuit = pool->currentWorkerCount() > pool->mCorePoolSize;
                task = pool->getTask(allowQuit ? pool->mKeepAliveTime : -1);
            }
            if (task) {
                task->operator()();
                task.reset();
            } else {
                // getTask() returned nullptr
                // either the pool is shutdown or we timed out
                break;
            }
        }
    } catch (...) {
        worker->onTerminate();
        // rethrow
        throw;
    }
    worker->onTerminate();
}

void CachedThreadPool::Impl::Worker::onTerminate() noexcept {
    mPool->onWorkerExit(this);
}

// Impl

CachedThreadPool::Impl::Impl(int corePoolSize, int maxPoolSize, int keepAliveTime, int queueSize)
        : mCorePoolSize(corePoolSize),
          mMaxPoolSize(maxPoolSize),
          mKeepAliveTime(keepAliveTime),
          mMaxQueueSize(queueSize) {
    // don't allow mCorePoolSize to be greater than mMaxPoolSize
    if (mCorePoolSize > mMaxPoolSize) {
        mCorePoolSize = mMaxPoolSize;
    }
    // don't allow mKeepAliveTime to be less than zero
    if (mKeepAliveTime < 0) {
        mKeepAliveTime = 0;
    }
    // don't allow mQueueSize to be less than zero
    if (mMaxQueueSize < 0) {
        mMaxQueueSize = 64;
    }
    // don't start workers now, they will be started when needed
}

void CachedThreadPool::Impl::startWorker(std::unique_ptr<std::function<void()>> firstTask, bool isCore) {
    std::scoped_lock<std::mutex> lock(mWorkerLock);
    pthread_attr_t attr;
    pthread_attr_init(&attr);
    pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_DETACHED);
    // create core workers and add to map
    auto worker = std::make_unique<Worker>(this, isCore, std::move(firstTask));
    pthread_t thread;
    int err = pthread_create(&thread, &attr, reinterpret_cast<void *(*)(void *)>(
            &CachedThreadPool::Impl::Worker::run), worker.get());
    if (err != 0) {
        throw std::runtime_error("cannot create core worker thread: error = " + std::to_string(err));
    }
    worker->mThread = thread;
    mWorkers.emplace(thread, std::move(worker));
}

size_t CachedThreadPool::Impl::currentWorkerCount() const {
    return mWorkers.size();
}

void CachedThreadPool::Impl::execute(std::unique_ptr<std::function<void()>> task) {
    if (!task || !*task) {
        // ignore nullptr tasks
        return;
    }
    if (mIsShutdown || mIsTerminated) {
        throw std::runtime_error("cannot execute task: thread pool is shutdown");
    }
    auto workerCount = currentWorkerCount();
    if (workerCount < mCorePoolSize) {
        // create a new worker if there are less than mCorePoolSize workers
        startWorker(std::move(task), true);
    } else {
        auto queueSize = mTaskQueue.size();
        if (queueSize > 0 && workerCount < mMaxPoolSize) {
            // create a new worker if the queue is not empty and there are less than mMaxPoolSize workers
            startWorker(std::move(task), false);
        } else {
            // add the task to the queue
            std::scoped_lock<std::mutex> lock(mQueueLock);
            mTaskQueue.push(std::move(task));
            mQueueCondition.notify_one();
        }
    }
}

std::unique_ptr<std::function<void()>> CachedThreadPool::Impl::getTask(int timeout) {
    std::unique_lock<std::mutex> lock(mQueueLock);
    if (timeout == 0) {
        // do not wait, only return if there is a task
        if (mTaskQueue.empty()) {
            return nullptr;
        }
        auto task = std::move(mTaskQueue.front());
        mTaskQueue.pop();
        return task;
    } else if (timeout < 0) {
        // wait forever
        while (mTaskQueue.empty() && !mIsShutdown) {
            mQueueCondition.wait(lock);
        }
        if (mTaskQueue.empty()) {
            return nullptr;
        }
        auto task = std::move(mTaskQueue.front());
        mTaskQueue.pop();
        return task;
    } else {
        // wait for timeout
        auto timeoutTime = std::chrono::steady_clock::now() + std::chrono::milliseconds(timeout);
        if (mTaskQueue.empty() && !mIsShutdown) {
            mQueueCondition.wait_until(lock, timeoutTime);
            // we don't need to check the return value of wait_until, in each case, we will return
        }
        if (mTaskQueue.empty()) {
            return nullptr;
        }
        auto task = std::move(mTaskQueue.front());
        mTaskQueue.pop();
        return task;
    }
}

void CachedThreadPool::Impl::shutdown() {
    std::scoped_lock<std::mutex> lock(mQueueLock);
    mIsShutdown = true;
    // notify all waiting threads
    mQueueCondition.notify_all();
}

bool CachedThreadPool::Impl::isShutdown() const {
    return mIsShutdown;
}

bool CachedThreadPool::Impl::isTerminated() const {
    return mIsTerminated;
}

void CachedThreadPool::Impl::shutdownNow() {
    shutdown();
    // shutdownNow() is not implemented
}

CachedThreadPool::Impl::~Impl() {
    shutdown();
    // wait for all workers to exit
    awaitTermination(-1);
}

void CachedThreadPool::Impl::awaitTermination(int timeout) {
    std::unique_lock<std::mutex> lock(mTerminationLock);
    if (mIsTerminated) {
        return;
    }
    if (timeout < 0) {
        mTerminationCondition.wait(lock);
    } else {
        auto timeoutTime = std::chrono::steady_clock::now() + std::chrono::milliseconds(timeout);
        mTerminationCondition.wait_until(lock, timeoutTime);
    }
}

void CachedThreadPool::Impl::onWorkerExit(Worker *worker) noexcept {
    if (worker == nullptr) {
        return;
    }
    {
        std::scoped_lock<std::mutex> lock(mWorkerLock);
        mWorkers.erase(worker->mThread);
    }
    if (mIsShutdown) {
        // if the thread pool is shutdown, we need to set the mIsTerminated flag when all workers exit
        std::unique_lock<std::mutex> lock(mTerminationLock);
        if (mWorkers.empty()) {
            mIsTerminated = true;
            mTerminationCondition.notify_all();
        }
    }
}

}
