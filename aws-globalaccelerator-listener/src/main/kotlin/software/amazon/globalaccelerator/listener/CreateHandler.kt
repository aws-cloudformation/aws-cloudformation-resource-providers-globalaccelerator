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
        val model = request.desiredResourceState
        HandlerCommons.getAccelerator(model.acceleratorArn, proxy, agaClient, logger)
                ?: return ProgressEvent.defaultFailureHandler(
                        Exception(String.format("Failed to find accelerator with arn: [%s].  Can not create listener", model.acceleratorArn)),
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
        model.listenerArn = listener.listenerArn
        model.clientAffinity = listener.clientAffinity
        model.protocol = listener.protocol
        return ProgressEvent.defaultSuccessHandler(model)
    }

    private fun createListener(model: ResourceModel,
                               handlerRequest: ResourceHandlerRequest<ResourceModel>,
                               proxy: AmazonWebServicesClientProxy,
                               agaClient: AWSGlobalAccelerator): Listener {
        val convertedPortRanges = model.portRanges.map {
            x -> PortRange().withFromPort(x.fromPort).withToPort(x.toPort)}

        val createListenerRequest = CreateListenerRequest()
                .withAcceleratorArn(model.acceleratorArn)
                .withClientAffinity(model.clientAffinity)
                .withProtocol(model.protocol)
                .withPortRanges(convertedPortRanges)
                .withIdempotencyToken(handlerRequest.logicalResourceIdentifier)

        return proxy.injectCredentialsAndInvoke(createListenerRequest, agaClient::createListener).listener;
    }
}
