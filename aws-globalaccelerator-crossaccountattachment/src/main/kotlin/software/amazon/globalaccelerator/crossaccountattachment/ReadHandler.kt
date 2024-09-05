package software.amazon.globalaccelerator.crossaccountattachment

import com.amazonaws.services.globalaccelerator.model.Attachment
import com.amazonaws.services.globalaccelerator.model.Tag
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy
import software.amazon.cloudformation.proxy.HandlerErrorCode
import software.amazon.cloudformation.proxy.Logger
import software.amazon.cloudformation.proxy.ProgressEvent
import software.amazon.cloudformation.proxy.ResourceHandlerRequest
import software.amazon.globalaccelerator.crossaccountattachment.AcceleratorClientBuilder.client
import software.amazon.globalaccelerator.crossaccountattachment.HandlerCommons.getAttachment
import software.amazon.globalaccelerator.crossaccountattachment.HandlerCommons.getTags
import software.amazon.globalaccelerator.crossaccountattachment.HandlerCommons.toResourceModel

/**
 * Read handler implementation for Attachment resource.
 */
class ReadHandler : BaseHandler<CallbackContext?>() {
    override fun handleRequest(proxy: AmazonWebServicesClientProxy,
                               request: ResourceHandlerRequest<ResourceModel>,
                               callbackContext: CallbackContext?,
                               logger: Logger): ProgressEvent<ResourceModel, CallbackContext?> {
        logger.debug("Read Attachemnt Request: $request")
        val agaClient = client
        val model = request.desiredResourceState
        val attachment = getAttachment(model.attachmentArn, proxy, client, logger)
        val tags = getTags(model.attachmentArn, proxy, agaClient, logger)
        val attachmentResourceModel = attachment?.let { convertAttachment(it, tags) }
        return if (attachmentResourceModel == null) {
            logger.error("Attachment with arn: [${model.attachmentArn}] not found.")
            ProgressEvent.failed(model, callbackContext, HandlerErrorCode.NotFound, "Attachment not found.")
        } else {
            ProgressEvent.defaultSuccessHandler(attachmentResourceModel)
        }
    }

    fun convertAttachment(attachment: Attachment, tags: List<Tag>): ResourceModel? {
        return attachment?.let { toResourceModel(it, tags) }
    }
}
