package software.amazon.globalaccelerator.listener

import com.amazonaws.services.globalaccelerator.AWSGlobalAccelerator
import com.amazonaws.services.globalaccelerator.AWSGlobalAcceleratorClientBuilder

object AcceleratorClientBuilder {
    private val SUPPORTED_REGION = "us-west-2"
    internal val client: AWSGlobalAccelerator
        get() = AWSGlobalAcceleratorClientBuilder.standard().withRegion(SUPPORTED_REGION).build()
}
