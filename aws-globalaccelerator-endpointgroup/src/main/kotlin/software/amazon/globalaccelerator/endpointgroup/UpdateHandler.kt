package software.amazon.globalaccelerator.endpointgroup

import com.amazonaws.services.globalaccelerator.AWSGlobalAccelerator
import com.amazonaws.services.globalaccelerator.model.EndpointConfiguration
import com.amazonaws.services.globalaccelerator.model.PortOverride
import com.amazonaws.services.globalaccelerator.model.UpdateEndpointGroupRequest
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy
import software.amazon.cloudformation.proxy.HandlerErrorCode
import software.amazon.cloudformation.proxy.Logger
import software.amazon.cloudformation.proxy.ProgressEvent
import software.amazon.cloudformation.proxy.ResourceHandlerRequest
import java.util.ArrayList

/**
 * Update handler implementation for Endpoint Group resource.
 */
class UpdateHandler : BaseHandler<CallbackContext>() {
    override fun handleRequest(proxy: AmazonWebServicesClientProxy,
                               request: ResourceHandlerRequest<ResourceModel>,
                               callbackContext: CallbackContext?,
                               logger: Logger): ProgressEvent<ResourceModel, CallbackContext?> {
        logger.debug("Update EndpointGroup request: $request")
        val agaClient = AcceleratorClientBuilder.client
        val inferredCallbackContext = callbackContext
                ?: CallbackContext(stabilizationRetriesRemaining = HandlerCommons.NUMBER_OF_STATE_POLL_RETRIES, pendingStabilization = false)
        val model = request.desiredResourceState
        return if (!inferredCallbackContext.pendingStabilization) {
            HandlerCommons.getEndpointGroup(model.endpointGroupArn, proxy, agaClient, logger)
                    ?: return ProgressEvent.failed(model, callbackContext, HandlerErrorCode.NotFound, "Endpoint Group Not Found")
            updateEndpointGroup(model, request.previousResourceState, proxy, agaClient)
        } else {
            HandlerCommons.waitForSynchronizedStep(inferredCallbackContext, model, proxy, agaClient, logger)
        }
    }

    private fun updateEndpointGroup(model: ResourceModel,
                                    previousModel: ResourceModel,
                                    proxy: AmazonWebServicesClientProxy,
                                    agaClient: AWSGlobalAccelerator): ProgressEvent<ResourceModel, CallbackContext?> {
        val convertedEndpointConfigurations = model.endpointConfigurations?.map {
            EndpointConfiguration()
                    .withEndpointId(it.endpointId).withWeight(it.weight)
        }
        val trafficDialPercentage = model.trafficDialPercentage?.toFloat() ?: 100.0f
        val request = UpdateEndpointGroupRequest()
                .withEndpointGroupArn(model.endpointGroupArn)
                .withHealthCheckPort(model.healthCheckPort)
                .withHealthCheckIntervalSeconds(model.healthCheckIntervalSeconds)
                .withHealthCheckProtocol(model.healthCheckProtocol)
                .withHealthCheckPath(model.healthCheckPath)
                .withThresholdCount(model.thresholdCount)
                .withTrafficDialPercentage(trafficDialPercentage)
                .withEndpointConfigurations(convertedEndpointConfigurations)

        val portOverrides = model.portOverrides?.map { PortOverride().withListenerPort(it.listenerPort).withEndpointPort(it.endpointPort) }
        val previousPortOverrides = previousModel.portOverrides?.map {PortOverride().withListenerPort(it.listenerPort).withEndpointPort(it.endpointPort) }

        // Port-overrides are not updated if they are missing in previous and current CloudFormation stack templates.
        // This is to preserve any changes that are done outside CloudFormation context (AWS CLI or console).
        // CloudFormation launch is a followup to SDK and CLI, so this approach avoids any accidental overrides.
        if (portOverrides != null) {
            request.withPortOverrides(portOverrides)
        } else if (previousPortOverrides != null) {
            request.withPortOverrides(ArrayList<PortOverride>())
        }

        proxy.injectCredentialsAndInvoke(request, agaClient::updateEndpointGroup).endpointGroup
        val callbackContext = CallbackContext(stabilizationRetriesRemaining = HandlerCommons.NUMBER_OF_STATE_POLL_RETRIES,
                pendingStabilization = true)
        return ProgressEvent.defaultInProgressHandler(callbackContext, 0, model)
    }
}
