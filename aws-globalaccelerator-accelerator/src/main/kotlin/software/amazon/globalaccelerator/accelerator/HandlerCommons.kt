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

/**
 * Singleton class for common methods used by CRUD handlers
 */
object HandlerCommons {
    const val CALLBACK_DELAY_IN_SECONDS = 1
    const val NUMBER_OF_STATE_POLL_RETRIES = 60 / CALLBACK_DELAY_IN_SECONDS * 60 * 4 // 4 hours
    const val TIMED_OUT_MESSAGE = "Timed out waiting for global accelerator to be deployed."

    /**
     * Wait for accelerator to go in-sync (DEPLOYED)
     */
    fun waitForSynchronizedStep(context: CallbackContext, model: ResourceModel, proxy: AmazonWebServicesClientProxy, agaClient: AWSGlobalAccelerator, logger: Logger):
            ProgressEvent<ResourceModel, CallbackContext?> {

        logger.debug("Waiting for accelerator to be deployed with arn: [${model.acceleratorArn}]. " +
                "Stabilization retries remaining ${context.stabilizationRetriesRemaining}")

        val newCallbackContext = context.copy(stabilizationRetriesRemaining = context.stabilizationRetriesRemaining - 1)
        if (newCallbackContext.stabilizationRetriesRemaining < 0) {
            throw RuntimeException(TIMED_OUT_MESSAGE)
        }

        val accelerator = getAccelerator(model.acceleratorArn, proxy, agaClient, logger)
        return if (accelerator!!.status == AcceleratorStatus.DEPLOYED.toString()) {
            logger.debug("Accelerator with arn: [${accelerator.acceleratorArn}] is deployed.")
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
        return try {
            val request = DescribeAcceleratorRequest().withAcceleratorArn(arn)
            proxy.injectCredentialsAndInvoke(request, agaClient::describeAccelerator).accelerator
        } catch (ex: AcceleratorNotFoundException) {
            logger.debug("Accelerator with arn: [$arn] not found.")
            null
        }
    }

    /**
     * Get tags associated with resource
     * @param arn ARN of the resource (accelerator)
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

    fun toResourceModel(accelerator: Accelerator, tags: List<Tag>): ResourceModel {
        return ResourceModel().apply {
            this.acceleratorArn = accelerator.acceleratorArn
            this.name = accelerator.name
            this.enabled = accelerator.enabled
            this.ipAddressType = accelerator.ipAddressType
            this.dnsName = accelerator.dnsName
            this.ipAddresses = accelerator.ipSets?.flatMap { it.ipAddresses }
            this.tags = tags.map { Tag(it.key, it.value) }
            this.ipv4Addresses = accelerator.ipSets?.filter { it.ipFamily.equals("IPV4", ignoreCase = true) }?.flatMap { it.ipAddresses }
        }
    }
}
