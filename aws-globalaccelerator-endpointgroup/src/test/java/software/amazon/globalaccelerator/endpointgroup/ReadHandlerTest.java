package software.amazon.globalaccelerator.endpointgroup;

import com.amazonaws.services.globalaccelerator.model.DescribeEndpointGroupRequest;
import com.amazonaws.services.globalaccelerator.model.DescribeEndpointGroupResult;
import com.amazonaws.services.globalaccelerator.model.EndpointDescription;
import com.amazonaws.services.globalaccelerator.model.EndpointGroup;
import com.amazonaws.services.globalaccelerator.model.EndpointGroupNotFoundException;
import lombok.val;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
public class ReadHandlerTest {

    @Mock
    private AmazonWebServicesClientProxy proxy;

    @Mock
    private Logger logger;

    @BeforeEach
    public void setup() {
        proxy = mock(AmazonWebServicesClientProxy.class);
        logger = mock(Logger.class);
    }

    private ResourceModel createTestResourceModel() {
        val endpointConfigurations = new ArrayList<EndpointConfiguration>();
        endpointConfigurations.add(EndpointConfiguration.builder().endpointId("EPID1").build());
        endpointConfigurations.add(EndpointConfiguration.builder().endpointId("EPID2").build());

        return ResourceModel.builder()
                .endpointGroupArn("ENDPOINTGROUP_ARN")
                .endpointGroupRegion("us-west-2")
                .listenerArn("LISTENER_ARN")
                .healthCheckPort(10)
                .thresholdCount(100)
                .endpointConfigurations(endpointConfigurations)
                .healthCheckPath("/Health")
                .trafficDialPercentage(100)
                .healthCheckIntervalSeconds(10)
                .healthCheckProtocol("TCP")
                .listenerArn("arn:aws:globalaccelerator::474880776455:accelerator/abcd1234/listener/12341234")
                .build();
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
    public void handleRequest_returnsMappedEndpointGroup() {
        ResourceModel model = createTestResourceModel();
        DescribeEndpointGroupResult describeEndpointGroupResult = new DescribeEndpointGroupResult()
                .withEndpointGroup(new EndpointGroup()
                        .withEndpointGroupArn("ENDPOINTGROUP_ARN")
                        .withEndpointGroupRegion("us-west-2")
                        .withTrafficDialPercentage(50.0f)
                        .withEndpointDescriptions(createEndpointDescription()));

        doReturn(describeEndpointGroupResult).when(proxy).injectCredentialsAndInvoke(any(DescribeEndpointGroupRequest.class), any());

        val request = ResourceHandlerRequest.<ResourceModel>builder().desiredResourceState(model).build();
        val response = new ReadHandler().handleRequest(proxy, request, null, logger);

        Assertions.assertNotNull(response);
        Assertions.assertEquals(response.getStatus(), OperationStatus.SUCCESS);
        Assertions.assertNotNull(response.getResourceModel());
        Assertions.assertNull(response.getCallbackContext());
        Assertions.assertEquals(response.getResourceModel().getEndpointGroupArn(), "ENDPOINTGROUP_ARN");
        Assertions.assertEquals(response.getResourceModel().getEndpointGroupRegion(), "us-west-2");
    }

    @Test
    public void handleRequest_nonExistingEndpointGroup() {
        ResourceModel model = createTestResourceModel();
        doThrow(new EndpointGroupNotFoundException("NOT FOUND")).when(proxy).
                injectCredentialsAndInvoke(any(DescribeEndpointGroupRequest.class), any());
        val request = ResourceHandlerRequest.<ResourceModel>builder().desiredResourceState(model).build();
        val response = new ReadHandler().handleRequest(proxy, request, null, logger);

        Assertions.assertNotNull(response);
        Assertions.assertEquals(response.getStatus(), OperationStatus.FAILED);
        Assertions.assertEquals(response.getErrorCode(), HandlerErrorCode.NotFound);
        Assertions.assertNull(response.getResourceModel());
    }
}
