package software.amazon.globalaccelerator.accelerator;


import com.amazonaws.services.globalaccelerator.model.*;
import lombok.val;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

import java.util.ArrayList;

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

    @Test
    public void handleRequest_InitialStateCreatesAccelerator() {
        val result = new CreateAcceleratorResult()
                .withAccelerator(new Accelerator()
                    .withStatus(AcceleratorStatus.IN_PROGRESS)
                    .withEnabled(true)
                    .withAcceleratorArn("ACCELERATOR_ARN"));

        doReturn(result).when(proxy).injectCredentialsAndInvoke(any(CreateAcceleratorRequest.class), any());

        // create the input
        val handler = new CreateHandler();
        val tags = new ArrayList<Tag>();
        tags.add(software.amazon.globalaccelerator.accelerator.Tag.builder().key("K1").value("V1").build());
        val model = ResourceModel.builder().enabled(true).name("AcceleratorTest").tags(tags).build();
        val request = ResourceHandlerRequest.<ResourceModel>builder().desiredResourceState(model).build();

        val response = handler.handleRequest(proxy, request, null, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getCallbackContext()).isNotNull();
        assertThat(response.getResourceModel()).isNotNull();
        assertThat(response.getResourceModel().getAcceleratorArn()).isEqualTo("ACCELERATOR_ARN");
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
    }

    @Test
    public void handleRequest_PendingDeployedReturnsInProgress() {
        val result = new DescribeAcceleratorResult()
                .withAccelerator(new Accelerator()
                        .withStatus(AcceleratorStatus.IN_PROGRESS)
                        .withEnabled(true)
                        .withAcceleratorArn("ACCELERATOR_ARN"));

        doReturn(result).when(proxy).injectCredentialsAndInvoke(any(DescribeAcceleratorRequest.class), any());

        // create the input
        val handler = new CreateHandler();
        val tags = new ArrayList<Tag>();
        tags.add(software.amazon.globalaccelerator.accelerator.Tag.builder().key("K1").value("V1").build());
        val model = ResourceModel.builder()
                .enabled(true)
                .name("AcceleratorTest")
                .tags(tags)
                .acceleratorArn("ACCELERATOR_ARN").build();
        val request = ResourceHandlerRequest.<ResourceModel>builder().desiredResourceState(model).build();

        val callbackContext = CallbackContext.builder().stabilizationRetriesRemaining(10).build();
        val response = handler.handleRequest(proxy, request, callbackContext, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(1);
        assertThat(response.getCallbackContext()).isNotNull();
        assertThat(response.getCallbackContext().getStabilizationRetriesRemaining()).isEqualTo(callbackContext.getStabilizationRetriesRemaining()-1);
        assertThat(response.getResourceModel()).isNotNull();
        assertThat(response.getResourceModel().getAcceleratorArn()).isEqualTo("ACCELERATOR_ARN");
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
    }

    @Test
    public void handleRequest_DeployedReturnsSuccess() {
        val result = new DescribeAcceleratorResult()
                .withAccelerator(new Accelerator()
                        .withStatus(AcceleratorStatus.DEPLOYED)
                        .withEnabled(true)
                        .withAcceleratorArn("ACCELERATOR_ARN"));

        doReturn(result).when(proxy).injectCredentialsAndInvoke(any(DescribeAcceleratorRequest.class), any());

        // create the input
        val handler = new CreateHandler();
        val tags = new ArrayList<Tag>();
        tags.add(software.amazon.globalaccelerator.accelerator.Tag.builder().key("K1").value("V1").build());
        val model = ResourceModel.builder()
                .enabled(true)
                .name("AcceleratorTest")
                .tags(tags)
                .acceleratorArn("ACCELERATOR_ARN").build();
        val request = ResourceHandlerRequest.<ResourceModel>builder().desiredResourceState(model).build();

        val callbackContext = CallbackContext.builder().stabilizationRetriesRemaining(10).build();
        val response = handler.handleRequest(proxy, request, callbackContext, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getCallbackContext()).isNull();
        assertThat(response.getResourceModel()).isNotNull();
        assertThat(response.getResourceModel().getAcceleratorArn()).isEqualTo("ACCELERATOR_ARN");
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
    }

    @Test
    public void handleRequest_StabilizationTooLongFails() {
        // create the input
        val handler = new CreateHandler();
        val model = ResourceModel.builder()
                .acceleratorArn("ACCELERATOR_ARN").build();
        val request = ResourceHandlerRequest.<ResourceModel>builder().desiredResourceState(model).build();

        val callbackContext = CallbackContext.builder().stabilizationRetriesRemaining(0).build();
        assertThatExceptionOfType(RuntimeException.class).isThrownBy(() -> handler.handleRequest(proxy, request, callbackContext, logger))
            .withMessageMatching("Timed out waiting for global accelerator to be deployed.");
    }
}
