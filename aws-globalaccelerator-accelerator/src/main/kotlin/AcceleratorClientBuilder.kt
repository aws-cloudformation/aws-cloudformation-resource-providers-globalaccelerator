package software.amazon.globalaccelerator.accelerator

import com.amazonaws.services.globalaccelerator.AWSGlobalAccelerator
import com.amazonaws.services.globalaccelerator.AWSGlobalAcceleratorClientBuilder

object AcceleratorClientBuilder {
    private const val SUPPORTED_REGION = "us-west-2"
    val client: AWSGlobalAccelerator
        get() = AWSGlobalAcceleratorClientBuilder.standard().withRegion(SUPPORTED_REGION).build()
}
