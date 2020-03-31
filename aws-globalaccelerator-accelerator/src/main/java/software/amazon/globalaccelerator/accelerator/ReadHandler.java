package software.amazon.globalaccelerator.accelerator;

import com.amazonaws.services.globalaccelerator.model.Accelerator;
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

        val agaClient = AcceleratorClientBuilder.getClient();
        final ResourceModel model = request.getDesiredResourceState();
        logger.log(String.format("Read request for accelerator: [%s]", request));

        val accelerator = HandlerCommons.getAccelerator(model.getAcceleratorArn(), proxy, agaClient, logger);
        val acceleratorResourceModel = convertAcceleratorToResourceModel(accelerator);

        if (acceleratorResourceModel == null){
            logger.log(String.format("Accelerator with ARN [%s] not found", model.getAcceleratorArn()));
            return ProgressEvent.defaultFailureHandler(new Exception("Listener not found."), HandlerErrorCode.NotFound);
        } else {
            return ProgressEvent.defaultSuccessHandler(acceleratorResourceModel);
        }
    }
    private ResourceModel convertAcceleratorToResourceModel(Accelerator accelerator) {
        ResourceModel newModel = null;
        if (accelerator != null) {
            newModel = new ResourceModel();
            newModel.setAcceleratorArn(accelerator.getAcceleratorArn());
            newModel.setName(accelerator.getName());
            newModel.setEnabled(accelerator.getEnabled());
            newModel.setIpAddressType(accelerator.getIpAddressType());
            newModel.setIpAddresses(accelerator.getIpSets() == null ? null :
                            accelerator.getIpSets().stream()
                            .flatMap(x -> x.getIpAddresses().stream())
                            .collect(Collectors.toList()));
        }
        return newModel;
    }
}
