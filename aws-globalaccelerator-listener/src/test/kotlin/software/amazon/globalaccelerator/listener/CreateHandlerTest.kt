package software.amazon.globalaccelerator.listener

import com.amazonaws.services.globalaccelerator.model.Listener
import com.amazonaws.services.globalaccelerator.model.CreateListenerResult
import com.amazonaws.services.globalaccelerator.model.DescribeAcceleratorResult
import com.amazonaws.services.globalaccelerator.model.Accelerator
import com.amazonaws.services.globalaccelerator.model.AcceleratorStatus
import com.amazonaws.services.globalaccelerator.model.AcceleratorNotFoundException
import com.amazonaws.services.globalaccelerator.model.Protocol
import com.amazonaws.services.globalaccelerator.model.ClientAffinity
import com.amazonaws.services.globalaccelerator.model.PortRange
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
class CreateHandlerTest {
    @MockK
    lateinit var proxy: AmazonWebServicesClientProxy

    @MockK(relaxed = true)
    lateinit var logger: Logger

    @BeforeEach
    fun setup() = MockKAnnotations.init(this)

    private val acceleratorArn = "arn:aws:globalaccelerator::444607872184:accelerator/88127aa5-01d8-484c-80a0-349daaefce1d"
    private val listenerArn = "arn:aws:globalaccelerator::444607872184:accelerator/88127aa5-01d8-484c-80a0-349daaefce1d/listener/ee7358c2"

    @Test
    fun handleRequest_AcceleratorDoesNotExist() {
        // create the model
        val portRanges = ArrayList<software.amazon.globalaccelerator.listener.PortRange>()
        portRanges.add(PortRange(80, 81))
        val model = ResourceModel.builder()
                .acceleratorArn(acceleratorArn)
                .protocol(Protocol.TCP.toString())
                .portRanges(portRanges)
                .build()
        every { proxy.injectCredentialsAndInvoke(ofType(), ofType<ProxyDescribeAccelerator>()) } throws(AcceleratorNotFoundException("NOT FOUND"))

        // make the handler request
        val request = ResourceHandlerRequest.builder<ResourceModel>().desiredResourceState(model).build()
        val response = CreateHandler().handleRequest(proxy, request, null, logger)

        assertNotNull(response)
        assertEquals(OperationStatus.FAILED, response.status)
        assertNull(response.callbackContext)
        assertEquals(HandlerErrorCode.NotFound, response.errorCode)
    }

    @Test
    fun handleRequest_ListenerCreation_DoesNotWaitForAcceleratorDeployed() {
        val createListenerResult = CreateListenerResult()
                .withListener(Listener()
                        .withListenerArn(listenerArn)
                        .withProtocol(Protocol.TCP.toString())
                        .withClientAffinity(ClientAffinity.SOURCE_IP.toString())
                        .withPortRanges(PortRange().withFromPort(80).withToPort(81)))
        every { proxy.injectCredentialsAndInvoke(ofType(), ofType<ProxyCreateListener>()) } returns createListenerResult

        val describeAcceleratorResult = DescribeAcceleratorResult()
                .withAccelerator(Accelerator()
                        .withAcceleratorArn(acceleratorArn)
                        .withStatus(AcceleratorStatus.IN_PROGRESS.toString()))
        every { proxy.injectCredentialsAndInvoke(ofType(), ofType<ProxyDescribeAccelerator>()) } returns describeAcceleratorResult

        // create the input
        val portRanges = ArrayList<software.amazon.globalaccelerator.listener.PortRange>()
        portRanges.add(PortRange(80, 81))

        val model = ResourceModel.builder()
                .acceleratorArn(acceleratorArn)
                .protocol(Protocol.TCP.toString())
                .portRanges(portRanges)
                .build()

        val request = ResourceHandlerRequest.builder<ResourceModel>().desiredResourceState(model).build()
        val response = CreateHandler().handleRequest(proxy, request, null, logger)

        assertNotNull(response)
        assertEquals(OperationStatus.SUCCESS, response.status)
        assertNotNull(response.resourceModel)
        assertNull(response.callbackContext)
        assertEquals(0, response.callbackDelaySeconds)
        assertEquals(model, response.resourceModel)
        assertNull(response.message)
        assertNull(response.resourceModels)
    }
}

