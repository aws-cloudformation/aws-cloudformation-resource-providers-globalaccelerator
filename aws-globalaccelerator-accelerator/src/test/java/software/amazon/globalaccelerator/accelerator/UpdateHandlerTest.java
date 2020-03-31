package software.amazon.globalaccelerator.accelerator;

import com.amazonaws.services.globalaccelerator.model.Accelerator;
import com.amazonaws.services.globalaccelerator.model.AcceleratorNotFoundException;
import com.amazonaws.services.globalaccelerator.model.AcceleratorStatus;
import com.amazonaws.services.globalaccelerator.model.DescribeAcceleratorRequest;
import com.amazonaws.services.globalaccelerator.model.DescribeAcceleratorResult;
import com.amazonaws.services.globalaccelerator.model.UpdateAcceleratorRequest;
import com.amazonaws.services.globalaccelerator.model.UpdateAcceleratorResult;
import lombok.val;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
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

    private List<Tag> listOfTags;
    private List<String> ipAddresses;
    private String ACCELERATOR_ARN = "arn:aws:globalaccelerator::444607872184:accelerator/88127aa5-01d8-484c-80a0-349daaefce1d";


    private ResourceModel createTestResourceModel() {
        Tag t1 = Tag.builder().key("Key1").value("Value1").build();
        listOfTags = new ArrayList<Tag>();
        listOfTags.add(t1);

        ipAddresses = new ArrayList<String>();
        ipAddresses.add("10.10.10.1");
        ipAddresses.add("10.10.10.2");

        return ResourceModel.builder()
                .acceleratorArn(ACCELERATOR_ARN)
                .enabled(true)
                .name("ACCELERATOR_NAME_2")
                .tags(listOfTags)
                .ipAddressType("IPV4")
                .ipAddresses(ipAddresses)
                .build();
    }

    @BeforeEach
    public void setup() {
        proxy = mock(AmazonWebServicesClientProxy.class);
        logger = mock(Logger.class);
    }

    @Test
    public void handleRequest_NonExistingAccelerator() {
        final UpdateHandler handler = new UpdateHandler();
        ResourceModel desiredModel = createTestResourceModel();
        doThrow(AcceleratorNotFoundException.class).when(proxy).injectCredentialsAndInvoke(any(DescribeAcceleratorRequest.class), any());

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(desiredModel)
                .build();

        val response = handler.handleRequest(proxy, request, null, logger);
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.NotFound);
        assertThat(response.getResourceModel()).isNull();

    }

    @Test
    public void handleRequest_UpdateAccelerator_PendingReturnsInProgress() {
        final UpdateHandler handler = new UpdateHandler();
        ResourceModel desiredModel = createTestResourceModel();

        DescribeAcceleratorResult describeAcceleratorResult = new DescribeAcceleratorResult()
                .withAccelerator(new Accelerator()
                        .withAcceleratorArn(ACCELERATOR_ARN)
                        .withStatus(AcceleratorStatus.IN_PROGRESS.toString()));

        UpdateAcceleratorResult updateAcceleratorResult = new UpdateAcceleratorResult()
                .withAccelerator(new Accelerator()
                        .withAcceleratorArn(ACCELERATOR_ARN));

        doReturn(describeAcceleratorResult).when(proxy).injectCredentialsAndInvoke(any(DescribeAcceleratorRequest.class), any());
        doReturn(updateAcceleratorResult).when(proxy).injectCredentialsAndInvoke(any(UpdateAcceleratorRequest.class), any());

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(desiredModel)
                .build();

        final ProgressEvent<ResourceModel, CallbackContext> response
                = handler.handleRequest(proxy, request, null, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getCallbackContext()).isNotNull();
        assertThat(response.getResourceModel()).isNotNull();
        assertThat(response.getResourceModel().getAcceleratorArn()).isEqualTo(ACCELERATOR_ARN);
        assertThat(response.getResourceModel().getEnabled()).isEqualTo(desiredModel.getEnabled());
        assertThat(response.getResourceModel().getName()).isEqualTo(desiredModel.getName());
        assertThat(response.getResourceModel().getIpAddressType()).isEqualTo(desiredModel.getIpAddressType());

        assertThat(response.getCallbackContext().isPendingStabilization()).isEqualTo(true);
    }

    @Test
    public void handleRequest_UpdateAccelerator_DeployedReturnsSuccess() {
        final UpdateHandler handler = new UpdateHandler();
        ResourceModel desiredModel = createTestResourceModel();

        DescribeAcceleratorResult describeAcceleratorResult = new DescribeAcceleratorResult()
                .withAccelerator(new Accelerator()
                        .withAcceleratorArn(ACCELERATOR_ARN)
                        .withStatus(AcceleratorStatus.DEPLOYED.toString()));

        doReturn(describeAcceleratorResult).when(proxy).injectCredentialsAndInvoke(any(DescribeAcceleratorRequest.class), any());

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(desiredModel)
                .build();

        CallbackContext context = new CallbackContext();
        context.setStabilizationRetriesRemaining(100);
        context.setPendingStabilization(true);

        final ProgressEvent<ResourceModel, CallbackContext> response
                = handler.handleRequest(proxy, request, context, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getCallbackContext()).isNull();
        assertThat(response.getResourceModel()).isNotNull();
        assertThat(response.getResourceModel().getAcceleratorArn()).isEqualTo(ACCELERATOR_ARN);
        assertThat(response.getResourceModel().getEnabled()).isEqualTo(desiredModel.getEnabled());
        assertThat(response.getResourceModel().getName()).isEqualTo(desiredModel.getName());
        assertThat(response.getResourceModel().getIpAddressType()).isEqualTo(desiredModel.getIpAddressType());
    }
    @Test
    public void testStabilizationTimeout() {
        final UpdateHandler handler = new UpdateHandler();
        ResourceModel desiredModel = createTestResourceModel();

        DescribeAcceleratorResult describeAcceleratorResult = new DescribeAcceleratorResult()
                .withAccelerator(new Accelerator()
                        .withAcceleratorArn(ACCELERATOR_ARN)
                        .withStatus(AcceleratorStatus.IN_PROGRESS.toString()));

        doReturn(describeAcceleratorResult).when(proxy).injectCredentialsAndInvoke(any(DescribeAcceleratorRequest.class), any());

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(desiredModel)
                .build();

        CallbackContext context = new CallbackContext();
        context.setStabilizationRetriesRemaining(0);
        context.setPendingStabilization(true);

        assertThatExceptionOfType(RuntimeException.class).isThrownBy(() ->
                handler.handleRequest(proxy, request, context, logger)).
                withMessageMatching(HandlerCommons.TIMED_OUT_MESSAGE);
    }
}
