package software.amazon.globalaccelerator.endpointgroup

import com.amazonaws.services.globalaccelerator.model.EndpointDescription
import com.amazonaws.services.globalaccelerator.model.PortOverride

/**
 * Maps GlobalAccelerator port overrides to resource model.
 */
fun getPortOverrides(overrides: List<PortOverride>?): List<software.amazon.globalaccelerator.endpointgroup.PortOverride>? {
    return overrides?.map {
        software.amazon.globalaccelerator.endpointgroup.PortOverride.builder()
                .listenerPort(it.listenerPort)
                .endpointPort(it.endpointPort)
                .build()
    }
}

fun getEndpointConfigurations(endpointDescriptions: List<EndpointDescription>): List<EndpointConfiguration> {
    return endpointDescriptions.map {
        EndpointConfiguration.builder()
                .clientIPPreservationEnabled(it.clientIPPreservationEnabled)
                .endpointId(it.endpointId)
                .weight(it.weight)
                .build()
    }
}
