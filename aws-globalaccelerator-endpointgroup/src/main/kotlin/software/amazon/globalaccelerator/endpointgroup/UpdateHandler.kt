package software.amazon.globalaccelerator.endpointgroup

import com.amazonaws.services.globalaccelerator.AWSGlobalAccelerator
import com.amazonaws.services.globalaccelerator.model.EndpointConfiguration
import com.amazonaws.services.globalaccelerator.model.UpdateEndpointGroupRequest
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy
import software.amazon.cloudformation.proxy.HandlerErrorCode
import software.amazon.cloudformation.proxy.Logger
import software.amazon.cloudformation.proxy.ProgressEvent
import software.amazon.cloudformation.proxy.ResourceHandlerRequest

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
        HandlerCommons.getEndpointGroup(model.endpointGroupArn, proxy, agaClient, logger)
                ?: return ProgressEvent.defaultFailureHandler(
                        Exception("Failed to find endpoint group with arn:[${model.endpointGroupArn}]"),
                        HandlerErrorCode.NotFound
                )
        return if (!inferredCallbackContext.pendingStabilization) {
            updateEndpointGroup(model, proxy, agaClient, logger)
        } else {
            HandlerCommons.waitForSynchronizedStep(inferredCallbackContext, model, proxy, agaClient, logger)
        }
    }

    private fun updateEndpointGroup(model: ResourceModel,
                                    proxy: AmazonWebServicesClientProxy,
                                    agaClient: AWSGlobalAccelerator,
                                    logger: Logger): ProgressEvent<ResourceModel, CallbackContext?> {
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
        proxy.injectCredentialsAndInvoke(request, agaClient::updateEndpointGroup).endpointGroup
        val callbackContext = CallbackContext(stabilizationRetriesRemaining = HandlerCommons.NUMBER_OF_STATE_POLL_RETRIES,
                pendingStabilization = true)
        return ProgressEvent.defaultInProgressHandler(callbackContext, 0, model)
    }
}
