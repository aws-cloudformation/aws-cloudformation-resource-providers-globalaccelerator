package software.amazon.globalaccelerator.accelerator

import com.amazonaws.services.globalaccelerator.AWSGlobalAccelerator
import com.amazonaws.services.globalaccelerator.model.UpdateAcceleratorRequest
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy
import software.amazon.cloudformation.proxy.HandlerErrorCode
import software.amazon.cloudformation.proxy.Logger
import software.amazon.cloudformation.proxy.ProgressEvent
import software.amazon.cloudformation.proxy.ResourceHandlerRequest
import software.amazon.globalaccelerator.accelerator.AcceleratorClientBuilder.client
import software.amazon.globalaccelerator.accelerator.HandlerCommons.getAccelerator
import software.amazon.globalaccelerator.accelerator.HandlerCommons.waitForSynchronizedStep

class UpdateHandler : BaseHandler<CallbackContext?>() {
    override fun handleRequest(
            proxy: AmazonWebServicesClientProxy,
            request: ResourceHandlerRequest<ResourceModel>,
            callbackContext: CallbackContext?,
            logger: Logger): ProgressEvent<ResourceModel, CallbackContext?> {
        val agaClient = client
        val inferredCallbackContext = callbackContext
                ?: CallbackContext(stabilizationRetriesRemaining = HandlerCommons.NUMBER_OF_STATE_POLL_RETRIES,
                        pendingStabilization = false)
        val model = request.desiredResourceState
        getAccelerator(model.acceleratorArn, proxy, agaClient, logger)
                ?: return ProgressEvent.defaultFailureHandler(
                        Exception(String.format("Failed to find accelerator with arn:[%s]", model.acceleratorArn)),
                        HandlerErrorCode.NotFound
                )

        // a. Check if update is started.
        // b. If started, then poll on accelerator to go in sync
        // c. else, trigger an update operation
        val isUpdateStarted: Boolean = inferredCallbackContext.pendingStabilization
        return if (!isUpdateStarted) {
            updateAccelerator(model, proxy, agaClient, logger)
        } else {
            waitForSynchronizedStep(inferredCallbackContext, model, proxy, agaClient, logger)
        }
    }

    private fun updateAccelerator(model: ResourceModel,
                                  proxy: AmazonWebServicesClientProxy,
                                  agaClient: AWSGlobalAccelerator,
                                  logger: Logger): ProgressEvent<ResourceModel, CallbackContext?> {
        logger.log(String.format("Updating accelerator with arn: [%s]", model.acceleratorArn))
        val request = UpdateAcceleratorRequest()
                .withAcceleratorArn(model.acceleratorArn)
                .withEnabled(model.enabled)
                .withIpAddressType(model.ipAddressType)
                .withName(model.name)
        proxy.injectCredentialsAndInvoke(request, { updateAcceleratorRequest: UpdateAcceleratorRequest? -> agaClient.updateAccelerator(updateAcceleratorRequest) }).accelerator
        val callbackContext = CallbackContext(stabilizationRetriesRemaining = HandlerCommons.NUMBER_OF_STATE_POLL_RETRIES,
                pendingStabilization = true)
        return ProgressEvent.defaultInProgressHandler(callbackContext, 0, model)
    }
}
