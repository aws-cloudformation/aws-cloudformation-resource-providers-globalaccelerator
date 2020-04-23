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
        logger.log("Reading listener with request [$request]")

        val model = request.desiredResourceState
        val agaClient = AcceleratorClientBuilder.client

        val listener = HandlerCommons.getListener(model.listenerArn, proxy, agaClient, logger)
        val convertedModel = convertListenerToResourceModel(listener, model)

        logger.log("Current found listener is: [${convertedModel ?: "null"}]")
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
            converted.apply {
                this.listenerArn = listener.listenerArn
                this.protocol = listener.protocol
                this.acceleratorArn = currentModel.acceleratorArn
                this.clientAffinity = listener.clientAffinity
                this.portRanges = listener.portRanges.map{ x ->
                    val portRange = PortRange()
                    portRange.fromPort = x.fromPort
                    portRange.toPort = x.toPort
                    portRange
                }
            }
        }

        return converted
    }
}
