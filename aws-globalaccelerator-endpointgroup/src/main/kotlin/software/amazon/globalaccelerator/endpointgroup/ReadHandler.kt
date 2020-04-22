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

        val model = request.desiredResourceState

        val agaClient = AcceleratorClientBuilder.client
        logger.log(String.format("Read request for endpoint group: [%s]", request))

        val endpointGroup = HandlerCommons.getEndpointGroup(model.endpointGroupArn, proxy, agaClient, logger)
        val endpointGroupResourceModel = convertEndpointGroupToResourceModel(endpointGroup)

        if (endpointGroupResourceModel == null) {
            logger.log(String.format("Endpoint group with ARN [%s] not found", model.endpointGroupArn))
            return ProgressEvent.defaultFailureHandler(Exception("Endpoint group not found."), HandlerErrorCode.NotFound)
        } else {
            return ProgressEvent.defaultSuccessHandler(endpointGroupResourceModel)
        }
    }

    private fun convertEndpointGroupToResourceModel(endpointGroup: EndpointGroup?): ResourceModel? {
        var newModel: ResourceModel? = null
        if (endpointGroup != null) {
            newModel = ResourceModel()
            newModel.endpointGroupArn = endpointGroup.endpointGroupArn
            newModel.healthCheckIntervalSeconds = endpointGroup.healthCheckIntervalSeconds
            newModel.healthCheckPath = endpointGroup.healthCheckPath
            newModel.healthCheckPort = endpointGroup.healthCheckPort
            newModel.healthCheckProtocol = endpointGroup.healthCheckProtocol
            newModel.thresholdCount = endpointGroup.thresholdCount
            newModel.trafficDialPercentage = endpointGroup.trafficDialPercentage.toInt()
            newModel.endpointGroupRegion = endpointGroup.endpointGroupRegion
            newModel.endpointConfigurations = getEndpointConfigurations(endpointGroup.endpointDescriptions)
        }
        return newModel
    }

    private fun getEndpointConfigurations(endpointDescriptions: List<EndpointDescription>): List<EndpointConfiguration> {
        return endpointDescriptions.map{ EndpointConfiguration.builder()
                    .clientIPPreservationEnabled(it.clientIPPreservationEnabled)
                    .endpointId(it.endpointId)
                    .weight(it.weight)
                    .build()}
    }
}
