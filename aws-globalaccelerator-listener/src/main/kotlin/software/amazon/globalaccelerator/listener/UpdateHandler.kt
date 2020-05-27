package software.amazon.globalaccelerator.listener

import com.amazonaws.services.globalaccelerator.AWSGlobalAccelerator
import com.amazonaws.services.globalaccelerator.model.Listener
import com.amazonaws.services.globalaccelerator.model.PortRange
import com.amazonaws.services.globalaccelerator.model.UpdateListenerRequest
import software.amazon.cloudformation.proxy.Logger
import software.amazon.cloudformation.proxy.ProgressEvent
import software.amazon.cloudformation.proxy.ResourceHandlerRequest
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy
import software.amazon.cloudformation.proxy.HandlerErrorCode

/**
 * Update handler implementation for listener resource.
 */
class UpdateHandler : BaseHandler<CallbackContext>() {
    override fun handleRequest(
            proxy: AmazonWebServicesClientProxy,
            request: ResourceHandlerRequest<ResourceModel>,
            callbackContext: CallbackContext?,
            logger: Logger): ProgressEvent<ResourceModel, CallbackContext> {
        logger.debug("Update Listener Request $request")
        val agaClient = AcceleratorClientBuilder.client
        val inferredCallbackContext = callbackContext
                ?: CallbackContext(stabilizationRetriesRemaining = HandlerCommons.NUMBER_OF_STATE_POLL_RETRIES, pendingStabilization = false);
        val model = request.desiredResourceState
        return if (!inferredCallbackContext.pendingStabilization) {
            updateListenerStep(model, request, proxy, agaClient, logger)
        } else {
            HandlerCommons.waitForSynchronizedStep(inferredCallbackContext, model, proxy, agaClient, logger)
        }
    }

    private fun updateListenerStep(model: ResourceModel,
                                   handlerRequest: ResourceHandlerRequest<ResourceModel>,
                                   proxy: AmazonWebServicesClientProxy,
                                   agaClient: AWSGlobalAccelerator,
                                   logger: Logger): ProgressEvent<ResourceModel, CallbackContext> {
        val listener = updateListener(model, handlerRequest, proxy, agaClient)
        model.clientAffinity = listener.clientAffinity
        model.protocol = listener.protocol
        val callbackContext = CallbackContext(stabilizationRetriesRemaining = (HandlerCommons.NUMBER_OF_STATE_POLL_RETRIES),
                pendingStabilization = true)
        return ProgressEvent.defaultInProgressHandler(callbackContext, 0, model)
    }

    private fun updateListener(model: ResourceModel,
                               handlerRequest: ResourceHandlerRequest<ResourceModel>,
                               proxy: AmazonWebServicesClientProxy,
                               agaClient: AWSGlobalAccelerator): Listener {
        val convertedPortRanges = model.portRanges.map { x ->
            PortRange().withFromPort(x.fromPort).withToPort(x.toPort)
        }
        val updateListenerRequest = UpdateListenerRequest()
                .withListenerArn(model.listenerArn)
                .withClientAffinity(model.clientAffinity)
                .withProtocol(model.protocol)
                .withPortRanges(convertedPortRanges)
        return proxy.injectCredentialsAndInvoke(updateListenerRequest, agaClient::updateListener).listener;
    }

    companion object {
        var UPDATE_COMPLETED_KEY = "UPDATE_COMPLETED"
    }
}
