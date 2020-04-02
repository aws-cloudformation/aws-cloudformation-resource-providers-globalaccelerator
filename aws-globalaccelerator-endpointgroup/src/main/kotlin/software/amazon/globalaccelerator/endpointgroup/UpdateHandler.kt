package software.amazon.globalaccelerator.endpointgroup

import com.amazonaws.services.globalaccelerator.AWSGlobalAccelerator
import com.amazonaws.services.globalaccelerator.model.EndpointConfiguration
import com.amazonaws.services.globalaccelerator.model.UpdateEndpointGroupRequest
import lombok.`val`
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy
import software.amazon.cloudformation.proxy.HandlerErrorCode
import software.amazon.cloudformation.proxy.Logger
import software.amazon.cloudformation.proxy.ProgressEvent
import software.amazon.cloudformation.proxy.OperationStatus
import software.amazon.cloudformation.proxy.ResourceHandlerRequest
import java.util.stream.Collectors

class UpdateHandler : BaseHandler<CallbackContext>() {

    @Override
    override fun handleRequest(
            proxy: AmazonWebServicesClientProxy,
            request: ResourceHandlerRequest<ResourceModel>,
            callbackContext: CallbackContext?,
            logger: Logger): ProgressEvent<ResourceModel, CallbackContext?> {

        val agaClient = AcceleratorClientBuilder.client
        val inferredCallbackContext = callbackContext
                ?: CallbackContext(stabilizationRetriesRemaining = HandlerCommons.NUMBER_OF_STATE_POLL_RETRIES,
                        pendingStabilization = false)

        val model = request.getDesiredResourceState()

        val foundEndpointGroup = HandlerCommons.getEndpointGroup(model.getEndpointGroupArn(), proxy, agaClient, logger)
                ?: return ProgressEvent.defaultFailureHandler(
                        Exception(String.format("Failed to find endpoint group with arn:[%s]", model.getEndpointGroupArn())),
                        HandlerErrorCode.NotFound
                )

        // a. Check if update is started.
        // b. If started, then poll on endpointgroup to go in sync
        // c. else, trigger an update operation
        val isUpdateStarted = inferredCallbackContext.pendingStabilization

        return if (!isUpdateStarted) {
            updateEndpointGroup(model, proxy, agaClient, logger)
        } else {
            HandlerCommons.waitForSynchronizedStep(inferredCallbackContext, model, proxy, agaClient, logger)
        }
    }

    private fun updateEndpointGroup(model: ResourceModel,
                                    proxy: AmazonWebServicesClientProxy,
                                    agaClient: AWSGlobalAccelerator,
                                    logger: Logger): ProgressEvent<ResourceModel, CallbackContext?> {

        logger.log(String.format("Updating endpoint group with arn: [%s]", model.getEndpointGroupArn()))
        var convertedEndpointConfigurations: List<com.amazonaws.services.globalaccelerator.model.EndpointConfiguration>? = null

        if (model.getEndpointConfigurations() != null) {
            convertedEndpointConfigurations = model.getEndpointConfigurations().stream()
                    .map({ x -> EndpointConfiguration().withEndpointId(x.getEndpointId()).withWeight(x.getWeight()) })
                    .collect(Collectors.toList())
        }

        val request = UpdateEndpointGroupRequest()
                .withEndpointGroupArn(model.getEndpointGroupArn())
                .withHealthCheckPort(model.getHealthCheckPort())
                .withHealthCheckIntervalSeconds(model.getHealthCheckIntervalSeconds())
                .withHealthCheckProtocol(model.getHealthCheckProtocol())
                .withHealthCheckPath(model.getHealthCheckPath())
                .withThresholdCount(model.getThresholdCount())
                .withEndpointConfigurations(convertedEndpointConfigurations)

        proxy.injectCredentialsAndInvoke(request, agaClient::updateEndpointGroup).endpointGroup
        val callbackContext = CallbackContext(stabilizationRetriesRemaining = HandlerCommons.NUMBER_OF_STATE_POLL_RETRIES,
                pendingStabilization = true)
        return ProgressEvent.defaultInProgressHandler(callbackContext, 0, model)
    }
}
