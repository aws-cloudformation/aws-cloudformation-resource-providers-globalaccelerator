package software.amazon.globalaccelerator.listener;

import com.amazonaws.services.globalaccelerator.AWSGlobalAccelerator;
import com.amazonaws.services.globalaccelerator.model.*;
import com.amazonaws.services.globalaccelerator.model.PortRange;
import lombok.val;
import software.amazon.cloudformation.proxy.*;

import java.util.stream.Collectors;

public class CreateHandler extends BaseHandler<CallbackContext> {
    @Override
    public ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy proxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final Logger logger
    ) {
        logger.log(String.format("Creating listener with request [%s]", request));

        val agaClient = AcceleratorClientBuilder.getClient();
        val inferredCallbackContext = callbackContext != null ?
                callbackContext :
                CallbackContext.builder()
                        .stabilizationRetriesRemaining(HandlerCommons.NUMBER_OF_STATE_POLL_RETRIES)
                        .build();

        // confirm we can find the accelerator
        final ResourceModel model = request.getDesiredResourceState();
        val foundAccelerator = HandlerCommons.getAccelerator(model.getAcceleratorArn(), proxy, agaClient, logger);
        if (foundAccelerator == null) {
            return ProgressEvent.defaultFailureHandler(
                    new Exception(String.format("Failed to find accelerator with arn: [%s].  Can not create listener", model.getAcceleratorArn())),
                    HandlerErrorCode.NotFound);
        }

        // if this is our first try then we will return
        if (model.getListenerArn() == null) {
            return createListenerStep(model, request, proxy, agaClient, logger);
        } else {
            return HandlerCommons.waitForSynchronizedStep(inferredCallbackContext, model, proxy, agaClient, logger);
        }
    }

    /**
     * Create an accelerator and create the correct progress continuation context
     */
    private ProgressEvent<ResourceModel, CallbackContext> createListenerStep(final ResourceModel model,
                                                                             final ResourceHandlerRequest<ResourceModel> handlerRequest,
                                                                             final AmazonWebServicesClientProxy proxy,
                                                                             final AWSGlobalAccelerator agaClient,
                                                                             final Logger logger) {
        logger.log("Creating new listener.");
        val listener = createListener(model, handlerRequest, proxy, agaClient);
        model.setListenerArn(listener.getListenerArn());
        model.setClientAffinity(listener.getClientAffinity());
        model.setProtocol(listener.getProtocol());

        val callbackContext = CallbackContext.builder()
                .stabilizationRetriesRemaining(HandlerCommons.NUMBER_OF_STATE_POLL_RETRIES)
                .build();

        return ProgressEvent.defaultInProgressHandler(callbackContext, 0, model);
    }

    private Listener createListener(final ResourceModel model,
                                    final ResourceHandlerRequest<ResourceModel> handlerRequest,
                                    final AmazonWebServicesClientProxy proxy,
                                    final AWSGlobalAccelerator agaClient) {
        val convertedPortRanges = model.getPortRanges().stream()
                .map(x -> new PortRange().withFromPort(x.getFromPort()).withToPort(x.getToPort()))
                .collect(Collectors.toList());

        val createListenerRequest = new CreateListenerRequest()
                .withAcceleratorArn(model.getAcceleratorArn())
                .withClientAffinity(model.getClientAffinity())
                .withProtocol(model.getProtocol())
                .withPortRanges(convertedPortRanges)
                .withIdempotencyToken(handlerRequest.getLogicalResourceIdentifier());

        return proxy.injectCredentialsAndInvoke(createListenerRequest, agaClient::createListener).getListener();
    }
}
