package com.thin.downloadmanager

import android.os.Handler
import android.os.Looper

import com.thin.downloadmanager.util.Log

import java.security.InvalidParameterException
import java.util.HashSet
import java.util.Locale
import java.util.concurrent.Executor
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.atomic.AtomicInteger

class DownloadRequestQueue {

    /**
     * The set of all requests currently being processed by this RequestQueue. A Request will be in this set if it is waiting in any queue or currently being processed by any dispatcher.
     */
    private var mCurrentRequests: MutableMap<Int, DownloadRequest>? = HashMap()

    /** The queue of requests that are actually going out to the network.  */
    private var mDownloadQueue: PriorityBlockingQueue<DownloadRequest>? = PriorityBlockingQueue()

    /** The download dispatchers  */
    private var mDownloadDispatchers: Array<DownloadDispatcher?>? = null

    /** Used for generating monotonically-increasing sequence numbers for requests.  */
    private val mSequenceGenerator = AtomicInteger()

    private var mDelivery: CallBackDelivery? = null

    /**
     * Gets a sequence number.
     */
    private val downloadId: Int
        get() = mSequenceGenerator.incrementAndGet()

    /**
     * Delivery class to delivery the call back to call back registrar in main thread.
     */
    internal inner class CallBackDelivery
    /**
     * Constructor taking a handler to main thread.
     */
    (handler: Handler) {

        /** Used for posting responses, typically to the main thread.  */
        private val mCallBackExecutor: Executor

        init {
            // Make an Executor that just wraps the handler.
            mCallBackExecutor = Executor { command -> handler.post(command) }
        }

        fun postDownloadComplete(request: DownloadRequest) {
            mCallBackExecutor.execute {
                request.getDownloadListener()?.onDownloadComplete(request.downloadId)
                request.getStatusListener()?.onDownloadComplete(request)
            }
        }

        fun postDownloadFailed(request: DownloadRequest, errorCode: Int, errorMsg: String) {
            mCallBackExecutor.execute {
                request.getDownloadListener()?.onDownloadFailed(request.downloadId, errorCode, errorMsg)
                request.getStatusListener()?.onDownloadFailed(request, errorCode, errorMsg)
            }
        }

        fun postProgressUpdate(request: DownloadRequest, totalBytes: Long, downloadedBytes: Long, progress: Int) {
            mCallBackExecutor.execute {
                request.getDownloadListener()?.onProgress(request.downloadId, totalBytes, downloadedBytes, progress)
                request.getStatusListener()?.onProgress(request, totalBytes, downloadedBytes, progress)
            }
        }
    }

    /**
     * Default constructor.
     */
    constructor() {
        initialize(Handler(Looper.getMainLooper()))
    }

    /**
     * Creates the download dispatchers workers pool.
     *
     * Deprecated:
     */
    constructor(threadPoolSize: Int) {
        initialize(Handler(Looper.getMainLooper()), threadPoolSize)
    }

    /**
     * Construct with provided callback handler.
     *
     * @param callbackHandler
     */
    @Throws(InvalidParameterException::class)
    constructor(callbackHandler: Handler?) {
        if (callbackHandler == null) {
            throw InvalidParameterException("callbackHandler must not be null")
        }

        initialize(callbackHandler)
    }

    fun start() {
        stop() // Make sure any currently running dispatchers are stopped.

        // Create download dispatchers (and corresponding threads) up to the pool size.
        val downloadDispatchers = mDownloadDispatchers
        val queue = mDownloadQueue
        val delivery = mDelivery
        if (downloadDispatchers != null && queue != null && delivery != null) {
            for (i in downloadDispatchers.indices) {
                val downloadDispatcher = DownloadDispatcher(queue, delivery)
                downloadDispatchers[i] = downloadDispatcher
                downloadDispatcher.start()
            }
        }
    }

    // Package-Private methods.
    /**
     * Generates a download id for the request and adds the download request to the download request queue for the dispatchers pool to act on immediately.
     *
     * @param request
     * @return downloadId
     */
    internal fun add(request: DownloadRequest): Int {
        val downloadId = downloadId
        // Tag the request as belonging to this queue and add it to the set of current requests.
        request.setDownloadRequestQueue(this)

        val currentRequests = mCurrentRequests
        if (currentRequests != null) {
            synchronized(currentRequests) {
                mCurrentRequests?.set(request.downloadId, request)
            }
        }

        // Process requests in the order they are added.
        request.downloadId = downloadId
        mDownloadQueue?.add(request)

        return downloadId
    }

    /**
     * Returns the current download state for a download request.
     *
     * @param downloadId
     * @return
     */
    internal fun query(downloadId: Int): DownloadManager.Status {
        val currentRequests = mCurrentRequests ?: return DownloadManager.Status.NOT_FOUND
        synchronized(currentRequests) {
            return  currentRequests[downloadId]?.downloadState ?: DownloadManager.Status.NOT_FOUND
        }
    }

    /**
     * Cancel all the dispatchers in work and also stops the dispatchers.
     */
    internal fun cancelAll() {
        val currentRequests = mCurrentRequests ?: return
        synchronized(currentRequests) {
            for (request in currentRequests.values) {
                request.cancel()
            }

            // Remove all the requests from the queue.
            mCurrentRequests?.clear()
        }
    }

    /**
     * Cancel a particular download in progress. Returns 1 if the download Id is found else returns 0.
     *
     * @param downloadId
     * @return int
     */
    internal fun cancel(downloadId: Int): Int {
        val currentRequests = mCurrentRequests ?: return 0
        synchronized(currentRequests) {
            val request = currentRequests[downloadId]
            return if (request != null) {
                request.cancel()
                1
            } else {
                0
            }
        }
    }

    /**
     * Pause a particular download in progress.
     *
     * @param downloadId - selected download request Id
     * @return It will return 1 if the download Id is found else returns 0.
     */
    internal fun pause(downloadId: Int): Int {
        checkResumableDownloadEnabled(downloadId)
        return this.cancel(downloadId)
    }

    /**
     * Pause all the dispatchers in work and also cancel and stops the dispatchers.
     */
    internal fun pauseAll() {
        checkResumableDownloadEnabled(-1) // Error code -1 handle for cancelAll()
        this.cancelAll()
    }


    /**
     * This is called by methods that want to throw an exception if the [DownloadRequest]
     * hasn't enable isResumable feature.
     */
    private fun checkResumableDownloadEnabled(downloadId: Int) {
        val currentRequests = mCurrentRequests!!
        synchronized(currentRequests) {
            for (request in currentRequests.values) {
                if (downloadId == -1 && !request.isResumable) {
                    Log.e("ThinDownloadManager",
                            String.format(Locale.getDefault(), "This request has not enabled resume feature hence request will be cancelled. Request Id: %d", request.downloadId))
                } else if (request.downloadId == downloadId && !request.isResumable) {
                    throw IllegalStateException("You cannot pause the download, unless you have enabled Resume feature in DownloadRequest.")
                } else {
                    //ignored, It can not be a scenario to happen.
                }
            }
        }
    }

    internal fun finish(request: DownloadRequest) {
        val currentRequests = mCurrentRequests
        if (currentRequests != null) {//if finish and release are called together it throws NPE
            // Remove from the queue.
            synchronized(currentRequests) {
                mCurrentRequests?.remove(request.downloadId)
            }
        }
    }

    /**
     * Cancels all the pending & running requests and releases all the dispatchers.
     */
    internal fun release() {
        val currentRequests = mCurrentRequests
        if (currentRequests != null) {
            synchronized(currentRequests) {
                currentRequests.clear()
                mCurrentRequests = null
            }
        }

        if (mDownloadQueue != null) {
            mDownloadQueue = null
        }

        val dispatchers = mDownloadDispatchers
        if (dispatchers != null) {
            stop()

            for (i in dispatchers.indices) {
                dispatchers[i] = null
            }
            mDownloadDispatchers = null
        }

    }

    // Private methods.

    /**
     * Perform construction.
     *
     * @param callbackHandler
     */
    private fun initialize(callbackHandler: Handler) {
        val processors = Runtime.getRuntime().availableProcessors()
        mDownloadDispatchers = arrayOfNulls(processors)
        mDelivery = CallBackDelivery(callbackHandler)
    }

    /**
     * Perform construction with custom thread pool size.
     */
    private fun initialize(callbackHandler: Handler, threadPoolSize: Int) {
        mDownloadDispatchers = arrayOfNulls(threadPoolSize)
        mDelivery = CallBackDelivery(callbackHandler)
    }

    /**
     * Stops download dispatchers.
     */
    private fun stop() {
        val downloadDispatchers = mDownloadDispatchers ?: return
        for (dispatcher in downloadDispatchers) {
            dispatcher?.quit()
        }
    }
}
