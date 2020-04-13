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
 * CFN update handler for accelerator.
 */

class UpdateHandler : BaseHandler<CallbackContext?>() {
    override fun handleRequest(
            proxy: AmazonWebServicesClientProxy,
            request: ResourceHandlerRequest<ResourceModel>,
            callbackContext: CallbackContext?,
            logger: Logger): ProgressEvent<ResourceModel, CallbackContext?> {
        val agaClient = client
        val inferredCallbackContext = callbackContext
                ?: CallbackContext(stabilizationRetriesRemaining = HandlerCommons.NUMBER_OF_STATE_POLL_RETRIES,
                        pendingStabilization = false)
        val model = request.desiredResourceState
        val previousModel = request.previousResourceState

        getAccelerator(model.acceleratorArn, proxy, agaClient, logger)
                ?: return ProgressEvent.defaultFailureHandler(
                        Exception(String.format("Failed to find accelerator with arn:[%s]", model.acceleratorArn)),
                        HandlerErrorCode.NotFound
                )

        if (byoipIPsUpdated(model, previousModel)) {
            logger.logError("Failed attempt to update BYOIP IPs.")
            return ProgressEvent.defaultFailureHandler(
                    // Why BYOIP updates is not supported today:-
                    // Fact 1. IP address cannot be shared between 2 accelerators.
                    // Fact 2. At present, global accelerator APIs don't support BYOIP IP updates.
                    // So, one way to support BYOIP IPs is to add IpAddresses as CreateOnly property, that will create
                    // new accelerator and then delete old. Corner case is, if customer updates one of the IP address
                    // out of the 2 BYOIPs then it will lead to 2 accelerators with same IPs that's not permitted.
                    Exception("Updates for BYOIP IP addresses is not a supported operation. Delete existing accelerator and create new accelerator with updated IPs."),
                    HandlerErrorCode.InvalidRequest)
        }

        val isUpdateStarted: Boolean = inferredCallbackContext.pendingStabilization
        return if (!isUpdateStarted) {
            validateAndUpdateAccelerator(model, previousModel, proxy, agaClient, logger)
        } else {
            waitForSynchronizedStep(inferredCallbackContext, model, proxy, agaClient, logger)
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
                                             logger: Logger): ProgressEvent<ResourceModel, CallbackContext?>{

        logger.logDebug("Desired updated state model $model")

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

    private fun validateTags(model: ResourceModel) : Boolean {
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
                                  logger: Logger) : Accelerator {

        logger.logDebug("Updating accelerator with arn" + model.acceleratorArn)

        val request = UpdateAcceleratorRequest()
                .withAcceleratorArn(model.acceleratorArn)
                .withEnabled(model.enabled)
                .withIpAddressType(model.ipAddressType)
                .withName(model.name)

        return proxy.injectCredentialsAndInvoke(request, { updateAcceleratorRequest: UpdateAcceleratorRequest? -> agaClient.updateAccelerator(updateAcceleratorRequest) }).accelerator
    }

    private fun updateTags( accelerator: Accelerator,
                            model: ResourceModel,
                            previousModel: ResourceModel,
                            proxy: AmazonWebServicesClientProxy,
                            agaClient: AWSGlobalAccelerator,
                            logger: Logger) {

        logger.logDebug("Updating tags for accelerator with arn: "+ model.acceleratorArn)

        val previousStateTags = getPreviousStateTags(previousModel)
        val newTags = model.tags?.map{ Tag().withKey(it.key).withValue(it.value)}
        deleteOldMissingTags(previousStateTags, newTags, accelerator, proxy, agaClient, logger)
        updateOrAddTags(newTags, accelerator, proxy, agaClient, logger)
    }

    /**
     * Untag (remove tags) that were present in previous version of template but are missing in new CFN template
     */
    private fun deleteOldMissingTags(previousTags : List<Tag>?,
                                     newTags : List<Tag>?,
                                     accelerator: Accelerator ,
                                     proxy: AmazonWebServicesClientProxy,
                                     agaClient: AWSGlobalAccelerator,
                                     logger: Logger) {

        logger.logDebug("Looking for tags to be deleted")

        var newTagsMap = if(newTags == null) mapOf<String , String>()  else  newTags?.map { it.key to it.value }?.toMap()
        val keysToDelete = previousTags?.map { x -> x.key  }?.minus(newTagsMap.keys)

        if (!keysToDelete.isNullOrEmpty()) {
            logger.logDebug("Untagging tags: [$keysToDelete.toString()] for accelerator [${accelerator.acceleratorArn}]")
            val untagRequest = UntagResourceRequest()
                    .withResourceArn(accelerator.acceleratorArn)
                    .withTagKeys(keysToDelete)

            proxy.injectCredentialsAndInvoke(untagRequest, { untagResourceRequest: UntagResourceRequest? ->
                agaClient.untagResource(untagResourceRequest) })
        }
    }

    private fun updateOrAddTags(newTags : List<Tag>?,
                                accelerator: Accelerator ,
                                proxy: AmazonWebServicesClientProxy,
                                agaClient: AWSGlobalAccelerator,
                                logger: Logger) {
        if (newTags.isNullOrEmpty()) {
            logger.logDebug("No updates or new addition of tags.")
        } else {
            val tagRequest = TagResourceRequest()
                    .withResourceArn(accelerator.acceleratorArn)
                    .withTags(newTags)
            proxy.injectCredentialsAndInvoke(tagRequest, { tagRequest: TagResourceRequest? -> agaClient.tagResource(tagRequest) })
        }
    }

    private fun getPreviousStateTags(previousModel: ResourceModel) :
            List<Tag>? {
        return previousModel.tags?.map{ Tag().withKey(it.key).withValue(it.value)}
    }

    private fun byoipIPsUpdated(currentModel: ResourceModel, previousModel: ResourceModel) : Boolean {
        return currentModel.ipAddresses != previousModel.ipAddresses
    }

}
