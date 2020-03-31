package software.amazon.globalaccelerator.listener;

import com.amazonaws.services.globalaccelerator.AWSGlobalAccelerator;
import com.amazonaws.services.globalaccelerator.model.DeleteListenerRequest;
import lombok.val;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

public class DeleteHandler extends BaseHandler<CallbackContext> {
    @Override
    public ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy proxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final Logger logger) {
        logger.log(String.format("Deleting listener with request [%s]", request));

        val agaClient = AcceleratorClientBuilder.getClient();

        final ResourceModel model = request.getDesiredResourceState();
        val foundListener = HandlerCommons.getListener(model.getListenerArn(), proxy, agaClient, logger);
        if (foundListener != null) {
            deleteListener(model.getListenerArn(), proxy, agaClient, logger);
        }

        return ProgressEvent.defaultSuccessHandler(model);
    }

    private void deleteListener(final String listenerArn,
                                final AmazonWebServicesClientProxy proxy,
                                final AWSGlobalAccelerator agaClient, final Logger logger) {
        val request = new DeleteListenerRequest().withListenerArn(listenerArn);
        proxy.injectCredentialsAndInvoke(request, agaClient::deleteListener);
    }
}
