package software.amazon.globalaccelerator.endpointgroup

import com.amazonaws.services.globalaccelerator.model.EndpointDescription
import com.amazonaws.services.globalaccelerator.model.EndpointGroup
import com.amazonaws.services.globalaccelerator.model.PortOverride
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy
import software.amazon.cloudformation.proxy.HandlerErrorCode
import software.amazon.cloudformation.proxy.Logger
import software.amazon.cloudformation.proxy.ProgressEvent
import software.amazon.cloudformation.proxy.ResourceHandlerRequest

/**
 * Read handler implementation for Endpoint Group resource.
 */
class ReadHandler : BaseHandler<CallbackContext>() {
    override fun handleRequest(proxy: AmazonWebServicesClientProxy,
                               request: ResourceHandlerRequest<ResourceModel>,
                               callbackContext: CallbackContext?,
                               logger: Logger): ProgressEvent<ResourceModel, CallbackContext?> {
        logger.debug("Read EndpointGroup request: $request")
        val model = request.desiredResourceState
        val agaClient = AcceleratorClientBuilder.client
        val endpointGroup = HandlerCommons.getEndpointGroup(model.endpointGroupArn, proxy, agaClient, logger)
        val endpointGroupResourceModel = convertEndpointGroupToResourceModel(endpointGroup)
        return if (endpointGroupResourceModel == null) {
            logger.debug("Endpoint group with ARN [${model.endpointGroupArn}] not found")
            ProgressEvent.defaultFailureHandler(Exception("Endpoint group not found."), HandlerErrorCode.NotFound)
        } else {
            ProgressEvent.defaultSuccessHandler(endpointGroupResourceModel)
        }
    }

    private fun convertEndpointGroupToResourceModel(endpointGroup: EndpointGroup?): ResourceModel? {
        return if (endpointGroup != null) {
            ResourceModel().apply {
                this.endpointGroupArn = endpointGroup.endpointGroupArn
                this.healthCheckIntervalSeconds = endpointGroup.healthCheckIntervalSeconds
                this.healthCheckPath = endpointGroup.healthCheckPath
                this.healthCheckPort = endpointGroup.healthCheckPort
                this.healthCheckProtocol = endpointGroup.healthCheckProtocol
                this.thresholdCount = endpointGroup.thresholdCount
                this.trafficDialPercentage = endpointGroup.trafficDialPercentage.toDouble()
                this.endpointGroupRegion = endpointGroup.endpointGroupRegion
                this.endpointConfigurations = getEndpointConfigurations(endpointGroup.endpointDescriptions)
                this.portOverrides = getPortOverrides(endpointGroup.portOverrides)
            }
        } else {
            null
        }
    }
}
