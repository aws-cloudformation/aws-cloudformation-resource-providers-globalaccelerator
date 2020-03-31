package software.amazon.globalaccelerator.listener;

import com.amazonaws.services.globalaccelerator.model.Accelerator;
import com.amazonaws.services.globalaccelerator.model.AcceleratorStatus;
import com.amazonaws.services.globalaccelerator.model.ClientAffinity;
import com.amazonaws.services.globalaccelerator.model.DescribeAcceleratorRequest;
import com.amazonaws.services.globalaccelerator.model.DescribeAcceleratorResult;
import com.amazonaws.services.globalaccelerator.model.Listener;
import com.amazonaws.services.globalaccelerator.model.PortRange;
import com.amazonaws.services.globalaccelerator.model.Protocol;
import com.amazonaws.services.globalaccelerator.model.UpdateListenerRequest;
import com.amazonaws.services.globalaccelerator.model.UpdateListenerResult;
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
import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
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

    @Test
    public void handleRequest_InitialUpdate_ReturnsInProgress() {
        // we expect a call to get the accelerator status
        val describeAcceleratorResult = new DescribeAcceleratorResult()
                .withAccelerator(new Accelerator()
                    .withAcceleratorArn("ACCELERATOR_ARN")
                    .withStatus(AcceleratorStatus.IN_PROGRESS.toString()));
        doReturn(describeAcceleratorResult).when(proxy).injectCredentialsAndInvoke(any(DescribeAcceleratorRequest.class), any());

        // we expect a call to update
        val updateListenerResult = new UpdateListenerResult()
                .withListener(new Listener()
                    .withListenerArn("LISTENER_ARN")
                    .withClientAffinity(ClientAffinity.SOURCE_IP.toString())
                    .withProtocol(Protocol.TCP.toString())
                    .withPortRanges(new PortRange().withFromPort(80).withToPort(81)));
        doReturn(updateListenerResult).when(proxy).injectCredentialsAndInvoke(any(UpdateListenerRequest.class), any());


        // create the that will go to our handler
        val portRanges = new ArrayList<software.amazon.globalaccelerator.listener.PortRange>();
        portRanges.add(new software.amazon.globalaccelerator.listener.PortRange(80, 81));
        val model = ResourceModel.builder()
                .listenerArn("LISTENER_ARN")
                .acceleratorArn("ACCELERATOR_ARN")
                .clientAffinity("SOURCE_IP")
                .protocol("TCP")
                .portRanges(portRanges)
                .build();

        val request = ResourceHandlerRequest.<ResourceModel>builder().desiredResourceState(model).build();
        val response = new UpdateHandler().handleRequest(proxy, request, null, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(1);
        assertThat(response.getCallbackContext()).isNotNull();
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();

        // want to confirm data was updated correctly
        assertThat(response.getResourceModel()).isNotNull();
        assertThat(response.getResourceModel()).isEqualTo(model);
    }

    @Test
    public void handleRequest_AcceleratorInProgress_ReturnsInProgress() {
        // intentionally do not provided expectation that UpdateListener is called.
        // strict mocking will cause us to throw if the handler calls update.

        // we expect a call to get the accelerator status
        val describeAcceleratorResult = new DescribeAcceleratorResult()
                .withAccelerator(new Accelerator()
                        .withAcceleratorArn("ACCELERATOR_ARN")
                        .withStatus(AcceleratorStatus.IN_PROGRESS.toString()));
        doReturn(describeAcceleratorResult).when(proxy).injectCredentialsAndInvoke(any(DescribeAcceleratorRequest.class), any());

        // create the that will go to our handler
        val portRanges = new ArrayList<software.amazon.globalaccelerator.listener.PortRange>();
        portRanges.add(new software.amazon.globalaccelerator.listener.PortRange(80, 81));
        val model = ResourceModel.builder()
                .listenerArn("LISTENER_ARN")
                .acceleratorArn("ACCELERATOR_ARN")
                .clientAffinity("SOURCE_IP")
                .protocol("TCP")
                .portRanges(portRanges)
                .build();


        val callbackMap = new HashMap<String, String>();
        callbackMap.put(UpdateHandler.UPDATE_COMPLETED_KEY, Boolean.valueOf(true).toString());
        val context = CallbackContext.builder()
                .stabilizationRetriesRemaining(10)
                .callbackMap(callbackMap)
                .build();

        val request = ResourceHandlerRequest.<ResourceModel>builder().desiredResourceState(model).build();
        val response = new UpdateHandler().handleRequest(proxy, request, context, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(1);
        assertThat(response.getCallbackContext()).isNotNull();
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();

        // want to confirm data was updated correctly
        assertThat(response.getResourceModel()).isNotNull();
        assertThat(response.getResourceModel()).isEqualTo(model);
    }

    @Test
    public void handleRequest_AcceleratorDoneDeploying_ReturnsSuccess() {
        // we expect a call to get the accelerator status
        val describeAcceleratorResult = new DescribeAcceleratorResult()
                .withAccelerator(new Accelerator()
                        .withAcceleratorArn("ACCELERATOR_ARN")
                        .withStatus(AcceleratorStatus.DEPLOYED.toString()));
        doReturn(describeAcceleratorResult).when(proxy).injectCredentialsAndInvoke(any(DescribeAcceleratorRequest.class), any());

        // create the that will go to our handler
        val portRanges = new ArrayList<software.amazon.globalaccelerator.listener.PortRange>();
        portRanges.add(new software.amazon.globalaccelerator.listener.PortRange(80, 81));
        val model = ResourceModel.builder()
                .listenerArn("LISTENER_ARN")
                .acceleratorArn("ACCELERATOR_ARN")
                .clientAffinity("SOURCE_IP")
                .protocol("TCP")
                .portRanges(portRanges)
                .build();

        val callbackMap = new HashMap<String, String>();
        callbackMap.put(UpdateHandler.UPDATE_COMPLETED_KEY, Boolean.valueOf(true).toString());
        val context = CallbackContext.builder()
                .stabilizationRetriesRemaining(10)
                .callbackMap(callbackMap)
                .build();

        val request = ResourceHandlerRequest.<ResourceModel>builder().desiredResourceState(model).build();
        val response = new UpdateHandler().handleRequest(proxy, request, context, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();

        // want to confirm data was updated correctly
        assertThat(response.getResourceModel()).isNotNull();
        assertThat(response.getResourceModel()).isEqualTo(model);
    }

    @Test
    public void handleRequest_ThresholdTimeExceeded() {
        // create the that will go to our handler
        val portRanges = new ArrayList<software.amazon.globalaccelerator.listener.PortRange>();
        portRanges.add(new software.amazon.globalaccelerator.listener.PortRange(80, 81));
        val model = ResourceModel.builder()
                .listenerArn("LISTENER_ARN")
                .acceleratorArn("ACCELERATOR_ARN")
                .clientAffinity("SOURCE_IP")
                .protocol("TCP")
                .portRanges(portRanges)
                .build();

        val callbackMap = new HashMap<String, String>();
        callbackMap.put(UpdateHandler.UPDATE_COMPLETED_KEY, Boolean.valueOf(true).toString());
        val callbackContext = CallbackContext.builder()
                .stabilizationRetriesRemaining(0)
                .callbackMap(callbackMap)
                .build();

        val request = ResourceHandlerRequest.<ResourceModel>builder().desiredResourceState(model).build();
        assertThatExceptionOfType(RuntimeException.class).isThrownBy(() -> new UpdateHandler().handleRequest(proxy, request, callbackContext, logger))
                .withMessageMatching("Timed out waiting for listener to be deployed.");
    }
}
