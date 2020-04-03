package software.amazon.globalaccelerator.listener

import com.amazonaws.AmazonWebServiceResult
import com.amazonaws.ResponseMetadata
import com.amazonaws.services.globalaccelerator.model.*
import com.amazonaws.services.globalaccelerator.model.PortRange
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy
import software.amazon.cloudformation.proxy.HandlerErrorCode
import software.amazon.cloudformation.proxy.Logger
import software.amazon.cloudformation.proxy.OperationStatus
import software.amazon.cloudformation.proxy.ResourceHandlerRequest

import java.util.ArrayList

import org.junit.jupiter.api.Assertions
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.*
import java.util.function.Function

@ExtendWith(MockitoExtension::class)
class CreateHandlerTest {

    @Mock
    private var proxy: AmazonWebServicesClientProxy? = null

    @Mock
    private var logger: Logger? = null

    @BeforeEach
    fun setup() {
        proxy = mock(AmazonWebServicesClientProxy::class.java)
        logger = mock(Logger::class.java)
    }

    @Test
    fun handleRequest_AcceleratorDoesNotExist() {

        doThrow(AcceleratorNotFoundException("NOT FOUND")).`when`(proxy)!!.injectCredentialsAndInvoke(any(DescribeAcceleratorRequest::class.java),
                any<Function<DescribeAcceleratorRequest, AmazonWebServiceResult<ResponseMetadata>>>())

        // create the model
        val portRanges = ArrayList<software.amazon.globalaccelerator.listener.PortRange>()
        portRanges.add(PortRange(80, 81))
        val model = ResourceModel.builder()
                .acceleratorArn("ACCELERATOR_ARN")
                .protocol(Protocol.TCP.toString())
                .portRanges(portRanges)
                .build()

        // make the handler request
        val request = ResourceHandlerRequest.builder<ResourceModel>().desiredResourceState(model).build()
        val handler = CreateHandler()
        val response = handler.handleRequest(proxy!!, request, null, logger!!)

        Assertions.assertNotNull(response)
        Assertions.assertEquals(response.getStatus(), OperationStatus.FAILED)
        Assertions.assertNull(response.getCallbackContext())
        Assertions.assertEquals(response.getErrorCode(), HandlerErrorCode.NotFound)
    }

    @Test
    fun handleRequest_ListenerCreation_DoesNotWaitForAcceleratorDeployed() {
        val createListenerResult = CreateListenerResult()
                .withListener(Listener()
                        .withListenerArn("LISTENER_ARN")
                        .withProtocol(Protocol.TCP.toString())
                        .withClientAffinity(ClientAffinity.SOURCE_IP.toString())
                        .withPortRanges(PortRange().withFromPort(80).withToPort(81)))
        doReturn(createListenerResult).`when`(proxy!!).injectCredentialsAndInvoke(ArgumentMatchers.any(CreateListenerRequest::class.java), ArgumentMatchers.any<Function<CreateListenerRequest, AmazonWebServiceResult<ResponseMetadata>>>())
        val describeAcceleratorResult = DescribeAcceleratorResult()
                .withAccelerator(Accelerator()
                        .withAcceleratorArn("ACCELERATOR_ARN")
                        .withStatus(AcceleratorStatus.IN_PROGRESS.toString()))
        doReturn(describeAcceleratorResult).`when`(proxy!!).injectCredentialsAndInvoke(ArgumentMatchers.any(DescribeAcceleratorRequest::class.java), ArgumentMatchers.any<Function<DescribeAcceleratorRequest, AmazonWebServiceResult<ResponseMetadata>>>())

        // create the input
        val portRanges = ArrayList<software.amazon.globalaccelerator.listener.PortRange>()
        portRanges.add(software.amazon.globalaccelerator.listener.PortRange(80, 81))

        val model = ResourceModel.builder()
                .acceleratorArn("ACCELERATOR_ARN")
                .protocol(Protocol.TCP.toString())
                .portRanges(portRanges)
                .build()

        val request = ResourceHandlerRequest.builder<ResourceModel>().desiredResourceState(model).build()
        val handler = CreateHandler()
        val response = handler.handleRequest(proxy!!, request, null, logger!!)

        Assertions.assertNotNull(response)
        Assertions.assertEquals(response.getStatus(), OperationStatus.SUCCESS)
        Assertions.assertNotNull(response.getResourceModel())
        Assertions.assertNull(response.getCallbackContext())
        Assertions.assertEquals(response.getCallbackDelaySeconds(), 0)
        Assertions.assertEquals(response.getResourceModel(), model)
        Assertions.assertNull(response.getMessage())
        Assertions.assertNull(response.getResourceModels())

    }
}
