package software.amazon.globalaccelerator.accelerator

import com.amazonaws.services.globalaccelerator.AWSGlobalAccelerator
import com.amazonaws.services.globalaccelerator.model.Accelerator
import com.amazonaws.services.globalaccelerator.model.AcceleratorNotFoundException
import com.amazonaws.services.globalaccelerator.model.AcceleratorStatus
import com.amazonaws.services.globalaccelerator.model.DescribeAcceleratorRequest
import com.amazonaws.services.globalaccelerator.model.ListTagsForResourceRequest
import com.amazonaws.services.globalaccelerator.model.Tag
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy
import software.amazon.cloudformation.proxy.Logger
import software.amazon.cloudformation.proxy.ProgressEvent

object HandlerCommons {
    const val CALLBACK_DELAY_IN_SECONDS = 1
    const val NUMBER_OF_STATE_POLL_RETRIES = 60 / CALLBACK_DELAY_IN_SECONDS * 60 * 4 // 4 hours
    const val TIMED_OUT_MESSAGE = "Timed out waiting for global accelerator to be deployed."

    /**
     * Check to see if accelerator creation is complete and create the correct progress continuation context
     */
    fun waitForSynchronizedStep(context: CallbackContext,
                                model: ResourceModel,
                                proxy: AmazonWebServicesClientProxy,
                                agaClient: AWSGlobalAccelerator,
                                logger: Logger): ProgressEvent<ResourceModel, CallbackContext?> {
        logger.debug("Waiting for accelerator with arn ${model.acceleratorArn} to synchronize")
        logger.debug("Stabilization retries remaining ${context.stabilizationRetriesRemaining}")

        // check to see if we have exceeded what we are allowed to do
        val newCallbackContext = context.copy(stabilizationRetriesRemaining =  context.stabilizationRetriesRemaining - 1)

        if (newCallbackContext.stabilizationRetriesRemaining < 0) {
            throw RuntimeException(TIMED_OUT_MESSAGE)
        }
        val accelerator = getAccelerator(model.acceleratorArn, proxy, agaClient, logger)
        return if (accelerator!!.status == AcceleratorStatus.DEPLOYED.toString()) {
            logger.debug("Accelerator with arn ${accelerator.acceleratorArn} is DEPLOYED")
            ProgressEvent.defaultSuccessHandler(model)
        } else {
            ProgressEvent.defaultInProgressHandler(newCallbackContext, CALLBACK_DELAY_IN_SECONDS, model)
        }
    }

    /**
     * Get the accelerator with the specified ARN.
     * @param arn ARN of the accelerator
     * @return NULL if the accelerator does not exist
     */
    fun getAccelerator(arn: String?, proxy: AmazonWebServicesClientProxy,
                       agaClient: AWSGlobalAccelerator, logger: Logger): Accelerator? {
        var accelerator: Accelerator? = null
        try {
            val request = DescribeAcceleratorRequest().withAcceleratorArn(arn)
            accelerator = proxy.injectCredentialsAndInvoke(request, { describeAcceleratorRequest: DescribeAcceleratorRequest? -> agaClient.describeAccelerator(describeAcceleratorRequest) }).accelerator
        } catch (ex: AcceleratorNotFoundException) {
            logger.error("Did not find accelerator with arn $arn")
        }
        return accelerator
    }

    fun getTags(arn: String?, proxy: AmazonWebServicesClientProxy,
                       agaClient: AWSGlobalAccelerator, logger: Logger): List<Tag> {
        var tags: List<Tag> = emptyList()
        try {
            val request = ListTagsForResourceRequest()
            tags = proxy.injectCredentialsAndInvoke(request, { listTagsForResourceRequest : ListTagsForResourceRequest? -> agaClient.listTagsForResource(listTagsForResourceRequest) }).tags
        } catch (ex: Exception) {
            logger.error("Exception while getting tags for accelerator with arn $arn")
        }
        return tags
    }
}
