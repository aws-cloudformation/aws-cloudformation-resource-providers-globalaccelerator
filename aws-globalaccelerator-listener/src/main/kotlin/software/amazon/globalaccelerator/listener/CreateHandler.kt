package software.amazon.globalaccelerator.listener

import com.amazonaws.services.globalaccelerator.AWSGlobalAccelerator
import com.amazonaws.services.globalaccelerator.model.CreateListenerRequest
import com.amazonaws.services.globalaccelerator.model.Listener
import com.amazonaws.services.globalaccelerator.model.PortRange
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy
import software.amazon.cloudformation.proxy.HandlerErrorCode
import software.amazon.cloudformation.proxy.Logger
import software.amazon.cloudformation.proxy.ProgressEvent
import software.amazon.cloudformation.proxy.ResourceHandlerRequest

class CreateHandler : BaseHandler<CallbackContext>() {
    @Override
    override fun handleRequest(
            proxy: AmazonWebServicesClientProxy,
            request: ResourceHandlerRequest<ResourceModel>,
            callbackContext: CallbackContext?,
            logger: Logger
    ): ProgressEvent<ResourceModel, CallbackContext> {
        logger.log(String.format("Creating listener with request [%s]", request))

        val agaClient = AcceleratorClientBuilder.client

        // confirm we can find the accelerator
        val model = request.getDesiredResourceState()
        HandlerCommons.getAccelerator(model.getAcceleratorArn(), proxy, agaClient, logger)
                ?: return ProgressEvent.defaultFailureHandler(
                        Exception(String.format("Failed to find accelerator with arn: [%s].  Can not create listener", model.getAcceleratorArn())),
                        HandlerErrorCode.NotFound)

        return createListenerStep(model, request, proxy, agaClient, logger)
    }

    /**
     * Create an accelerator and create the correct progress continuation context
     */
    private fun createListenerStep(model: ResourceModel,
                                   handlerRequest: ResourceHandlerRequest<ResourceModel>,
                                   proxy: AmazonWebServicesClientProxy,
                                   agaClient: AWSGlobalAccelerator,
                                   logger: Logger): ProgressEvent<ResourceModel, CallbackContext> {
        logger.log("Creating new listener.")
        val listener = createListener(model, handlerRequest, proxy, agaClient)
        model.setListenerArn(listener.getListenerArn())
        model.setClientAffinity(listener.getClientAffinity())
        model.setProtocol(listener.getProtocol())
        return ProgressEvent.defaultSuccessHandler(model)
    }

    private fun createListener(model: ResourceModel,
                               handlerRequest: ResourceHandlerRequest<ResourceModel>,
                               proxy: AmazonWebServicesClientProxy,
                               agaClient: AWSGlobalAccelerator): Listener {
        val convertedPortRanges = model.getPortRanges().map {
            x -> PortRange().withFromPort(x.getFromPort()).withToPort(x.getToPort())}

        val createListenerRequest = CreateListenerRequest()
                .withAcceleratorArn(model.getAcceleratorArn())
                .withClientAffinity(model.getClientAffinity())
                .withProtocol(model.getProtocol())
                .withPortRanges(convertedPortRanges)
                .withIdempotencyToken(handlerRequest.getLogicalResourceIdentifier())

        return proxy.injectCredentialsAndInvoke(createListenerRequest, agaClient::createListener).listener;
    }
}
