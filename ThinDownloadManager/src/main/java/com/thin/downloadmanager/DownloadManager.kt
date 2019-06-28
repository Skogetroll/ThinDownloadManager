package com.thin.downloadmanager

interface DownloadManager {

    val isReleased: Boolean

    fun add(request: DownloadRequest?): Int

    fun cancel(downloadId: Int): Int

    fun cancelAll()

    fun pause(downloadId: Int): Int

    fun pauseAll()

    fun query(downloadId: Int): Status

    fun release()

    enum class Status(val intValue: Int) {
        /**
         * Status when the download is currently pending.
         */
        PENDING(1 shl 0),
        /**
         * Status when the download has started.
         */
        STARTED(1 shl 1),
        /**
         * Status when the download network call is connecting to destination.
         */
        CONNECTING(1 shl 2),
        /**
         * Status when the download is currently running.
         */
        RUNNING(1 shl 3),
        /**
         * Status when the download has successfully completed.
         */
        SUCCESSFUL(1 shl 4),
        /**
         * Status when the download has failed.
         */
        FAILED(1 shl 5),
        /**
         * Status when the download has failed due to broken url or invalid download url
         */
        NOT_FOUND(1 shl 6),
        /**
         * Status when the download is attempted for retry due to connection timeouts.
         */
        RETRYING(1 shl 7);
    }

    enum class Error(val code: Int) {
        /**
         * Error code when writing download content to the destination file.
         */
        FILE_ERROR(1001),
        /**
         * Error code when an HTTP code was received that download manager can't
         * handle.
         */
        UNHANDLED_HTTP_CODE(1002),
        /**
         * Error code when an error receiving or processing data occurred at the
         * HTTP level.
         */
        HTTP_DATA_ERROR(1004),
        /**
         * Error code when there were too many redirects.
         */
        TOO_MANY_REDIRECTS(1005),
        /**
         * Error code when size of the file is unknown.
         */
        DOWNLOAD_SIZE_UNKNOWN(1006),
        /**
         * Error code when passed URI is malformed.
         */
        MALFORMED_URI(1007),
        /**
         * Error code when download is cancelled.
         */
        DOWNLOAD_CANCELLED(1008),
        /**
         * Error code when there is connection timeout after maximum retries
         */
        CONNECTION_TIMEOUT_AFTER_RETRIES(1009)
    }

    companion object

}
