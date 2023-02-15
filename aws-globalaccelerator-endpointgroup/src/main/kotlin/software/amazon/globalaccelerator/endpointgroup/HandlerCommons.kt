package software.amazon.globalaccelerator.endpointgroup

import com.amazonaws.services.globalaccelerator.AWSGlobalAccelerator
import com.amazonaws.services.globalaccelerator.model.Accelerator
import com.amazonaws.services.globalaccelerator.model.AcceleratorNotFoundException
import com.amazonaws.services.globalaccelerator.model.AcceleratorStatus
import com.amazonaws.services.globalaccelerator.model.DescribeAcceleratorRequest
import com.amazonaws.services.globalaccelerator.model.DescribeEndpointGroupRequest
import com.amazonaws.services.globalaccelerator.model.DescribeListenerRequest
import com.amazonaws.services.globalaccelerator.model.EndpointGroup
import com.amazonaws.services.globalaccelerator.model.EndpointGroupNotFoundException
import com.amazonaws.services.globalaccelerator.model.Listener
import com.amazonaws.services.globalaccelerator.model.ListenerNotFoundException
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy
import software.amazon.cloudformation.proxy.Logger
import software.amazon.cloudformation.proxy.ProgressEvent
import software.amazon.globalaccelerator.arns.ListenerArn

/**
 * Singleton class for common methods used by CRUD handlers
 */
object HandlerCommons {
    private const val CALLBACK_DELAY_IN_SECONDS = 1
    const val NUMBER_OF_STATE_POLL_RETRIES = 60 / CALLBACK_DELAY_IN_SECONDS * 60 * 4 // 4 hours
    private const val TIMED_OUT_MESSAGE = "Timed out waiting for endpoint group to be deployed."

    /**
     * Wait for accelerator to go in-sync (DEPLOYED)
     */
    fun waitForSynchronizedStep(context: CallbackContext,
                                model: ResourceModel,
                                proxy: AmazonWebServicesClientProxy,
                                agaClient: AWSGlobalAccelerator,
                                logger: Logger,
                                isDelete: Boolean = false): ProgressEvent<ResourceModel, CallbackContext?> {

        val acceleratorArn = ListenerArn(model.listenerArn).acceleratorArn
        logger.debug("Waiting for accelerator with arn: [$acceleratorArn] to be deployed. " +
                "Stabilization retries remaining ${context.stabilizationRetriesRemaining}")

        val newCallbackContext = context.copy(stabilizationRetriesRemaining = context.stabilizationRetriesRemaining - 1)
        if (newCallbackContext.stabilizationRetriesRemaining < 0) {
            throw RuntimeException(TIMED_OUT_MESSAGE)
        }

        val accelerator = getAccelerator(acceleratorArn, proxy, agaClient, logger)

        // Addresses race condition: accelerator associated with endpointgroup is deleted out-of-band.
        // Sequence diagram :: Delete EndpointGroup -> (accelerator deleted) -> waiting for accelerator to go-in-sync
        // Ignore AcceleratorNotFoundException exception.
        if (accelerator == null && isDelete) {
            return ProgressEvent.defaultSuccessHandler(null)
        }

        return if (accelerator!!.status == AcceleratorStatus.DEPLOYED.toString()) {
            // Delete contract expects no model to be returned upon delete success
            var resourceModel: ResourceModel? = model
            if (isDelete) {
                resourceModel = null
            }
            ProgressEvent.defaultSuccessHandler(resourceModel)
        } else {
            ProgressEvent.defaultInProgressHandler(newCallbackContext, CALLBACK_DELAY_IN_SECONDS, model)
        }
    }

    /**
     * Get the accelerator with the specified ARN.
     * @param arn ARN of the accelerator
     * @return NULL if the accelerator does not exist
     */
    fun getAccelerator(arn: String, proxy: AmazonWebServicesClientProxy,
                       agaClient: AWSGlobalAccelerator, logger: Logger): Accelerator? {
        return try {
            val request = DescribeAcceleratorRequest().withAcceleratorArn(arn)
            proxy.injectCredentialsAndInvoke(request, agaClient::describeAccelerator).accelerator
        } catch (ex: AcceleratorNotFoundException) {
            logger.error("Did not find accelerator with arn: [$arn].")
            null
        }
    }

    /** Gets the listener with the specified ARN
     * @param arn ARN of the listener
     * @return NULL if listener does not exist
     */
    fun getListener(arn: String, proxy: AmazonWebServicesClientProxy,
                    agaClient: AWSGlobalAccelerator, logger: Logger): Listener? {
        return try {
            val request = DescribeListenerRequest().withListenerArn(arn)
            proxy.injectCredentialsAndInvoke(request, agaClient::describeListener).listener
        } catch (ex: ListenerNotFoundException) {
            logger.error("Did not find listener with arn: [$arn].")
            null
        }
    }

    /** Gets the Endpoint Group with the specified ARN
     * @param arn ARN of the Endpoint Group
     * @return NULL if listener does not exist
     */
    fun getEndpointGroup(arn: String, proxy: AmazonWebServicesClientProxy,
                         agaClient: AWSGlobalAccelerator, logger: Logger): EndpointGroup? {
        return try {
            val request = DescribeEndpointGroupRequest().withEndpointGroupArn(arn)
            proxy.injectCredentialsAndInvoke(request, agaClient::describeEndpointGroup).endpointGroup
        } catch (ex: EndpointGroupNotFoundException) { // Should we be throwing this instead?
            logger.debug("Did not find endpoint group with arn: [$arn].")
            null
        }
    }

    /** Gets the Listener arn by parsing the endpointGroupArn
     * @param endpointGroupArn Arn of the Endpoint Group
     * @return The Listener arn
     */
    fun getListenerArnFromEndpointGroupArn(endpointGroupArn: String) : String {
        val ENDPOINTGROUP_SEPARATOR = "/endpoint-group/"
        return endpointGroupArn.split(ENDPOINTGROUP_SEPARATOR)[0]
    }
}
