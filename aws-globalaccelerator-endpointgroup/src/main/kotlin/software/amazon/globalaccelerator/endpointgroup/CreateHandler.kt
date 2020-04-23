package software.amazon.globalaccelerator.endpointgroup

import com.amazonaws.services.globalaccelerator.AWSGlobalAccelerator
import com.amazonaws.services.globalaccelerator.model.CreateEndpointGroupRequest
import com.amazonaws.services.globalaccelerator.model.EndpointConfiguration
import com.amazonaws.services.globalaccelerator.model.EndpointDescription
import com.amazonaws.services.globalaccelerator.model.EndpointGroup
import software.amazon.cloudformation.proxy.*
import software.amazon.globalaccelerator.arns.ListenerArn

class CreateHandler : BaseHandler<CallbackContext?>() {

    @Override
    override fun handleRequest(
            proxy: AmazonWebServicesClientProxy,
            request: ResourceHandlerRequest<ResourceModel>,
            callbackContext: CallbackContext?,
            logger: Logger): ProgressEvent<ResourceModel, CallbackContext?> {
        logger.log("Create request [$request]")

        val agaClient = AcceleratorClientBuilder.client
        val inferredCallbackContext = callbackContext
                ?: CallbackContext(stabilizationRetriesRemaining = HandlerCommons.NUMBER_OF_STATE_POLL_RETRIES);

        val model = request.desiredResourceState
        return if (model.endpointGroupArn == null) {
            createEndpointGroupStep(model, request, proxy, agaClient, logger)
        } else {
            HandlerCommons.waitForSynchronizedStep(inferredCallbackContext, model, proxy, agaClient, logger)
        }
    }

    /**
     * Create an accelerator and create the correct progress continuation context
     */
    private fun createEndpointGroupStep(model: ResourceModel,
                                        handlerRequest: ResourceHandlerRequest<ResourceModel>,
                                        proxy: AmazonWebServicesClientProxy,
                                        agaClient: AWSGlobalAccelerator,
                                        logger: Logger): ProgressEvent<ResourceModel, CallbackContext?> {

        HandlerCommons.getListener(model.listenerArn, proxy, agaClient, logger)
                ?: return ProgressEvent.defaultFailureHandler(
                        Exception("Failed to find listener with arn: [${model.listenerArn}].  Can not create endpoint group."),
                        HandlerErrorCode.NotFound)

        // first thing get the accelerator associated to listener so we can update our model
        val listenerArn = ListenerArn(model.listenerArn)
        HandlerCommons.getAccelerator(listenerArn.acceleratorArn, proxy, agaClient, logger)
                ?: return ProgressEvent.defaultFailureHandler(
                        Exception("Could not find accelerator for listener [${model.listenerArn}]"),
                        HandlerErrorCode.NotFound)

        // now we can move forward and create the endpoint group and update model with everything that is optional
        val endpointGroup = createEndpointGroup(model, handlerRequest, proxy, agaClient)
        model.apply {
            this.endpointGroupArn = endpointGroup.endpointGroupArn
            this.healthCheckIntervalSeconds = endpointGroup.healthCheckIntervalSeconds
            this.healthCheckPath = endpointGroup.healthCheckPath
            this.healthCheckPort = endpointGroup.healthCheckPort
            this.healthCheckProtocol = endpointGroup.healthCheckProtocol
            this.thresholdCount = endpointGroup.thresholdCount
            this.endpointConfigurations = getEndpointConfigurations(endpointGroup.endpointDescriptions)
        }

        val callbackContext = CallbackContext(stabilizationRetriesRemaining = HandlerCommons.NUMBER_OF_STATE_POLL_RETRIES);

        return ProgressEvent.defaultInProgressHandler(callbackContext, 0, model)
    }

    private fun getEndpointConfigurations(endpointDescriptions: List<EndpointDescription>): List<software.amazon.globalaccelerator.endpointgroup.EndpointConfiguration> {
        return endpointDescriptions.map {software.amazon.globalaccelerator.endpointgroup.EndpointConfiguration.builder()
                    .clientIPPreservationEnabled(it.clientIPPreservationEnabled)
                    .endpointId(it.endpointId)
                    .weight(it.weight)
                    .build()}
    }

    private fun createEndpointGroup(model: ResourceModel,
                                    handlerRequest: ResourceHandlerRequest<ResourceModel>,
                                    proxy: AmazonWebServicesClientProxy,
                                    agaClient: AWSGlobalAccelerator): EndpointGroup {

        // we need to map all of our endpoint configurations
        val convertedEndpointConfigurations = model.endpointConfigurations?.map {EndpointConfiguration()
                    .withEndpointId(it.endpointId).withWeight(it.weight)
                    .withClientIPPreservationEnabled(it.clientIPPreservationEnabled)}

        val createEndpointGroupRequest = CreateEndpointGroupRequest()
                .withListenerArn(model.listenerArn)
                .withEndpointGroupRegion(model.endpointGroupRegion)
                .withHealthCheckPort(model.healthCheckPort)
                .withHealthCheckIntervalSeconds(model.healthCheckIntervalSeconds)
                .withHealthCheckProtocol(model.healthCheckProtocol)
                .withHealthCheckPath(model.healthCheckPath)
                .withThresholdCount(model.thresholdCount)
                .withEndpointConfigurations(convertedEndpointConfigurations)
                .withIdempotencyToken(handlerRequest.logicalResourceIdentifier)

        return proxy.injectCredentialsAndInvoke(createEndpointGroupRequest, agaClient::createEndpointGroup).endpointGroup;
    }
}
