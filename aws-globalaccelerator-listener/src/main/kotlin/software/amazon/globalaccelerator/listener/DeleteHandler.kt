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
    @Override
    override fun handleRequest(
            proxy: AmazonWebServicesClientProxy,
            request: ResourceHandlerRequest<ResourceModel>,
            callbackContext: CallbackContext?,
            logger: Logger): ProgressEvent<ResourceModel, CallbackContext?> {
        logger.debug("Delete Listener Request [$request]")
        val agaClient = AcceleratorClientBuilder.client
        val model = request.desiredResourceState
        val foundListener = HandlerCommons.getListener(model.listenerArn, proxy, agaClient, logger)
        if (foundListener != null) {
            deleteListener(model.listenerArn, proxy, agaClient, logger)
        }
        return ProgressEvent.defaultSuccessHandler(model)
    }

    private fun deleteListener(listenerArn: String,
                               proxy: AmazonWebServicesClientProxy,
                               agaClient: AWSGlobalAccelerator, logger: Logger) {
        val request = DeleteListenerRequest().withListenerArn(listenerArn)
        proxy.injectCredentialsAndInvoke(request, agaClient::deleteListener);
    }
}
