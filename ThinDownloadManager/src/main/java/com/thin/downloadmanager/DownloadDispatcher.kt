package com.thin.downloadmanager

import android.os.Process

import com.thin.downloadmanager.util.Log

import org.apache.http.conn.ConnectTimeoutException

import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLConnection
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.BlockingQueue

import android.content.ContentValues.TAG
import java.net.HttpURLConnection.HTTP_INTERNAL_ERROR
import java.net.HttpURLConnection.HTTP_MOVED_PERM
import java.net.HttpURLConnection.HTTP_MOVED_TEMP
import java.net.HttpURLConnection.HTTP_OK
import java.net.HttpURLConnection.HTTP_PARTIAL
import java.net.HttpURLConnection.HTTP_SEE_OTHER
import java.net.HttpURLConnection.HTTP_UNAVAILABLE

/**
 * This thread/class used to make [HttpURLConnection], receives Response from server
 * and Dispatch the response to respective [DownloadRequest]
 *
 * @author Mani Selvaraj
 * @author Praveen Kumar
 */
internal class DownloadDispatcher
/**
 * Constructor take the dependency (DownloadRequest queue) that all the Dispatcher needs
 */
(
        /**
         * The queue of download requests to service.
         */
        private val mQueue: BlockingQueue<DownloadRequest>,
        /**
         * To Delivery call back response on main thread
         */
        private val mDelivery: DownloadRequestQueue.CallBackDelivery) : Thread() {
    /**
     * Used to tell the dispatcher to die.
     */
    @Volatile
    private var mQuit = false
    /**
     * How many times redirects happened during a download request.
     */
    private var mRedirectionCount = 0
    private var mContentLength: Long = 0
    private var shouldAllowRedirects = true

    /**
     * This variable is part of resumable download feature.
     * It will load the downloaded file cache length, If It had been already in available Downloaded Requested output path.
     * Otherwise it would keep "0" always by default.
     */
    private var mDownloadedCacheSize: Long = 0

    private var mTimer: Timer? = null

    override fun run() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
        mTimer = Timer()
        while (true) {
            var request: DownloadRequest? = null
            try {
                request = mQueue.take()
                mRedirectionCount = 0
                shouldAllowRedirects = true
                Log.v("Download initiated for " + request!!.downloadId)
                updateDownloadState(request, DownloadManager.Status.STARTED)
                executeDownload(request, request.getUri().toString())
            } catch (e: InterruptedException) {
                // We may have been interrupted because it was time to quit.
                if (mQuit) {
                    if (request != null) {
                        request.finish()
                        // don't remove files that have been downloaded sucessfully.
                        if (request.downloadState != DownloadManager.Status.SUCCESSFUL) {
                            updateDownloadFailed(request, DownloadManager.Error.DOWNLOAD_CANCELLED, "Download cancelled")
                        }
                    }
                    mTimer!!.cancel()
                    return
                }
            }

        }
    }

    fun quit() {
        mQuit = true
        interrupt()
    }


    private fun executeDownload(request: DownloadRequest, downloadUrl: String) {
        val url: URL
        try {
            url = URL(downloadUrl)
        } catch (e: MalformedURLException) {
            updateDownloadFailed(request, DownloadManager.Error.MALFORMED_URI, "MalformedURLException: URI passed is malformed.")
            return
        }

        var conn: HttpURLConnection? = null

        try {
            conn = url.openConnection() as HttpURLConnection
            val destinationFile = File(request.getDestinationURI()!!.path!!)
            if (destinationFile.exists()) {
                mDownloadedCacheSize = destinationFile.length().toInt().toLong()
            }
            conn.setRequestProperty("Range", "bytes=$mDownloadedCacheSize-")

            Log.d(TAG, "Existing file mDownloadedCacheSize: $mDownloadedCacheSize")
            conn.instanceFollowRedirects = false
            conn.connectTimeout = request.retryPolicy.currentTimeout
            conn.readTimeout = request.retryPolicy.currentTimeout

            val customHeaders = request.customHeaders
            for (headerName in customHeaders.keys) {
                conn.addRequestProperty(headerName, customHeaders[headerName])
            }

            // Status Connecting is set here before
            // urlConnection is trying to connect to destination.
            updateDownloadState(request, DownloadManager.Status.CONNECTING)

            val responseCode = conn.responseCode

            Log.v("Response code obtained for downloaded Id "
                    + request.downloadId
                    + " : httpResponse Code "
                    + responseCode)

            when (responseCode) {
                HTTP_PARTIAL, HTTP_OK -> {
                    shouldAllowRedirects = false
                    if (readResponseHeaders(request, conn, responseCode) == 1) {
                        Log.d(TAG, "Existing mDownloadedCacheSize: $mDownloadedCacheSize")
                        Log.d(TAG, "File mContentLength: $mContentLength")
                        if (mDownloadedCacheSize == mContentLength) { // Mark as success, If end of stream already reached
                            updateDownloadComplete(request)
                            Log.d(TAG, "Download Completed")
                        } else {
                            transferData(request, conn)
                        }
                    } else {
                        updateDownloadFailed(request, DownloadManager.Error.DOWNLOAD_SIZE_UNKNOWN, "Transfer-Encoding not found as well as can't know size of download, giving up")
                    }
                    return
                }
                HTTP_MOVED_PERM, HTTP_MOVED_TEMP, HTTP_SEE_OTHER, HTTP_TEMP_REDIRECT -> {
                    // Take redirect url and call executeDownload recursively until
                    // MAX_REDIRECT is reached.
                    while (mRedirectionCount < MAX_REDIRECTS && shouldAllowRedirects) {
                        mRedirectionCount++
                        Log.v(TAG, "Redirect for downloaded Id " + request.downloadId)
                        val location = conn.getHeaderField("Location")
                        executeDownload(request, location)
                    }

                    if (mRedirectionCount > MAX_REDIRECTS && shouldAllowRedirects) {
                        updateDownloadFailed(request, DownloadManager.Error.TOO_MANY_REDIRECTS, "Too many redirects, giving up")
                        return
                    }
                }
                HTTP_REQUESTED_RANGE_NOT_SATISFIABLE -> updateDownloadFailed(request, HTTP_REQUESTED_RANGE_NOT_SATISFIABLE, conn.responseMessage)
                HTTP_UNAVAILABLE -> updateDownloadFailed(request, HTTP_UNAVAILABLE, conn.responseMessage)
                HTTP_INTERNAL_ERROR -> updateDownloadFailed(request, HTTP_INTERNAL_ERROR, conn.responseMessage)
                else -> updateDownloadFailed(request, DownloadManager.Error.UNHANDLED_HTTP_CODE, "Unhandled HTTP response:" + responseCode + " message:" + conn.responseMessage)
            }
        } catch (e: SocketTimeoutException) {
            e.printStackTrace()
            // Retry.
            attemptRetryOnTimeOutException(request)
        } catch (e: ConnectTimeoutException) {
            e.printStackTrace()
            attemptRetryOnTimeOutException(request)
        } catch (e: IOException) {
            e.printStackTrace()
            updateDownloadFailed(request, DownloadManager.Error.HTTP_DATA_ERROR, "Trouble with low-level sockets")
        } finally {
            conn?.disconnect()
        }
    }

    private fun transferData(request: DownloadRequest, conn: HttpURLConnection) {
        var `in`: BufferedInputStream? = null
        var accessFile: RandomAccessFile? = null
        cleanupDestination(request, false)
        try {
            try {
                `in` = BufferedInputStream(conn.inputStream)
            } catch (e: IOException) {
                e.printStackTrace()
            }

            val destinationFile = File(request.getDestinationURI()!!.path!!)

            var errorCreatingDestinationFile = false
            // Create destination file if it doesn't exists
            if (!destinationFile.exists()) {
                try {
                    // Check path
                    val parentPath = destinationFile.parentFile
                    if (parentPath != null && !parentPath.exists()) {
                        parentPath.mkdirs()
                    }
                    if (!destinationFile.createNewFile()) {
                        errorCreatingDestinationFile = true
                        updateDownloadFailed(request, DownloadManager.Error.FILE_ERROR,
                                "Error in creating destination file")
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                    errorCreatingDestinationFile = true
                    updateDownloadFailed(request, DownloadManager.Error.FILE_ERROR,
                            "Error in creating destination file")
                }

            } else {
                if (`in` != null) {
                    request.abortCancel()
                }
            }

            // If Destination file couldn't be created. Abort the data transfer.
            if (!errorCreatingDestinationFile) {
                try {
                    accessFile = RandomAccessFile(destinationFile, "rw")
                    accessFile.seek(mDownloadedCacheSize)
                } catch (e: IOException) {
                    e.printStackTrace()
                }

                if (`in` == null) {
                    updateDownloadFailed(request, DownloadManager.Error.FILE_ERROR,
                            "Error in creating input stream")
                } else if (accessFile == null) {

                    updateDownloadFailed(request, DownloadManager.Error.FILE_ERROR,
                            "Error in writing download contents to the destination file")
                } else {
                    // Start streaming data
                    transferData(request, `in`, accessFile)
                }
            }

        } finally {
            try {
                `in`?.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }

            try {
                accessFile?.close()
            } catch (e: IOException) {
                e.printStackTrace()
            } finally {
                try {
                    accessFile?.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }

            }
        }
    }

    private fun transferData(request: DownloadRequest, `in`: InputStream, out: RandomAccessFile) {
        val data = ByteArray(Companion.BUFFER_SIZE)
        var mCurrentBytes = mDownloadedCacheSize
        request.downloadState = DownloadManager.Status.RUNNING
        Log.v("Content Length: " + mContentLength + " for Download Id " + request.downloadId)
        while (true) {
            if (request.isCancelled) {
                Log.v("Stopping the download as Download Request is cancelled for Downloaded Id " + request.downloadId)
                request.finish()
                updateDownloadFailed(request, DownloadManager.Error.DOWNLOAD_CANCELLED, "Download cancelled")
                return
            }
            val bytesRead = readFromResponse(request, data, `in`)

            if (mContentLength != -1L && mContentLength > 0) {
                val progress = (mCurrentBytes * 100 / mContentLength).toInt()
                updateDownloadProgress(request, progress, mCurrentBytes)
            }

            if (bytesRead == -1) { // success, end of stream already reached
                updateDownloadComplete(request)
                return
            } else if (bytesRead == Integer.MIN_VALUE) {
                return
            }

            if (writeDataToDestination(request, data, bytesRead, out)) {
                mCurrentBytes += bytesRead.toLong()
            } else {
                request.finish()
                updateDownloadFailed(request, DownloadManager.Error.FILE_ERROR, "Failed writing file")
                return
            }
        }
    }

    private fun readFromResponse(request: DownloadRequest, data: ByteArray, entityStream: InputStream): Int {
        try {
            return entityStream.read(data)
        } catch (ex: IOException) {
            if ("unexpected end of stream" == ex.message) {
                return -1
            }
            updateDownloadFailed(request, DownloadManager.Error.HTTP_DATA_ERROR, "IOException: Failed reading response")
            return Integer.MIN_VALUE
        }

    }

    private fun writeDataToDestination(request: DownloadRequest, data: ByteArray, bytesRead: Int, out: RandomAccessFile): Boolean {
        var successInWritingToDestination = true
        try {
            out.write(data, 0, bytesRead)
        } catch (ex: IOException) {
            updateDownloadFailed(request, DownloadManager.Error.FILE_ERROR, "IOException when writing download contents to the destination file")
            successInWritingToDestination = false
        } catch (e: Exception) {
            updateDownloadFailed(request, DownloadManager.Error.FILE_ERROR, "Exception when writing download contents to the destination file")
            successInWritingToDestination = false
        }

        return successInWritingToDestination
    }

    private fun readResponseHeaders(request: DownloadRequest, conn: HttpURLConnection, responseCode: Int): Int {
        val transferEncoding = conn.getHeaderField("Transfer-Encoding")
        mContentLength = -1

        if (transferEncoding == null) {
            if (responseCode == HTTP_OK) {
                // If file download already completed, 200 HttpStatusCode will thrown by service.
                mContentLength = getHeaderFieldLong(conn, "Content-Length", -1)
            } else {
                // If file download already partially completed, 206 HttpStatusCode will thrown by service and we can resume remaining chunks downloads.
                mContentLength = getHeaderFieldLong(conn, "Content-Length", -1) + mDownloadedCacheSize
            }
        } else {
            Log.v("Ignoring Content-Length since Transfer-Encoding is also defined for Downloaded Id " + request.downloadId)
        }

        return if (mContentLength != -1L) {
            1
        } else if (transferEncoding == null || !transferEncoding.equals("chunked", ignoreCase = true)) {
            -1
        } else {
            1
        }
    }

    private fun getHeaderFieldLong(conn: URLConnection, field: String, defaultValue: Long): Long =
            try {
                java.lang.Long.parseLong(conn.getHeaderField(field))
            } catch (e: NumberFormatException) {
                defaultValue
            }

    private fun attemptRetryOnTimeOutException(request: DownloadRequest) {
        updateDownloadState(request, DownloadManager.Status.RETRYING)
        val retryPolicy = request.retryPolicy
        try {
            retryPolicy.retry()
            mTimer!!.schedule(object : TimerTask() {
                override fun run() {
                    executeDownload(request, request.getUri().toString())
                }
            }, retryPolicy.currentTimeout.toLong())
        } catch (e: RetryError) {
            // Update download failed.
            updateDownloadFailed(request, DownloadManager.Error.CONNECTION_TIMEOUT_AFTER_RETRIES,
                    "Connection time out after maximum retires attempted")
        }

    }

    /**
     * Called just before the thread finishes, regardless of status, to take any necessary action on
     * the downloaded file with mDownloadedCacheSize file.
     *
     * @param forceClean -  It will delete downloaded cache, Even streaming is enabled, If user intentionally cancelled.
     */
    private fun cleanupDestination(request: DownloadRequest, forceClean: Boolean) {
        if (!request.isResumable || forceClean) {
            val path = request.getDestinationURI()!!.path!!
            Log.d("cleanupDestination() deleting $path")
            val destinationFile = File(path)
            if (destinationFile.exists()) {
                destinationFile.delete()
            }
        }
    }

    private fun updateDownloadState(request: DownloadRequest, state: DownloadManager.Status) {
        request.downloadState = state
    }

    private fun updateDownloadComplete(request: DownloadRequest) {
        mDownloadedCacheSize = 0 // reset into Zero.
        mDelivery.postDownloadComplete(request)
        request.downloadState = DownloadManager.Status.SUCCESSFUL
        request.finish()
    }

    private fun updateDownloadFailed(request: DownloadRequest, error: DownloadManager.Error, errorMsg: String) {
        updateDownloadFailed(request, error.code, errorMsg)
    }

    private fun updateDownloadFailed(request: DownloadRequest, errorCode: Int, errorMsg: String) {
        mDownloadedCacheSize = 0 // reset into Zero.
        shouldAllowRedirects = false
        request.downloadState = DownloadManager.Status.FAILED
        if (request.getDeleteDestinationFileOnFailure()) {
            cleanupDestination(request, true)
        }
        mDelivery.postDownloadFailed(request, errorCode, errorMsg)
        request.finish()
    }

    private fun updateDownloadProgress(request: DownloadRequest, progress: Int, downloadedBytes: Long) {
        mDelivery.postProgressUpdate(request, mContentLength, downloadedBytes, progress)
    }

    companion object {
        /**
         * The buffer size used to stream the data
         */
        private const val BUFFER_SIZE = 4096
        /**
         * The maximum number of redirects.
         */
        private const val MAX_REDIRECTS = 5 // can't be more than 7.
        private const val HTTP_REQUESTED_RANGE_NOT_SATISFIABLE = 416
        private const val HTTP_TEMP_REDIRECT = 307
    }
}
