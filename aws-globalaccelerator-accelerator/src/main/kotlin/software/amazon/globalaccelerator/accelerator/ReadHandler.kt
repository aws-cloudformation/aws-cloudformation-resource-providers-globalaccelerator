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

/**
 * Read handler implementation for accelerator resource.
 */
class ReadHandler : BaseHandler<CallbackContext?>() {
    override fun handleRequest(proxy: AmazonWebServicesClientProxy,
                               request: ResourceHandlerRequest<ResourceModel>,
                               callbackContext: CallbackContext?,
                               logger: Logger): ProgressEvent<ResourceModel, CallbackContext?> {
        logger.debug("Read Accelerator Request: $request")
        val agaClient = client
        val model = request.desiredResourceState
        val accelerator = getAccelerator(model.acceleratorArn, proxy, agaClient, logger)
        val tags = getTags(model.acceleratorArn, proxy, agaClient, logger)
        val acceleratorResourceModel = convertAcceleratorToResourceModel(accelerator, tags)
        return if (acceleratorResourceModel == null) {
            logger.error("Accelerator with arn: [${model.acceleratorArn}] not found.")
            ProgressEvent.defaultFailureHandler(Exception("Accelerator not found."), HandlerErrorCode.NotFound)
        } else {
            ProgressEvent.defaultSuccessHandler(acceleratorResourceModel)
        }
    }

    private fun convertAcceleratorToResourceModel(accelerator: Accelerator?, tags: List<Tag>): ResourceModel? {
        return if (accelerator != null) {
            ResourceModel().apply {
                this.acceleratorArn = accelerator.acceleratorArn
                this.name = accelerator.name
                this.enabled = accelerator.enabled
                this.ipAddressType = accelerator.ipAddressType
                this.dnsName = accelerator.dnsName
                this.ipAddresses = accelerator.ipSets?.flatMap { it.ipAddresses }
                this.tags = tags.map { Tag(it.key, it.value) }
            }
        } else {
            null
        }
    }
}
