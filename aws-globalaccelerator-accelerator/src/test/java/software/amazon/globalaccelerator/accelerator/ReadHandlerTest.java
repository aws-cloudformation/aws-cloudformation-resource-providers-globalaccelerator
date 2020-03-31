package software.amazon.globalaccelerator.accelerator;

import com.amazonaws.services.globalaccelerator.model.Accelerator;
import com.amazonaws.services.globalaccelerator.model.AcceleratorNotFoundException;
import com.amazonaws.services.globalaccelerator.model.AcceleratorStatus;
import com.amazonaws.services.globalaccelerator.model.DescribeAcceleratorRequest;
import com.amazonaws.services.globalaccelerator.model.DescribeAcceleratorResult;
import com.amazonaws.services.globalaccelerator.model.IpSet;
import lombok.val;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

    private String ACCELERATOR_ARN = "arn:aws:globalaccelerator::444607872184:accelerator/88127aa5-01d8-484c-80a0-349daaefce1d";
    private List<String> ipAddresses;
    private IpSet ipSet;

    private ResourceModel createTestResourceModel() {
        ipAddresses = new ArrayList<String>();
        ipAddresses.add("10.10.10.1");
        ipAddresses.add("10.10.10.2");
        ipSet = new IpSet().withIpAddresses(ipAddresses);
        return ResourceModel.builder()
                .acceleratorArn(ACCELERATOR_ARN)
                .enabled(true)
                .name("Name")
                .ipAddresses(ipAddresses)
                .ipAddressType("IPV4")
                .build();
    }

    @BeforeEach
    public void setup() {
        proxy = mock(AmazonWebServicesClientProxy.class);
        logger = mock(Logger.class);
    }

    @Test
    public void handleRequest_returnsMappedAccelerator() {
        ResourceModel model = createTestResourceModel();
        DescribeAcceleratorResult describeAcceleratorResult = new DescribeAcceleratorResult()
                .withAccelerator(new Accelerator()
                        .withAcceleratorArn(ACCELERATOR_ARN)
                        .withStatus(AcceleratorStatus.IN_PROGRESS.toString())
                        .withEnabled(true)
                        .withIpAddressType("IPV4")
                        .withName("Name")
                        .withIpSets(ipSet)
                );

        doReturn(describeAcceleratorResult).when(proxy).injectCredentialsAndInvoke(any(DescribeAcceleratorRequest.class), any());

        val request = ResourceHandlerRequest.<ResourceModel>builder().desiredResourceState(model).build();
        val response = new ReadHandler().handleRequest(proxy, request, null, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackContext()).isNull();
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();

        assertThat(response.getResourceModel()).isNotNull();
        assertThat(response.getResourceModel().getAcceleratorArn()).isEqualTo(ACCELERATOR_ARN);
        assertThat(response.getResourceModel().getIpAddressType()).isEqualTo("IPV4");
        assertThat(response.getResourceModel().getName()).isEqualTo("Name");
        assertThat(response.getResourceModel().getEnabled()).isEqualTo(true);
        assertThat(response.getResourceModel().getIpAddresses()).isEqualTo(ipAddresses);
    }

    @Test
    public void handleRequest_nonExistingAccelerator() {
        ResourceModel model = createTestResourceModel();
        doThrow(new AcceleratorNotFoundException("NOT FOUND")).when(proxy).
                injectCredentialsAndInvoke(any(DescribeAcceleratorRequest.class), any());
        val request = ResourceHandlerRequest.<ResourceModel>builder().desiredResourceState(model).build();
        val response = new ReadHandler().handleRequest(proxy, request, null, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.NotFound);
        assertThat(response.getResourceModel()).isNull();
    }
}
