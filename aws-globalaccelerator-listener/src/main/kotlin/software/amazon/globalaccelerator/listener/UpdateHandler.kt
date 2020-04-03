package software.amazon.globalaccelerator.listener

import com.amazonaws.services.globalaccelerator.AWSGlobalAccelerator
import com.amazonaws.services.globalaccelerator.model.Listener
import com.amazonaws.services.globalaccelerator.model.PortRange
import com.amazonaws.services.globalaccelerator.model.UpdateListenerRequest
import software.amazon.cloudformation.proxy.*

class UpdateHandler : BaseHandler<CallbackContext>() {


    @Override
    override fun handleRequest(
            proxy: AmazonWebServicesClientProxy,
            request: ResourceHandlerRequest<ResourceModel>,
            callbackContext: CallbackContext?,
            logger: Logger): ProgressEvent<ResourceModel, CallbackContext> {
        logger.log(String.format("Updating listener with request [%s]", request))

        val agaClient = AcceleratorClientBuilder.client
        val inferredCallbackContext = callbackContext
                ?: CallbackContext(stabilizationRetriesRemaining = HandlerCommons.NUMBER_OF_STATE_POLL_RETRIES, pendingStabilization =  false);

        val model = request.getDesiredResourceState()
        if (!inferredCallbackContext.pendingStabilization) {
            updateListenerStep(model, request, proxy, agaClient, logger)
        }

        return HandlerCommons.waitForSynchronizedStep(inferredCallbackContext, model, proxy, agaClient, logger)
    }

    /**
     * Create an accelerator and create the correct progress continuation context
     */
    private fun updateListenerStep(model: ResourceModel,
                                   handlerRequest: ResourceHandlerRequest<ResourceModel>,
                                   proxy: AmazonWebServicesClientProxy,
                                   agaClient: AWSGlobalAccelerator,
                                   logger: Logger): ProgressEvent<ResourceModel, CallbackContext> {
        logger.log("Updating the listener")
        val listener = updateListener(model, handlerRequest, proxy, agaClient)
        model.clientAffinity = listener.getClientAffinity()
        model.protocol = listener.getProtocol()

        val callbackContext = CallbackContext(stabilizationRetriesRemaining =  (HandlerCommons.NUMBER_OF_STATE_POLL_RETRIES),
                pendingStabilization = true)

        return ProgressEvent.defaultInProgressHandler(callbackContext, 0, model)
    }

    private fun updateListener(model: ResourceModel,
                               handlerRequest: ResourceHandlerRequest<ResourceModel>,
                               proxy: AmazonWebServicesClientProxy,
                               agaClient: AWSGlobalAccelerator): Listener {
        val convertedPortRanges = model.getPortRanges().map{ x ->
            PortRange().withFromPort(x.getFromPort()).withToPort(x.getToPort()) }

        val updateListenerRequest = UpdateListenerRequest()
                .withListenerArn(model.getListenerArn())
                .withClientAffinity(model.getClientAffinity())
                .withProtocol(model.getProtocol())
                .withPortRanges(convertedPortRanges)

        return proxy.injectCredentialsAndInvoke(updateListenerRequest, agaClient::updateListener).listener;
    }

    companion object {
        var UPDATE_COMPLETED_KEY = "UPDATE_COMPLETED"
    }
}