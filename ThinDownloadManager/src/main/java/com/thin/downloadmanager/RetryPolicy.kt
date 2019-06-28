package com.thin.downloadmanager

/**
 * Created by maniselvaraj on 15/4/15.
 */
interface RetryPolicy {

    /**
     * Returns the current timeout (used for logging).
     */
    val currentTimeout: Int

    /**
     * Returns the current retry count (used for logging).
     */
    val currentRetryCount: Int

    /**
     * Return back off multiplier
     */
    val backOffMultiplier: Float


    @Throws(RetryError::class)
    fun retry()


}
