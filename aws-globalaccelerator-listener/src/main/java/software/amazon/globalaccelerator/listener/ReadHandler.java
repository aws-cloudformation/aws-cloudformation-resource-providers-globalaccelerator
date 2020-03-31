package software.amazon.globalaccelerator.listener;

import com.amazonaws.services.globalaccelerator.model.Listener;
import lombok.val;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

import java.util.stream.Collectors;

public class ReadHandler extends BaseHandler<CallbackContext> {

    @Override
    public ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy proxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final Logger logger) {
        logger.log(String.format("Reading listener with request [%s]", request));

        val currentModel = request.getDesiredResourceState();
        val agaClient = AcceleratorClientBuilder.getClient();
        val desiredState = request.getDesiredResourceState();

        val listener = HandlerCommons.getListener(desiredState.getListenerArn(), proxy, agaClient, logger);
        val convertedModel = convertListenerToResourceModel(listener, currentModel);

        logger.log(String.format("Current found listener is: [%s]", convertedModel == null ? "null" : convertedModel));
        if (convertedModel != null) {
            return ProgressEvent.defaultSuccessHandler(convertedModel);
        } else {
            return ProgressEvent.defaultFailureHandler(new Exception("Listener not found."), HandlerErrorCode.NotFound);
        }
    }

    private ResourceModel convertListenerToResourceModel(Listener listener, ResourceModel currentModel) {
        ResourceModel converted = null;

        if (listener != null) {
            converted = new ResourceModel();
            converted.setListenerArn(listener.getListenerArn());
            converted.setProtocol(listener.getProtocol());
            converted.setAcceleratorArn(currentModel.getAcceleratorArn());
            converted.setClientAffinity(listener.getClientAffinity());
            converted.setPortRanges(
                    listener.getPortRanges().stream().map(x -> {
                        val portRange = new PortRange();
                        portRange.setFromPort(x.getFromPort());
                        portRange.setToPort(x.getToPort());
                        return portRange;
                    }).collect(Collectors.toList()));
        }

        return converted;
    }
}
