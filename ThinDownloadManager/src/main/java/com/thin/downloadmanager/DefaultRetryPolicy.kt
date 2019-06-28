package com.thin.downloadmanager

/**
 * Created by maniselvaraj on 15/4/15.
 */
class DefaultRetryPolicy
/**
 * Constructs a new retry policy.
 * @param initialTimeoutMs The initial timeout for the policy.
 * @param maxNumRetries The maximum number of retries.
 * @param backoffMultiplier Backoff multiplier for the policy.
 */
@JvmOverloads constructor(initialTimeoutMs: Int = DEFAULT_TIMEOUT_MS,
                          /** The maximum number of attempts.  */
                          private val mMaxNumRetries: Int = DEFAULT_MAX_RETRIES,
                          /** The backoff multiplier for for the policy.  */
                          override val backOffMultiplier: Float = DEFAULT_BACKOFF_MULT) : RetryPolicy {

    /** The current timeout in milliseconds.  */
    override var currentTimeout: Int = 0
        private set

    /** The current retry count.  */
    override var currentRetryCount: Int = 0
        private set

    init {
        currentTimeout = initialTimeoutMs
    }

    @Throws(RetryError::class)
    override fun retry() {
        currentRetryCount++
        currentTimeout += (currentTimeout * backOffMultiplier).toInt()
        if (!hasAttemptRemaining()) {
            throw RetryError()
        }
    }

    /**
     * Returns true if this policy has attempts remaining, false otherwise.
     */
    private fun hasAttemptRemaining(): Boolean {
        return currentRetryCount <= mMaxNumRetries
    }

    companion object {

        /** The default socket timeout in milliseconds  */
        const val DEFAULT_TIMEOUT_MS = 5000

        /** The default number of retries  */
        const val DEFAULT_MAX_RETRIES = 1

        /** The default backoff multiplier  */
        const val DEFAULT_BACKOFF_MULT = 1f
    }

}
/**
 * Constructs a new retry policy using the default timeouts.
 */
