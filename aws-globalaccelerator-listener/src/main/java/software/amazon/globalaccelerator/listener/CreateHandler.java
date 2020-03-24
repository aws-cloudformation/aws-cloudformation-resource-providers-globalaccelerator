package software.amazon.globalaccelerator.listener;

import com.amazonaws.services.globalaccelerator.AWSGlobalAccelerator;
import com.amazonaws.services.globalaccelerator.model.*;
import com.amazonaws.services.globalaccelerator.model.PortRange;
import lombok.val;
import software.amazon.cloudformation.proxy.*;

import java.util.stream.Collectors;

public class CreateHandler extends BaseHandler<CallbackContext> {
    private static final int CALLBACK_DELAY_IN_SECONDS = 1;
    private static final int NUMBER_OF_STATE_POLL_RETRIES = (60 / CALLBACK_DELAY_IN_SECONDS) * 60 * 4; // 4 hours
    private static final String TIMED_OUT_MESSAGE = "Timed out waiting for listener to be deployed.";

    private AWSGlobalAccelerator agaClient;
    private AmazonWebServicesClientProxy clientProxy;
    private Logger logger;

    @Override
    public ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy proxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final Logger logger
    ) {
        this.logger = logger;
        clientProxy = proxy;
        agaClient = AcceleratorClientBuilder.getClient();
        final ResourceModel model = request.getDesiredResourceState();

        // confirm we can find the accelerator
        val foundAccelerator = getAccelerator(model.getAcceleratorArn());
        if (foundAccelerator == null) {
            return ProgressEvent.defaultFailureHandler(
                    new Exception(String.format("Failed to find accelerator with arn: [%s].  Can not create listener")),
                    HandlerErrorCode.NotFound);
        }

        // if this is our first try then we will return
        if (callbackContext == null) {
            return CreateListenerStep(model, request);
        } else {
            if (callbackContext.getStabilizationRetriesRemaining() <= 0) {
                throw new RuntimeException(TIMED_OUT_MESSAGE);
            }

            return WaitForSynchronziedStep(callbackContext, model);
        }
    }

    /**
     * Create an accelerator and create the correct progress continuation context
     */
    private ProgressEvent<ResourceModel, CallbackContext> CreateListenerStep(
            final ResourceModel model, final ResourceHandlerRequest<ResourceModel> handlerRequest
    ) {
        logger.log("Creating new accelerator.");
        val listener = createListener(model, handlerRequest);
        model.setListenerArn(listener.getListenerArn());
        model.setClientAffinity(listener.getClientAffinity());
        model.setProtocol(listener.getProtocol());

        val callbackContext = CallbackContext.builder()
                .stabilizationRetriesRemaining(NUMBER_OF_STATE_POLL_RETRIES)
                .build();

        return ProgressEvent.defaultInProgressHandler(callbackContext, 0, model);
    }

    /**
     * Check to see if accelerator creation is complete and create the correct progress continuation context
     */
    private ProgressEvent<ResourceModel, CallbackContext> WaitForSynchronziedStep(
            final CallbackContext context, final ResourceModel model
    ) {
        logger.log(String.format("Waiting for accelerator with arn [%s] to synchronize", model.getAcceleratorArn()));

        val accelerator = getAccelerator(model.getAcceleratorArn());
        if (accelerator.getStatus().equals(AcceleratorStatus.DEPLOYED.toString())) {
            return ProgressEvent.defaultSuccessHandler(model);
        } else {
            val callbackContext = CallbackContext.builder()
                    .stabilizationRetriesRemaining(context.getStabilizationRetriesRemaining()-1)
                    .build();
            return ProgressEvent.defaultInProgressHandler(callbackContext, CALLBACK_DELAY_IN_SECONDS, model);
        }
    }

    private Listener createListener(
            final ResourceModel model, final ResourceHandlerRequest<ResourceModel> handlerRequest
    ) {
        val convertedPortRanges = model.getPortRanges().stream()
                .map(x -> new PortRange().withFromPort(x.getFromPort()).withToPort(x.getToPort()))
                .collect(Collectors.toList());

        val createListenerRequest = new CreateListenerRequest()
                .withAcceleratorArn(model.getAcceleratorArn())
                .withClientAffinity(model.getClientAffinity())
                .withProtocol(model.getProtocol())
                .withPortRanges(convertedPortRanges)
                .withIdempotencyToken(handlerRequest.getLogicalResourceIdentifier());

        return clientProxy.injectCredentialsAndInvoke(createListenerRequest, agaClient::createListener).getListener();
    }

    /**
     * Get the accelerator with the specified ARN.
     * @param arn ARN of the accelerator
     * @return NULL if the accelerator does not exist
     */
    private Accelerator getAccelerator(final String arn) {
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
}
