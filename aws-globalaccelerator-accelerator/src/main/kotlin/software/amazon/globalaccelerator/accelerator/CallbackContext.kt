package software.amazon.globalaccelerator.accelerator

data class CallbackContext(val stabilizationRetriesRemaining: Int = 0, val pendingStabilization: Boolean = false)