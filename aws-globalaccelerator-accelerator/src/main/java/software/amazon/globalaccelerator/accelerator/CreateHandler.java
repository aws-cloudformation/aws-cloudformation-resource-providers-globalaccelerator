package software.amazon.globalaccelerator.accelerator;

import com.amazonaws.services.globalaccelerator.AWSGlobalAccelerator;
import com.amazonaws.services.globalaccelerator.model.Accelerator;
import com.amazonaws.services.globalaccelerator.model.AcceleratorStatus;
import com.amazonaws.services.globalaccelerator.model.CreateAcceleratorRequest;
import com.amazonaws.services.globalaccelerator.model.DescribeAcceleratorRequest;
import com.amazonaws.services.globalaccelerator.model.AcceleratorNotFoundException;
import com.amazonaws.services.globalaccelerator.model.Tag;
import lombok.val;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

import java.util.ArrayList;
import java.util.stream.Collectors;

public class CreateHandler extends BaseHandler<CallbackContext> {
    private static final int CALLBACK_DELAY_IN_SECONDS = 1;
    private static final int NUMBER_OF_STATE_POLL_RETRIES = (60 / CALLBACK_DELAY_IN_SECONDS) * 60 * 4; // 4 hours
    private static final String TIMED_OUT_MESSAGE = "Timed out waiting for global accelerator to be deployed.";

    private AWSGlobalAccelerator agaClient;
    private AmazonWebServicesClientProxy clientProxy;

    @Override
    public ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy proxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final Logger logger
    ) {
        clientProxy = proxy;
        agaClient = AcceleratorClientBuilder.getClient();

        // create a context if we don't have one yet.
        val currentContext = callbackContext == null ?
                CallbackContext.builder().stabilizationRetriesRemaining(NUMBER_OF_STATE_POLL_RETRIES).build() :
                callbackContext;

        // If we run out of stabilization attempts, then we quit
        if (currentContext.getStabilizationRetriesRemaining() <= 0) {
            throw new RuntimeException(TIMED_OUT_MESSAGE);
        }

        val model = request.getDesiredResourceState();
        if (callbackContext == null) {
            return CreateAcceleratorStep(model, logger);
        } else {
            return WaitForSynchronziedStep(currentContext, model, logger);
        }
    }

    /**
     * Create an accelerator and create the correct progress continuation context
     */
    private ProgressEvent<ResourceModel, CallbackContext> CreateAcceleratorStep(
            ResourceModel model, Logger logger
    ) {
        logger.log("Creating new accelerator.");
        val acc = createAccelerator(model);
        model.setAcceleratorArn(acc.getAcceleratorArn());

        val allAddresses = new ArrayList<String>();
        val ipSets = acc.getIpSets();
        if (ipSets != null) {
            for (int i = 0; i != ipSets.size(); ++i) {
                val ipSetAddresses = ipSets.get(i).getIpAddresses();
                for (int j = 0; j != ipSetAddresses.size(); ++j) {
                    allAddresses.add(ipSetAddresses.get(j));
                }
            }
        }
        model.setIpAddresses(allAddresses);

        val callbackContext = CallbackContext.builder()
                .stabilizationRetriesRemaining(NUMBER_OF_STATE_POLL_RETRIES)
                .build();

        return ProgressEvent.defaultInProgressHandler(callbackContext, 0, model);
    }

    /**
     * Check to see if accelerator creation is complete and create the correct progress continuation context
     */
    private ProgressEvent<ResourceModel, CallbackContext> WaitForSynchronziedStep(
            CallbackContext context, ResourceModel model, Logger logger
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

    /**
     * Create the accelerator based on the provided ResourceModel
     */
    private Accelerator createAccelerator(ResourceModel model) {
        val convertedTags = model.getTags().stream()
                .map(x -> new Tag().withKey(x.getKey()).withValue(x.getValue()))
                .collect(Collectors.toList());

        val request = new CreateAcceleratorRequest()
                        .withName(model.getName())
                        .withEnabled(model.getEnabled())
                        .withIpAddresses(model.getIpAddresses())
                        .withTags(convertedTags)
                        .withIdempotencyToken(model.getName()); //Pending design discussion.

        return clientProxy.injectCredentialsAndInvoke(request, agaClient::createAccelerator).getAccelerator();
    }

    /**
     * Get the accelerator with the specified ARN.
     * @param arn ARN of the accelerator
     * @return NULL if the accelerator does not exist
     */
    private Accelerator getAccelerator(String arn) {
        Accelerator accelerator = null;
        try {
            val request = new DescribeAcceleratorRequest().withAcceleratorArn(arn);
            accelerator =  clientProxy.injectCredentialsAndInvoke(request, agaClient::describeAccelerator).getAccelerator();
        }
        catch (AcceleratorNotFoundException ex) {
            // TODO: Log here?
        }
        return accelerator;
    }
}
