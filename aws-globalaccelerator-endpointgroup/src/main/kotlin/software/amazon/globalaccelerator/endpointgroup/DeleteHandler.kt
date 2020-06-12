package software.amazon.globalaccelerator.endpointgroup

import com.amazonaws.services.globalaccelerator.AWSGlobalAccelerator
import com.amazonaws.services.globalaccelerator.model.DeleteEndpointGroupRequest
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy
import software.amazon.cloudformation.proxy.Logger
import software.amazon.cloudformation.proxy.ProgressEvent
import software.amazon.cloudformation.proxy.ResourceHandlerRequest

/**
 * Delete handler implementation for Endpoint Group resource.
 */
class DeleteHandler : BaseHandler<CallbackContext>() {
    override fun handleRequest(proxy: AmazonWebServicesClientProxy,
                               request: ResourceHandlerRequest<ResourceModel>,
                               callbackContext: CallbackContext?,
                               logger: Logger): ProgressEvent<ResourceModel, CallbackContext?> {
        logger.debug("Delete EndpointGroup request: $request")
        val agaClient = AcceleratorClientBuilder.client
        val inferredCallbackContext = callbackContext
                ?: CallbackContext(stabilizationRetriesRemaining = HandlerCommons.NUMBER_OF_STATE_POLL_RETRIES);
        val model = request.desiredResourceState
        return if (!inferredCallbackContext.pendingStabilization) {
            HandlerCommons.getEndpointGroup(model.endpointGroupArn, proxy, agaClient, logger)
                    ?: return ProgressEvent.defaultSuccessHandler(model)
            deleteEndpointGroup(model, proxy, agaClient)
        } else {
            HandlerCommons.waitForSynchronizedStep(inferredCallbackContext, model, proxy, agaClient, logger)
        }
    }

    private fun deleteEndpointGroup(model: ResourceModel,
                                    proxy: AmazonWebServicesClientProxy,
                                    agaClient: AWSGlobalAccelerator): ProgressEvent<ResourceModel, CallbackContext?> {
        val request = DeleteEndpointGroupRequest().withEndpointGroupArn(model.endpointGroupArn)
        proxy.injectCredentialsAndInvoke(request, agaClient::deleteEndpointGroup);
        val callbackContext = CallbackContext(stabilizationRetriesRemaining = (HandlerCommons.NUMBER_OF_STATE_POLL_RETRIES),
                pendingStabilization = true)
        return ProgressEvent.defaultInProgressHandler(callbackContext, 0, model)
    }
}
