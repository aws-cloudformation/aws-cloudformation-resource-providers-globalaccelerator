package software.amazon.globalaccelerator.crossaccountattachment

import com.amazonaws.services.globalaccelerator.AWSGlobalAccelerator
import com.amazonaws.services.globalaccelerator.model.CreateCrossAccountAttachmentRequest
import com.amazonaws.services.globalaccelerator.model.Attachment
import com.amazonaws.services.globalaccelerator.model.Tag
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy
import software.amazon.cloudformation.proxy.Logger
import software.amazon.cloudformation.proxy.ProgressEvent
import software.amazon.cloudformation.proxy.ResourceHandlerRequest

/**
 * Create handler implementation for Cross Account Attachment resource.
 */
class CreateHandler : BaseHandler<CallbackContext?>() {
    override fun handleRequest(proxy: AmazonWebServicesClientProxy,
                               request: ResourceHandlerRequest<ResourceModel>,
                               callbackContext: CallbackContext?,
                               logger: Logger): ProgressEvent<ResourceModel, CallbackContext?> {
        logger.debug("Create new attachment request: $request")
        val agaClient = AcceleratorClientBuilder.client
        val model = request.desiredResourceState
        return createAttachmentStep(model, request, proxy, agaClient, logger)
    }


    private fun createAttachmentStep(model: ResourceModel,
                                     handlerRequest: ResourceHandlerRequest<ResourceModel>,
                                     proxy: AmazonWebServicesClientProxy,
                                     agaClient: AWSGlobalAccelerator,
                                     logger: Logger): ProgressEvent<ResourceModel, CallbackContext?> {
        logger.debug("Creating new cross account attachment with model: $model")
        val crossAccountAttachment = createCrossAccountAttachment(model, handlerRequest, proxy, agaClient)
        model.apply {
            this.attachmentArn = crossAccountAttachment.attachmentArn
            this.name = crossAccountAttachment.name
            this.principals = crossAccountAttachment.principals
            this.resources = getCrossAccountAttachmentResources(crossAccountAttachment.resources)
            this.tags = model.tags
        }
        return ProgressEvent.defaultSuccessHandler(model)
    }


    private fun createCrossAccountAttachment(model: ResourceModel,
                                             handlerRequest: ResourceHandlerRequest<ResourceModel>,
                                             proxy: AmazonWebServicesClientProxy,
                                             agaClient: AWSGlobalAccelerator): Attachment {
        val convertedTags = model.tags?.map { Tag().withKey(it.key).withValue(it.value) }
        val convertedResources = model.resources?.map {com.amazonaws.services.globalaccelerator.model.Resource().withEndpointId(it.endpointId).withCidr(it.cidr).withRegion(it.region)}
        val createAttachmentRequest = CreateCrossAccountAttachmentRequest()
                .withName(model.name)
                .withPrincipals(model.principals)
                .withResources(convertedResources)
                .withTags(convertedTags)
                .withIdempotencyToken(handlerRequest.clientRequestToken)

        return proxy.injectCredentialsAndInvoke(createAttachmentRequest, agaClient::createCrossAccountAttachment).crossAccountAttachment
    }
}
