package software.amazon.globalaccelerator.listener;

import com.amazonaws.services.globalaccelerator.model.*;
import com.amazonaws.services.lambda.runtime.Client;
import lombok.val;
import software.amazon.cloudformation.proxy.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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

    @Test
    public void handleRequest_Read_ReturnsCorrectlyMappedModel() {
        // All we really care about is what describe returns to confirm the mapping is
        // working as expected
        val describeListenerResult = new DescribeListenerResult()
                .withListener(new Listener()
                        .withListenerArn("LISTENER_ARN")
                        .withProtocol(Protocol.TCP.toString())
                        .withClientAffinity(ClientAffinity.SOURCE_IP)
                        .withPortRanges(new com.amazonaws.services.globalaccelerator.model.PortRange().withFromPort(90).withToPort(90)));
        doReturn(describeListenerResult).when(proxy).injectCredentialsAndInvoke(any(DescribeListenerRequest.class), any());

        val portRanges = new ArrayList<PortRange>();
        portRanges.add(new software.amazon.globalaccelerator.listener.PortRange(90, 91));
        val model = ResourceModel.builder()
                .listenerArn("LISTENER_ARN")
                .acceleratorArn("ACCELERATOR_ARN")
                .portRanges(portRanges)
                .build();

        val request = ResourceHandlerRequest.<ResourceModel>builder().desiredResourceState(model).build();
        val response = new ReadHandler().handleRequest(proxy, request, null, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackContext()).isNull();
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();

        // want to confirm data was updated correctly
        assertThat(response.getResourceModel()).isNotNull();
        assertThat(response.getResourceModel().getAcceleratorArn()).isEqualTo("ACCELERATOR_ARN");
        assertThat(response.getResourceModel().getProtocol()).isEqualTo(Protocol.TCP.toString());
        assertThat(response.getResourceModel().getClientAffinity()).isEqualTo(ClientAffinity.SOURCE_IP.toString());
    }

    @Test
    public void handleRequest_MissingListener_ReturnsFailureAndNullModel() {
        doThrow(new ListenerNotFoundException("NOT FOUND")).when(proxy).injectCredentialsAndInvoke(any(DescribeListenerRequest.class), any());

        val portRanges = new ArrayList<PortRange>();
        portRanges.add(new software.amazon.globalaccelerator.listener.PortRange(90, 91));
        val model = ResourceModel.builder()
                .listenerArn("LISTENER_ARN")
                .acceleratorArn("ACCELERATOR_ARN")
                .portRanges(portRanges)
                .build();

        val request = ResourceHandlerRequest.<ResourceModel>builder().desiredResourceState(model).build();
        val response = new ReadHandler().handleRequest(proxy, request, null, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.NotFound);
        assertThat(response.getResourceModel()).isNull();
    }
}
