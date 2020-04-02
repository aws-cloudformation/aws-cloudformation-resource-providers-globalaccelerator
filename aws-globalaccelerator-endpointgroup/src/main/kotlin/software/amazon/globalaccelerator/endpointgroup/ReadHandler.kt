package software.amazon.globalaccelerator.endpointgroup

import com.amazonaws.services.globalaccelerator.model.EndpointDescription
import com.amazonaws.services.globalaccelerator.model.EndpointGroup
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy
import software.amazon.cloudformation.proxy.HandlerErrorCode
import software.amazon.cloudformation.proxy.Logger
import software.amazon.cloudformation.proxy.ProgressEvent
import software.amazon.cloudformation.proxy.ResourceHandlerRequest

class ReadHandler : BaseHandler<CallbackContext>() {

    @Override
    override fun handleRequest(
            proxy: AmazonWebServicesClientProxy,
            request: ResourceHandlerRequest<ResourceModel>,
            callbackContext: CallbackContext?,
            logger: Logger): ProgressEvent<ResourceModel, CallbackContext?> {

        val model = request.getDesiredResourceState()

        val agaClient = AcceleratorClientBuilder.client
        logger.log(String.format("Read request for endpoint group: [%s]", request))

        val endpointGroup = HandlerCommons.getEndpointGroup(model.getEndpointGroupArn(), proxy, agaClient, logger)
        val endpointGroupResourceModel = convertEndpointGroupToResourceModel(endpointGroup)

        if (endpointGroupResourceModel == null) {
            logger.log(String.format("Endpoint group with ARN [%s] not found", model.getEndpointGroupArn()))
            return ProgressEvent.defaultFailureHandler(Exception("Endpoint group not found."), HandlerErrorCode.NotFound)
        } else {
            return ProgressEvent.defaultSuccessHandler(endpointGroupResourceModel)
        }
    }

    private fun convertEndpointGroupToResourceModel(endpointGroup: EndpointGroup?): ResourceModel? {
        var newModel: ResourceModel? = null
        if (endpointGroup != null) {
            newModel = ResourceModel()
            newModel.setEndpointGroupArn(endpointGroup.getEndpointGroupArn())
            newModel.setHealthCheckIntervalSeconds(endpointGroup.getHealthCheckIntervalSeconds())
            newModel.setHealthCheckPath(endpointGroup.getHealthCheckPath())
            newModel.setHealthCheckPort(endpointGroup.getHealthCheckPort())
            newModel.setHealthCheckProtocol(endpointGroup.getHealthCheckProtocol())
            newModel.setThresholdCount(endpointGroup.getThresholdCount())
            newModel.setTrafficDialPercentage(endpointGroup.getTrafficDialPercentage().toInt())
            newModel.setEndpointGroupRegion(endpointGroup.getEndpointGroupRegion())
            newModel.setEndpointConfigurations(getEndpointConfigurations(endpointGroup.getEndpointDescriptions()))
        }
        return newModel
    }

    private fun getEndpointConfigurations(endpointDescriptions: List<EndpointDescription>): List<EndpointConfiguration> {
        return endpointDescriptions.map{ EndpointConfiguration.builder()
                    .clientIPPreservationEnabled(it.getClientIPPreservationEnabled())
                    .endpointId(it.getEndpointId())
                    .weight(it.getWeight())
                    .build()}
    }
}
