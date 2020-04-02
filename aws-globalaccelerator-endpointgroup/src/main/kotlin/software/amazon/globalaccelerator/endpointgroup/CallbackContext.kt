package software.amazon.globalaccelerator.endpointgroup

data class CallbackContext(val stabilizationRetriesRemaining: Int = 0, val pendingStabilization: Boolean = false)
