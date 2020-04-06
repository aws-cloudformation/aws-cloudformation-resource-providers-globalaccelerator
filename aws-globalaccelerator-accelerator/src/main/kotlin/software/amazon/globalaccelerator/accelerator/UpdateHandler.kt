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

        logger.log("[DEBUG] Desired updated state model $model")

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
            if ((!regex.matches(it.key)) or (!regex.matches(it.value))) {
                return false
            }
        }
        return true
    }

    private fun updateAccelerator(model: ResourceModel,
                                  proxy: AmazonWebServicesClientProxy,
                                  agaClient: AWSGlobalAccelerator,
                                  logger: Logger) : Accelerator {

        logger.log(String.format("[DEBUG] Updating accelerator with arn: [%s]", model.acceleratorArn))

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

        logger.log(String.format("[DEBUG] Updating tags for accelerator with arn: [%s]", model.acceleratorArn))

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

        logger.log("[DEBUG] Looking for tags to be deleted")

        var newTagsMap = mapOf<String , String>()
        if (newTags != null) {
            newTagsMap = newTags?.map { it.key to it.value }?.toMap()
        }

        previousTags?.forEach{
            if (! newTagsMap?.containsKey(it.key)) {
                logger.log(String.format("[DEBUG] Untagging tag: [%s] for accelerator [%s]", it.key, accelerator.acceleratorArn))
                val untagRequest = UntagResourceRequest()
                        .withResourceArn(accelerator.acceleratorArn)
                        .withTagKeys(it.key)

                proxy.injectCredentialsAndInvoke(untagRequest, { untagResourceRequest: UntagResourceRequest? ->
                    agaClient.untagResource(untagResourceRequest) })
            }
        }
    }

    private fun updateOrAddTags(newTags : List<Tag>?,
                                accelerator: Accelerator ,
                                proxy: AmazonWebServicesClientProxy,
                                agaClient: AWSGlobalAccelerator,
                                logger: Logger) {
        if (newTags.isNullOrEmpty()) {
            logger.log("[DEBUG] No updates or new addition of tags.")
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
}
