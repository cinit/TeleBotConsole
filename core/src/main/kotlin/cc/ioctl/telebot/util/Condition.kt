package cc.ioctl.telebot.util

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.nanoseconds

/**
 * Source: https://gist.github.com/paulo-raca/ef6a827046a5faec95024ff406d3a692
 * This should be part of kotlin-coroutines: https://github.com/Kotlin/kotlinx.coroutines/issues/2531
 */
class Condition(val mutex: Mutex) {
    val waiting = LinkedHashSet<Mutex>()

    /**
     * Blocks this coroutine until the predicate is true or the specified timeout has elapsed
     *
     * The associated mutex is unlocked while this coroutine is awaiting
     *
     * @return true If this coroutine was waked by signal() or signalAll(), false if the timeout has elapsed
     */
    @ExperimentalTime
    suspend fun awaitUntil(
        timeout: Duration = Duration.INFINITE,
        owner: Any? = null,
        predicate: () -> Boolean
    ): Boolean {
        val start = System.nanoTime()
        while (!predicate()) {
            val elapsed = (System.nanoTime() - start).nanoseconds
            val remainingTimeout = timeout - elapsed
            if (remainingTimeout < Duration.ZERO) {
                return false  // Timeout elapsed without success
            }
            await(remainingTimeout, owner)
        }
        return true
    }

    /**
     * Blocks this coroutine until unblocked by signal() or signalAll(), or the specified timeout has elapsed
     *
     * The associated mutex is unlocked while this coroutine is awaiting
     *
     * @return true If this coroutine was waked by signal() or signalAll(), false if the timeout has elapsed
     */
//    @ExperimentalTime
    suspend fun await(timeout: Duration = Duration.INFINITE, owner: Any? = null): Boolean {
        ensureLocked(owner, "wait")
        val waiter = Mutex(true)
        waiting.add(waiter)
        mutex.unlock(owner)
        try {
            withTimeout(timeout) {
                waiter.lock()
            }
            return true
        } catch (e: TimeoutCancellationException) {
            return false
        } finally {
            mutex.lock(owner)
            waiting.remove(waiter)
        }
    }

    /**
     * Wakes up one coroutine blocked in await()
     */
    fun signal(owner: Any? = null) {
        ensureUnlocked(owner, "notify")
        val it = waiting.iterator()
        if (it.hasNext()) {
            val waiter = it.next()
            it.remove()
            waiter.unlock()
        }
    }

    /**
     * Wakes up all coroutines blocked in await()
     */
    fun signalAll(owner: Any? = null) {
        ensureUnlocked(owner, "notifyAll")
        val it = waiting.iterator()
        while (it.hasNext()) {
            val waiter = it.next()
            it.remove()
            waiter.unlock()
        }
    }

    internal fun ensureLocked(owner: Any?, funcName: String) {
        val isLocked = if (owner == null) mutex.isLocked else mutex.holdsLock(owner)
        if (!isLocked) {
            throw IllegalStateException("${funcName} requires a locked mutex")
        }
    }

    internal fun ensureUnlocked(owner: Any?, funcName: String) {
        val isLocked = mutex.isLocked
        if (isLocked) {
            throw IllegalStateException("${funcName} requires an unlocked mutex")
        }
    }
}

fun Mutex.newCondition(): Condition {
    return Condition(this)
}
