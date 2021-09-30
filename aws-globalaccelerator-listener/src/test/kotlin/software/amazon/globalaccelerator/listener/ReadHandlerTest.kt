package software.amazon.globalaccelerator.listener

import com.amazonaws.services.globalaccelerator.model.ClientAffinity
import com.amazonaws.services.globalaccelerator.model.DescribeListenerRequest
import com.amazonaws.services.globalaccelerator.model.DescribeListenerResult
import com.amazonaws.services.globalaccelerator.model.Listener
import com.amazonaws.services.globalaccelerator.model.ListenerNotFoundException
import com.amazonaws.services.globalaccelerator.model.Protocol
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy
import software.amazon.cloudformation.proxy.HandlerErrorCode
import software.amazon.cloudformation.proxy.Logger
import software.amazon.cloudformation.proxy.OperationStatus
import software.amazon.cloudformation.proxy.ResourceHandlerRequest
import java.util.ArrayList


@ExtendWith(MockKExtension::class)
class ReadHandlerTest {
    @MockK
    lateinit var proxy: AmazonWebServicesClientProxy

    @MockK(relaxed = true)
    lateinit var logger: Logger

    @BeforeEach
    fun setup() = MockKAnnotations.init(this)

    private val acceleratorArn = "arn:aws:globalaccelerator::444607872184:accelerator/88127aa5-01d8-484c-80a0-349daaefce1d"
    private val listenerArn = "arn:aws:globalaccelerator::444607872184:accelerator/88127aa5-01d8-484c-80a0-349daaefce1d/listener/ee7358c2"

    @Test
    fun handleRequest_Read_ReturnsCorrectlyMappedModel() {
        val describeListenerResult = DescribeListenerResult()
                .withListener(Listener()
                        .withListenerArn(listenerArn)
                        .withProtocol(Protocol.TCP.toString())
                        .withClientAffinity(ClientAffinity.SOURCE_IP)
                        .withPortRanges(com.amazonaws.services.globalaccelerator.model.PortRange().withFromPort(90).withToPort(90)))
        every { proxy.injectCredentialsAndInvoke(ofType(), ofType<ProxyDescribeListener>()) } returns describeListenerResult

        val portRanges = ArrayList<PortRange>()
        portRanges.add(PortRange(90, 91))
        val model = ResourceModel.builder()
                .listenerArn(listenerArn)
                .acceleratorArn(acceleratorArn)
                .portRanges(portRanges)
                .build()

        val request = ResourceHandlerRequest.builder<ResourceModel>().desiredResourceState(model).build()
        val response = ReadHandler().handleRequest(proxy, request, null, logger)

        assertNotNull(response)
        assertEquals(OperationStatus.SUCCESS, response.status)
        assertNull(response.callbackContext)
        assertNull(response.resourceModels)
        assertNull(response.message)

        assertNotNull(response.resourceModel)
        assertEquals(acceleratorArn, response.resourceModel.acceleratorArn)
        assertEquals(Protocol.TCP.toString(), response.resourceModel.protocol)
        assertEquals(ClientAffinity.SOURCE_IP.toString(), response.resourceModel.clientAffinity)

    }

    @Test
    fun handleRequest_MissingListener_ReturnsFailureAndNullModel() {
        every { proxy.injectCredentialsAndInvoke(ofType(), ofType<ProxyDescribeListener>()) } throws(ListenerNotFoundException("NOT FOUND"))

        val portRanges = ArrayList<PortRange>()
        portRanges.add(PortRange(90, 91))
        val model = ResourceModel.builder()
                .listenerArn(listenerArn)
                .acceleratorArn(acceleratorArn)
                .portRanges(portRanges)
                .build()

        val request = ResourceHandlerRequest.builder<ResourceModel>().desiredResourceState(model).build()
        val response = ReadHandler().handleRequest(proxy, request, null, logger)

        assertNotNull(response)
        assertEquals(OperationStatus.FAILED, response.status)
        assertEquals(HandlerErrorCode.NotFound, response.errorCode)
        assertNull(response.resourceModel)
    }
}

