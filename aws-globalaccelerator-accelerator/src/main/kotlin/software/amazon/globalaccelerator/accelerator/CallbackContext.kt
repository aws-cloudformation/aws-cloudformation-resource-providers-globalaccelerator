package software.amazon.globalaccelerator.accelerator

/**
 * Holds state to be passed between callbacks.
 * stabilizationRetriesRemaining: Number of seconds remaining before giving up on retries.
 * pendingStabilization: Waiting for termination or completion
 */
data class CallbackContext(val stabilizationRetriesRemaining: Int = 0, val pendingStabilization: Boolean = false)
