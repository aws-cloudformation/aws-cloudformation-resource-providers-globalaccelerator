package software.amazon.globalaccelerator.accelerator;

import com.amazonaws.services.globalaccelerator.AWSGlobalAccelerator;
import com.amazonaws.services.globalaccelerator.model.*;
import lombok.val;
import software.amazon.cloudformation.proxy.*;

public class DeleteHandler extends BaseHandler<CallbackContext> {
    @Override
    public ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy proxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final Logger logger) {
        logger.log(String.format("DELETE REQUEST: [%s]", request));

        val agaClient = AcceleratorClientBuilder.getClient();
        val inferredCallbackContext = callbackContext != null ?
                callbackContext :
                CallbackContext.builder()
                        .stabilizationRetriesRemaining(HandlerCommons.NUMBER_OF_STATE_POLL_RETRIES)
                        .build();

        val model = request.getDesiredResourceState();
        val foundAccelerator = HandlerCommons.getAccelerator(model.getAcceleratorArn(), proxy, agaClient, logger);

        if (foundAccelerator == null) {
            return ProgressEvent.defaultSuccessHandler(model);
        } else if (foundAccelerator.getEnabled()) {
            disableAccelerator(foundAccelerator.getAcceleratorArn(), proxy, agaClient, logger);
        } else if (foundAccelerator.getStatus().equals(AcceleratorStatus.DEPLOYED.toString())) {
            deleteAccelerator(foundAccelerator.getAcceleratorArn(), proxy, agaClient, logger);
        }

        return waitForDeletedStep(inferredCallbackContext, model, proxy, agaClient, logger);
    }

    /**
     * Check to see if accelerator creation is complete and create the correct progress continuation context
     */
    private static ProgressEvent<ResourceModel, CallbackContext> waitForDeletedStep(final CallbackContext context,
                                                                                   final ResourceModel model,
                                                                                   final AmazonWebServicesClientProxy proxy,
                                                                                   final AWSGlobalAccelerator agaClient,
                                                                                   final Logger logger) {
        logger.log(String.format("Waiting for accelerator with arn [%s] to be deleted", model.getAcceleratorArn()));

        // check to see if we have exceeded what we are allowed to do
        val newCallbackContext = CallbackContext.builder()
                .stabilizationRetriesRemaining(context.getStabilizationRetriesRemaining()-1)
                .build();

        if (newCallbackContext.getStabilizationRetriesRemaining() < 0) {
            throw new RuntimeException(HandlerCommons.TIMED_OUT_MESSAGE);
        }

        val accelerator = HandlerCommons.getAccelerator(model.getAcceleratorArn(), proxy, agaClient, logger);
        if (accelerator == null) {
            return ProgressEvent.defaultSuccessHandler(model);
        } else {
            return ProgressEvent.defaultInProgressHandler(newCallbackContext, HandlerCommons.CALLBACK_DELAY_IN_SECONDS, model);
        }
    }

    private static Accelerator disableAccelerator(final String arn,
                                           final AmazonWebServicesClientProxy proxy,
                                           final AWSGlobalAccelerator agaClient,
                                           final Logger logger) {
        val request = new UpdateAcceleratorRequest().withAcceleratorArn(arn).withEnabled(false);
        return proxy.injectCredentialsAndInvoke(request, agaClient::updateAccelerator).getAccelerator();
    }

    private static void deleteAccelerator(final String arn,
                                   final AmazonWebServicesClientProxy proxy,
                                   final AWSGlobalAccelerator agaClient,
                                   final Logger logger) {
        val request = new DeleteAcceleratorRequest().withAcceleratorArn(arn);
        proxy.injectCredentialsAndInvoke(request, agaClient::deleteAccelerator);
    }
}
