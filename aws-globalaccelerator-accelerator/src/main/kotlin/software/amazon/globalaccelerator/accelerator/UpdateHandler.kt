package software.amazon.globalaccelerator.accelerator

import com.amazonaws.services.globalaccelerator.AWSGlobalAccelerator
import com.amazonaws.services.globalaccelerator.model.Accelerator
import com.amazonaws.services.globalaccelerator.model.Tag
import com.amazonaws.services.globalaccelerator.model.TagResourceRequest
import com.amazonaws.services.globalaccelerator.model.UntagResourceRequest
import com.amazonaws.services.globalaccelerator.model.UpdateAcceleratorRequest
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy
import software.amazon.cloudformation.proxy.HandlerErrorCode
import software.amazon.cloudformation.proxy.Logger
import software.amazon.cloudformation.proxy.ProgressEvent
import software.amazon.cloudformation.proxy.ResourceHandlerRequest
import software.amazon.globalaccelerator.accelerator.AcceleratorClientBuilder.client
import software.amazon.globalaccelerator.accelerator.HandlerCommons.getAccelerator
import software.amazon.globalaccelerator.accelerator.HandlerCommons.waitForSynchronizedStep

/**
 * Update handler implementation for accelerator resource.
 */
class UpdateHandler : BaseHandler<CallbackContext?>() {
    override fun handleRequest(proxy: AmazonWebServicesClientProxy,
                               request: ResourceHandlerRequest<ResourceModel>,
                               callbackContext: CallbackContext?,
                               logger: Logger): ProgressEvent<ResourceModel, CallbackContext?> {
        logger.debug("Update Accelerator Request: $request")
        val agaClient = client
        val inferredCallbackContext = callbackContext
                ?: CallbackContext(stabilizationRetriesRemaining = HandlerCommons.NUMBER_OF_STATE_POLL_RETRIES, pendingStabilization = false)
        val model = request.desiredResourceState
        val previousModel = request.previousResourceState
        getAccelerator(model.acceleratorArn, proxy, agaClient, logger)
                ?: return ProgressEvent.defaultFailureHandler(Exception("Failed to find accelerator. arn: [${model.acceleratorArn}]"), HandlerErrorCode.NotFound)
        if (byoipIPsUpdated(model, previousModel)) {
            logger.error("Failed attempt to update BYOIP IPs.")
            return ProgressEvent.defaultFailureHandler(
                    // Global Accelerator APIs don't support updates of IPs so customer will need to create accelerator with updated IPs.
                    Exception("Updates for BYOIP IP addresses is not a supported operation. Delete existing accelerator and create new accelerator with updated IPs."),
                    HandlerErrorCode.InvalidRequest)
        }
        return if (inferredCallbackContext.pendingStabilization) {
            waitForSynchronizedStep(inferredCallbackContext, model, proxy, agaClient, logger)
        } else {
            validateAndUpdateAccelerator(model, previousModel, proxy, agaClient, logger)
        }
    }

    /**
     * Validates tags, updates accelerator and updates tags
     * This is best effort to make sure tagging doesn't rollback after update of accelerator
     * We might need a need a better handle for tag rollbacks
     * This implementation takes care of tags drift between different versions
     */
    private fun validateAndUpdateAccelerator(model: ResourceModel,
                                             previousModel: ResourceModel,
                                             proxy: AmazonWebServicesClientProxy,
                                             agaClient: AWSGlobalAccelerator,
                                             logger: Logger): ProgressEvent<ResourceModel, CallbackContext?> {
        logger.debug("Desired updated state model: $model")
        if (!validateTags(model)) {
            return ProgressEvent.defaultFailureHandler(
                    Exception("Invalid tag format in template"),
                    HandlerErrorCode.InvalidRequest
            )
        }
        val accelerator = updateAccelerator(model, proxy, agaClient, logger)
        updateTags(accelerator, model, previousModel, proxy, agaClient, logger)
        val callbackContext = CallbackContext(stabilizationRetriesRemaining = HandlerCommons.NUMBER_OF_STATE_POLL_RETRIES,
                pendingStabilization = true)
        return ProgressEvent.defaultInProgressHandler(callbackContext, 0, model)
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

    private fun updateAccelerator(model: ResourceModel,
                                  proxy: AmazonWebServicesClientProxy,
                                  agaClient: AWSGlobalAccelerator,
                                  logger: Logger): Accelerator {
        logger.debug("Updating accelerator with arn: [${model.acceleratorArn}].")
        val request = UpdateAcceleratorRequest()
                .withAcceleratorArn(model.acceleratorArn)
                .withEnabled(model.enabled)
                .withIpAddressType(model.ipAddressType)
                .withName(model.name)
        return proxy.injectCredentialsAndInvoke(request, agaClient::updateAccelerator).accelerator
    }

    private fun updateTags(accelerator: Accelerator,
                           model: ResourceModel,
                           previousModel: ResourceModel,
                           proxy: AmazonWebServicesClientProxy,
                           agaClient: AWSGlobalAccelerator,
                           logger: Logger) {
        logger.debug("Updating tags for accelerator with arn: [${model.acceleratorArn}].")
        val previousStateTags = getPreviousStateTags(previousModel)
        val newTags = model.tags?.map { Tag().withKey(it.key).withValue(it.value) }
        deleteOldMissingTags(previousStateTags, newTags, accelerator, proxy, agaClient, logger)
        updateOrAddTags(newTags, accelerator, proxy, agaClient, logger)
    }

    /**
     * Untag (remove tags) that were present in previous version of template but are missing in new CFN template
     */
    private fun deleteOldMissingTags(previousTags: List<Tag>?,
                                     newTags: List<Tag>?,
                                     accelerator: Accelerator,
                                     proxy: AmazonWebServicesClientProxy,
                                     agaClient: AWSGlobalAccelerator,
                                     logger: Logger) {
        logger.debug("Looking for tags to be deleted")
        val newTagsMap = newTags?.map { it.key to it.value }?.toMap() ?: mapOf<String, String>()
        val keysToDelete = previousTags?.map { x -> x.key }?.minus(newTagsMap.keys)
        if (!keysToDelete.isNullOrEmpty()) {
            logger.debug("Untagging tags: [$keysToDelete.toString()] for accelerator with arn: [${accelerator.acceleratorArn}].")
            val untagRequest = UntagResourceRequest().withResourceArn(accelerator.acceleratorArn).withTagKeys(keysToDelete)
            proxy.injectCredentialsAndInvoke(untagRequest,agaClient::untagResource)
        }
    }

    private fun updateOrAddTags(newTags: List<Tag>?,
                                accelerator: Accelerator,
                                proxy: AmazonWebServicesClientProxy,
                                agaClient: AWSGlobalAccelerator,
                                logger: Logger) {
        if (newTags.isNullOrEmpty()) {
            logger.debug("No updates or new addition of tags.")
        } else {
            val tagRequest = TagResourceRequest().withResourceArn(accelerator.acceleratorArn).withTags(newTags)
            proxy.injectCredentialsAndInvoke(tagRequest, agaClient::tagResource)
        }
    }

    private fun getPreviousStateTags(previousModel: ResourceModel): List<Tag>? {
        return previousModel.tags?.map { Tag().withKey(it.key).withValue(it.value) }
    }

    private fun byoipIPsUpdated(currentModel: ResourceModel, previousModel: ResourceModel): Boolean {
        var ipAddressesUpdated = false
        if (currentModel.ipAddresses != null && currentModel.ipAddresses != previousModel.ipAddresses) {
            ipAddressesUpdated = true
        }
        return ipAddressesUpdated
    }

}
