package software.amazon.globalaccelerator.listener

import com.amazonaws.services.globalaccelerator.model.Listener
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy
import software.amazon.cloudformation.proxy.HandlerErrorCode
import software.amazon.cloudformation.proxy.Logger
import software.amazon.cloudformation.proxy.ProgressEvent
import software.amazon.cloudformation.proxy.ResourceHandlerRequest

/**
 * Read handler implementation for listener resource.
 */
class ReadHandler : BaseHandler<CallbackContext>() {
    override fun handleRequest(
            proxy: AmazonWebServicesClientProxy,
            request: ResourceHandlerRequest<ResourceModel>,
            callbackContext: CallbackContext?,
            logger: Logger): ProgressEvent<ResourceModel, CallbackContext?> {
        logger.debug("Read Listener Request [$request]")
        val model = request.desiredResourceState
        val agaClient = AcceleratorClientBuilder.client
        val listener = HandlerCommons.getListener(model.listenerArn, proxy, agaClient, logger)
        val convertedModel = convertListenerToResourceModel(listener, model)
        return if (convertedModel != null) {
            ProgressEvent.defaultSuccessHandler(convertedModel)
        } else {
            logger.error("Listener with arn: [${model.listenerArn}] not found.")
            ProgressEvent.defaultFailureHandler(Exception("Listener not found."), HandlerErrorCode.NotFound)
        }
    }

    private fun convertListenerToResourceModel(listener: Listener?, currentModel: ResourceModel): ResourceModel? {
        return if (listener != null) {
            ResourceModel().apply {
                this.listenerArn = listener.listenerArn
                this.protocol = listener.protocol
                this.acceleratorArn = HandlerCommons.getAcceleratorArnFromListenerArn(currentModel.listenerArn)
                this.clientAffinity = listener.clientAffinity
                this.portRanges = listener.portRanges.map{ x ->
                    val portRange = PortRange()
                    portRange.fromPort = x.fromPort
                    portRange.toPort = x.toPort
                    portRange
                }
            }
        } else {
            null
        }
    }
}
