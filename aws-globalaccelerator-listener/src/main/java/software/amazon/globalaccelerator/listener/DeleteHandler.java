package software.amazon.globalaccelerator.listener;

import com.amazonaws.services.globalaccelerator.AWSGlobalAccelerator;
import com.amazonaws.services.globalaccelerator.model.*;
import lombok.val;
import software.amazon.cloudformation.proxy.*;

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
                CallbackContext.builder()
                        .stabilizationRetriesRemaining(HandlerCommons.NUMBER_OF_STATE_POLL_RETRIES)
                        .build();

        final ResourceModel model = request.getDesiredResourceState();
        val foundListener = HandlerCommons.getListener(model.getListenerArn(), proxy, agaClient, logger);
        if (foundListener != null) {
            deleteListener(foundListener.getListenerArn(), proxy, agaClient, logger);
        }

        return HandlerCommons.WaitForSynchronziedStep(inferredCallbackContext, model, proxy, agaClient, logger);
    }

    private void deleteListener(final String listenerArn,
                                final AmazonWebServicesClientProxy proxy,
                                final AWSGlobalAccelerator agaClient, final Logger logger) {
        val request = new DeleteListenerRequest().withListenerArn(listenerArn);
        proxy.injectCredentialsAndInvoke(request, agaClient::deleteListener);
    }
}
