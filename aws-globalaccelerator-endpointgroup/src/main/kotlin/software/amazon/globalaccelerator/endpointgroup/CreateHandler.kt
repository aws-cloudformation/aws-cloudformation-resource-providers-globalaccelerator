package software.amazon.globalaccelerator.endpointgroup

import com.amazonaws.services.globalaccelerator.AWSGlobalAccelerator
import com.amazonaws.services.globalaccelerator.model.CreateEndpointGroupRequest
import com.amazonaws.services.globalaccelerator.model.EndpointConfiguration
import com.amazonaws.services.globalaccelerator.model.EndpointDescription
import com.amazonaws.services.globalaccelerator.model.EndpointGroup
import lombok.`val`
import software.amazon.cloudformation.proxy.*
import software.amazon.globalaccelerator.arns.ListenerArn
import java.util.stream.Collectors

class CreateHandler : BaseHandler<CallbackContext?>() {

    @Override
    override fun handleRequest(
            proxy: AmazonWebServicesClientProxy,
            request: ResourceHandlerRequest<ResourceModel>,
            callbackContext: CallbackContext?,
            logger: Logger): ProgressEvent<ResourceModel, CallbackContext?> {
        logger.log(String.format("Create request [%s]", request))

        val agaClient = AcceleratorClientBuilder.client
        val inferredCallbackContext = callbackContext
                ?: CallbackContext(stabilizationRetriesRemaining = HandlerCommons.NUMBER_OF_STATE_POLL_RETRIES);

        val model = request.getDesiredResourceState()
        return if (model.getEndpointGroupArn() == null) {
            CreateEndpointGroupStep(model, request, proxy, agaClient, logger)
        } else {
            HandlerCommons.waitForSynchronizedStep(inferredCallbackContext, model, proxy, agaClient, logger)
        }

        return if (model.getEndpointGroupArn() == null) {
            CreateEndpointGroupStep(model, request, proxy, agaClient, logger)
        } else {
            HandlerCommons.waitForSynchronizedStep(inferredCallbackContext, model, proxy, agaClient, logger)
        };
    }

    /**
     * Create an accelerator and create the correct progress continuation context
     */
    private fun CreateEndpointGroupStep(model: ResourceModel,
                                        handlerRequest: ResourceHandlerRequest<ResourceModel>,
                                        proxy: AmazonWebServicesClientProxy,
                                        agaClient: AWSGlobalAccelerator,
                                        logger: Logger): ProgressEvent<ResourceModel, CallbackContext?> {

        HandlerCommons.getListener(model.getListenerArn(), proxy, agaClient, logger)
                ?: return ProgressEvent.defaultFailureHandler(
                        Exception(String.format("Failed to find listener with arn: [%s].  Can not create endpoint group.")),
                        HandlerErrorCode.NotFound)

        // first thing get the accelerator associated to listener so we can update our model
        val listenerArn = ListenerArn(model.getListenerArn())
        HandlerCommons.getAccelerator(listenerArn.acceleratorArn, proxy, agaClient, logger)
                ?: return ProgressEvent.defaultFailureHandler(
                        Exception(String.format("Could not find accelerator for listener [%s]", model.getListenerArn())),
                        HandlerErrorCode.NotFound)

        // now we can move forward and create the endpoint group and update model with everything that is optional
        val endpointGroup = CreateEndpointGroup(model, handlerRequest, proxy, agaClient)
        model.setEndpointGroupArn(endpointGroup.getEndpointGroupArn())
        model.setHealthCheckIntervalSeconds(endpointGroup.getHealthCheckIntervalSeconds())
        model.setHealthCheckPath(endpointGroup.getHealthCheckPath())
        model.setHealthCheckPort(endpointGroup.getHealthCheckPort())
        model.setHealthCheckProtocol(endpointGroup.getHealthCheckProtocol())
        model.setThresholdCount(endpointGroup.getThresholdCount())
        model.setEndpointConfigurations(getEndpointConfigurations(endpointGroup.getEndpointDescriptions()))

        val callbackContext = CallbackContext(stabilizationRetriesRemaining = HandlerCommons.NUMBER_OF_STATE_POLL_RETRIES);

        return ProgressEvent.defaultInProgressHandler(callbackContext, 0, model)
    }

    private fun getEndpointConfigurations(endpointDescriptions: List<EndpointDescription>): List<software.amazon.globalaccelerator.endpointgroup.EndpointConfiguration> {
        return endpointDescriptions.stream().map({ x ->
            software.amazon.globalaccelerator.endpointgroup.EndpointConfiguration.builder()
                    .clientIPPreservationEnabled(x.getClientIPPreservationEnabled())
                    .endpointId(x.getEndpointId())
                    .weight(x.getWeight())
                    .build()
        }).collect(Collectors.toList())
    }

    private fun CreateEndpointGroup(model: ResourceModel,
                                    handlerRequest: ResourceHandlerRequest<ResourceModel>,
                                    proxy: AmazonWebServicesClientProxy,
                                    agaClient: AWSGlobalAccelerator): EndpointGroup {
        // If health check port is not provided we want to fall back
        val healthCheckPort = if (model.getHealthCheckPort() < 0) null else model.getHealthCheckPort()

        // we need to map all of our endpoint configurations
        var convertedEndpointConfigurations: List<EndpointConfiguration>? = null
        if (model.getEndpointConfigurations() != null) {
            convertedEndpointConfigurations = model.getEndpointConfigurations().stream()
                    .map({ x ->
                        EndpointConfiguration().withEndpointId(x.getEndpointId()).withWeight(x.getWeight())
                                .withClientIPPreservationEnabled(x.getClientIPPreservationEnabled())
                    })
                    .collect(Collectors.toList())
        }

        val createEndpointGroupRequest = CreateEndpointGroupRequest()
                .withListenerArn(model.getListenerArn())
                .withEndpointGroupRegion(model.getEndpointGroupRegion())
                .withHealthCheckPort(healthCheckPort)
                .withHealthCheckIntervalSeconds(model.getHealthCheckIntervalSeconds())
                .withHealthCheckProtocol(model.getHealthCheckProtocol())
                .withHealthCheckPath(model.getHealthCheckPath())
                .withThresholdCount(model.getThresholdCount())
                .withEndpointConfigurations(convertedEndpointConfigurations)
                .withIdempotencyToken(handlerRequest.getLogicalResourceIdentifier())

        return proxy.injectCredentialsAndInvoke(createEndpointGroupRequest, agaClient::createEndpointGroup).endpointGroup;
    }
}
