package software.amazon.globalaccelerator.crossaccountattachment

import com.amazonaws.services.globalaccelerator.AWSGlobalAccelerator
import com.amazonaws.services.globalaccelerator.model.AttachmentNotFoundException
import com.amazonaws.services.globalaccelerator.model.Attachment
import com.amazonaws.services.globalaccelerator.model.DescribeCrossAccountAttachmentRequest
import com.amazonaws.services.globalaccelerator.model.ListTagsForResourceRequest
import com.amazonaws.services.globalaccelerator.model.Tag
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy
import software.amazon.cloudformation.proxy.Logger

/**
 * Singleton class for common methods used by CRUD handlers
 */
object HandlerCommons {
    private const val CALLBACK_DELAY_IN_SECONDS = 1
    const val NUMBER_OF_STATE_POLL_RETRIES = 60 / CALLBACK_DELAY_IN_SECONDS * 60 * 4 // 4 hours
    private const val TIMED_OUT_MESSAGE = "Timed out waiting for cross account attachment."

    /**
     * Get tags associated with resource
     * @param arn ARN of the resource (attachment)
     */
    fun getTags(arn: String?, proxy: AmazonWebServicesClientProxy,
                agaClient: AWSGlobalAccelerator, logger: Logger): List<Tag> {
        return try {
            val request = ListTagsForResourceRequest().withResourceArn(arn)
            proxy.injectCredentialsAndInvoke(request, agaClient::listTagsForResource).tags
        } catch (ex: Exception) {
            logger.error("Exception while getting tags for accelerator with arn: [$arn].")
            emptyList()
        }
    }


    /**
     * Get the cross account attachment with the specified ARN.
     * @param arn ARN of the attachment
     * @return NULL if the attachment does not exist
     */
    fun getAttachment(arn: String?, proxy: AmazonWebServicesClientProxy,
                       agaClient: AWSGlobalAccelerator, logger: Logger): Attachment? {
        return try {
            val request = DescribeCrossAccountAttachmentRequest().withAttachmentArn(arn)
            proxy.injectCredentialsAndInvoke(request, agaClient::describeCrossAccountAttachment).crossAccountAttachment
        } catch (ex: AttachmentNotFoundException) {
            logger.debug("Attachment with arn: [$arn] not found.")
            null
        }
    }

    fun toResourceModel(attachment: Attachment, tags: List<Tag>): ResourceModel {
        return ResourceModel().apply {
            this.attachmentArn = attachment.attachmentArn
            this.name = attachment.name
            this.resources = getCrossAccountAttachmentResources(attachment.resources)
            this.principals = attachment.principals
            this.tags = tags.map { Tag(it.key, it.value) }
        }
    }
}
