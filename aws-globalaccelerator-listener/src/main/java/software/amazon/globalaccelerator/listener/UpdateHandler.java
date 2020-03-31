package software.amazon.globalaccelerator.listener;

import com.amazonaws.services.globalaccelerator.AWSGlobalAccelerator;
import com.amazonaws.services.globalaccelerator.model.Listener;
import com.amazonaws.services.globalaccelerator.model.PortRange;
import com.amazonaws.services.globalaccelerator.model.UpdateListenerRequest;
import lombok.val;
import software.amazon.cloudformation.proxy.*;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class UpdateHandler extends BaseHandler<CallbackContext> {

    public static String UPDATE_COMPLETED_KEY = "UPDATE_COMPLETED";


    @Override
    public ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy proxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final Logger logger) {
        logger.log(String.format("Updating listener with request [%s]", request));

        val agaClient = AcceleratorClientBuilder.getClient();
        val inferredCallbackContext = callbackContext != null ?
                callbackContext :
                CallbackContext.builder()
                        .stabilizationRetriesRemaining(HandlerCommons.NUMBER_OF_STATE_POLL_RETRIES)
                        .pendingStabilization(false)
                        .build();

        // confirm we can find the accelerator
        final ResourceModel model = request.getDesiredResourceState();
        if (!inferredCallbackContext.isPendingStabilization()) {
            updateListenerStep(model, request, proxy, agaClient, logger);
            inferredCallbackContext.setPendingStabilization(true);
        }

        return HandlerCommons.waitForSynchronizedStep(inferredCallbackContext, model, proxy, agaClient, logger);
    }

    /**
     * Create an accelerator and create the correct progress continuation context
     */
    private ProgressEvent<ResourceModel, CallbackContext> updateListenerStep(final ResourceModel model,
                                                                             final ResourceHandlerRequest<ResourceModel> handlerRequest,
                                                                             final AmazonWebServicesClientProxy proxy,
                                                                             final AWSGlobalAccelerator agaClient,
                                                                             final Logger logger) {
        logger.log("Updating the listener");
        val listener = updateListener(model, handlerRequest, proxy, agaClient);
        model.setClientAffinity(listener.getClientAffinity());
        model.setProtocol(listener.getProtocol());

        val callbackContext = CallbackContext.builder()
                .stabilizationRetriesRemaining(HandlerCommons.NUMBER_OF_STATE_POLL_RETRIES)
                .build();

        return ProgressEvent.defaultInProgressHandler(callbackContext, 0, model);
    }

    private Listener updateListener(final ResourceModel model,
                                    final ResourceHandlerRequest<ResourceModel> handlerRequest,
                                    final AmazonWebServicesClientProxy proxy,
                                    final AWSGlobalAccelerator agaClient) {
        val convertedPortRanges = model.getPortRanges().stream()
                .map(x -> new PortRange().withFromPort(x.getFromPort()).withToPort(x.getToPort()))
                .collect(Collectors.toList());

        val updateListenerRequest = new UpdateListenerRequest()
                .withListenerArn(model.getListenerArn())
                .withClientAffinity(model.getClientAffinity())
                .withProtocol(model.getProtocol())
                .withPortRanges(convertedPortRanges);

        return proxy.injectCredentialsAndInvoke(updateListenerRequest, agaClient::updateListener).getListener();
    }
}
