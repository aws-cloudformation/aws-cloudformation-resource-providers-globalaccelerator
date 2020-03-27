package software.amazon.globalaccelerator.accelerator

import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy
import software.amazon.cloudformation.proxy.Logger
import software.amazon.cloudformation.proxy.OperationStatus
import software.amazon.cloudformation.proxy.ProgressEvent
import software.amazon.cloudformation.proxy.ResourceHandlerRequest

class ReadHandler : BaseHandler<CallbackContext?>() {
    override fun handleRequest(
            proxy: AmazonWebServicesClientProxy,
            request: ResourceHandlerRequest<ResourceModel>,
            callbackContext: CallbackContext?,
            logger: Logger): ProgressEvent<ResourceModel, CallbackContext?> {
        val model = request.desiredResourceState
        logger.log(String.format("READING: [%s]", request))
        return ProgressEvent.builder<ResourceModel, CallbackContext>()
                .resourceModel(model)
                .status(OperationStatus.SUCCESS)
                .build()
    }
}
