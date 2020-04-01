package software.amazon.globalaccelerator.endpointgroup;

import com.amazonaws.services.globalaccelerator.AWSGlobalAccelerator;
import com.amazonaws.services.globalaccelerator.model.EndpointConfiguration;
import com.amazonaws.services.globalaccelerator.model.UpdateEndpointGroupRequest;
import lombok.val;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

import java.util.List;
import java.util.stream.Collectors;

public class UpdateHandler extends BaseHandler<CallbackContext> {

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
                        .pendingStabilization(false)
                        .build();

        final ResourceModel model = request.getDesiredResourceState();

        val foundEndpointGroup = HandlerCommons.getEndpointGroup(model.getEndpointGroupArn(), proxy, agaClient, logger);
        if (foundEndpointGroup == null){
            return ProgressEvent.defaultFailureHandler(
                    new Exception(String.format("Failed to find endpoint group with arn:[%s]", model.getEndpointGroupArn())),
                    HandlerErrorCode.NotFound
            );
        }

        // a. Check if update is started.
        // b. If started, then poll on endpointgroup to go in sync
        // c. else, trigger an update operation
        final boolean isUpdateStarted = inferredCallbackContext.isPendingStabilization();

        if (!isUpdateStarted) {
            return updateEndpointGroup(model, proxy, agaClient, logger);
        }  else {
            return HandlerCommons.waitForSynchronizedStep(inferredCallbackContext, model, proxy, agaClient, logger);
        }
    }

    private ProgressEvent<ResourceModel, CallbackContext>  updateEndpointGroup(final ResourceModel model,
                                                                             final AmazonWebServicesClientProxy proxy,
                                                                             final AWSGlobalAccelerator agaClient,
                                                                             final Logger logger) {

        logger.log(String.format("Updating endpoint group with arn: [%s]", model.getEndpointGroupArn()));
        List<com.amazonaws.services.globalaccelerator.model.EndpointConfiguration> convertedEndpointConfigurations = null;

        if (model.getEndpointConfigurations() != null) {
            convertedEndpointConfigurations = model.getEndpointConfigurations().stream()
                    .map((x) -> new EndpointConfiguration().withEndpointId(x.getEndpointId()).withWeight(x.getWeight()))
                    .collect(Collectors.toList());
        }

        val request = new UpdateEndpointGroupRequest()
                .withEndpointGroupArn(model.getEndpointGroupArn())
                .withHealthCheckPort(model.getHealthCheckPort())
                .withHealthCheckIntervalSeconds(model.getHealthCheckIntervalSeconds())
                .withHealthCheckProtocol(model.getHealthCheckProtocol())
                .withHealthCheckPath(model.getHealthCheckPath())
                .withThresholdCount(model.getThresholdCount())
                .withEndpointConfigurations(convertedEndpointConfigurations);

        proxy.injectCredentialsAndInvoke(request, agaClient::updateEndpointGroup);
        val callbackContext = CallbackContext.builder()
                .stabilizationRetriesRemaining(HandlerCommons.NUMBER_OF_STATE_POLL_RETRIES)
                .pendingStabilization(true)
                .build();
        return ProgressEvent.defaultInProgressHandler(callbackContext, 0, model);
    }
}
