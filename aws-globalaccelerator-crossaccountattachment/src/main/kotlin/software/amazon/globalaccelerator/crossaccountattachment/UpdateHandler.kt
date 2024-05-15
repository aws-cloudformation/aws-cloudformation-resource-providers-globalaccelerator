package software.amazon.globalaccelerator.crossaccountattachment

import com.amazonaws.services.globalaccelerator.AWSGlobalAccelerator
import com.amazonaws.services.globalaccelerator.model.Attachment
import com.amazonaws.services.globalaccelerator.model.UpdateCrossAccountAttachmentRequest
import com.amazonaws.services.globalaccelerator.model.UntagResourceRequest
import com.amazonaws.services.globalaccelerator.model.TagResourceRequest
import com.amazonaws.services.globalaccelerator.model.Tag
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy
import software.amazon.cloudformation.proxy.HandlerErrorCode
import software.amazon.cloudformation.proxy.Logger
import software.amazon.cloudformation.proxy.ProgressEvent
import software.amazon.cloudformation.proxy.ResourceHandlerRequest
import software.amazon.globalaccelerator.crossaccountattachment.AcceleratorClientBuilder.client
import software.amazon.globalaccelerator.crossaccountattachment.HandlerCommons.getAttachment

/**
 * Update handler implementation for Attachment resource.
 */
class UpdateHandler : BaseHandler<CallbackContext?>() {
    override fun handleRequest(proxy: AmazonWebServicesClientProxy,
                               request: ResourceHandlerRequest<ResourceModel>,
                               callbackContext: CallbackContext?,
                               logger: Logger): ProgressEvent<ResourceModel, CallbackContext?> {
        logger.debug("Update Accelerator Request: $request")
        val agaClient = client
        val model = request.desiredResourceState
        val previousModel = request.previousResourceState
        var existingAttachment = getAttachment(model.attachmentArn, proxy, agaClient, logger)
        return if (existingAttachment == null) {
            logger.error("Attachment with arn: [${model.attachmentArn}] not found.")
            ProgressEvent.failed(model, callbackContext, HandlerErrorCode.NotFound, "Attachment not found.")
        } else {
            validateAndUpdateAttachment(model, previousModel, proxy, agaClient, logger, existingAttachment)
        }
    }

    /**
     * Validates tags, updates attachment and updates tags
     */
    private fun validateAndUpdateAttachment(model: ResourceModel,
                                             previousModel: ResourceModel,
                                             proxy: AmazonWebServicesClientProxy,
                                             agaClient: AWSGlobalAccelerator,
                                             logger: Logger,
                                             existingAttachment: Attachment): ProgressEvent<ResourceModel, CallbackContext?> {
        logger.debug("Desired updated state model: $model")
        if (!validateTags(model)) {
            return ProgressEvent.defaultFailureHandler(
                    Exception("Invalid tag format in template"),
                    HandlerErrorCode.InvalidRequest
            )
        }
        val attachment = updateAttachment(model, proxy, agaClient, logger, existingAttachment)
        updateTags(attachment, model, previousModel, proxy, agaClient, logger)
        return ProgressEvent.defaultSuccessHandler(model)
    }

    private fun validateTags(model: ResourceModel): Boolean {
        val regex = "^([\\p{L}\\p{Z}\\p{N}_.:/=+\\-@]*)$".toRegex()
        model.tags?.forEach {
            if ((!regex.matches(it.key)) or (!regex.matches(it.value)) or (it.key.startsWith("aws:"))) {
                return false
            }
        }
        return true
    }

    private fun updateAttachment(model: ResourceModel,
                                  proxy: AmazonWebServicesClientProxy,
                                  agaClient: AWSGlobalAccelerator,
                                  logger: Logger,
                                  existingAttachment: Attachment): Attachment {
        logger.debug("Updating attachment with arn: [${model.attachmentArn}].")

        updatedPrincipals(model, proxy, agaClient, logger, existingAttachment)
        return updateResources(model, proxy, agaClient, logger, existingAttachment)
    }

    private fun updatedPrincipals(model: ResourceModel,
                                  proxy: AmazonWebServicesClientProxy,
                                  agaClient: AWSGlobalAccelerator,
                                  logger: Logger,
                                  existingAttachment: Attachment) : Attachment{
        logger.debug("Updating attachment Principals for Attachment arn: [${model.attachmentArn}].")

        val principalUpdateRequest = buildPrincipalRequest(model, existingAttachment, logger)
        return proxy.injectCredentialsAndInvoke(principalUpdateRequest, agaClient::updateCrossAccountAttachment).crossAccountAttachment
    }

    fun buildPrincipalRequest(model: ResourceModel, existingAttachment: Attachment, logger: Logger): UpdateCrossAccountAttachmentRequest {
        // Principals in the request model but not in the existing attachment
        val principalsToAdd = model.principals.orEmpty() - (existingAttachment.principals.orEmpty() ?: listOf())
        logger.debug("Updating attachment Principals for Attachment arn: [${model.attachmentArn}]. Adding: [${principalsToAdd}] ")
        // Principals not in request model but in the existing attachment
        val principalsToRemove = (existingAttachment.principals.orEmpty() ?: listOf()) - model.principals.orEmpty()
        logger.debug("Updating attachment Principals for Attachment arn: [${model.attachmentArn}]. Removing: [${principalsToRemove}] ")
        val principalUpdateRequest = UpdateCrossAccountAttachmentRequest()
                .withName(model.name)
                .withAttachmentArn(model.attachmentArn)
                .withAddPrincipals(principalsToAdd)
                .withRemovePrincipals(principalsToRemove)
        return principalUpdateRequest
    }

    private fun updateResources(model: ResourceModel,
                                  proxy: AmazonWebServicesClientProxy,
                                  agaClient: AWSGlobalAccelerator,
                                  logger: Logger,
                                  existingAttachment: Attachment) : Attachment {
        logger.debug("Updating attachment Resources for Attachment arn: [${model.attachmentArn}].")
        val resourceUpdateRequest = buildResourceRequest(model, existingAttachment, logger)
        return proxy.injectCredentialsAndInvoke(resourceUpdateRequest, agaClient::updateCrossAccountAttachment).crossAccountAttachment
    }

     fun buildResourceRequest(model: ResourceModel, existingAttachment: Attachment, logger: Logger): UpdateCrossAccountAttachmentRequest {
        val resourcesToAdd = if(model.resources != null && model.resources.size > 0) {
            model.resources.filterNot {incomingResource -> existingAttachment.resources.any{it.endpointId.contains(incomingResource.endpointId)}}
        } else {
            emptyList()
        }

        logger.debug("Updating attachment Resources for Attachment arn: [${model.attachmentArn}]. Adding: [${resourcesToAdd}] ")

        if (model.resources == null) {
            model.resources = emptyList()
        }

        val resourcesToRemove = if(existingAttachment.resources != null && existingAttachment.resources.size > 0) {
            existingAttachment.resources.filterNot {existingResource -> model.resources.any{existingResource.endpointId.contains(it.endpointId)}}
        } else {
            emptyList()
        }

        logger.debug("Updating attachment Resources for Attachment arn: [${model.attachmentArn}]. Removing: [${resourcesToRemove}] ")
        val convertedResourcesToAdd = resourcesToAdd.map {com.amazonaws.services.globalaccelerator.model.Resource().withEndpointId(it.endpointId).withRegion(it.region)}

        val resourceUpdateRequest = UpdateCrossAccountAttachmentRequest()
                .withName(model.name)
                .withAttachmentArn(model.attachmentArn)
                .withAddResources(convertedResourcesToAdd)
                .withRemoveResources(resourcesToRemove)

        return resourceUpdateRequest
    }

    private fun updateTags(attachment: Attachment,
                           model: ResourceModel,
                           previousModel: ResourceModel,
                           proxy: AmazonWebServicesClientProxy,
                           agaClient: AWSGlobalAccelerator,
                           logger: Logger) {
        logger.debug("Updating tags for accelerator with arn: [${model.attachmentArn}].")
        val previousStateTags = getPreviousStateTags(previousModel)
        val newTags = model.tags?.map { Tag().withKey(it.key).withValue(it.value) }
        deleteOldMissingTags(previousStateTags, newTags, attachment, proxy, agaClient, logger)
        updateOrAddTags(newTags, attachment, proxy, agaClient, logger)
    }

    /**
     * Untag (remove tags) that were present in previous version of template but are missing in new CFN template
     */
    private fun deleteOldMissingTags(previousTags: List<Tag>?,
                                     newTags: List<Tag>?,
                                     attachment: Attachment,
                                     proxy: AmazonWebServicesClientProxy,
                                     agaClient: AWSGlobalAccelerator,
                                     logger: Logger) {
        logger.debug("Looking for tags to be deleted")
        val newTagsMap = newTags?.map { it.key to it.value }?.toMap() ?: mapOf<String, String>()
        val keysToDelete = previousTags?.map { x -> x.key }?.minus(newTagsMap.keys)
        if (!keysToDelete.isNullOrEmpty()) {
            logger.debug("Untagging tags: [$keysToDelete.toString()] for accelerator with arn: [${attachment.attachmentArn}].")
            val untagRequest = UntagResourceRequest().withResourceArn(attachment.attachmentArn).withTagKeys(keysToDelete)
            proxy.injectCredentialsAndInvoke(untagRequest,agaClient::untagResource)
        }
    }

    private fun updateOrAddTags(newTags: List<Tag>?,
                                attachment: Attachment,
                                proxy: AmazonWebServicesClientProxy,
                                agaClient: AWSGlobalAccelerator,
                                logger: Logger) {
        if (newTags.isNullOrEmpty()) {
            logger.debug("No updates or new addition of tags.")
        } else {
            val tagRequest = TagResourceRequest().withResourceArn(attachment.attachmentArn).withTags(newTags)
            proxy.injectCredentialsAndInvoke(tagRequest, agaClient::tagResource)
        }
    }

    private fun getPreviousStateTags(previousModel: ResourceModel): List<Tag>? {
        return previousModel.tags?.map { Tag().withKey(it.key).withValue(it.value) }
    }
}
