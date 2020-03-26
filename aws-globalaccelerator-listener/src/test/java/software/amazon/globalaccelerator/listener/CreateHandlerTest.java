package software.amazon.globalaccelerator.listener;

import com.amazonaws.services.globalaccelerator.model.*;
import com.amazonaws.services.globalaccelerator.model.PortRange;
import lombok.val;
import software.amazon.cloudformation.proxy.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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
    public void handleRequest_AcceleratorDoesNotExist() {
        doThrow(new AcceleratorNotFoundException("NOT FOUND"))
                .when(proxy).injectCredentialsAndInvoke(any(DescribeAcceleratorRequest.class), any());

        // create the model
        val portRanges = new ArrayList<software.amazon.globalaccelerator.listener.PortRange>();
        portRanges.add(new software.amazon.globalaccelerator.listener.PortRange(80, 81));
        val model = ResourceModel.builder()
                .acceleratorArn("ACCELERATOR_ARN")
                .protocol(Protocol.TCP.toString())
                .portRanges(portRanges)
                .build();

        // make the handler request
        val request = ResourceHandlerRequest.<ResourceModel>builder().desiredResourceState(model).build();
        val handler = new CreateHandler();
        val response = handler.handleRequest(proxy, request, null, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getCallbackContext()).isNull();
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.NotFound);
    }

    @Test
    public void handleRequest_ListenerDoesNotExist() {
        val createListenerResult = new CreateListenerResult()
                .withListener(new Listener()
                        .withListenerArn("LISTENER_ARN")
                        .withProtocol(Protocol.TCP.toString())
                        .withClientAffinity(ClientAffinity.SOURCE_IP.toString())
                        .withPortRanges(new PortRange().withFromPort(80).withToPort(81)));
        doReturn(createListenerResult).when(proxy).injectCredentialsAndInvoke(any(CreateListenerRequest.class), any());

        val describeAcceleratorResult = new DescribeAcceleratorResult()
                .withAccelerator(new Accelerator()
                    .withAcceleratorArn("ACCELERATOR_ARN"));
        doReturn(describeAcceleratorResult).when(proxy).injectCredentialsAndInvoke(any(DescribeAcceleratorRequest.class), any());

        // create the input
        val portRanges = new ArrayList<software.amazon.globalaccelerator.listener.PortRange>();
        portRanges.add(new software.amazon.globalaccelerator.listener.PortRange(80, 81));

        val model = ResourceModel.builder()
                .acceleratorArn("ACCELERATOR_ARN")
                .protocol(Protocol.TCP.toString())
                .portRanges(portRanges)
                .build();

        val request = ResourceHandlerRequest.<ResourceModel>builder().desiredResourceState(model).build();
        val handler = new CreateHandler();
        val response = handler.handleRequest(proxy, request, null, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getCallbackContext()).isNotNull();
        assertThat(response.getResourceModel()).isNotNull();
        assertThat(response.getResourceModel()).isEqualTo(model);
        assertThat(response.getResourceModel().getClientAffinity()).isEqualTo(ClientAffinity.SOURCE_IP.toString());
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
    }
}
