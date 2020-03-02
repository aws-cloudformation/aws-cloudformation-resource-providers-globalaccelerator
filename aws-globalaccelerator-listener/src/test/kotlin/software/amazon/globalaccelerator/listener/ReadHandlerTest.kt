package software.amazon.globalaccelerator.listener

import com.amazonaws.AmazonWebServiceResult
import com.amazonaws.ResponseMetadata
import com.amazonaws.services.globalaccelerator.model.ClientAffinity
import com.amazonaws.services.globalaccelerator.model.DescribeListenerRequest
import com.amazonaws.services.globalaccelerator.model.DescribeListenerResult
import com.amazonaws.services.globalaccelerator.model.Listener
import com.amazonaws.services.globalaccelerator.model.ListenerNotFoundException
import com.amazonaws.services.globalaccelerator.model.Protocol
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
class ReadHandlerTest {

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
    fun handleRequest_Read_ReturnsCorrectlyMappedModel() {
        // All we really care about is what describe returns to confirm the mapping is
        // working as expected
        val describeListenerResult = DescribeListenerResult()
                .withListener(Listener()
                        .withListenerArn("LISTENER_ARN")
                        .withProtocol(Protocol.TCP.toString())
                        .withClientAffinity(ClientAffinity.SOURCE_IP)
                        .withPortRanges(com.amazonaws.services.globalaccelerator.model.PortRange().withFromPort(90).withToPort(90)))
        doReturn(describeListenerResult).`when`(proxy!!).injectCredentialsAndInvoke(ArgumentMatchers.any(DescribeListenerRequest::class.java), ArgumentMatchers.any<Function<DescribeListenerRequest, AmazonWebServiceResult<ResponseMetadata>>>())

        val portRanges = ArrayList<PortRange>()
        portRanges.add(software.amazon.globalaccelerator.listener.PortRange(90, 91))
        val model = ResourceModel.builder()
                .listenerArn("LISTENER_ARN")
                .acceleratorArn("ACCELERATOR_ARN")
                .portRanges(portRanges)
                .build()

        val request = ResourceHandlerRequest.builder<ResourceModel>().desiredResourceState(model).build()
        val response = ReadHandler().handleRequest(proxy!!, request, null, logger!!)

        Assertions.assertNotNull(response)
        Assertions.assertEquals(response.status, OperationStatus.SUCCESS)
        Assertions.assertNull(response.callbackContext)
        Assertions.assertNull(response.resourceModels)
        Assertions.assertNull(response.message)

        Assertions.assertNotNull(response.resourceModel)
        Assertions.assertEquals(response.resourceModel.acceleratorArn, "ACCELERATOR_ARN")
        Assertions.assertEquals(response.resourceModel.protocol, Protocol.TCP.toString())
        Assertions.assertEquals(response.resourceModel.clientAffinity, ClientAffinity.SOURCE_IP.toString())

    }

    @Test
    fun handleRequest_MissingListener_ReturnsFailureAndNullModel() {

        doThrow(ListenerNotFoundException("NOT FOUND")).`when`(proxy)!!.injectCredentialsAndInvoke(any(DescribeListenerRequest::class.java),
                any<Function<DescribeListenerRequest, AmazonWebServiceResult<ResponseMetadata>>>())

        val portRanges = ArrayList<PortRange>()
        portRanges.add(PortRange(90, 91))
        val model = ResourceModel.builder()
                .listenerArn("LISTENER_ARN")
                .acceleratorArn("ACCELERATOR_ARN")
                .portRanges(portRanges)
                .build()

        val request = ResourceHandlerRequest.builder<ResourceModel>().desiredResourceState(model).build()
        val response = ReadHandler().handleRequest(proxy!!, request, null, logger!!)

        Assertions.assertNotNull(response)
        Assertions.assertEquals(response.status, OperationStatus.FAILED)
        Assertions.assertEquals(response.errorCode, HandlerErrorCode.NotFound)
        Assertions.assertNull(response.resourceModel)
    }
}
