package software.amazon.globalaccelerator.endpointgroup

import com.amazonaws.services.globalaccelerator.model.EndpointGroup
import com.amazonaws.services.globalaccelerator.model.ListEndpointGroupsRequest
import com.amazonaws.services.globalaccelerator.model.ListEndpointGroupsResult
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy
import software.amazon.cloudformation.proxy.HandlerErrorCode
import software.amazon.cloudformation.proxy.Logger
import software.amazon.cloudformation.proxy.OperationStatus
import software.amazon.cloudformation.proxy.ProgressEvent
import software.amazon.cloudformation.proxy.ResourceHandlerRequest
import software.amazon.globalaccelerator.endpointgroup.AcceleratorClientBuilder.client

/**
 * List handler implementation for endpoint group resource.
 */
class ListHandler : BaseHandler<CallbackContext?>() {
    override fun handleRequest(proxy: AmazonWebServicesClientProxy,
                               request: ResourceHandlerRequest<ResourceModel>,
                               callbackContext: CallbackContext?,
                               logger: Logger): ProgressEvent<ResourceModel, CallbackContext?> {
        logger.debug("List EndpointGroup Request: $request")
        val agaClient = client
        val model = request.desiredResourceState
        val listEndpointGroupsRequest = ListEndpointGroupsRequest().withListenerArn(model.listenerArn).withNextToken(request.nextToken)
        val listEndpointGroupsResult = proxy.injectCredentialsAndInvoke(listEndpointGroupsRequest, agaClient::listEndpointGroups)
        val endpointGroupList = convertEndpointGroupList(listEndpointGroupsResult.endpointGroups, model.listenerArn)
        return ProgressEvent.builder<ResourceModel, CallbackContext>()
                .status(OperationStatus.SUCCESS)
                .resourceModels(endpointGroupList)
                .nextToken(listEndpointGroupsResult.nextToken)
                .build()
    }

    fun convertEndpointGroupList(endpointGroups: List<EndpointGroup>, listenerArn: String): List<ResourceModel> {
        return endpointGroups
                .map { ResourceModel.builder()
                        .listenerArn(listenerArn)
                        .endpointGroupArn(it.endpointGroupArn)
                        .healthCheckIntervalSeconds(it.healthCheckIntervalSeconds)
                        .healthCheckPath(it.healthCheckPath)
                        .healthCheckPort(it.healthCheckPort)
                        .healthCheckProtocol(it.healthCheckProtocol)
                        .thresholdCount(it.thresholdCount)
                        .trafficDialPercentage(it.trafficDialPercentage.toDouble())
                        .endpointGroupRegion(it.endpointGroupRegion)
                        .endpointConfigurations(getEndpointConfigurations(it.endpointDescriptions))
                        .portOverrides(getPortOverrides(it.portOverrides))
                        .build()
                }
    }
}
