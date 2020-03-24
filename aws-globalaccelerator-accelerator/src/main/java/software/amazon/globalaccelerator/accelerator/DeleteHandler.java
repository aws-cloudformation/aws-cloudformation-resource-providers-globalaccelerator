package software.amazon.globalaccelerator.accelerator;

import com.amazonaws.services.globalaccelerator.AWSGlobalAccelerator;
import com.amazonaws.services.globalaccelerator.model.*;
import lombok.val;
import software.amazon.cloudformation.proxy.*;

public class DeleteHandler extends BaseHandler<CallbackContext> {
    private static final int CALLBACK_DELAY_IN_SECONDS = 1;
    private static final int NUMBER_OF_STATE_POLL_RETRIES = (60 / CALLBACK_DELAY_IN_SECONDS) * 60 * 4; // 4 hours
    private static final String TIMED_OUT_MESSAGE = "Timed out waiting for global accelerator to be deployed.";

    private AWSGlobalAccelerator agaClient;
    private AmazonWebServicesClientProxy clientProxy;
    private Logger logger;

    @Override
    public ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy proxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final Logger logger) {
        clientProxy = proxy;
        agaClient = AcceleratorClientBuilder.getClient();
        this.logger = logger;

        logger.log(String.format("DELETE REQUEST: [%s]", request));
        val acceleratorArn = request.getDesiredResourceState().getAcceleratorArn();
        val foundAccelerator = getAccelerator(acceleratorArn);

        // check if we have exceeded our permitted stabilization count
        val stabilizationAttemptsRemaining = callbackContext == null ?
                NUMBER_OF_STATE_POLL_RETRIES : callbackContext.getStabilizationRetriesRemaining() - 1;
        if (stabilizationAttemptsRemaining <= 0) {
            throw new RuntimeException(TIMED_OUT_MESSAGE);
        }

        // if we did not find the accelerator and this is the first call in the chain,
        // CFN states we should return NotFound
        if (foundAccelerator == null && callbackContext == null) {
            return ProgressEvent.<ResourceModel, CallbackContext>builder()
                    .status(OperationStatus.FAILED)
                    .errorCode(HandlerErrorCode.NotFound)
                    .build();
        }

        if (foundAccelerator == null) {
            return ProgressEvent.defaultSuccessHandler(request.getDesiredResourceState());
        } else if (foundAccelerator.getEnabled()) {
            disableAccelerator(foundAccelerator.getAcceleratorArn());
        } else if (foundAccelerator.getStatus().equals(AcceleratorStatus.DEPLOYED.toString())) {
            deleteAccelerator(foundAccelerator.getAcceleratorArn());
        }

        val newCallbackContext = CallbackContext.builder()
                .stabilizationRetriesRemaining(stabilizationAttemptsRemaining)
                .build();

        // must still be working on things
        return ProgressEvent.<ResourceModel, CallbackContext>builder()
                .callbackContext(newCallbackContext)
                .resourceModel(request.getDesiredResourceState())
                .callbackDelaySeconds(CALLBACK_DELAY_IN_SECONDS)
                .status(OperationStatus.IN_PROGRESS)
                .errorCode(HandlerErrorCode.NotStabilized)
                .build();
    }

    private Accelerator getAccelerator(String arn) {
        Accelerator accelerator = null;
        try {
            val request = new DescribeAcceleratorRequest().withAcceleratorArn(arn);
            accelerator =  clientProxy.injectCredentialsAndInvoke(request, agaClient::describeAccelerator).getAccelerator();
        }
        catch (AcceleratorNotFoundException ex) {
            logger.log(String.format("Did not find accelerator with arn [%s]", arn));
        }
        return accelerator;
    }

    private Accelerator disableAccelerator(String arn) {
        val request = new UpdateAcceleratorRequest().withAcceleratorArn(arn).withEnabled(false);
        return clientProxy.injectCredentialsAndInvoke(request, agaClient::updateAccelerator).getAccelerator();
    }

    private void deleteAccelerator(String arn) {
        val request = new DeleteAcceleratorRequest().withAcceleratorArn(arn);
        clientProxy.injectCredentialsAndInvoke(request, agaClient::deleteAccelerator);
    }
}
