package software.amazon.globalaccelerator.crossaccountattachment

import com.amazonaws.services.globalaccelerator.AWSGlobalAccelerator
import com.amazonaws.services.globalaccelerator.model.DeleteCrossAccountAttachmentRequest
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy
import software.amazon.cloudformation.proxy.Logger
import software.amazon.cloudformation.proxy.ProgressEvent
import software.amazon.cloudformation.proxy.ResourceHandlerRequest
import software.amazon.cloudformation.proxy.HandlerErrorCode
import software.amazon.globalaccelerator.crossaccountattachment.HandlerCommons.getAttachment

/**
 * Delete handler implementation for Cross Account Attachment  resource.
 */
class DeleteHandler : BaseHandler<CallbackContext>() {
    override fun handleRequest(proxy: AmazonWebServicesClientProxy,
                               request: ResourceHandlerRequest<ResourceModel>,
                               callbackContext: CallbackContext?,
                               logger: Logger): ProgressEvent<ResourceModel, CallbackContext?> {
        logger.debug("Delete Cross Account Attachment Request: $request")
        val agaClient = AcceleratorClientBuilder.client
        var model = request.desiredResourceState
        val existingAttachment = getAttachment(model.attachmentArn, proxy, agaClient, logger)
        return if (existingAttachment == null) {
            logger.error("Attachment with arn: [${model.attachmentArn}] not found.")
            ProgressEvent.failed(model, callbackContext, HandlerErrorCode.NotFound, "Attachment not found.")
        } else {
            return deleteCrossAccountAttachment(model, proxy, agaClient)
        }
    }

    private fun deleteCrossAccountAttachment(model: ResourceModel,
                                    proxy: AmazonWebServicesClientProxy,
                                    agaClient: AWSGlobalAccelerator): ProgressEvent<ResourceModel, CallbackContext?> {
        val request = DeleteCrossAccountAttachmentRequest().withAttachmentArn(model.attachmentArn)
        proxy.injectCredentialsAndInvoke(request, agaClient::deleteCrossAccountAttachment)
        return ProgressEvent.defaultSuccessHandler(model)
    }
}
