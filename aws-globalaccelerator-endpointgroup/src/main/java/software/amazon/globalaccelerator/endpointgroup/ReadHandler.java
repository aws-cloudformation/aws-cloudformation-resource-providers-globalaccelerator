package software.amazon.globalaccelerator.endpointgroup;

import com.amazonaws.services.globalaccelerator.model.EndpointDescription;
import com.amazonaws.services.globalaccelerator.model.EndpointGroup;
import software.amazon.globalaccelerator.endpointgroup.EndpointConfiguration;
import lombok.val;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ReadHandler extends BaseHandler<CallbackContext> {

    @Override
    public ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy proxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final Logger logger) {

        final ResourceModel model = request.getDesiredResourceState();

        val agaClient = AcceleratorClientBuilder.getClient();
        logger.log(String.format("Read request for endpoint group: [%s]", request));

        val endpointGroup = HandlerCommons.getEndpointGroup(model.getEndpointGroupArn(), proxy, agaClient, logger);
        val endpointGroupResourceModel = convertEndpointGroupToResourceModel(endpointGroup);

        if (endpointGroupResourceModel == null){
            logger.log(String.format("Endpoint group with ARN [%s] not found", model.getEndpointGroupArn()));
            return ProgressEvent.defaultFailureHandler(new Exception("Endpoint group not found."), HandlerErrorCode.NotFound);
        } else {
            return ProgressEvent.defaultSuccessHandler(endpointGroupResourceModel);
        }
    }

    private ResourceModel convertEndpointGroupToResourceModel(EndpointGroup endpointGroup) {
        ResourceModel newModel = null;
        if (endpointGroup != null) {
            newModel = new ResourceModel();
            newModel.setEndpointGroupArn(endpointGroup.getEndpointGroupArn());
            newModel.setHealthCheckIntervalSeconds(endpointGroup.getHealthCheckIntervalSeconds());
            newModel.setHealthCheckPath(endpointGroup.getHealthCheckPath());
            newModel.setHealthCheckPort(endpointGroup.getHealthCheckPort());
            newModel.setHealthCheckProtocol(endpointGroup.getHealthCheckProtocol());
            newModel.setThresholdCount(endpointGroup.getThresholdCount());
            newModel.setTrafficDialPercentage(endpointGroup.getTrafficDialPercentage().intValue());
            newModel.setEndpointGroupRegion(endpointGroup.getEndpointGroupRegion());
            newModel.setEndpointConfigurations(getEndpointConfigurations(endpointGroup.getEndpointDescriptions()));
        }
        return newModel;
    }

    private List<EndpointConfiguration> getEndpointConfigurations (List<EndpointDescription> endpointDescriptions) {
        return endpointDescriptions.stream().map((x) -> EndpointConfiguration.builder()
                .clientIPPreservationEnabled(x.getClientIPPreservationEnabled())
                .endpointId(x.getEndpointId())
                .weight(x.getWeight())
                .build()).collect(Collectors.toList());
    }
}
