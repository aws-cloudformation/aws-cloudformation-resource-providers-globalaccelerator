package software.amazon.globalaccelerator.listener

data class CallbackContext(val stabilizationRetriesRemaining: Int = 0, val pendingStabilization: Boolean = false)
