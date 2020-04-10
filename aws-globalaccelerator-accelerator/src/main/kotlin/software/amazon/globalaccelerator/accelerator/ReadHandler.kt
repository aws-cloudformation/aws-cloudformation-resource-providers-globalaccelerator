package software.amazon.globalaccelerator.accelerator

import com.amazonaws.services.globalaccelerator.model.Accelerator
import com.amazonaws.services.globalaccelerator.model.Tag
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy
import software.amazon.cloudformation.proxy.HandlerErrorCode
import software.amazon.cloudformation.proxy.Logger
import software.amazon.cloudformation.proxy.ProgressEvent
import software.amazon.cloudformation.proxy.ResourceHandlerRequest
import software.amazon.globalaccelerator.accelerator.AcceleratorClientBuilder.client
import software.amazon.globalaccelerator.accelerator.HandlerCommons.getAccelerator
import software.amazon.globalaccelerator.accelerator.HandlerCommons.getTags

class ReadHandler : BaseHandler<CallbackContext?>() {
    override fun handleRequest(
            proxy: AmazonWebServicesClientProxy,
            request: ResourceHandlerRequest<ResourceModel>,
            callbackContext: CallbackContext?,
            logger: Logger): ProgressEvent<ResourceModel, CallbackContext?> {
        val agaClient = client
        val model = request.desiredResourceState
        logger.log(String.format("[DEBUG] Read request for accelerator: [%s]", request))
        val accelerator = getAccelerator(model.acceleratorArn, proxy, agaClient, logger)
        val tags = getTags(model.acceleratorArn, proxy, agaClient, logger)
        val acceleratorResourceModel = convertAcceleratorToResourceModel(accelerator, tags)
        return if (acceleratorResourceModel == null) {
            logger.log(String.format("[ERROR] Accelerator with ARN [%s] not found", model.acceleratorArn))
            ProgressEvent.defaultFailureHandler(Exception("Accelerator not found."), HandlerErrorCode.NotFound)
        } else {
            ProgressEvent.defaultSuccessHandler(acceleratorResourceModel)
        }
    }

    private fun convertAcceleratorToResourceModel(accelerator: Accelerator?, tags: List<Tag>): ResourceModel? {
        return if (accelerator != null) {
            val newModel = ResourceModel()
            newModel.acceleratorArn = accelerator.acceleratorArn
            newModel.name = accelerator.name
            newModel.enabled = accelerator.enabled
            newModel.ipAddressType = accelerator.ipAddressType
            newModel.dnsName = accelerator.dnsName
            newModel.ipAddresses = accelerator.ipSets?.flatMap { it.ipAddresses }
            newModel.tags = tags.map{software.amazon.globalaccelerator.accelerator.Tag(it.key, it.value)}
            newModel
        } else {
            null
        }
    }
}
