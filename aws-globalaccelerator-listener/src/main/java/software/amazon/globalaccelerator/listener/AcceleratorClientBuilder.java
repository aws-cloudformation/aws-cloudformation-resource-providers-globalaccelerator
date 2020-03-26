package software.amazon.globalaccelerator.listener;

import com.amazonaws.services.globalaccelerator.AWSGlobalAccelerator;
import com.amazonaws.services.globalaccelerator.AWSGlobalAcceleratorClientBuilder;

public class AcceleratorClientBuilder {
    private static final String SUPPORTED_REGION = "us-west-2";
    static AWSGlobalAccelerator getClient() {
        return AWSGlobalAcceleratorClientBuilder.standard().withRegion(SUPPORTED_REGION).build();
    }
}
