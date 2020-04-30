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
        logger.log("Read request for endpoint group: [$request]")

        val endpointGroup = HandlerCommons.getEndpointGroup(model.endpointGroupArn, proxy, agaClient, logger)
        val endpointGroupResourceModel = convertEndpointGroupToResourceModel(endpointGroup)

        return if (endpointGroupResourceModel == null) {
            logger.log("Endpoint group with ARN [${model.endpointGroupArn}] not found")
            ProgressEvent.defaultFailureHandler(Exception("Endpoint group not found."), HandlerErrorCode.NotFound)
        } else {
            ProgressEvent.defaultSuccessHandler(endpointGroupResourceModel)
        }
    }

    private fun convertEndpointGroupToResourceModel(endpointGroup: EndpointGroup?): ResourceModel? {
        var newModel: ResourceModel? = null
        if (endpointGroup != null) {
            newModel = ResourceModel()
            newModel.apply {
                this.endpointGroupArn = endpointGroup.endpointGroupArn
                this.healthCheckIntervalSeconds = endpointGroup.healthCheckIntervalSeconds
                this.healthCheckPath = endpointGroup.healthCheckPath
                this.healthCheckPort = endpointGroup.healthCheckPort
                this.healthCheckProtocol = endpointGroup.healthCheckProtocol
                this.thresholdCount = endpointGroup.thresholdCount
                this.trafficDialPercentage = endpointGroup.trafficDialPercentage.toDouble()
                this.endpointGroupRegion = endpointGroup.endpointGroupRegion
                this.endpointConfigurations = getEndpointConfigurations(endpointGroup.endpointDescriptions)
            }
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
