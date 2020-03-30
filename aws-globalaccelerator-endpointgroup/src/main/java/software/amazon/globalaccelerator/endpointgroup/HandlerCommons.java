package software.amazon.globalaccelerator.endpointgroup;

import com.amazonaws.services.globalaccelerator.AWSGlobalAccelerator;
import com.amazonaws.services.globalaccelerator.model.*;
import lombok.val;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.globalaccelerator.arns.ListenerArn;

public class HandlerCommons {
    public static final int CALLBACK_DELAY_IN_SECONDS = 1;
    public static final int NUMBER_OF_STATE_POLL_RETRIES = (60 / CALLBACK_DELAY_IN_SECONDS) * 60 * 4; // 4 hours
    private static final String TIMED_OUT_MESSAGE = "Timed out waiting for endpoint group to be deployed.";

    /**
     * Check to see if accelerator creation is complete and create the correct progress continuation context
     */
    public static ProgressEvent<ResourceModel, CallbackContext> waitForSynchronizedStep(final CallbackContext context,
                                                                                        final ResourceModel model,
                                                                                        final AmazonWebServicesClientProxy proxy,
                                                                                        final AWSGlobalAccelerator agaClient,
                                                                                        final Logger logger) {
        val acceleratorArn = new ListenerArn(model.getListenerArn()).getAcceleratorArn();
        logger.log(String.format("Waiting for accelerator with arn [%s] to synchronize", acceleratorArn));

        // check to see if we have exceeded what we are allowed to do
        val newCallbackContext = CallbackContext.builder()
                .stabilizationRetriesRemaining(context.getStabilizationRetriesRemaining()-1)
                .build();

        if (newCallbackContext.getStabilizationRetriesRemaining() < 0) {
            throw new RuntimeException(TIMED_OUT_MESSAGE);
        }


        val accelerator = getAccelerator(acceleratorArn, proxy, agaClient, logger);
        if (accelerator.getStatus().equals(AcceleratorStatus.DEPLOYED.toString())) {
            return ProgressEvent.defaultSuccessHandler(model);
        } else {
            return ProgressEvent.defaultInProgressHandler(newCallbackContext, CALLBACK_DELAY_IN_SECONDS, model);
        }
    }

    /**
     * Get the accelerator with the specified ARN.
     * @param arn ARN of the accelerator
     * @return NULL if the accelerator does not exist
     */
    public static Accelerator getAccelerator(final String arn, final AmazonWebServicesClientProxy proxy,
                                             final AWSGlobalAccelerator agaClient, final Logger logger) {
        Accelerator accelerator = null;
        try {
            val request = new DescribeAcceleratorRequest().withAcceleratorArn(arn);
            accelerator =  proxy.injectCredentialsAndInvoke(request, agaClient::describeAccelerator).getAccelerator();
        }
        catch (AcceleratorNotFoundException ex) {
            logger.log(String.format("Did not find accelerator with arn [%s]", arn));
        }
        return accelerator;
    }

    /** Gets the listener with the specified ARN **/
    public static Listener getListener(String listenerArn, final AmazonWebServicesClientProxy proxy,
                                       final AWSGlobalAccelerator agaClient, final Logger logger) {
        Listener listener = null;
        try {
            val request = new DescribeListenerRequest().withListenerArn(listenerArn);
            listener =  proxy.injectCredentialsAndInvoke(request, agaClient::describeListener).getListener();
        } catch (ListenerNotFoundException ex) {
            logger.log(String.format("Did not find listener with arn [%s]", listenerArn));
        }

        return listener;
    }

    public static EndpointGroup getEndpointGroup(String endpointGroupArn, final AmazonWebServicesClientProxy proxy,
                                                 final AWSGlobalAccelerator agaClient, final Logger logger) {
        EndpointGroup endpointGroup = null;
        try {
            val request = new DescribeEndpointGroupRequest().withEndpointGroupArn(endpointGroupArn);
            endpointGroup = proxy.injectCredentialsAndInvoke(request, agaClient::describeEndpointGroup).getEndpointGroup();
        } catch (EndpointGroupNotFoundException eex) {
            logger.log(String.format("Did not find endpoint group with arn [%s]", endpointGroupArn));
        }

        return endpointGroup;
    }
}