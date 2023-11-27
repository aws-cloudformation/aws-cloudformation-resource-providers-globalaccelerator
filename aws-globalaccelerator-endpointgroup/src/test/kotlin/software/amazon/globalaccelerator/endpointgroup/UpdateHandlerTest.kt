package software.amazon.globalaccelerator.endpointgroup

import com.amazonaws.services.globalaccelerator.model.Accelerator
import com.amazonaws.services.globalaccelerator.model.AcceleratorStatus
import com.amazonaws.services.globalaccelerator.model.DescribeAcceleratorRequest
import com.amazonaws.services.globalaccelerator.model.DescribeAcceleratorResult
import com.amazonaws.services.globalaccelerator.model.DescribeEndpointGroupRequest
import com.amazonaws.services.globalaccelerator.model.DescribeEndpointGroupResult
import com.amazonaws.services.globalaccelerator.model.EndpointGroup
import com.amazonaws.services.globalaccelerator.model.EndpointGroupNotFoundException
import com.amazonaws.services.globalaccelerator.model.UpdateEndpointGroupRequest
import com.amazonaws.services.globalaccelerator.model.UpdateEndpointGroupResult
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.verify
import io.mockk.slot
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
import java.util.function.Function
import kotlin.collections.ArrayList

@ExtendWith(MockKExtension::class)
class UpdateHandlerTest {

    @MockK
    lateinit var proxy: AmazonWebServicesClientProxy

    @MockK(relaxed = true)
    lateinit var logger: Logger

    @BeforeEach
    fun setup() = MockKAnnotations.init(this)

    private val endpointGroupRegion = "us-west-2"
    private val acceleratorArn = "arn:aws:globalaccelerator::444607872184:accelerator/88127aa5-01d8-484c-80a0-349daaefce1d"
    private val listenerArn = "arn:aws:globalaccelerator::444607872184:accelerator/88127aa5-01d8-484c-80a0-349daaefce1d/listener/ee7358c2"
    private val endpointGroupArn = "arn:aws:globalaccelerator::444607872184:accelerator/88127aa5-01d8-484c-80a0-349daaefce1d/listener/ee7358c2/endpoint-group/de69a4b45005"


    private fun createTestResourceModel(): ResourceModel {
        val endpointConfigurations = ArrayList<EndpointConfiguration>()
        endpointConfigurations.add(EndpointConfiguration.builder().endpointId("EPID1").weight(100).clientIPPreservationEnabled(true).build())
        endpointConfigurations.add(EndpointConfiguration.builder().endpointId("EPID2").weight(100).clientIPPreservationEnabled(true).build())

        return ResourceModel.builder()
                .endpointGroupArn(endpointGroupArn)
                .endpointGroupRegion(endpointGroupRegion)
                .listenerArn(listenerArn)
                .healthCheckPort(10)
                .thresholdCount(100)
                .endpointConfigurations(endpointConfigurations)
                .healthCheckPath("/Health")
                .trafficDialPercentage(100.0)
                .healthCheckIntervalSeconds(10)
                .healthCheckProtocol("TCP")
                .build()
    }

    @Test
    fun handleRequest_NonExistingEndpointGroup() {
        val desiredModel = createTestResourceModel()
        every { proxy.injectCredentialsAndInvoke(ofType(), ofType<ProxyDescribeEndpointGroup>()) } throws EndpointGroupNotFoundException("NOT FOUND")

        val request = ResourceHandlerRequest.builder<ResourceModel>()
                .desiredResourceState(desiredModel)
                .build()

        val response = UpdateHandler().handleRequest(proxy, request, null, logger)
        assertNotNull(response)
        assertEquals(OperationStatus.FAILED, response.status)
        assertEquals(HandlerErrorCode.NotFound, response.errorCode)
        assertEquals(desiredModel, response.resourceModel)
    }

    @Test
    fun handleRequest_UpdateEndpointGroup_PendingReturnsInProgress() {
        val previousModel = createTestResourceModel()
        val desiredModel = createTestResourceModel()

        val describeEndpointGroupResult = DescribeEndpointGroupResult()
                .withEndpointGroup(EndpointGroup()
                        .withEndpointGroupArn(endpointGroupArn))
        every { proxy.injectCredentialsAndInvoke(ofType(), ofType<ProxyDescribeEndpointGroup>()) } returns describeEndpointGroupResult

        val updateEndpointGroupResult = UpdateEndpointGroupResult()
                .withEndpointGroup(EndpointGroup().withEndpointGroupArn(endpointGroupArn))
        every { proxy.injectCredentialsAndInvoke(ofType(), ofType<ProxyUpdateEndpointGroup>()) } returns updateEndpointGroupResult

        val request = ResourceHandlerRequest.builder<ResourceModel>()
                .desiredResourceState(desiredModel)
                .previousResourceState(previousModel)
                .build()

        val response = UpdateHandler().handleRequest(proxy, request, null, logger)

        assertNotNull(response)
        assertEquals(OperationStatus.IN_PROGRESS, response.status)
        assertNotNull(response.resourceModel)
        assertNotNull(response.callbackContext)
        assertEquals(0, response.callbackDelaySeconds)
        assertEquals(endpointGroupArn, response.resourceModel.endpointGroupArn)
        assertTrue(response.callbackContext!!.pendingStabilization)
    }

    @Test
    fun handleRequest_UpdateEndpointGroup_DeployedReturnsSuccess() {
        val desiredModel = createTestResourceModel()

        val describeAcceleratorResult = DescribeAcceleratorResult()
                .withAccelerator(Accelerator()
                        .withAcceleratorArn(acceleratorArn)
                        .withStatus(AcceleratorStatus.DEPLOYED.toString()))
        every { proxy.injectCredentialsAndInvoke(ofType(), ofType<ProxyDescribeAccelerator>()) } returns describeAcceleratorResult

        val request = ResourceHandlerRequest.builder<ResourceModel>()
                .desiredResourceState(desiredModel)
                .build()

        val context = CallbackContext(100, true)

        val response = UpdateHandler().handleRequest(proxy, request, context, logger)

        assertNotNull(response)
        assertEquals(OperationStatus.SUCCESS, response.status)
        assertNotNull(response.resourceModel)
        assertNull(response.callbackContext)
        assertEquals(0, response.callbackDelaySeconds)
        assertEquals(endpointGroupArn, response.resourceModel.endpointGroupArn)
    }

    @Test
    fun testStabilizationTimeout() {
        val desiredModel = createTestResourceModel()

        val request = ResourceHandlerRequest.builder<ResourceModel>()
                .desiredResourceState(desiredModel)
                .build()

        val context = CallbackContext(0, true)

        val handler = UpdateHandler()
        val exception = assertThrows(RuntimeException::class.java) {
            handler.handleRequest(proxy, request, context, logger)
        }
        assertEquals("Timed out waiting for endpoint group to be deployed.", exception.message)
    }

    @Test
    fun handleRequest_UpdateEndpointGroupWithPortOverrides() {
        val previousModel = createTestResourceModel()
        val desiredModel = createTestResourceModel()
        val portOverrides = listOf(PortOverride(80, 8080))
        desiredModel.portOverrides = portOverrides

        val describeEndpointGroupResult = DescribeEndpointGroupResult()
                .withEndpointGroup(EndpointGroup()
                        .withEndpointGroupArn(endpointGroupArn))
        every { proxy.injectCredentialsAndInvoke(ofType(), ofType<ProxyDescribeEndpointGroup>()) } returns describeEndpointGroupResult

        val updateEndpointGroupResult = UpdateEndpointGroupResult()
                .withEndpointGroup(EndpointGroup().withEndpointGroupArn(endpointGroupArn))
        every { proxy.injectCredentialsAndInvoke(ofType(), ofType<ProxyUpdateEndpointGroup>()) } returns updateEndpointGroupResult

        val request = ResourceHandlerRequest.builder<ResourceModel>()
                .desiredResourceState(desiredModel)
                .previousResourceState(previousModel)
                .build()

        val response = UpdateHandler().handleRequest(proxy, request, null, logger)

        assertNotNull(response)
        assertEquals(OperationStatus.IN_PROGRESS, response.status)
        assertNotNull(response.resourceModel)
        assertNotNull(response.callbackContext)
        assertEquals(0, response.callbackDelaySeconds)
        assertEquals(endpointGroupArn, response.resourceModel.endpointGroupArn)
        assertTrue(response.callbackContext!!.pendingStabilization)
        assertEquals(1, response.resourceModel.portOverrides.size)
        assertEquals(portOverrides[0].listenerPort, response.resourceModel.portOverrides[0].listenerPort)
        assertEquals(portOverrides[0].endpointPort, response.resourceModel.portOverrides[0].endpointPort)
    }

    @Test
    fun handleRequest_UpdateEndpointGroupWithNullWithPreviousPortOverrides() {
        val portOverrides = listOf(PortOverride(80, 8080))
        val previousModel = createTestResourceModel()
        previousModel.portOverrides = portOverrides

        val desiredModel = createTestResourceModel()
        val describeEndpointGroupResult = DescribeEndpointGroupResult()
                .withEndpointGroup(EndpointGroup()
                        .withEndpointGroupArn(endpointGroupArn))
        every { proxy.injectCredentialsAndInvoke(ofType(), ofType<ProxyDescribeEndpointGroup>()) } returns describeEndpointGroupResult

	val updateEndpointGroupRequestSlot = slot<UpdateEndpointGroupRequest>()
        val updateEndpointGroupResult = UpdateEndpointGroupResult()
                .withEndpointGroup(EndpointGroup().withEndpointGroupArn(endpointGroupArn))
        every { proxy.injectCredentialsAndInvoke(capture(updateEndpointGroupRequestSlot), ofType<ProxyUpdateEndpointGroup>()) } returns updateEndpointGroupResult

        val request = ResourceHandlerRequest.builder<ResourceModel>()
                .desiredResourceState(desiredModel)
                .previousResourceState(previousModel)
                .build()

        val response = UpdateHandler().handleRequest(proxy, request, null, logger)

        assertNotNull(response)
        assertEquals(response.status, OperationStatus.IN_PROGRESS)
        assertNotNull(response.resourceModel)
        assertNotNull(response.callbackContext)
        assertEquals(response.callbackDelaySeconds, 0)
        assertEquals(response.resourceModel.endpointGroupArn, endpointGroupArn)
        assertTrue(response.callbackContext!!.pendingStabilization)
        assertNull(response.resourceModel.portOverrides)
        verify(exactly = 1) { proxy.injectCredentialsAndInvoke(ofType(), ofType<ProxyUpdateEndpointGroup>()) }
        assertEquals(ArrayList<String>(), updateEndpointGroupRequestSlot.captured.portOverrides) // Empty array to clean port-overrides.
    }

    @Test
    fun handleRequest_UpdateEndpointGroupWithNullWithPreviousNullPortOverrides() {
        val previousModel = createTestResourceModel()
        val desiredModel = createTestResourceModel()

        val describeEndpointGroupResult = DescribeEndpointGroupResult()
                .withEndpointGroup(EndpointGroup()
                        .withEndpointGroupArn(endpointGroupArn))
        every { proxy.injectCredentialsAndInvoke(ofType(), ofType<ProxyDescribeEndpointGroup>()) } returns describeEndpointGroupResult

        val updateEndpointGroupRequestSlot = slot<UpdateEndpointGroupRequest>()
        val updateEndpointGroupResult = UpdateEndpointGroupResult()
                .withEndpointGroup(EndpointGroup().withEndpointGroupArn(endpointGroupArn))
        every { proxy.injectCredentialsAndInvoke(capture(updateEndpointGroupRequestSlot), ofType<ProxyUpdateEndpointGroup>()) } returns updateEndpointGroupResult

        val request = ResourceHandlerRequest.builder<ResourceModel>()
                .desiredResourceState(desiredModel)
                .previousResourceState(previousModel)
                .build()

        val response = UpdateHandler().handleRequest(proxy, request, null, logger)

        assertNotNull(response)
        assertEquals(OperationStatus.IN_PROGRESS, response.status)
        assertNotNull(response.resourceModel)
        assertNotNull(response.callbackContext)
        assertEquals(0, response.callbackDelaySeconds)
        assertEquals(endpointGroupArn, response.resourceModel.endpointGroupArn)
        assertTrue(response.callbackContext!!.pendingStabilization)
        assertNull(response.resourceModel.portOverrides)
        verify(exactly = 1) { proxy.injectCredentialsAndInvoke(ofType(), ofType<ProxyUpdateEndpointGroup>()) }
        assertNull(updateEndpointGroupRequestSlot.captured.portOverrides)
    }

    @Test
    fun handleRequest_UpdateEndpointGroupWithEmptyPortOverrides() {
        val portOverrides = ArrayList<PortOverride>()
        val previousModel = createTestResourceModel()

        val desiredModel = createTestResourceModel()
        desiredModel.portOverrides = portOverrides
        val describeEndpointGroupResult = DescribeEndpointGroupResult()
                .withEndpointGroup(EndpointGroup()
                        .withEndpointGroupArn(endpointGroupArn))
        every { proxy.injectCredentialsAndInvoke(ofType(), ofType<ProxyDescribeEndpointGroup>()) } returns describeEndpointGroupResult

        val updateEndpointGroupRequestSlot = slot<UpdateEndpointGroupRequest>()
	val updateEndpointGroupResult = UpdateEndpointGroupResult()
                .withEndpointGroup(EndpointGroup().withEndpointGroupArn(endpointGroupArn))
        every { proxy.injectCredentialsAndInvoke(capture(updateEndpointGroupRequestSlot), ofType<ProxyUpdateEndpointGroup>()) } returns updateEndpointGroupResult

        val request = ResourceHandlerRequest.builder<ResourceModel>()
                .desiredResourceState(desiredModel)
                .previousResourceState(previousModel)
                .build()

        val response = UpdateHandler().handleRequest(proxy, request, null, logger)

        assertNotNull(response)
        assertEquals(OperationStatus.IN_PROGRESS, response.status)
        assertNotNull(response.resourceModel)
        assertNotNull(response.callbackContext)
        assertEquals(0, response.callbackDelaySeconds)
        assertEquals(endpointGroupArn, response.resourceModel.endpointGroupArn)
        assertTrue(response.callbackContext!!.pendingStabilization)
        assertEquals(0, response.resourceModel.portOverrides.size)
        verify(exactly = 1) { proxy.injectCredentialsAndInvoke(ofType(), ofType<ProxyUpdateEndpointGroup>()) }
        assertEquals(ArrayList<String>(), updateEndpointGroupRequestSlot.captured.portOverrides) // Empty array to clean port-overrides.
    }

    @Test
    fun handleRequest_UpdateEndpointGroupWithUpdatedEndpointConfigurations() {
        val previousModel = createTestResourceModel()
        val desiredModel = createTestResourceModel()
        val endpointConfigurations = ArrayList<EndpointConfiguration>()
        endpointConfigurations.add(EndpointConfiguration.builder().endpointId("EPID1").weight(50).clientIPPreservationEnabled(false).build())
        endpointConfigurations.add(EndpointConfiguration.builder().endpointId("EPID2").weight(50).clientIPPreservationEnabled(false).build())
        endpointConfigurations.add(EndpointConfiguration.builder().endpointId("EPID3").weight(50).clientIPPreservationEnabled(true).build())
        desiredModel.endpointConfigurations = endpointConfigurations

        val describeEndpointGroupResult = DescribeEndpointGroupResult()
                .withEndpointGroup(EndpointGroup()
                        .withEndpointGroupArn(endpointGroupArn))
        every { proxy.injectCredentialsAndInvoke(ofType(), ofType<ProxyDescribeEndpointGroup>()) } returns describeEndpointGroupResult

        val updateEndpointGroupResult = UpdateEndpointGroupResult()
                .withEndpointGroup(EndpointGroup().withEndpointGroupArn(endpointGroupArn))
        every { proxy.injectCredentialsAndInvoke(ofType(), ofType<ProxyUpdateEndpointGroup>()) } returns updateEndpointGroupResult

        val request = ResourceHandlerRequest.builder<ResourceModel>()
                .desiredResourceState(desiredModel)
                .previousResourceState(previousModel)
                .build()

        val response = UpdateHandler().handleRequest(proxy, request, null, logger)

        assertNotNull(response)
        assertEquals(OperationStatus.IN_PROGRESS, response.status)
        assertNotNull(response.resourceModel)
        assertNotNull(response.callbackContext)
        assertEquals(0, response.callbackDelaySeconds)
        assertEquals(endpointGroupArn, response.resourceModel.endpointGroupArn)
        assertTrue(response.callbackContext!!.pendingStabilization)
        assertEquals(3, response.resourceModel.endpointConfigurations.size)
        assertEquals(response.resourceModel.endpointConfigurations, endpointConfigurations)
    }    
}
