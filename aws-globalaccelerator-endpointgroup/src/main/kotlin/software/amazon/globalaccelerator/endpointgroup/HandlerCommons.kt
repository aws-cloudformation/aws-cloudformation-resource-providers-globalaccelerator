package software.amazon.globalaccelerator.endpointgroup

import com.amazonaws.services.globalaccelerator.AWSGlobalAccelerator
import com.amazonaws.services.globalaccelerator.model.*
import lombok.`val`
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy
import software.amazon.cloudformation.proxy.Logger
import software.amazon.cloudformation.proxy.ProgressEvent
import software.amazon.globalaccelerator.arns.ListenerArn

object HandlerCommons {
    val CALLBACK_DELAY_IN_SECONDS = 1
    val NUMBER_OF_STATE_POLL_RETRIES = 60 / CALLBACK_DELAY_IN_SECONDS * 60 * 4 // 4 hours
    private val TIMED_OUT_MESSAGE = "Timed out waiting for endpoint group to be deployed."

    /**
     * Check to see if accelerator creation is complete and create the correct progress continuation context
     */
    fun waitForSynchronizedStep(context: CallbackContext,
                                model: ResourceModel,
                                proxy: AmazonWebServicesClientProxy,
                                agaClient: AWSGlobalAccelerator,
                                logger: Logger): ProgressEvent<ResourceModel, CallbackContext?> {
        val acceleratorArn = ListenerArn(model.getListenerArn()).acceleratorArn
        logger.log(String.format("Waiting for accelerator with arn [%s] to synchronize", acceleratorArn))

        // check to see if we have exceeded what we are allowed to do
        val newCallbackContext = CallbackContext(stabilizationRetriesRemaining = context.stabilizationRetriesRemaining - 1)

        if (newCallbackContext.stabilizationRetriesRemaining < 0) {
            throw RuntimeException(TIMED_OUT_MESSAGE)
        }

        val accelerator = getAccelerator(acceleratorArn, proxy, agaClient, logger)
        return if (accelerator!!.getStatus() == AcceleratorStatus.DEPLOYED.toString()) {
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
    fun getAccelerator(arn: String, proxy: AmazonWebServicesClientProxy,
                       agaClient: AWSGlobalAccelerator, logger: Logger): Accelerator? {
        var accelerator: Accelerator? = null
        try {
            val request = DescribeAcceleratorRequest().withAcceleratorArn(arn)
            accelerator = proxy.injectCredentialsAndInvoke(request, agaClient::describeAccelerator).accelerator
        } catch (ex: AcceleratorNotFoundException) {
            logger.log(String.format("Did not find accelerator with arn [%s]", arn))
        }

        return accelerator
    }

    /** Gets the listener with the specified ARN  */
    fun getListener(listenerArn: String, proxy: AmazonWebServicesClientProxy,
                    agaClient: AWSGlobalAccelerator, logger: Logger): Listener? {
        val listener = try {
            val request = DescribeListenerRequest().withListenerArn(listenerArn)
            proxy.injectCredentialsAndInvoke(request, agaClient::describeListener).listener
        } catch (ex: ListenerNotFoundException) {
            logger.log(String.format("Did not find listener with arn [%s]", listenerArn))
            null
        }

        return listener
    }

    fun getEndpointGroup(endpointGroupArn: String, proxy: AmazonWebServicesClientProxy,
                         agaClient: AWSGlobalAccelerator, logger: Logger): EndpointGroup? {
        var endpointGroup = try {
            val request = DescribeEndpointGroupRequest().withEndpointGroupArn(endpointGroupArn)
            proxy.injectCredentialsAndInvoke(request, agaClient::describeEndpointGroup).endpointGroup
        } catch (eex: EndpointGroupNotFoundException) {
            logger.log(String.format("Did not find endpoint group with arn [%s]", endpointGroupArn))
            null
        }

        return endpointGroup
    }
}
