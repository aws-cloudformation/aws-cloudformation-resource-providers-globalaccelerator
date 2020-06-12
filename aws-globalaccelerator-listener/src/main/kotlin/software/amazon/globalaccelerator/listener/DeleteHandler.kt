package software.amazon.globalaccelerator.listener

import com.amazonaws.services.globalaccelerator.AWSGlobalAccelerator
import com.amazonaws.services.globalaccelerator.model.DeleteListenerRequest
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy
import software.amazon.cloudformation.proxy.Logger
import software.amazon.cloudformation.proxy.ProgressEvent
import software.amazon.cloudformation.proxy.ResourceHandlerRequest

/**
 * Delete handler implementation for listener resource.
 */
class DeleteHandler : BaseHandler<CallbackContext>() {
    override fun handleRequest(
            proxy: AmazonWebServicesClientProxy,
            request: ResourceHandlerRequest<ResourceModel>,
            callbackContext: CallbackContext?,
            logger: Logger): ProgressEvent<ResourceModel, CallbackContext?> {
        logger.debug("Delete Listener Request [$request]")
        val agaClient = AcceleratorClientBuilder.client
        val inferredCallbackContext = callbackContext
                ?: CallbackContext(stabilizationRetriesRemaining = HandlerCommons.NUMBER_OF_STATE_POLL_RETRIES, pendingStabilization = false);
        val model = request.desiredResourceState

        return if (!inferredCallbackContext.pendingStabilization) {
            HandlerCommons.getListener(model.listenerArn, proxy, agaClient, logger)
                    ?: return ProgressEvent.defaultSuccessHandler(model)
            deleteListener(model, proxy, agaClient)
        } else {
            HandlerCommons.waitForSynchronizedStep(inferredCallbackContext, model, proxy, agaClient, logger)
        }
    }

    private fun deleteListener(model: ResourceModel,
                               proxy: AmazonWebServicesClientProxy,
                               agaClient: AWSGlobalAccelerator): ProgressEvent<ResourceModel, CallbackContext?> {
        val request = DeleteListenerRequest().withListenerArn(model.listenerArn)
        proxy.injectCredentialsAndInvoke(request, agaClient::deleteListener);
        val callbackContext = CallbackContext(stabilizationRetriesRemaining = (HandlerCommons.NUMBER_OF_STATE_POLL_RETRIES),
                pendingStabilization = true)
        return ProgressEvent.defaultInProgressHandler(callbackContext, 0, model)
    }
}
