package com.thin.downloadmanager.util

object Log {
    private val sTag = "ThinDownloadManager"
    var isEnabled = false

    fun isLoggingEnabled(): Boolean {
        return isEnabled
    }

    fun v(msg: String): Int {
        return if (isEnabled) {
            android.util.Log.v(sTag, msg)
        } else 0
    }

    fun v(tag: String, msg: String): Int {
        return if (isEnabled) {
            android.util.Log.v(tag, msg)
        } else 0
    }

    fun v(msg: String, tr: Throwable): Int {
        return if (isEnabled) {
            android.util.Log.v(sTag, msg, tr)
        } else 0
    }

    fun v(tag: String, msg: String, tr: Throwable): Int {
        return if (isEnabled) {
            android.util.Log.v(tag, msg, tr)
        } else 0
    }

    fun d(msg: String): Int {
        return if (isEnabled) {
            android.util.Log.d(sTag, msg)
        } else 0
    }

    fun d(tag: String, msg: String): Int {
        return if (isEnabled) {
            android.util.Log.d(tag, msg)
        } else 0
    }

    fun d(msg: String, tr: Throwable): Int {
        return if (isEnabled) {
            android.util.Log.d(sTag, msg, tr)
        } else 0
    }

    fun d(tag: String, msg: String, tr: Throwable): Int {
        return if (isEnabled) {
            android.util.Log.d(tag, msg, tr)
        } else 0
    }

    fun i(msg: String): Int {
        return if (isEnabled) {
            android.util.Log.i(sTag, msg)
        } else 0
    }

    fun i(tag: String, msg: String): Int {
        return if (isEnabled) {
            android.util.Log.i(tag, msg)
        } else 0
    }

    fun i(msg: String, tr: Throwable): Int {
        return if (isEnabled) {
            android.util.Log.i(sTag, msg, tr)
        } else 0
    }

    fun i(tag: String, msg: String, tr: Throwable): Int {
        return if (isEnabled) {
            android.util.Log.i(tag, msg, tr)
        } else 0
    }

    fun w(msg: String): Int {
        return if (isEnabled) {
            android.util.Log.w(sTag, msg)
        } else 0
    }

    fun w(tag: String, msg: String): Int {
        return if (isEnabled) {
            android.util.Log.w(tag, msg)
        } else 0
    }

    fun w(msg: String, tr: Throwable): Int {
        return if (isEnabled) {
            android.util.Log.w(sTag, msg, tr)
        } else 0
    }

    fun w(tag: String, msg: String, tr: Throwable): Int {
        return if (isEnabled) {
            android.util.Log.w(tag, msg, tr)
        } else 0
    }

    fun e(msg: String): Int {
        return if (isEnabled) {
            android.util.Log.e(sTag, msg)
        } else 0
    }

    fun e(tag: String, msg: String): Int {
        return if (isEnabled) {
            android.util.Log.e(tag, msg)
        } else 0
    }

    fun e(msg: String, tr: Throwable): Int {
        return if (isEnabled) {
            android.util.Log.e(sTag, msg, tr)
        } else 0
    }

    fun e(tag: String, msg: String, tr: Throwable): Int {
        return if (isEnabled) {
            android.util.Log.e(tag, msg, tr)
        } else 0
    }

    fun t(msg: String, vararg args: Any): Int {
        return if (isEnabled) {
            android.util.Log.v("test", String.format(msg, *args))
        } else 0
    }
}