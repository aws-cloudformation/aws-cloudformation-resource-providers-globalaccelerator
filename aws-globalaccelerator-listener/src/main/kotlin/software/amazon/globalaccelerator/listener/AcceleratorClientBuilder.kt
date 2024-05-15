package software.amazon.globalaccelerator.listener

import com.amazonaws.services.globalaccelerator.AWSGlobalAccelerator
import com.amazonaws.services.globalaccelerator.AWSGlobalAcceleratorClientBuilder

/**
 * Singleton class for AWS Global Accelerator client
 */
object AcceleratorClientBuilder {
    private final const val SUPPORTED_REGION = "us-west-2"
    internal final val client: AWSGlobalAccelerator
        get() = AWSGlobalAcceleratorClientBuilder.standard().withRegion(SUPPORTED_REGION).build()
}
