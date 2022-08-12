package cc.ioctl.telebot.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

fun CoroutineScope.postDelayed(millis: Long, block: suspend () -> Unit): Job {
    return launch {
        delay(millis)
        block()
    }
}
