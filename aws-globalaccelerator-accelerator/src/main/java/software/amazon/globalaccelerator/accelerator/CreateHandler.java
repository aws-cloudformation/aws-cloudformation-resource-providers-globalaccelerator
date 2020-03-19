package software.amazon.globalaccelerator.accelerator;

import com.amazonaws.services.globalaccelerator.AWSGlobalAccelerator;
import com.amazonaws.services.globalaccelerator.model.Accelerator;
import com.amazonaws.services.globalaccelerator.model.CreateAcceleratorRequest;
import com.amazonaws.services.globalaccelerator.model.DescribeAcceleratorRequest;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

public class CreateHandler extends BaseHandler<CallbackContext> {
    private AWSGlobalAccelerator agaClient;
    private AmazonWebServicesClientProxy clientProxy;

    // Number of poll retries 120 tries each 1s
    private static final int NUMBER_OF_STATE_POLL_RETRIES = 120;
    private static final int POLL_RETRY_DELAY_IN_MS = 1000;

    private static final String GLOBALACCELERATOR_DEPLOYED_STATE = "DEPLOYED";
    private static final String TIMED_OUT_MESSAGE = "Timed out waiting for global accelerator to be deployed.";

    @Override
    public ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy proxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final Logger logger) {

	    final ResourceModel model = request.getDesiredResourceState();
            clientProxy = proxy;

            logger.log(String.format("Create accelerator handler is called with [%s] desired state", model));

            final CallbackContext currentContext = callbackContext == null ?
                CallbackContext.builder().stabilizationRetriesRemaining(NUMBER_OF_STATE_POLL_RETRIES).build() :
                callbackContext;

            agaClient = AcceleratorClientBuilder.getClient();
            return createAcceleratorAndUpdateProgress(model, logger, currentContext);
        }

        private ProgressEvent<ResourceModel, CallbackContext> createAcceleratorAndUpdateProgress(ResourceModel model, final Logger logger,
                                                                                                  final CallbackContext currentContext) {
            if (currentContext.getStabilizationRetriesRemaining() == 0) {
                throw new RuntimeException(TIMED_OUT_MESSAGE);
            }

            final Accelerator existingAccelerator = currentContext.getAccelerator();

            if (existingAccelerator == null) {
                logger.log("Creating new accelerator.");
                Accelerator acc = createAccelerator(model);
                CallbackContext callbackContext = CallbackContext.builder()
                        .accelerator(acc)
                        .stabilizationRetriesRemaining(NUMBER_OF_STATE_POLL_RETRIES)
                        .build();
                return ProgressEvent.<ResourceModel, CallbackContext>builder()
                        .resourceModel(model)
                        .errorCode(HandlerErrorCode.NotStabilized)
                        .status(OperationStatus.IN_PROGRESS)
                        .callbackContext(callbackContext).build();
            } else if (getAcceleratorState(existingAccelerator).equals(GLOBALACCELERATOR_DEPLOYED_STATE)) {
                logger.log("Accelerator is in deployed state.");
                model.setName(existingAccelerator.getName());
                model.setAcceleratorArn(existingAccelerator.getAcceleratorArn());
                return ProgressEvent.<ResourceModel, CallbackContext>builder()
                        .resourceModel(model)
                        .status(OperationStatus.SUCCESS)
                        .build();
            } else {
                logger.log("Accelerator is still deploying.");
                try {
                    Thread.sleep(POLL_RETRY_DELAY_IN_MS);
                } catch (InterruptedException ex) {
                    throw new RuntimeException(ex);
                }
                return ProgressEvent.<ResourceModel, CallbackContext>builder()
                        .resourceModel(model)
                        .status(OperationStatus.IN_PROGRESS)
                        .build();
            }
        }

        private Accelerator createAccelerator(ResourceModel model) {
            final CreateAcceleratorRequest createAcceleratorRequest =
                    new CreateAcceleratorRequest()
                            .withName(model.getName())
                            .withIdempotencyToken(model.getName()); //Pending design discussion.
            return clientProxy.injectCredentialsAndInvoke(createAcceleratorRequest, agaClient::createAccelerator)
                    .getAccelerator();
        }

        private String getAcceleratorState(Accelerator existingAccelerator) {
            final DescribeAcceleratorRequest describeAcceleratorRequest =
                    new DescribeAcceleratorRequest().withAcceleratorArn(existingAccelerator.getAcceleratorArn());
            String status = clientProxy.injectCredentialsAndInvoke(describeAcceleratorRequest, agaClient::describeAccelerator).
                    getAccelerator().getStatus();
            return status;
        }
}
