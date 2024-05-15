package software.amazon.globalaccelerator.endpointgroup

import com.amazonaws.services.globalaccelerator.model.EndpointGroup
import com.amazonaws.services.globalaccelerator.model.EndpointDescription
import com.amazonaws.services.globalaccelerator.model.Accelerator
import com.amazonaws.services.globalaccelerator.model.Listener
import com.amazonaws.services.globalaccelerator.model.AcceleratorStatus
import com.amazonaws.services.globalaccelerator.model.DescribeAcceleratorResult
import com.amazonaws.services.globalaccelerator.model.DescribeListenerResult
import com.amazonaws.services.globalaccelerator.model.CreateEndpointGroupResult
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse
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

@ExtendWith(MockKExtension::class)
class CreateHandlerTest {
    @MockK
    lateinit var proxy: AmazonWebServicesClientProxy

    @MockK(relaxed = true)
    lateinit var logger: Logger

    @BeforeEach
    fun setup() = MockKAnnotations.init(this)

    private val endpointGroupRegion = "us-west-2"
    private val listenerArn = "arn:aws:globalaccelerator::474880776455:accelerator/abcd1234/listener/12341234"
    private val endpointGroupArn = "arn:aws:globalaccelerator::444607872184:accelerator/88127aa5-01d8-484c-80a0-349daaefce1d/listener/ee7358c2/endpoint-group/de69a4b45005"

    private fun createEndpointDescription(): List<EndpointDescription> {
        val array = ArrayList<EndpointDescription>()
        array.add(EndpointDescription()
                .withClientIPPreservationEnabled(true)
                .withHealthState("HS1")
                .withEndpointId("ID1")
                .withWeight(100)
                .withHealthReason("Reason1"))
        array.add(EndpointDescription()
                .withClientIPPreservationEnabled(false)
                .withHealthState("HS2")
                .withEndpointId("ID2")
                .withWeight(100)
                .withHealthReason("Reason2"))
        return array
    }

    @Test
    fun handleRequest_InitialCreate_ReturnsInProgress() {
        // return an accelerator that is IN_PROGRESS
        val describeAcceleratorResult = DescribeAcceleratorResult()
                .withAccelerator(Accelerator()
                        .withStatus(AcceleratorStatus.IN_PROGRESS))
        every { proxy.injectCredentialsAndInvoke(ofType(), ofType<ProxyDescribeAccelerator>()) } returns describeAcceleratorResult

        // return a listener
        val describeListenerResult = DescribeListenerResult()
                .withListener(Listener()
                        .withListenerArn(listenerArn))
        every { proxy.injectCredentialsAndInvoke(ofType(), ofType<ProxyDescribeListener>()) } returns describeListenerResult

        // the endpoint group create result
        val createEndpointGroupResult = CreateEndpointGroupResult()
                .withEndpointGroup(EndpointGroup()
                        .withEndpointGroupArn(endpointGroupArn)
                        .withHealthCheckPath("/MYPATH")
                        .withHealthCheckPort(200)
                        .withEndpointGroupRegion(endpointGroupRegion)
                        .withHealthCheckProtocol("HTTP")
                        .withHealthCheckIntervalSeconds(10)
                        .withThresholdCount(4)
                        .withTrafficDialPercentage(100.0f)
                        .withEndpointDescriptions(createEndpointDescription())
                )
        every { proxy.injectCredentialsAndInvoke(ofType(), ofType<ProxyCreateEndpointGroup>()) } returns createEndpointGroupResult

        val model = ResourceModel.builder()
                .endpointGroupRegion(endpointGroupRegion)
                .listenerArn(listenerArn)
                .healthCheckPort(-1)
                .thresholdCount(3)
                .build()

        val request = ResourceHandlerRequest.builder<ResourceModel>().desiredResourceState(model).build()
        val response = CreateHandler().handleRequest(proxy, request, null, logger)

        assertNotNull(response)
        assertEquals(OperationStatus.IN_PROGRESS, response.status)
        assertEquals(0, response.callbackDelaySeconds)
        assertNotNull(response.callbackContext)
        assertNotNull(response.resourceModel)
        assertNull(response.message)
        assertNull(response.resourceModels)
        assertEquals(endpointGroupArn, response.resourceModel.endpointGroupArn)
        assertFalse(response.callbackContext!!.pendingStabilization)

        assertEquals("/MYPATH", response.resourceModel.healthCheckPath)
        assertEquals(200, response.resourceModel.healthCheckPort)
        assertEquals(endpointGroupRegion, response.resourceModel.endpointGroupRegion)
        assertEquals("HTTP", response.resourceModel.healthCheckProtocol)
        assertEquals(10, response.resourceModel.healthCheckIntervalSeconds)
        assertEquals(4, response.resourceModel.thresholdCount)
        assertEquals(100.0, response.resourceModel.trafficDialPercentage)
    }

    @Test
    fun handleRequest_AwaitAcceleratorToGoInSync_ReturnsInProgress() {
        // return an accelerator that is IN_PROGRESS
        val describeAcceleratorResult = DescribeAcceleratorResult()
                .withAccelerator(Accelerator()
                        .withStatus(AcceleratorStatus.IN_PROGRESS))
        every { proxy.injectCredentialsAndInvoke(ofType(), ofType<ProxyDescribeAccelerator>()) } returns describeAcceleratorResult

        // Create the model we will provide to our handler
        val endpointConfigurations = ArrayList<EndpointConfiguration>()
        endpointConfigurations.add(EndpointConfiguration.builder().endpointId("EPID1").attachmentArn("ATT1").build())
        endpointConfigurations.add(EndpointConfiguration.builder().endpointId("EPID2").build())
        val model = ResourceModel.builder()
                .endpointGroupRegion(endpointGroupRegion)
                .listenerArn(listenerArn)
                .endpointGroupArn(endpointGroupArn)
                .healthCheckPort(20)
                .thresholdCount(3)
                .trafficDialPercentage(100.0)
                .healthCheckPath("/HEALTH")
                .endpointConfigurations(endpointConfigurations)
                .build()

        // call the handler
        val request = ResourceHandlerRequest.builder<ResourceModel>().desiredResourceState(model).build()
        val callbackContext = CallbackContext(5)
        val response = CreateHandler().handleRequest(proxy, request, callbackContext, logger)

        assertNotNull(response)
        assertEquals(OperationStatus.IN_PROGRESS, response.status)
        assertEquals(1, response.callbackDelaySeconds)
        assertEquals(4, response.callbackContext!!.stabilizationRetriesRemaining)
        assertNotNull(response.callbackContext)
        assertNull(response.message)
        assertNull(response.resourceModels)
        assertEquals(model, response.resourceModel)
    }

    @Test
    fun handleRequest_AcceleratorIsInSync_ReturnsSuccess() {
        // return an accelerator that is IN_PROGRESS
        val describeAcceleratorResult = DescribeAcceleratorResult()
                .withAccelerator(Accelerator()
                        .withStatus(AcceleratorStatus.DEPLOYED))
        every { proxy.injectCredentialsAndInvoke(ofType(), ofType<ProxyDescribeAccelerator>()) } returns describeAcceleratorResult

        // Create the model we will provide to our handler
        val endpointConfigurations = ArrayList<EndpointConfiguration>()
        endpointConfigurations.add(EndpointConfiguration.builder().endpointId("EPID1").attachmentArn("ATT1").build())
        endpointConfigurations.add(EndpointConfiguration.builder().endpointId("EPID2").build())
        val model = ResourceModel.builder()
                .endpointGroupRegion(endpointGroupRegion)
                .listenerArn(listenerArn)
                .endpointGroupArn(endpointGroupArn)
                .healthCheckPort(20)
                .thresholdCount(3)
                .trafficDialPercentage(100.0)
                .healthCheckPath("/HEALTH")
                .endpointConfigurations(endpointConfigurations)
                .build()

        // call the handler
        val request = ResourceHandlerRequest.builder<ResourceModel>().desiredResourceState(model).build()
        val callbackContext = CallbackContext(5)
        val response = CreateHandler().handleRequest(proxy, request, callbackContext, logger)

        assertNotNull(response)
        assertEquals(OperationStatus.SUCCESS, response.status)
        assertEquals(0, response.callbackDelaySeconds)
        assertNull(response.callbackContext)
        assertNull(response.message)
        assertNull(response.resourceModels)
        assertEquals(model, response.resourceModel)

    }

    @Test
    fun handleRequest_ThresholdTimeExceeded() {
        // Create the model we will provide to our handler
        val endpointConfigurations = ArrayList<EndpointConfiguration>()
        endpointConfigurations.add(EndpointConfiguration.builder().endpointId("EPID1").attachmentArn("ATT1").build())
        endpointConfigurations.add(EndpointConfiguration.builder().endpointId("EPID2").build())
        val model = ResourceModel.builder()
                .endpointGroupRegion(endpointGroupRegion)
                .listenerArn(listenerArn)
                .endpointGroupArn(endpointGroupArn)
                .healthCheckPort(20)
                .thresholdCount(3)
                .trafficDialPercentage(100.0)
                .healthCheckPath("/HEALTH")
                .endpointConfigurations(endpointConfigurations)
                .build()

        // call the handler
        val request = ResourceHandlerRequest.builder<ResourceModel>().desiredResourceState(model).build()
        val callbackContext = CallbackContext(0)
        val handler = CreateHandler()

        val exception = assertThrows(RuntimeException::class.java) {
            handler.handleRequest(proxy, request, callbackContext, logger)
        }
        assertEquals("Timed out waiting for endpoint group to be deployed.", exception.message)
    }

    @Test
    fun handleRequest_CreateEndpointGroupWithPortOverrides_ReturnsInProgress() {
        val describeAcceleratorResult = DescribeAcceleratorResult()
                .withAccelerator(Accelerator()
                        .withStatus(AcceleratorStatus.IN_PROGRESS))
        every { proxy.injectCredentialsAndInvoke(ofType(), ofType<ProxyDescribeAccelerator>()) } returns describeAcceleratorResult

        val describeListenerResult = DescribeListenerResult().withListener(Listener().withListenerArn(listenerArn))
        every { proxy.injectCredentialsAndInvoke(ofType(), ofType<ProxyDescribeListener>()) } returns describeListenerResult

        val returnedPortOverrides = com.amazonaws.services.globalaccelerator.model.PortOverride().withListenerPort(80).withEndpointPort(8080)
        val createEndpointGroupResult = CreateEndpointGroupResult()
                .withEndpointGroup(EndpointGroup()
                        .withEndpointGroupArn(endpointGroupArn)
                        .withHealthCheckPath("/MYPATH")
                        .withHealthCheckPort(200)
                        .withEndpointGroupRegion(endpointGroupRegion)
                        .withHealthCheckProtocol("HTTP")
                        .withHealthCheckIntervalSeconds(10)
                        .withThresholdCount(4)
                        .withTrafficDialPercentage(100.0f)
                        .withEndpointDescriptions(createEndpointDescription())
                        .withPortOverrides(returnedPortOverrides)
                )
        every { proxy.injectCredentialsAndInvoke(ofType(), ofType<ProxyCreateEndpointGroup>()) } returns createEndpointGroupResult

        val portOverrides = listOf(PortOverride(80, 8080))
        val model = ResourceModel.builder()
                .endpointGroupRegion(endpointGroupRegion)
                .listenerArn(listenerArn)
                .healthCheckPort(-1)
                .thresholdCount(3)
                .portOverrides(portOverrides)
                .build()

        val request = ResourceHandlerRequest.builder<ResourceModel>().desiredResourceState(model).build()
        val response = CreateHandler().handleRequest(proxy, request, null, logger)

        assertNotNull(response)
        assertEquals(OperationStatus.IN_PROGRESS, response.status)
        assertEquals(0, response.callbackDelaySeconds)
        assertNotNull(response.callbackContext)
        assertNotNull(response.resourceModel)
        assertNull(response.message)
        assertNull(response.resourceModels)
        assertEquals(endpointGroupArn, response.resourceModel.endpointGroupArn)
        assertFalse(response.callbackContext!!.pendingStabilization)

        assertEquals("/MYPATH", response.resourceModel.healthCheckPath)
        assertEquals(200, response.resourceModel.healthCheckPort)
        assertEquals(endpointGroupRegion, response.resourceModel.endpointGroupRegion)
        assertEquals("HTTP", response.resourceModel.healthCheckProtocol)
        assertEquals(10, response.resourceModel.healthCheckIntervalSeconds)
        assertEquals(4, response.resourceModel.thresholdCount)
        assertEquals(100.0, response.resourceModel.trafficDialPercentage)
        assertEquals(1, response.resourceModel.portOverrides.size)
        assertEquals(returnedPortOverrides.listenerPort, response.resourceModel.portOverrides[0].listenerPort)
        assertEquals(returnedPortOverrides.endpointPort, response.resourceModel.portOverrides[0].endpointPort)
    }
}
