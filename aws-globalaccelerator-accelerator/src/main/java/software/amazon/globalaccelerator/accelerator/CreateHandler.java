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
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CreateHandler extends BaseHandler<CallbackContext> {
    @Override
    public ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy proxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final Logger logger
    ) {
        val agaClient = AcceleratorClientBuilder.getClient();
        val inferredCallbackContext = callbackContext != null ?
                callbackContext :
                CallbackContext.builder()
                        .stabilizationRetriesRemaining(HandlerCommons.NUMBER_OF_STATE_POLL_RETRIES)
                        .build();

        val model = request.getDesiredResourceState();
        if (model.getAcceleratorArn() == null) {
            return createAcceleratorStep(model, request, proxy, agaClient, logger);
        } else {
            return HandlerCommons.waitForSynchronizedStep(inferredCallbackContext, model, proxy, agaClient, logger);
        }
    }

    /**
     * Create an accelerator and create the correct progress continuation context
     */
    private ProgressEvent<ResourceModel, CallbackContext> createAcceleratorStep(final ResourceModel model,
                                                                                final ResourceHandlerRequest<ResourceModel> handlerRequest,
                                                                                final AmazonWebServicesClientProxy proxy,
                                                                                final AWSGlobalAccelerator agaClient,
                                                                                final Logger logger) {
        logger.log("Creating new accelerator.");
        val acc = createAccelerator(model, handlerRequest, proxy, agaClient);
        model.setAcceleratorArn(acc.getAcceleratorArn());


        List<String> allAddresses = null;
        val ipSets = acc.getIpSets();
        if (ipSets != null) {
            allAddresses = ipSets.stream()
                    .flatMap(x -> x.getIpAddresses().stream())
                    .collect(Collectors.toList());
        }
        model.setIpAddresses(allAddresses);

        val callbackContext = CallbackContext.builder()
                .stabilizationRetriesRemaining(HandlerCommons.NUMBER_OF_STATE_POLL_RETRIES)
                .build();

        return ProgressEvent.defaultInProgressHandler(callbackContext, 0, model);
    }

    /**
     * Create the accelerator based on the provided ResourceModel
     */
    private Accelerator createAccelerator(final ResourceModel model,
                                          final ResourceHandlerRequest<ResourceModel> handlerRequest,
                                          final AmazonWebServicesClientProxy proxy,
                                          final AWSGlobalAccelerator agaClient) {
        List<Tag> convertedTags = null;
        if (model.getTags() != null) {
            convertedTags = model.getTags().stream()
                    .map(x -> new Tag().withKey(x.getKey()).withValue(x.getValue()))
                    .collect(Collectors.toList());
        }

        val request = new CreateAcceleratorRequest()
                        .withName(model.getName())
                        .withEnabled(model.getEnabled())
                        .withIpAddresses(model.getIpAddresses())
                        .withTags(convertedTags)
                        .withIdempotencyToken(handlerRequest.getLogicalResourceIdentifier());

        return proxy.injectCredentialsAndInvoke(request, agaClient::createAccelerator).getAccelerator();
    }
}
