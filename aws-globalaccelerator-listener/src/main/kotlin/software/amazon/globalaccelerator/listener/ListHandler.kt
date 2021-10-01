package software.amazon.globalaccelerator.listener

import com.amazonaws.services.globalaccelerator.model.Listener
import com.amazonaws.services.globalaccelerator.model.ListListenersRequest
import com.amazonaws.services.globalaccelerator.model.ListListenersResult
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy
import software.amazon.cloudformation.proxy.HandlerErrorCode
import software.amazon.cloudformation.proxy.Logger
import software.amazon.cloudformation.proxy.OperationStatus
import software.amazon.cloudformation.proxy.ProgressEvent
import software.amazon.cloudformation.proxy.ResourceHandlerRequest
import software.amazon.globalaccelerator.listener.AcceleratorClientBuilder.client

/**
 * List handler implementation for listener resource.
 */
class ListHandler : BaseHandler<CallbackContext?>() {
    override fun handleRequest(proxy: AmazonWebServicesClientProxy,
                               request: ResourceHandlerRequest<ResourceModel>,
                               callbackContext: CallbackContext?,
                               logger: Logger): ProgressEvent<ResourceModel, CallbackContext?> {
        logger.debug("List Listeners Request: $request")
        val agaClient = client
        val model = request.desiredResourceState
        val listListenersRequest = ListListenersRequest().withAcceleratorArn(model.acceleratorArn).withNextToken(request.nextToken)
        val listListenersResult = proxy.injectCredentialsAndInvoke(listListenersRequest, agaClient::listListeners)
        val listenerList = convertListenerList(listListenersResult.listeners, model.acceleratorArn)
        return ProgressEvent.builder<ResourceModel, CallbackContext>()
                .status(OperationStatus.SUCCESS)
                .resourceModels(listenerList)
                .nextToken(listListenersResult.nextToken)
                .build()
    }

    fun convertListenerList(listeners: List<Listener>, acceleratorArn: String): List<ResourceModel> {
        return listeners
                .map {
                    ResourceModel.builder()
                            .listenerArn(it.listenerArn)
                            .acceleratorArn(acceleratorArn)
                            .protocol(it.protocol)
                            .clientAffinity(it.clientAffinity)
                            .portRanges(it.portRanges.map { x -> PortRange(x.fromPort, x.toPort) })
                            .build()
                }
    }
}
