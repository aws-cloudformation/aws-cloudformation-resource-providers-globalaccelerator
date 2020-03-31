package software.amazon.globalaccelerator.endpointgroup;

import com.amazonaws.services.globalaccelerator.model.*;
import lombok.val;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DeleteHandlerTest {

    @Mock
    private AmazonWebServicesClientProxy proxy;

    @Mock
    private Logger logger;

    @BeforeEach
    public void setup() {
        proxy = mock(AmazonWebServicesClientProxy.class);
        logger = mock(Logger.class);
    }

    @Test
    public void handleRequest_ResourceStillExists_DeletesAndReturnsInProgress() {
        // return an accelerator that is IN_PROGRESS
        val describeAcceleratorResult = new DescribeAcceleratorResult()
                .withAccelerator(new Accelerator()
                        .withStatus(AcceleratorStatus.IN_PROGRESS));
        doReturn(describeAcceleratorResult).when(proxy).injectCredentialsAndInvoke(any(DescribeAcceleratorRequest.class), any());

        // we expect delete to be called
        doReturn(new DeleteEndpointGroupResult()).when(proxy).injectCredentialsAndInvoke(any(DeleteEndpointGroupRequest.class), any());

        // the endpoint group describe request
        val describeEndpointGroupResult = new DescribeEndpointGroupResult()
                .withEndpointGroup(new EndpointGroup()
                        .withEndpointGroupArn("ENDPOINT_GROUP_ARN"));
        doReturn(describeEndpointGroupResult).when(proxy).injectCredentialsAndInvoke(any(DescribeEndpointGroupRequest.class), any());

        // create the that will go to our handler
        val model = ResourceModel.builder()
                .endpointGroupRegion("us-west-2")
                .listenerArn("arn:aws:globalaccelerator::474880776455:accelerator/abcd1234/listener/12341234")
                .endpointGroupArn("ENDPOINT_GROUP_ARN")
                .healthCheckPort(-1)
                .thresholdCount(3)
                .build();

        val request = ResourceHandlerRequest.<ResourceModel>builder().desiredResourceState(model).build();
        val handler = new DeleteHandler();
        val response = handler.handleRequest(proxy, request, null, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(1);
        assertThat(response.getCallbackContext()).isNotNull();
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();

        // want to confirm model remains the same
        assertThat(response.getResourceModel()).isEqualTo(model);
    }

    @Test
    public void handleRequest_ResourceDoesNotExists_AcceleratorInProgress_ReturnsInProgress() {
        // return an accelerator that is IN_PROGRESS
        val describeAcceleratorResult = new DescribeAcceleratorResult()
                .withAccelerator(new Accelerator()
                        .withStatus(AcceleratorStatus.IN_PROGRESS));
        doReturn(describeAcceleratorResult).when(proxy).injectCredentialsAndInvoke(any(DescribeAcceleratorRequest.class), any());

        // the endpoint group describe request will return not found
        doThrow(new EndpointGroupNotFoundException("NOT FOUND"))
                .when(proxy).injectCredentialsAndInvoke(any(DescribeEndpointGroupRequest.class), any());

        // create the that will go to our handler
        val model = ResourceModel.builder()
                .endpointGroupRegion("us-west-2")
                .listenerArn("arn:aws:globalaccelerator::474880776455:accelerator/abcd1234/listener/12341234")
                .endpointGroupArn("ENDPOINT_GROUP_ARN")
                .healthCheckPort(-1)
                .thresholdCount(3)
                .build();

        val request = ResourceHandlerRequest.<ResourceModel>builder().desiredResourceState(model).build();
        val handler = new DeleteHandler();
        val context = CallbackContext.builder().stabilizationRetriesRemaining(2).build();
        val response = handler.handleRequest(proxy, request, context, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(1);
        assertThat(response.getCallbackContext()).isNotNull();
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();

        // want to confirm model remains the same
        assertThat(response.getResourceModel()).isEqualTo(model);
    }

    @Test
    public void handleRequest_ResourceDoesNotExist_AcceleratorDeployed_ReturnsSuccess() {
        // return an accelerator that is IN_PROGRESS
        val describeAcceleratorResult = new DescribeAcceleratorResult()
                .withAccelerator(new Accelerator()
                        .withStatus(AcceleratorStatus.DEPLOYED));
        doReturn(describeAcceleratorResult).when(proxy).injectCredentialsAndInvoke(any(DescribeAcceleratorRequest.class), any());

        // the endpoint group describe request will return not found
        doThrow(new EndpointGroupNotFoundException("NOT FOUND"))
                .when(proxy).injectCredentialsAndInvoke(any(DescribeEndpointGroupRequest.class), any());

        // create the that will go to our handler
        val model = ResourceModel.builder()
                .endpointGroupRegion("us-west-2")
                .listenerArn("arn:aws:globalaccelerator::474880776455:accelerator/abcd1234/listener/12341234")
                .endpointGroupArn("ENDPOINT_GROUP_ARN")
                .healthCheckPort(-1)
                .thresholdCount(3)
                .build();

        val request = ResourceHandlerRequest.<ResourceModel>builder().desiredResourceState(model).build();
        val handler = new DeleteHandler();
        val context = CallbackContext.builder().stabilizationRetriesRemaining(2).build();
        val response = handler.handleRequest(proxy, request, context, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getCallbackContext()).isNull();
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();

        // want to confirm model remains the same
        assertThat(response.getResourceModel()).isEqualTo(model);
    }

    @Test
    public void handleRequest_ResourceDoesNotExist_ThresholdTimeExceeded() {
        // the endpoint group describe request will return not found
        doThrow(new EndpointGroupNotFoundException("NOT FOUND"))
                .when(proxy).injectCredentialsAndInvoke(any(DescribeEndpointGroupRequest.class), any());

        // Create the model we will provide to our handler
        val endpointConfigurations = new ArrayList<EndpointConfiguration>();
        endpointConfigurations.add(EndpointConfiguration.builder().endpointId("EPID1").build());
        endpointConfigurations.add(EndpointConfiguration.builder().endpointId("EPID2").build());
        val model = ResourceModel.builder()
                .endpointGroupRegion("us-west-2")
                .listenerArn("arn:aws:globalaccelerator::474880776455:accelerator/abcd1234/listener/12341234")
                .endpointGroupArn("ENDPOINT_GROUP_ARN")
                .healthCheckPort(20)
                .thresholdCount(3)
                .trafficDialPercentage(100)
                .healthCheckPath("/HEALTH")
                .endpointConfigurations(endpointConfigurations)
                .build();

        // call the handler
        val request = ResourceHandlerRequest.<ResourceModel>builder().desiredResourceState(model).build();
        val callbackContext = CallbackContext.builder().stabilizationRetriesRemaining(0).build();
        val handler = new DeleteHandler();

        assertThatExceptionOfType(RuntimeException.class).isThrownBy(() -> handler.handleRequest(proxy, request, callbackContext, logger))
                .withMessageMatching("Timed out waiting for endpoint group to be deployed.");
    }
}
