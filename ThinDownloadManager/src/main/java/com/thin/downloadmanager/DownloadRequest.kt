package com.thin.downloadmanager

import android.net.Uri

import java.util.HashMap

/**
 * This class used to handle user requests and provides priorities to the request even there may be 'n' number of request raised.
 * Basically this is [Comparable] class and It compares the [DownloadRequest]'s [Priority] levels and react accordingly.
 *
 * @author Mani Selvaraj
 * @author Praveen Kumar
 */
class DownloadRequest(
        /**
         * The URI resource that this request is to download
         */
        private var mUri: Uri?) : Comparable<DownloadRequest> {

    /**
     * Tells the current download state of this request
     */
    internal var downloadState: DownloadManager.Status = DownloadManager.Status.PENDING

    /**
     * Download Id assigned to this request
     */
    /**
     * Gets the download id.
     *
     * @return the download id
     */
    /**
     * Sets the download Id of this request.  Used by [DownloadRequestQueue].
     */
    var downloadId: Int = 0
        internal set

    /**
     * The destination path on the device where the downloaded files needs to be put
     * It can be either External Directory ( SDcard ) or
     * internal app cache or files directory.
     * For using external SDCard access, application should have
     * this permission android.permission.WRITE_EXTERNAL_STORAGE declared.
     */
    private var mDestinationURI: Uri? = null

    private var mRetryPolicy: RetryPolicy? = null

    /**
     * Whether or not this request has been canceled.
     */
    //Package-private methods.

    /**
     * Returns true if this request has been canceled.
     */
    var isCancelled = false
        private set

    private var mDeleteDestinationFileOnFailure = true

    private var mRequestQueue: DownloadRequestQueue? = null

    private var mDownloadListener: DownloadStatusListener? = null

    private var mDownloadStatusListenerV1: DownloadStatusListenerV1? = null

    private var mDownloadContext: Any? = null

    /**
     * Returns all custom headers set by user
     *
     * @return
     */
    internal val customHeaders: HashMap<String, String>
    private var mPriority = Priority.NORMAL

    var isResumable = false
        private set

    val retryPolicy: RetryPolicy
        get() = mRetryPolicy ?: DefaultRetryPolicy()


    /**
     * Priority values.  Requests will be processed from higher priorities to
     * lower priorities, in FIFO order.
     */
    enum class Priority {
        LOW,
        NORMAL,
        HIGH,
        IMMEDIATE
    }

    init {
        if (mUri == null) {
            throw NullPointerException()
        }

        val scheme = mUri!!.scheme
        if (scheme == null || scheme != "http" && scheme != "https") {
            throw IllegalArgumentException("Can only download HTTP/HTTPS URIs: " + mUri!!)
        }
        customHeaders = HashMap()
        downloadState = DownloadManager.Status.PENDING
    }

    /**
     * Returns the [Priority] of this request; [Priority.NORMAL] by default.
     */
    fun getPriority(): Priority {
        return mPriority
    }

    /**
     * Set the [Priority]  of this request;
     *
     * @param priority
     * @return request
     */
    fun setPriority(priority: Priority): DownloadRequest {
        mPriority = priority
        return this
    }

    /**
     * Adds custom header to request
     *
     * @param key
     * @param value
     */
    fun addCustomHeader(key: String, value: String): DownloadRequest {
        customHeaders[key] = value
        return this
    }

    /**
     * Associates this request with the given queue. The request queue will be notified when this
     * request has finished.
     */
    internal fun setDownloadRequestQueue(downloadQueue: DownloadRequestQueue) {
        mRequestQueue = downloadQueue
    }

    fun setRetryPolicy(mRetryPolicy: RetryPolicy): DownloadRequest {
        this.mRetryPolicy = mRetryPolicy
        return this
    }

    internal fun getDownloadListener(): DownloadStatusListener? {
        return mDownloadListener
    }

    /**
     * Sets the download listener for this download request. Use setStatusListener instead.
     *
     */
    @Deprecated("use {@link #setStatusListener} instead.")
    fun setDownloadListener(downloadListener: DownloadStatusListener): DownloadRequest {
        this.mDownloadListener = downloadListener
        return this
    }

    /**
     * Gets the status listener. For internal use.
     *
     * @return  the status listener
     */
    internal fun getStatusListener(): DownloadStatusListenerV1? {
        return mDownloadStatusListenerV1
    }

    /**
     * Sets the status listener for this download request. Download manager sends progress,
     * failure and completion updates to this listener for this download request.
     *
     * @param downloadStatusListenerV1 the status listener for this download
     */
    fun setStatusListener(downloadStatusListenerV1: DownloadStatusListenerV1): DownloadRequest {
        mDownloadStatusListenerV1 = downloadStatusListenerV1
        return this
    }

    fun getDownloadContext(): Any? {
        return mDownloadContext
    }

    fun setDownloadContext(downloadContext: Any): DownloadRequest {
        mDownloadContext = downloadContext
        return this
    }

    fun getUri(): Uri? {
        return mUri
    }

    fun setUri(mUri: Uri): DownloadRequest {
        this.mUri = mUri
        return this
    }

    fun getDestinationURI(): Uri? {
        return mDestinationURI
    }

    fun setDestinationURI(destinationURI: Uri): DownloadRequest {
        this.mDestinationURI = destinationURI
        return this
    }

    fun getDeleteDestinationFileOnFailure(): Boolean {
        return mDeleteDestinationFileOnFailure
    }

    /**
     * It marks the request with resumable feature and It is an optional feature
     * @param isDownloadResumable - It enables resumable feature for this request
     * @return - current [DownloadRequest]
     */
    fun setDownloadResumable(isDownloadResumable: Boolean): DownloadRequest {
        this.isResumable = isDownloadResumable
        setDeleteDestinationFileOnFailure(false) // If resumable feature enabled, downloaded file should not be deleted.
        return this
    }

    /**
     * Set if destination file should be deleted on download failure.
     * Use is optional: default is to delete.
     */
    fun setDeleteDestinationFileOnFailure(deleteOnFailure: Boolean): DownloadRequest {
        this.mDeleteDestinationFileOnFailure = deleteOnFailure
        return this
    }

    /**
     * Mark this request as canceled.  No callback will be delivered.
     */
    fun cancel() {
        isCancelled = true
    }


    /**
     * Marked the request as canceled is aborted.
     */
    fun abortCancel() {
        isCancelled = false
    }

    internal fun finish() {
        mRequestQueue?.finish(this)
    }

    override fun compareTo(other: DownloadRequest): Int {
        val left = this.getPriority()
        val right = other.getPriority()

        // High-priority requests are "lesser" so they are sorted to the front.
        // Equal priorities are sorted by sequence number to provide FIFO ordering.
        return if (left == right)
            this.downloadId - other.downloadId
        else
            right.ordinal - left.ordinal
    }
}
