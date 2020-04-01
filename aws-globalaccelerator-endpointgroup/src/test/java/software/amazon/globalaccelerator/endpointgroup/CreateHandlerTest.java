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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
public class CreateHandlerTest {

    @Mock
    private AmazonWebServicesClientProxy proxy;

    @Mock
    private Logger logger;

    @BeforeEach
    public void setup() {
        proxy = mock(AmazonWebServicesClientProxy.class);
        logger = mock(Logger.class);
    }


    private List<EndpointDescription> createEndpointDescription() {
        ArrayList<EndpointDescription> array = new ArrayList<EndpointDescription>();
        array.add(new EndpointDescription()
                .withClientIPPreservationEnabled(true)
                .withHealthState("HS1")
                .withEndpointId("ID1")
                .withWeight(100)
                .withHealthReason("Reason1"));
        array.add(new EndpointDescription()
                .withClientIPPreservationEnabled(false)
                .withHealthState("HS2")
                .withEndpointId("ID2")
                .withWeight(100)
                .withHealthReason("Reason2"));
        return array;
    }

    @Test
    public void handleRequest_InitialCreate_ReturnsInProgress() {
        // return an accelerator that is IN_PROGRESS
        val describeAcceleratorResult = new DescribeAcceleratorResult()
                .withAccelerator(new Accelerator()
                    .withStatus(AcceleratorStatus.IN_PROGRESS));
        doReturn(describeAcceleratorResult).when(proxy).injectCredentialsAndInvoke(any(DescribeAcceleratorRequest.class), any());

        // return a listener
        val describeListenerResult = new DescribeListenerResult()
                .withListener(new Listener()
                    .withListenerArn("arn:aws:globalaccelerator::474880776455:accelerator/abcd1234/listener/12341234"));
        doReturn(describeListenerResult).when(proxy).injectCredentialsAndInvoke(any(DescribeListenerRequest.class), any());

        // the endpoint group create result
        val createEndpointGroupResult = new CreateEndpointGroupResult()
                .withEndpointGroup(new EndpointGroup()
                    .withEndpointGroupArn("ENDPOINT_GROUP_ARN")
                    .withHealthCheckPath("/MYPATH")
                    .withHealthCheckPort(200)
                    .withEndpointGroupRegion("us-west-2")
                    .withHealthCheckProtocol("HTTP")
                    .withHealthCheckIntervalSeconds(10)
                    .withThresholdCount(4)
                    .withEndpointDescriptions(createEndpointDescription()));
        doReturn(createEndpointGroupResult).when(proxy).injectCredentialsAndInvoke(any(CreateEndpointGroupRequest.class), any());

        // create the that will go to our handler
        val model = ResourceModel.builder()
                .endpointGroupRegion("us-west-2")
                .listenerArn("arn:aws:globalaccelerator::474880776455:accelerator/abcd1234/listener/12341234")
                .healthCheckPort(-1)
                .thresholdCount(3)
                .build();

        val request = ResourceHandlerRequest.<ResourceModel>builder().desiredResourceState(model).build();
        val handler = new CreateHandler();
        val response = handler.handleRequest(proxy, request, null, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getCallbackContext()).isNotNull();
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();

        // want to confirm data was updated correctly
        assertThat(response.getResourceModel()).isNotNull();
        assertThat(response.getResourceModel().getEndpointGroupArn()).isEqualTo("ENDPOINT_GROUP_ARN");
        assertThat(response.getResourceModel().getHealthCheckPath()).isEqualTo("/MYPATH");
        assertThat(response.getResourceModel().getHealthCheckPort()).isEqualTo(200);
        assertThat(response.getResourceModel().getEndpointGroupRegion()).isEqualTo("us-west-2");
        assertThat(response.getResourceModel().getHealthCheckProtocol()).isEqualTo("HTTP");
        assertThat(response.getResourceModel().getHealthCheckIntervalSeconds()).isEqualTo(10);
        assertThat(response.getResourceModel().getThresholdCount()).isEqualTo(4);
    }

    @Test
    public void handleRequest_AwaitAcceleratorToGoInSync_ReturnsInProgress() {
        // return an accelerator that is IN_PROGRESS
        val describeAcceleratorResult = new DescribeAcceleratorResult()
                .withAccelerator(new Accelerator()
                        .withStatus(AcceleratorStatus.IN_PROGRESS));
        doReturn(describeAcceleratorResult).when(proxy).injectCredentialsAndInvoke(any(DescribeAcceleratorRequest.class), any());

        // Create the model we will provide to our handler
        val endpointConfigurations = new ArrayList<EndpointConfiguration>();
        endpointConfigurations.add(EndpointConfiguration.builder().endpointId("EPID1").build());
        endpointConfigurations.add(EndpointConfiguration.builder().endpointId("EPID2").build());
        val model = ResourceModel.builder()
                .endpointGroupRegion("us-west-2")
                .listenerArn("arn:aws:globalaccelerator::474880776455:accelerator/abcd1234/listener/12341234")
                .endpointGroupArn("ENDPOINT_GROUP")
                .healthCheckPort(20)
                .thresholdCount(3)
                .trafficDialPercentage(100)
                .healthCheckPath("/HEALTH")
                .endpointConfigurations(endpointConfigurations)
                .build();

        // call the handler
        val request = ResourceHandlerRequest.<ResourceModel>builder().desiredResourceState(model).build();
        val callbackContext = CallbackContext.builder().stabilizationRetriesRemaining(5).build();
        val handler = new CreateHandler();
        val response = handler.handleRequest(proxy, request, callbackContext, logger);

        // validate expectations
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(1);
        assertThat(response.getCallbackContext()).isNotNull();
        assertThat(response.getCallbackContext().getStabilizationRetriesRemaining()).isEqualTo(4);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();

        // confirm the model did not mutate
        assertThat(response.getResourceModel()).isEqualTo(model);
    }

    @Test
    public void handleRequest_AcceleratorIsInSync_ReturnsSuccess() {
        // return an accelerator that is IN_PROGRESS
        val describeAcceleratorResult = new DescribeAcceleratorResult()
                .withAccelerator(new Accelerator()
                        .withStatus(AcceleratorStatus.DEPLOYED));
        doReturn(describeAcceleratorResult).when(proxy).injectCredentialsAndInvoke(any(DescribeAcceleratorRequest.class), any());

        // Create the model we will provide to our handler
        val endpointConfigurations = new ArrayList<EndpointConfiguration>();
        endpointConfigurations.add(EndpointConfiguration.builder().endpointId("EPID1").build());
        endpointConfigurations.add(EndpointConfiguration.builder().endpointId("EPID2").build());
        val model = ResourceModel.builder()
                .endpointGroupRegion("us-west-2")
                .listenerArn("arn:aws:globalaccelerator::474880776455:accelerator/abcd1234/listener/12341234")
                .endpointGroupArn("ENDPOINT_GROUP")
                .healthCheckPort(20)
                .thresholdCount(3)
                .trafficDialPercentage(100)
                .healthCheckPath("/HEALTH")
                .endpointConfigurations(endpointConfigurations)
                .build();

        // call the handler
        val request = ResourceHandlerRequest.<ResourceModel>builder().desiredResourceState(model).build();
        val callbackContext = CallbackContext.builder().stabilizationRetriesRemaining(5).build();
        val handler = new CreateHandler();
        val response = handler.handleRequest(proxy, request, callbackContext, logger);

        // validate expectations
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getCallbackContext()).isNull();
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();

        // confirm the model did not mutate
        assertThat(response.getResourceModel()).isEqualTo(model);
    }

    @Test
    public void handleRequest_ThresholdTimeExceeded() {
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
        val handler = new CreateHandler();

        assertThatExceptionOfType(RuntimeException.class).isThrownBy(() -> handler.handleRequest(proxy, request, callbackContext, logger))
                .withMessageMatching("Timed out waiting for endpoint group to be deployed.");
    }
}
