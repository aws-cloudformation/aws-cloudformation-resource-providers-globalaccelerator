package software.amazon.globalaccelerator.accelerator

import com.amazonaws.services.globalaccelerator.AWSGlobalAccelerator
import com.amazonaws.services.globalaccelerator.model.Accelerator
import com.amazonaws.services.globalaccelerator.model.CreateAcceleratorRequest
import com.amazonaws.services.globalaccelerator.model.Tag
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy
import software.amazon.cloudformation.proxy.Logger
import software.amazon.cloudformation.proxy.ProgressEvent
import software.amazon.cloudformation.proxy.ResourceHandlerRequest
import software.amazon.globalaccelerator.accelerator.AcceleratorClientBuilder.client

class CreateHandler : BaseHandler<CallbackContext?>() {
    override fun handleRequest(
            proxy: AmazonWebServicesClientProxy,
            request: ResourceHandlerRequest<ResourceModel>,
            callbackContext: CallbackContext?,
            logger: Logger
    ): ProgressEvent<ResourceModel, CallbackContext?> {
        val agaClient = client
        val inferredCallbackContext = callbackContext
                ?: CallbackContext(HandlerCommons.NUMBER_OF_STATE_POLL_RETRIES)
        val model = request.desiredResourceState
        return if (model.acceleratorArn == null) {
            createAcceleratorStep(model, request, proxy, agaClient, logger)
        } else {
            HandlerCommons.waitForSynchronizedStep(inferredCallbackContext, model, proxy, agaClient, logger)
        }
    }

    /**
     * Create an accelerator and create the correct progress continuation context
     */
    private fun createAcceleratorStep(model: ResourceModel,
                                      handlerRequest: ResourceHandlerRequest<ResourceModel>,
                                      proxy: AmazonWebServicesClientProxy,
                                      agaClient: AWSGlobalAccelerator,
                                      logger: Logger): ProgressEvent<ResourceModel, CallbackContext?> {
        logger.logDebug("Creating new accelerator with model: " + model.toString())
        val acc = createAccelerator(model, handlerRequest, proxy, agaClient)
        model.acceleratorArn = acc.acceleratorArn
        model.ipAddresses = acc.ipSets?.flatMap { it.ipAddresses }
        val callbackContext: CallbackContext? = CallbackContext(HandlerCommons.NUMBER_OF_STATE_POLL_RETRIES)
        return ProgressEvent.defaultInProgressHandler(callbackContext, 0, model)
    }

    /**
     * Create the accelerator based on the provided ResourceModel
     */
    private fun createAccelerator(model: ResourceModel,
                                  handlerRequest: ResourceHandlerRequest<ResourceModel>,
                                  proxy: AmazonWebServicesClientProxy,
                                  agaClient: AWSGlobalAccelerator): Accelerator {
        val convertedTags = model.tags?.map { Tag().withKey(it.key).withValue(it.value) }
        val request = CreateAcceleratorRequest()
                .withName(model.name)
                .withEnabled(model.enabled)
                .withIpAddresses(model.ipAddresses)
                .withTags(convertedTags)
                .withIdempotencyToken(handlerRequest.logicalResourceIdentifier)
        return proxy.injectCredentialsAndInvoke(request, { createAcceleratorRequest: CreateAcceleratorRequest? -> agaClient.createAccelerator(createAcceleratorRequest) }).accelerator
    }
}
