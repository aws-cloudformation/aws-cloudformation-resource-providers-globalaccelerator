package software.amazon.globalaccelerator.listener

import com.amazonaws.services.globalaccelerator.model.Listener
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
        logger.log(String.format("Reading listener with request [%s]", request))

        val model = request.getDesiredResourceState()
        val agaClient = AcceleratorClientBuilder.client

        val listener = HandlerCommons.getListener(model.getListenerArn(), proxy, agaClient, logger)
        val convertedModel = convertListenerToResourceModel(listener, model)

        logger.log(String.format("Current found listener is: [%s]", if (convertedModel == null) "null" else convertedModel))
        return if (convertedModel != null) {
            ProgressEvent.defaultSuccessHandler(convertedModel)
        } else {
            ProgressEvent.defaultFailureHandler(Exception("Listener not found."), HandlerErrorCode.NotFound)
        }
    }

    private fun convertListenerToResourceModel(listener: Listener?, currentModel: ResourceModel): ResourceModel? {
        var converted: ResourceModel? = null

        if (listener != null) {
            converted = ResourceModel()
            converted.setListenerArn(listener.getListenerArn())
            converted.setProtocol(listener.getProtocol())
            converted.setAcceleratorArn(currentModel.getAcceleratorArn())
            converted.setClientAffinity(listener.getClientAffinity())
            converted.setPortRanges(
                    listener.getPortRanges().map{ x ->
                        val portRange = PortRange()
                        portRange.setFromPort(x.getFromPort())
                        portRange.setToPort(x.getToPort())
                        portRange
                    })
        }

        return converted
    }
}
