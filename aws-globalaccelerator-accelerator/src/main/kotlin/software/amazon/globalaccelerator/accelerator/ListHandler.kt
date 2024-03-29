package software.amazon.globalaccelerator.accelerator

import com.amazonaws.services.globalaccelerator.model.Accelerator
import com.amazonaws.services.globalaccelerator.model.ListAcceleratorsRequest
import com.amazonaws.services.globalaccelerator.model.ListAcceleratorsResult
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy
import software.amazon.cloudformation.proxy.HandlerErrorCode
import software.amazon.cloudformation.proxy.Logger
import software.amazon.cloudformation.proxy.OperationStatus
import software.amazon.cloudformation.proxy.ProgressEvent
import software.amazon.cloudformation.proxy.ResourceHandlerRequest
import software.amazon.globalaccelerator.accelerator.AcceleratorClientBuilder.client
import software.amazon.globalaccelerator.accelerator.HandlerCommons.toResourceModel

/**
 * List handler implementation for accelerator resource.
 */
class ListHandler : BaseHandler<CallbackContext?>() {
    override fun handleRequest(proxy: AmazonWebServicesClientProxy,
                               request: ResourceHandlerRequest<ResourceModel>,
                               callbackContext: CallbackContext?,
                               logger: Logger): ProgressEvent<ResourceModel, CallbackContext?> {
        logger.debug("List Accelerators Request: $request")
        val agaClient = client
        val listAcceleratorsRequest = ListAcceleratorsRequest().withNextToken(request.nextToken)
        val listAcceleratorsResult = proxy.injectCredentialsAndInvoke(listAcceleratorsRequest, agaClient::listAccelerators)
        val acceleratorList = convertAcceleratorList(listAcceleratorsResult.accelerators)
        return ProgressEvent.builder<ResourceModel, CallbackContext>()
                .status(OperationStatus.SUCCESS)
                .resourceModels(acceleratorList)
                .nextToken(listAcceleratorsResult.nextToken)
                .build()
    }

    fun convertAcceleratorList(accelerators: List<Accelerator>): List<ResourceModel> {
        return accelerators.map { toResourceModel(it, emptyList()) }
    }
}
