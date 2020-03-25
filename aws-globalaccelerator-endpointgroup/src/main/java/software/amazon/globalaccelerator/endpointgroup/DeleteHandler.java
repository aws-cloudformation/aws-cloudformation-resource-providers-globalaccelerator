package software.amazon.globalaccelerator.endpointgroup;

import com.amazonaws.services.globalaccelerator.AWSGlobalAccelerator;
import com.amazonaws.services.globalaccelerator.model.DeleteEndpointGroupRequest;
import com.amazonaws.services.globalaccelerator.model.DeleteListenerRequest;
import lombok.val;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

public class DeleteHandler extends BaseHandler<CallbackContext> {

    @Override
    public ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy proxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final Logger logger) {

        val agaClient = AcceleratorClientBuilder.getClient();
        val inferredCallbackContext = callbackContext != null ?
                callbackContext :
                CallbackContext.builder().build().builder()
                        .stabilizationRetriesRemaining(HandlerCommons.NUMBER_OF_STATE_POLL_RETRIES)
                        .build();

        final ResourceModel model = request.getDesiredResourceState();
        val foundEndpointGroup = HandlerCommons.getEndpointGroup(model.getEndpointGroupArn(), proxy, agaClient, logger);
        if (foundEndpointGroup != null) {
            deleteEndpointGroup(foundEndpointGroup.getEndpointGroupArn(), proxy, agaClient, logger);
        }

        return HandlerCommons.waitForSynchronziedStep(inferredCallbackContext, model, proxy, agaClient, logger);
    }

    private void deleteEndpointGroup(final String endpointGroupArn,
                                final AmazonWebServicesClientProxy proxy,
                                final AWSGlobalAccelerator agaClient, final Logger logger) {
        val request = new DeleteEndpointGroupRequest().withEndpointGroupArn(endpointGroupArn);
        proxy.injectCredentialsAndInvoke(request, agaClient::deleteEndpointGroup);
    }
}
