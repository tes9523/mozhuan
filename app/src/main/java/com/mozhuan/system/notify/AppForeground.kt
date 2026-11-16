package com.mozhuan.system.notify

/** 记录应用是否处于前台，避免在前台对话时弹出完成通知。 */
object AppForeground {

    @Volatile
    var isForeground: Boolean = false
        private set

    fun onStart() {
        isForeground = true
    }

    fun onStop() {
        isForeground = false
    }
}
