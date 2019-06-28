package com.thin.downloadmanager

/**
 * A Listener for the download progress.
 *
 */
@Deprecated("use {@link DownloadStatusListenerV1} instead")
interface DownloadStatusListener {

    //Callback when download is successfully completed
    fun onDownloadComplete(id: Int)

    //Callback if download is failed. Corresponding error code and error messages are provided
    fun onDownloadFailed(id: Int, errorCode: Int, errorMessage: String)

    //Callback provides download progress
    fun onProgress(id: Int, totalBytes: Long, downloadedBytes: Long, progress: Int)
}
