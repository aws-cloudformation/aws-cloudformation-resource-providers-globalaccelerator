package software.amazon.globalaccelerator.endpointgroup;

import com.amazonaws.services.globalaccelerator.model.Accelerator;
import com.amazonaws.services.globalaccelerator.model.AcceleratorStatus;
import com.amazonaws.services.globalaccelerator.model.DescribeAcceleratorRequest;
import com.amazonaws.services.globalaccelerator.model.DescribeAcceleratorResult;
import com.amazonaws.services.globalaccelerator.model.DescribeEndpointGroupRequest;
import com.amazonaws.services.globalaccelerator.model.DescribeEndpointGroupResult;
import com.amazonaws.services.globalaccelerator.model.EndpointGroup;
import com.amazonaws.services.globalaccelerator.model.EndpointGroupNotFoundException;
import com.amazonaws.services.globalaccelerator.model.UpdateEndpointGroupRequest;
import com.amazonaws.services.globalaccelerator.model.UpdateEndpointGroupResult;
import junit.framework.Assert;
import lombok.val;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.function.Executable;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import java.util.ArrayList;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
public class UpdateHandlerTest {

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

    @Test
    public void handleRequest_NonExistingEndpointGroup() {
        final UpdateHandler handler = new UpdateHandler();
        ResourceModel desiredModel = createTestResourceModel();
        doThrow(EndpointGroupNotFoundException.class).when(proxy).injectCredentialsAndInvoke(any(DescribeEndpointGroupRequest.class), any());

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(desiredModel)
                .build();

        val response = handler.handleRequest(proxy, request, null, logger);
        Assertions.assertNotNull(response);
        Assertions.assertEquals(response.getStatus(), OperationStatus.FAILED);
        Assertions.assertEquals(response.getErrorCode(), HandlerErrorCode.NotFound);
        Assertions.assertNull(response.getResourceModel());
    }

    @Test
    public void handleRequest_UpdateEndpointGroup_PendingReturnsInProgress() {
        final UpdateHandler handler = new UpdateHandler();
        ResourceModel desiredModel = createTestResourceModel();

        val describeEndpointGroupResult = new DescribeEndpointGroupResult()
                .withEndpointGroup(new EndpointGroup()
                        .withEndpointGroupArn("ENDPOINTGROUP_ARN"));

        UpdateEndpointGroupResult updateEndpointGroupResult = new UpdateEndpointGroupResult()
                .withEndpointGroup(new EndpointGroup().withEndpointGroupArn("ENDPOINTGROUP_ARN"));

        doReturn(describeEndpointGroupResult).when(proxy).injectCredentialsAndInvoke(any(DescribeEndpointGroupRequest.class), any());
        doReturn(updateEndpointGroupResult).when(proxy).injectCredentialsAndInvoke(any(UpdateEndpointGroupRequest.class), any());

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(desiredModel)
                .build();

        final ProgressEvent<ResourceModel, CallbackContext> response
                = handler.handleRequest(proxy, request, null, logger);

        Assertions.assertNotNull(response);
        Assertions.assertEquals(response.getStatus(), OperationStatus.IN_PROGRESS);
        Assertions.assertNotNull(response.getResourceModel());
        Assertions.assertNotNull(response.getCallbackContext());
        Assertions.assertEquals(response.getCallbackDelaySeconds(), 0);
        Assertions.assertEquals(response.getResourceModel().getEndpointGroupArn(), "ENDPOINTGROUP_ARN");

        Assertions.assertTrue(response.getCallbackContext().isPendingStabilization());

    }

    @Test
    public void handleRequest_UpdateEndpointGroup_DeployedReturnsSuccess() {
        final UpdateHandler handler = new UpdateHandler();
        ResourceModel desiredModel = createTestResourceModel();

        val describeEndpointGroupResult = new DescribeEndpointGroupResult()
                .withEndpointGroup(new EndpointGroup()
                        .withEndpointGroupArn("ENDPOINTGROUP_ARN"));

        val describeAcceleratorResult = new DescribeAcceleratorResult()
                .withAccelerator(new Accelerator()
                        .withAcceleratorArn("ACCELERATOR_ARN")
                        .withStatus(AcceleratorStatus.DEPLOYED.toString()));

        doReturn(describeAcceleratorResult).when(proxy).injectCredentialsAndInvoke(any(DescribeAcceleratorRequest.class), any());
        doReturn(describeEndpointGroupResult).when(proxy).injectCredentialsAndInvoke(any(DescribeEndpointGroupRequest.class), any());

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(desiredModel)
                .build();

        CallbackContext context = new CallbackContext();
        context.setStabilizationRetriesRemaining(100);
        context.setPendingStabilization(true);

        final ProgressEvent<ResourceModel, CallbackContext> response
                = handler.handleRequest(proxy, request, context, logger);

        Assertions.assertNotNull(response);
        Assertions.assertEquals(response.getStatus(), OperationStatus.SUCCESS);
        Assertions.assertNotNull(response.getResourceModel());
        Assertions.assertNull(response.getCallbackContext());
        Assertions.assertEquals(response.getCallbackDelaySeconds(), 0);
        Assertions.assertEquals(response.getResourceModel().getEndpointGroupArn(), "ENDPOINTGROUP_ARN");
    }

    @Test
    public void testStabilizationTimeout() {
        final UpdateHandler handler = new UpdateHandler();
        ResourceModel desiredModel = createTestResourceModel();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(desiredModel)
                .build();

        val describeEndpointGroupResult = new DescribeEndpointGroupResult()
                .withEndpointGroup(new EndpointGroup()
                        .withEndpointGroupArn("ENDPOINTGROUP_ARN"));
        doReturn(describeEndpointGroupResult).when(proxy).injectCredentialsAndInvoke(any(DescribeEndpointGroupRequest.class), any());

        CallbackContext context = new CallbackContext();
        context.setStabilizationRetriesRemaining(0);
        context.setPendingStabilization(true);

        val excThrown = Assertions.assertThrows(RuntimeException.class, () -> handler.handleRequest(proxy, request, context, logger));
        excThrown.getMessage().contains("Timed out waiting for endpoint group to be deployed.");
    }

}
