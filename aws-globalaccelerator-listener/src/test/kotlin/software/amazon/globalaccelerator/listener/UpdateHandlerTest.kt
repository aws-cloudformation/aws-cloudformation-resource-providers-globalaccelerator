package software.amazon.globalaccelerator.listener

import com.amazonaws.services.globalaccelerator.model.AcceleratorStatus
import com.amazonaws.services.globalaccelerator.model.DescribeAcceleratorResult
import com.amazonaws.services.globalaccelerator.model.ClientAffinity
import com.amazonaws.services.globalaccelerator.model.Protocol
import com.amazonaws.services.globalaccelerator.model.PortRange
import com.amazonaws.services.globalaccelerator.model.Accelerator
import com.amazonaws.services.globalaccelerator.model.DescribeListenerResult
import com.amazonaws.services.globalaccelerator.model.Listener
import com.amazonaws.services.globalaccelerator.model.UpdateListenerResult
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy
import software.amazon.cloudformation.proxy.HandlerErrorCode
import software.amazon.cloudformation.proxy.Logger
import software.amazon.cloudformation.proxy.OperationStatus
import software.amazon.cloudformation.proxy.ResourceHandlerRequest
import java.util.ArrayList
import java.util.HashMap

@ExtendWith(MockKExtension::class)
class UpdateHandlerTest {
    @MockK
    lateinit var proxy: AmazonWebServicesClientProxy

    @MockK(relaxed = true)
    lateinit var logger: Logger

    @BeforeEach
    fun setup() = MockKAnnotations.init(this)

    private val acceleratorArn = "arn:aws:globalaccelerator::444607872184:accelerator/88127aa5-01d8-484c-80a0-349daaefce1d"
    private val listenerArn = "arn:aws:globalaccelerator::444607872184:accelerator/88127aa5-01d8-484c-80a0-349daaefce1d/listener/ee7358c2"

    /**
     * Sets up expectation that describe accelerator will be called on our test mock
     * @param acceleratorStatus the status the describe call should return
     */
    private fun expectDescribeAccelerator(acceleratorStatus: AcceleratorStatus) {
        // we expect a call to get the accelerator status
        val describeAcceleratorResult = DescribeAcceleratorResult()
                .withAccelerator(Accelerator()
                        .withAcceleratorArn(acceleratorArn)
                        .withStatus(acceleratorStatus.toString()))
        every { proxy.injectCredentialsAndInvoke(ofType(), ofType<ProxyDescribeAccelerator>()) } returns describeAcceleratorResult
    }

    private fun createTestResourceModel(): ResourceModel {
        val portRanges = ArrayList<software.amazon.globalaccelerator.listener.PortRange>()
        portRanges.add(software.amazon.globalaccelerator.listener.PortRange(80, 81))
        return ResourceModel.builder()
                .listenerArn(listenerArn)
                .acceleratorArn(acceleratorArn)
                .clientAffinity(ClientAffinity.SOURCE_IP.toString())
                .protocol(Protocol.TCP.toString())
                .portRanges(portRanges)
                .build()
    }

    @Test
    fun handleRequest_InitialUpdate_ReturnsInProgress() {
        val describeListenerResult = DescribeListenerResult()
                .withListener(Listener()
                        .withListenerArn(listenerArn)
                        .withClientAffinity(ClientAffinity.SOURCE_IP.toString())
                        .withProtocol(Protocol.TCP.toString())
                        .withPortRanges(PortRange().withFromPort(80).withToPort(81)))
        every { proxy.injectCredentialsAndInvoke(ofType(), ofType<ProxyDescribeListener>()) } returns describeListenerResult

        val updateListenerResult = UpdateListenerResult()
                .withListener(Listener()
                        .withListenerArn(listenerArn)
                        .withClientAffinity(ClientAffinity.SOURCE_IP.toString())
                        .withProtocol(Protocol.TCP.toString())
                        .withPortRanges(PortRange().withFromPort(80).withToPort(81)))
        every { proxy.injectCredentialsAndInvoke(ofType(), ofType<ProxyUpdateListener>()) } returns updateListenerResult

        val model = createTestResourceModel()
        val request = ResourceHandlerRequest.builder<ResourceModel>().desiredResourceState(model).build()
        val response = UpdateHandler().handleRequest(proxy, request, null, logger)

        assertNotNull(response)
        assertEquals(OperationStatus.IN_PROGRESS, response.status)
        assertEquals(0, response.callbackDelaySeconds)
        assertNotNull(response.callbackContext)
        assertNull(response.resourceModels)
        assertNull(response.message)

        assertNotNull(response.resourceModel)
        assertEquals(model, response.resourceModel)
    }

    @Test
    fun handleRequest_AcceleratorInProgress_ReturnsInProgress() {
        // intentionally do not provided expectation that UpdateListener is called.
        // strict mocking will cause us to throw if the handler calls update.

        // we expect a call to get the accelerator status
        expectDescribeAccelerator(AcceleratorStatus.IN_PROGRESS)

        val model = createTestResourceModel()
        val context = CallbackContext(stabilizationRetriesRemaining = 10, pendingStabilization = true)

        val request = ResourceHandlerRequest.builder<ResourceModel>().desiredResourceState(model).build()
        val response = UpdateHandler().handleRequest(proxy, request, context, logger)


        assertNotNull(response)
        assertEquals(OperationStatus.IN_PROGRESS, response.status)
        assertEquals(1, response.callbackDelaySeconds)
        assertNotNull(response.callbackContext)
        assertNull(response.resourceModels)
        assertNull(response.message)

        assertNotNull(response.resourceModel)
        assertEquals(model, response.resourceModel)
    }

    @Test
    fun handleRequest_AcceleratorDoneDeploying_ReturnsSuccess() {
        expectDescribeAccelerator(AcceleratorStatus.DEPLOYED)

        // create the that will go to our handler
        val model = createTestResourceModel()
        val callbackMap = HashMap<String, String>()
        callbackMap[UpdateHandler.UPDATE_COMPLETED_KEY] = "true"
        val context = CallbackContext(stabilizationRetriesRemaining = 10, pendingStabilization = true)

        val request = ResourceHandlerRequest.builder<ResourceModel>().desiredResourceState(model).build()
        val response = UpdateHandler().handleRequest(proxy, request, context, logger)


        assertNotNull(response)
        assertEquals(OperationStatus.SUCCESS, response.status)
        assertNull(response.resourceModels)
        assertNull(response.message)

        assertNotNull(response.resourceModel)
        assertEquals(model, response.resourceModel)
    }

    @Test
    fun handleRequest_ThresholdTimeExceeded() {
        val model = createTestResourceModel()
        val callbackContext = CallbackContext(stabilizationRetriesRemaining = 0, pendingStabilization = true)

        val request = ResourceHandlerRequest.builder<ResourceModel>().desiredResourceState(model).build()
        val exception = assertThrows(RuntimeException::class.java) {
            UpdateHandler().handleRequest(proxy, request, callbackContext, logger)
        }
        assertEquals("Timed out waiting for listener to be deployed.", exception.message)
    }
}
