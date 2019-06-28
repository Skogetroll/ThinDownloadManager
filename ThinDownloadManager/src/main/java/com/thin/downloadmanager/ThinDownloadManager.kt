package com.thin.downloadmanager

import android.os.Handler

import com.thin.downloadmanager.util.Log

import java.security.InvalidParameterException
import kotlin.contracts.ExperimentalContracts

/**
 * This class used to handles long-running HTTP downloads, User can raise a [DownloadRequest] request with multiple features.
 * The download manager will conduct the download in the background, taking care of HTTP interactions, failures  and retrying downloads
 * across connectivity changes.
 *
 * @author Mani Selvaraj
 * @author Praveen Kumar
 */
class ThinDownloadManager : DownloadManager {

    /**
     * Download request queue takes care of handling the request based on priority.
     */
    private var mRequestQueue: DownloadRequestQueue? = null

    override val isReleased: Boolean
        get() = mRequestQueue == null

    /**
     * Construct with logging Enabled.
     * @param loggingEnabled - enable log info
     */
    @JvmOverloads
    constructor(loggingEnabled: Boolean = true) {
        mRequestQueue = DownloadRequestQueue()
        mRequestQueue?.start()
        setLoggingEnabled(loggingEnabled)
    }

    /**
     * Construct with provided callback handler
     *
     * @param callbackHandler - callback handler
     */
    @Throws(InvalidParameterException::class)
    constructor(callbackHandler: Handler) {
        mRequestQueue = DownloadRequestQueue(callbackHandler)
        mRequestQueue?.start()
        setLoggingEnabled(true)
    }

    /**
     * Constructor taking MAX THREAD POOL SIZE  Allows maximum of 4 threads.
     * Any number higher than four or less than one wont be respected.
     *
     * Deprecated use Default Constructor. As the thread pool size will not respected anymore through this constructor.
     * Thread pool size is determined with the number of available processors on the device.
     */
    constructor(threadPoolSize: Int) {
        mRequestQueue = DownloadRequestQueue(threadPoolSize)
        mRequestQueue?.start()
        setLoggingEnabled(true)
    }

    /**
     * Add a new download.  The download will start automatically once the download manager is
     * ready to execute it and connectivity is available.
     *
     * @param request the parameters specifying this download
     * @return an ID for the download, unique across the application.  This ID is used to make future
     * calls related to this download.
     * @throws IllegalArgumentException
     */
    @Throws(IllegalArgumentException::class)
    override fun add(request: DownloadRequest?): Int {
        val requestQueue = mRequestQueue
        checkReleased(requestQueue, "add(...) called on a released ThinDownloadManager.")
        if (request == null) {
            throw IllegalArgumentException("DownloadRequest cannot be null")
        }
        return requestQueue.add(request)
    }

    override fun cancel(downloadId: Int): Int {
        val requestQueue = mRequestQueue
        checkReleased(requestQueue, "cancel(...) called on a released ThinDownloadManager.")
        return requestQueue.cancel(downloadId)
    }

    override fun cancelAll() {
        val requestQueue = mRequestQueue
        checkReleased(requestQueue, "cancelAll() called on a released ThinDownloadManager.")
        requestQueue.cancelAll()
    }

    override fun pause(downloadId: Int): Int {
        val requestQueue = mRequestQueue
        checkReleased(requestQueue, "pause(...) called on a released ThinDownloadManager.")
        return requestQueue.pause(downloadId)
    }

    override fun pauseAll() {
        val requestQueue = mRequestQueue
        checkReleased(requestQueue, "pauseAll() called on a released ThinDownloadManager.")
        requestQueue.pauseAll()
    }

    override fun query(downloadId: Int): DownloadManager.Status {
        val requestQueue = mRequestQueue
        checkReleased(requestQueue, "query(...) called on a released ThinDownloadManager.")
        return requestQueue.query(downloadId)
    }

    override fun release() {
        if (!isReleased) {
            mRequestQueue?.release()
            mRequestQueue = null
        }
    }

    private fun setLoggingEnabled(enabled: Boolean) {
        Log.isEnabled = enabled
    }
}

/**
 * This is called by methods that want to throw an exception if the DownloadManager
 * has already been released.
 */
@UseExperimental(ExperimentalContracts::class)
private fun checkReleased(requestQueue: DownloadRequestQueue?, errorMessage: String) {
    kotlin.contracts.contract { returns() implies (requestQueue != null) }
    if (requestQueue == null) {
        throw IllegalStateException(errorMessage)
    }
}
