package software.amazon.globalaccelerator.crossaccountattachment

import com.amazonaws.services.globalaccelerator.model.ListCrossAccountAttachmentsRequest
import com.amazonaws.services.globalaccelerator.model.Attachment
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy
import software.amazon.cloudformation.proxy.Logger
import software.amazon.cloudformation.proxy.OperationStatus
import software.amazon.cloudformation.proxy.ProgressEvent
import software.amazon.cloudformation.proxy.ResourceHandlerRequest
import software.amazon.globalaccelerator.crossaccountattachment.AcceleratorClientBuilder.client
import software.amazon.globalaccelerator.crossaccountattachment.HandlerCommons.toResourceModel

/**
 * List handler implementation for attachment resource.
 */
class ListHandler : BaseHandler<CallbackContext?>() {
    override fun handleRequest(proxy: AmazonWebServicesClientProxy,
                               request: ResourceHandlerRequest<ResourceModel>,
                               callbackContext: CallbackContext?,
                               logger: Logger): ProgressEvent<ResourceModel, CallbackContext?> {
        logger.debug("List Attachment Request: $request")
        val agaClient = client
        val listAttachmentsRequest = ListCrossAccountAttachmentsRequest().withNextToken(request.nextToken)
        val listAttachmentsResult = proxy.injectCredentialsAndInvoke(listAttachmentsRequest, agaClient::listCrossAccountAttachments)
        val attachmentList = convertAttachmentsList(listAttachmentsResult.crossAccountAttachments)
        return ProgressEvent.builder<ResourceModel, CallbackContext>()
                .status(OperationStatus.SUCCESS)
                .resourceModels(attachmentList)
                .nextToken(listAttachmentsResult.nextToken)
                .build()
    }

    fun convertAttachmentsList(attachments: List<Attachment>): List<ResourceModel> {
        return attachments.map { toResourceModel(it, emptyList()) }
    }
}
