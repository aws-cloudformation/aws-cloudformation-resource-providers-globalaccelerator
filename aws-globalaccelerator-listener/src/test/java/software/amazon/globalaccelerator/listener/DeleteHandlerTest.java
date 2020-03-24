package software.amazon.globalaccelerator.listener;

import com.amazonaws.services.globalaccelerator.model.*;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

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
    public void handleRequest_DeletesListener() {
        doReturn(new DescribeListenerResult()
                .withListener(new Listener()
                    .withListenerArn("TEST_LISTENER_ARN")))
                .when(proxy).injectCredentialsAndInvoke(any(DescribeListenerRequest.class), any());

        doReturn(new DeleteListenerResult())
                .when(proxy).injectCredentialsAndInvoke(any(DeleteListenerRequest.class), any());

        doReturn(new DescribeAcceleratorResult()
                .withAccelerator(new Accelerator()
                    .withAcceleratorArn("ACCELERATOR_ARN")
                    .withStatus(AcceleratorStatus.IN_PROGRESS)))
                .when(proxy).injectCredentialsAndInvoke(any(DescribeAcceleratorRequest.class), any());

        final DeleteHandler handler = new DeleteHandler();
        final ResourceModel model = ResourceModel.builder()
                .acceleratorArn("TEST_LISTENER_ARN")
                .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .previousResourceState(model)
                .build();

        final ProgressEvent<ResourceModel, CallbackContext> response
                = handler.handleRequest(proxy, request, null, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getCallbackContext()).isNotNull();
        assertThat(response.getCallbackContext().getStabilizationRetriesRemaining()).isGreaterThan(0);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(1);
        assertThat(response.getResourceModel()).isEqualTo(model);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }
}
