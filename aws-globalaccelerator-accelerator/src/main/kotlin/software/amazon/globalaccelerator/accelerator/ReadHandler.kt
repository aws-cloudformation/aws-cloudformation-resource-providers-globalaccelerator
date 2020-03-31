package software.amazon.globalaccelerator.accelerator

import com.amazonaws.services.globalaccelerator.model.Accelerator
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy
import software.amazon.cloudformation.proxy.HandlerErrorCode
import software.amazon.cloudformation.proxy.Logger
import software.amazon.cloudformation.proxy.ProgressEvent
import software.amazon.cloudformation.proxy.ResourceHandlerRequest
import software.amazon.globalaccelerator.accelerator.AcceleratorClientBuilder.client
import software.amazon.globalaccelerator.accelerator.HandlerCommons.getAccelerator

class ReadHandler : BaseHandler<CallbackContext?>() {
    override fun handleRequest(
            proxy: AmazonWebServicesClientProxy,
            request: ResourceHandlerRequest<ResourceModel>,
            callbackContext: CallbackContext?,
            logger: Logger): ProgressEvent<ResourceModel, CallbackContext?> {
        val agaClient = client
        val model = request.desiredResourceState
        logger.log(String.format("Read request for accelerator: [%s]", request))
        val accelerator = getAccelerator(model.acceleratorArn, proxy, agaClient, logger)
        val acceleratorResourceModel = convertAcceleratorToResourceModel(accelerator)
        return if (acceleratorResourceModel == null) {
            logger.log(String.format("Accelerator with ARN [%s] not found", model.acceleratorArn))
            ProgressEvent.defaultFailureHandler(Exception("Accelerator not found."), HandlerErrorCode.NotFound)
        } else {
            ProgressEvent.defaultSuccessHandler(acceleratorResourceModel)
        }
    }

    private fun convertAcceleratorToResourceModel(accelerator: Accelerator?): ResourceModel? {
        return if (accelerator != null) {
            val newModel = ResourceModel()
            newModel.acceleratorArn = accelerator.acceleratorArn
            newModel.name = accelerator.name
            newModel.enabled = accelerator.enabled
            newModel.ipAddressType = accelerator.ipAddressType
            newModel.ipAddresses = accelerator.ipSets?.flatMap { it.ipAddresses }
            newModel
        } else {
            null
        }
    }
}
