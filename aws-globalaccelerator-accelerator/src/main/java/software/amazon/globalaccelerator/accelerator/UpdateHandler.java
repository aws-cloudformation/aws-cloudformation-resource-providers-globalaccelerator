package software.amazon.globalaccelerator.accelerator;

import com.amazonaws.services.globalaccelerator.AWSGlobalAccelerator;
import com.amazonaws.services.globalaccelerator.model.UpdateAcceleratorRequest;
import lombok.val;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

public class UpdateHandler extends BaseHandler<CallbackContext> {

    @Override
    public ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy proxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final Logger logger) {

        val agaClient = AcceleratorClientBuilder.getClient();
        val inferredCallbackContext = callbackContext != null ?
                callbackContext :
                CallbackContext.builder()
                        .stabilizationRetriesRemaining(HandlerCommons.NUMBER_OF_STATE_POLL_RETRIES)
                        .build();

        final ResourceModel model = request.getDesiredResourceState();

        val foundAccelerator = HandlerCommons.getAccelerator(model.getAcceleratorArn(), proxy, agaClient, logger);
        if (foundAccelerator == null){
            return ProgressEvent.defaultFailureHandler(
                    new Exception(String.format("Failed to find accelerator with arn:[%s]", model.getAcceleratorArn())),
                    HandlerErrorCode.NotFound
            );
        }

        // a. Check if update is started.
        // b. If started, then poll on accelerator to go in sync
        // c. else, trigger an update operation
        final boolean isUpdateStarted = inferredCallbackContext.isPendingStabilization();

        if (!isUpdateStarted) {
            return updateAccelerator(model, proxy, agaClient, logger);
        }  else {
            return HandlerCommons.waitForSynchronizedStep(inferredCallbackContext, model, proxy, agaClient, logger);
        }
    }

    private ProgressEvent<ResourceModel, CallbackContext>  updateAccelerator(final ResourceModel model,
                                                                             final AmazonWebServicesClientProxy proxy,
                                                                             final AWSGlobalAccelerator agaClient,
                                                                             final Logger logger) {
        logger.log(String.format("Updating accelerator with arn: [%s]", model.getAcceleratorArn()));
        val request = new UpdateAcceleratorRequest()
                .withAcceleratorArn(model.getAcceleratorArn())
                .withEnabled(model.getEnabled())
                .withIpAddressType(model.getIpAddressType())
                .withName(model.getName());
        proxy.injectCredentialsAndInvoke(request, agaClient::updateAccelerator).getAccelerator();
        val callbackContext = CallbackContext.builder()
                .stabilizationRetriesRemaining(HandlerCommons.NUMBER_OF_STATE_POLL_RETRIES)
                .pendingStabilization(true)
                .build();
        return ProgressEvent.defaultInProgressHandler(callbackContext, 0, model);
    }
}
