package software.amazon.globalaccelerator.accelerator

import com.amazonaws.services.globalaccelerator.AWSGlobalAccelerator
import com.amazonaws.services.globalaccelerator.model.Accelerator
import com.amazonaws.services.globalaccelerator.model.AcceleratorStatus
import com.amazonaws.services.globalaccelerator.model.DeleteAcceleratorRequest
import com.amazonaws.services.globalaccelerator.model.UpdateAcceleratorRequest
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy
import software.amazon.cloudformation.proxy.Logger
import software.amazon.cloudformation.proxy.ProgressEvent
import software.amazon.cloudformation.proxy.ResourceHandlerRequest
import software.amazon.globalaccelerator.accelerator.AcceleratorClientBuilder.client

class DeleteHandler : BaseHandler<CallbackContext?>() {
    override fun handleRequest(
            proxy: AmazonWebServicesClientProxy,
            request: ResourceHandlerRequest<ResourceModel>,
            callbackContext: CallbackContext?,
            logger: Logger): ProgressEvent<ResourceModel, CallbackContext?> {
        logger.log(java.lang.String.format("DELETE REQUEST: [%s]", request))
        val agaClient = client
        val inferredCallbackContext = callbackContext
                ?: CallbackContext(HandlerCommons.NUMBER_OF_STATE_POLL_RETRIES)
        val model = request.desiredResourceState
        val foundAccelerator = HandlerCommons.getAccelerator(model.acceleratorArn, proxy, agaClient, logger)
        if (foundAccelerator == null) {
            return ProgressEvent.defaultSuccessHandler(model)
        } else if (foundAccelerator.enabled) {
            disableAccelerator(foundAccelerator.acceleratorArn, proxy, agaClient, logger)
        } else if (foundAccelerator.status == AcceleratorStatus.DEPLOYED.toString()) {
            deleteAccelerator(foundAccelerator.acceleratorArn, proxy, agaClient, logger)
        }
        return waitForDeletedStep(inferredCallbackContext, model, proxy, agaClient, logger)
    }

    companion object {
        /**
         * Check to see if accelerator creation is complete and create the correct progress continuation context
         */
        private fun waitForDeletedStep(context: CallbackContext,
                                       model: ResourceModel,
                                       proxy: AmazonWebServicesClientProxy,
                                       agaClient: AWSGlobalAccelerator,
                                       logger: Logger): ProgressEvent<ResourceModel, CallbackContext?> {
            logger.log("Waiting for accelerator with arn [${model.acceleratorArn}] to be deleted")

            // check to see if we have exceeded what we are allowed to do
            val newCallbackContext = CallbackContext(context.stabilizationRetriesRemaining - 1)
            if (newCallbackContext.stabilizationRetriesRemaining < 0) {
                throw RuntimeException(HandlerCommons.TIMED_OUT_MESSAGE)
            }
            val accelerator = HandlerCommons.getAccelerator(model.acceleratorArn, proxy, agaClient, logger)
            return if (accelerator == null) {
                ProgressEvent.defaultSuccessHandler(model)
            } else {
                ProgressEvent.defaultInProgressHandler(newCallbackContext, HandlerCommons.CALLBACK_DELAY_IN_SECONDS, model)
            }
        }

        private fun disableAccelerator(arn: String,
                                       proxy: AmazonWebServicesClientProxy,
                                       agaClient: AWSGlobalAccelerator,
                                       logger: Logger): Accelerator {
            logger.log("Disabling accelerator with arn [$arn]")
            val request = UpdateAcceleratorRequest().withAcceleratorArn(arn).withEnabled(false)
            return proxy.injectCredentialsAndInvoke(request, agaClient::updateAccelerator).accelerator
        }

        private fun deleteAccelerator(arn: String,
                                      proxy: AmazonWebServicesClientProxy,
                                      agaClient: AWSGlobalAccelerator,
                                      logger: Logger) {
            logger.log("Deleting accelerator with arn [$arn]")
            val request = DeleteAcceleratorRequest().withAcceleratorArn(arn)
            proxy.injectCredentialsAndInvoke(request, agaClient::deleteAccelerator)
        }
    }
}
