package software.amazon.globalaccelerator.endpointgroup;

import com.amazonaws.services.globalaccelerator.AWSGlobalAccelerator;
import com.amazonaws.services.globalaccelerator.model.CreateEndpointGroupRequest;
import com.amazonaws.services.globalaccelerator.model.EndpointConfiguration;
import com.amazonaws.services.globalaccelerator.model.EndpointGroup;
import lombok.val;
import software.amazon.cloudformation.proxy.*;
import software.amazon.globalaccelerator.arns.ListenerArn;

import java.util.List;
import java.util.stream.Collectors;

public class CreateHandler extends BaseHandler<CallbackContext> {

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

        // confirm we can find the listener
        final ResourceModel model = request.getDesiredResourceState();
        val foundListener = HandlerCommons.getListener(model.getListenerArn(), proxy, agaClient, logger);
        if (foundListener == null) {
            return ProgressEvent.defaultFailureHandler(
                    new Exception(String.format("Failed to find listener with arn: [%s].  Can not create endpoint group.")),
                    HandlerErrorCode.NotFound);
        }

        if (model.getEndpointGroupArn() == null) {
            return CreateEndpointGroupStep(model, request, proxy, agaClient, logger);
        } else {
            return HandlerCommons.waitForSynchronziedStep(inferredCallbackContext, model, proxy, agaClient, logger);
        }
    }

    /**
     * Create an accelerator and create the correct progress continuation context
     */
    private ProgressEvent<ResourceModel, CallbackContext> CreateEndpointGroupStep(final ResourceModel model,
                                                                                  final ResourceHandlerRequest<ResourceModel> handlerRequest,
                                                                                  final AmazonWebServicesClientProxy proxy,
                                                                                  final AWSGlobalAccelerator agaClient,
                                                                                  final Logger logger) {
        // first thing get the accelerator associated to listener so we can update our model
        val listenerArn = new ListenerArn(model.getListenerArn());
        val accelerator = HandlerCommons.getAccelerator(listenerArn.getAcceleratorArn(), proxy, agaClient, logger);

        if (accelerator == null) {
            return ProgressEvent.defaultFailureHandler(
                    new Exception(String.format("Could not find accelerator for listener [%s]", model.getListenerArn())),
                    HandlerErrorCode.NotFound);
        }

        // now we can move forward and create the endpoint group and update model with everything that is optional
        val endpointGroup = CreateEndpointGroup(model, handlerRequest, proxy, agaClient);
        model.setEndpointGroupArn(endpointGroup.getEndpointGroupArn());
        model.setHealthCheckIntervalSeconds(endpointGroup.getHealthCheckIntervalSeconds());
        model.setHealthCheckPath(endpointGroup.getHealthCheckPath());
        model.setHealthCheckPort(endpointGroup.getHealthCheckPort());
        model.setHealthCheckProtocol(endpointGroup.getHealthCheckProtocol());

        val callbackContext = CallbackContext.builder()
                .stabilizationRetriesRemaining(HandlerCommons.NUMBER_OF_STATE_POLL_RETRIES)
                .build();

        return ProgressEvent.defaultInProgressHandler(callbackContext, 0, model);
    }

    private EndpointGroup CreateEndpointGroup(final ResourceModel model,
                                    final ResourceHandlerRequest<ResourceModel> handlerRequest,
                                    final AmazonWebServicesClientProxy proxy,
                                    final AWSGlobalAccelerator agaClient) {
        // If health check port is not provided we want to fall back
        Integer healthCheckPort = (model.getHealthCheckPort() < 0) ? null : model.getHealthCheckPort();

        // we need to map all of our endpoint configurations
        List<EndpointConfiguration> convertedEndpointConfigurations = null;
        if (model.getEndpointConfigurations() != null) {
            convertedEndpointConfigurations = model.getEndpointConfigurations().stream()
                    .map((x) -> new EndpointConfiguration().withEndpointId(x.getEndpointId()).withWeight(x.getWeight()))
                    .collect(Collectors.toList());
        }

        val createEndpointGroupRequest = new CreateEndpointGroupRequest()
                .withListenerArn(model.getListenerArn())
                .withEndpointGroupRegion(model.getEndpointGroupRegion())
                .withHealthCheckPort(null)
                .withHealthCheckIntervalSeconds(model.getHealthCheckIntervalSeconds())
                .withHealthCheckProtocol(model.getHealthCheckProtocol())
                .withHealthCheckPath(model.getHealthCheckPath())
                .withThresholdCount(model.getThresholdCount())
                .withEndpointConfigurations(convertedEndpointConfigurations)
                .withIdempotencyToken(handlerRequest.getLogicalResourceIdentifier());

        return proxy.injectCredentialsAndInvoke(createEndpointGroupRequest, agaClient::createEndpointGroup).getEndpointGroup();
    }
}
