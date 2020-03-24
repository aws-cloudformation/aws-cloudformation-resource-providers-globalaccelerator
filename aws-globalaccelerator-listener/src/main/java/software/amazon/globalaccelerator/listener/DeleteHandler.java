package software.amazon.globalaccelerator.listener;

import com.amazonaws.services.globalaccelerator.AWSGlobalAccelerator;
import com.amazonaws.services.globalaccelerator.model.*;
import lombok.val;
import software.amazon.cloudformation.proxy.*;

public class DeleteHandler extends BaseHandler<CallbackContext> {
    private AWSGlobalAccelerator agaClient;
    private AmazonWebServicesClientProxy clientProxy;
    private Logger logger;

    @Override
    public ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy proxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final Logger logger) {

        clientProxy = proxy;
        agaClient = AcceleratorClientBuilder.getClient();
        this.logger = logger;
        final ResourceModel model = request.getDesiredResourceState();

        val acceleratorArn = request.getDesiredResourceState().getListenerArn();
        val foundListener = getListener(acceleratorArn);

        if (foundListener != null) {
            deleteListener(foundListener.getListenerArn());
        }

        return ProgressEvent.defaultSuccessHandler(model);
    }

    private void deleteListener(String listenerArn) {
        val request = new DeleteListenerRequest().withListenerArn(listenerArn);
        clientProxy.injectCredentialsAndInvoke(request, agaClient::deleteListener);
    }

    private Listener getListener(String listenerArn) {
        Listener listener = null;
        try {
            val request = new DescribeListenerRequest().withListenerArn(listenerArn);
            listener =  clientProxy.injectCredentialsAndInvoke(request, agaClient::describeListener).getListener();
        }
        catch (ListenerNotFoundException ex) {
            logger.log(String.format("Did not find listener with arn [%s]", listenerArn));
        }
        return listener;
    }
}
