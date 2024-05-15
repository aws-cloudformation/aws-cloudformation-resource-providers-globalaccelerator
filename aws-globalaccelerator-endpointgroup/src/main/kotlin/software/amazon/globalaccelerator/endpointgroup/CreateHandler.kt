package software.amazon.globalaccelerator.endpointgroup

import com.amazonaws.services.globalaccelerator.AWSGlobalAccelerator
import com.amazonaws.services.globalaccelerator.model.CreateEndpointGroupRequest
import com.amazonaws.services.globalaccelerator.model.EndpointConfiguration
import com.amazonaws.services.globalaccelerator.model.EndpointGroup
import com.amazonaws.services.globalaccelerator.model.PortOverride
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy
import software.amazon.cloudformation.proxy.Logger
import software.amazon.cloudformation.proxy.ProgressEvent
import software.amazon.cloudformation.proxy.ResourceHandlerRequest
import software.amazon.cloudformation.proxy.HandlerErrorCode
import software.amazon.globalaccelerator.arns.ListenerArn

/**
 * Create handler implementation for Endpoint Group resource.
 */
class CreateHandler : BaseHandler<CallbackContext?>() {
    override fun handleRequest(proxy: AmazonWebServicesClientProxy,
                               request: ResourceHandlerRequest<ResourceModel>,
                               callbackContext: CallbackContext?,
                               logger: Logger): ProgressEvent<ResourceModel, CallbackContext?> {
        logger.debug("Create new EndpointGroup request: $request")
        val agaClient = AcceleratorClientBuilder.client
        val inferredCallbackContext = callbackContext ?: CallbackContext(stabilizationRetriesRemaining = HandlerCommons.NUMBER_OF_STATE_POLL_RETRIES)
        val model = request.desiredResourceState
        return when (model.endpointGroupArn) {
            null -> createEndpointGroupStep(model, request, proxy, agaClient, logger)
            else -> HandlerCommons.waitForSynchronizedStep(inferredCallbackContext, model, proxy, agaClient, logger)
        }
    }

    private fun createEndpointGroupStep(model: ResourceModel,
                                        handlerRequest: ResourceHandlerRequest<ResourceModel>,
                                        proxy: AmazonWebServicesClientProxy,
                                        agaClient: AWSGlobalAccelerator,
                                        logger: Logger): ProgressEvent<ResourceModel, CallbackContext?> {
        HandlerCommons.getListener(model.listenerArn, proxy, agaClient, logger)
                ?: return ProgressEvent.defaultFailureHandler(
                        Exception("Failed to create Endpoint Group. Cannot find listener with arn: [${model.listenerArn}]."),
                        HandlerErrorCode.NotFound)

        val listenerArn = ListenerArn(model.listenerArn)
        // Make sure accelerator exists
        HandlerCommons.getAccelerator(listenerArn.acceleratorArn, proxy, agaClient, logger)
                ?: return ProgressEvent.defaultFailureHandler(
                        Exception("Could not find accelerator for listener with arn: [${model.listenerArn}]."),
                        HandlerErrorCode.NotFound)
        val endpointGroup = createEndpointGroup(model, handlerRequest, proxy, agaClient)
        model.apply {
            this.endpointGroupArn = endpointGroup.endpointGroupArn
            this.healthCheckIntervalSeconds = endpointGroup.healthCheckIntervalSeconds
            this.healthCheckPath = endpointGroup.healthCheckPath
            this.healthCheckPort = endpointGroup.healthCheckPort
            this.healthCheckProtocol = endpointGroup.healthCheckProtocol
            this.thresholdCount = endpointGroup.thresholdCount
            this.trafficDialPercentage = endpointGroup.trafficDialPercentage.toDouble()
            this.endpointConfigurations = getEndpointConfigurations(endpointGroup.endpointDescriptions)
            this.portOverrides = getPortOverrides(endpointGroup.portOverrides)
        }
        val callbackContext = CallbackContext(stabilizationRetriesRemaining = HandlerCommons.NUMBER_OF_STATE_POLL_RETRIES)
        return ProgressEvent.defaultInProgressHandler(callbackContext, 0, model)
    }

    private fun createEndpointGroup(model: ResourceModel,
                                    handlerRequest: ResourceHandlerRequest<ResourceModel>,
                                    proxy: AmazonWebServicesClientProxy,
                                    agaClient: AWSGlobalAccelerator): EndpointGroup {
        // Mapping all endpoint configurations
        val convertedEndpointConfigurations = model.endpointConfigurations?.map {EndpointConfiguration()
                    .withEndpointId(it.endpointId).withWeight(it.weight).withAttachmentArn(it.attachmentArn)
                    .withClientIPPreservationEnabled(it.clientIPPreservationEnabled)}
        val trafficDialPercentage = model.trafficDialPercentage?.toFloat() ?: 100.0f
        val portOverrides = model.portOverrides?.map { PortOverride().withListenerPort(it.listenerPort).withEndpointPort(it.endpointPort) }
        val createEndpointGroupRequest = CreateEndpointGroupRequest()
                .withListenerArn(model.listenerArn)
                .withEndpointGroupRegion(model.endpointGroupRegion)
                .withHealthCheckPort(model.healthCheckPort)
                .withHealthCheckIntervalSeconds(model.healthCheckIntervalSeconds)
                .withHealthCheckProtocol(model.healthCheckProtocol)
                .withHealthCheckPath(model.healthCheckPath)
                .withThresholdCount(model.thresholdCount)
                .withTrafficDialPercentage(trafficDialPercentage)
                .withEndpointConfigurations(convertedEndpointConfigurations)
                .withPortOverrides(portOverrides)
                .withIdempotencyToken(handlerRequest.clientRequestToken)
        return proxy.injectCredentialsAndInvoke(createEndpointGroupRequest, agaClient::createEndpointGroup).endpointGroup
    }
}
