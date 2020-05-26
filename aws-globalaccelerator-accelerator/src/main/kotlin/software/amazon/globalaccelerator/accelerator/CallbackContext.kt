package software.amazon.globalaccelerator.accelerator

/**
 * Holds date to be passed between Call Backs.
 * stabilizationRetriesRemaining: Number of seconds remaining before giving up on retries.
 * pendingStabilization: Waiting for termination or completion
 */
data class CallbackContext(val stabilizationRetriesRemaining: Int = 0, val pendingStabilization: Boolean = false)
