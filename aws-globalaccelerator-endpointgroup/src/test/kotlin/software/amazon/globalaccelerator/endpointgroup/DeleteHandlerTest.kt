package software.amazon.globalaccelerator.endpointgroup

import com.amazonaws.services.globalaccelerator.model.Accelerator
import com.amazonaws.services.globalaccelerator.model.AcceleratorNotFoundException
import com.amazonaws.services.globalaccelerator.model.AcceleratorStatus
import com.amazonaws.services.globalaccelerator.model.DeleteEndpointGroupRequest
import com.amazonaws.services.globalaccelerator.model.DeleteEndpointGroupResult
import com.amazonaws.services.globalaccelerator.model.DescribeAcceleratorRequest
import com.amazonaws.services.globalaccelerator.model.DescribeAcceleratorResult
import com.amazonaws.services.globalaccelerator.model.DescribeEndpointGroupRequest
import com.amazonaws.services.globalaccelerator.model.DescribeEndpointGroupResult
import com.amazonaws.services.globalaccelerator.model.EndpointGroup
import com.amazonaws.services.globalaccelerator.model.EndpointGroupNotFoundException
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
import java.util.function.Function

@ExtendWith(MockKExtension::class)
class DeleteHandlerTest {
    @MockK
    lateinit var proxy: AmazonWebServicesClientProxy

    @MockK(relaxed = true)
    lateinit var logger: Logger

    @BeforeEach
    fun setup() = MockKAnnotations.init(this)

    private val endpointGroupRegion = "us-west-2"
    private val listenerArn = "arn:aws:globalaccelerator::444607872184:accelerator/88127aa5-01d8-484c-80a0-349daaefce1d/listener/ee7358c2"
    private val endpointGroupArn = "arn:aws:globalaccelerator::444607872184:accelerator/88127aa5-01d8-484c-80a0-349daaefce1d/listener/ee7358c2/endpoint-group/de69a4b45005"


    @Test
    fun handleRequest_ResourceStillExists_DeletesAndReturnsInProgress() {
        val describeEndpointGroupResult = DescribeEndpointGroupResult()
                .withEndpointGroup(EndpointGroup()
                        .withEndpointGroupArn(endpointGroupArn))
        every { proxy.injectCredentialsAndInvoke(ofType(), ofType<ProxyDescribeEndpointGroup>()) } returns describeEndpointGroupResult

        every { proxy.injectCredentialsAndInvoke(ofType(), ofType<ProxyDeleteEndpointGroup>()) } returns DeleteEndpointGroupResult()

        val model = ResourceModel.builder()
                .endpointGroupRegion(endpointGroupRegion)
                .listenerArn(listenerArn)
                .endpointGroupArn(endpointGroupArn)
                .healthCheckPort(-1)
                .thresholdCount(3)
                .build()

        val request = ResourceHandlerRequest.builder<ResourceModel>().desiredResourceState(model).build()
        val response = DeleteHandler().handleRequest(proxy, request, null, logger)

        assertNotNull(response)
        assertEquals(OperationStatus.IN_PROGRESS, response.status)
        assertEquals(0, response.callbackDelaySeconds)
        assertNotNull(response.callbackContext)
        assertNull(response.message)
        assertNull(response.resourceModels)
        assertEquals(model, response.resourceModel)
    }

    @Test
    fun handleRequest_ResourceDoesNotExists_AcceleratorInProgress_ReturnsInProgress() {
        val describeAcceleratorResult = DescribeAcceleratorResult()
                .withAccelerator(Accelerator()
                        .withStatus(AcceleratorStatus.IN_PROGRESS))
        every { proxy.injectCredentialsAndInvoke(ofType(), ofType<ProxyDescribeAccelerator>()) } returns describeAcceleratorResult

        val model = ResourceModel.builder()
                .endpointGroupRegion(endpointGroupRegion)
                .listenerArn(listenerArn)
                .endpointGroupArn(endpointGroupArn)
                .healthCheckPort(-1)
                .thresholdCount(3)
                .build()
        val request = ResourceHandlerRequest.builder<ResourceModel>().desiredResourceState(model).build()
        val context = CallbackContext(2, pendingStabilization = true)

        val response = DeleteHandler().handleRequest(proxy, request, context, logger)

        assertNotNull(response)
        assertEquals(OperationStatus.IN_PROGRESS, response.status)
        assertEquals(1, response.callbackDelaySeconds)
        assertNotNull(response.callbackContext)
        assertNull(response.message)
        assertNull(response.resourceModels)
        assertEquals(model, response.resourceModel)
    }

    @Test
    fun handleRequest_ResourceDoesNotExist_AcceleratorDeployed_ReturnsSuccess() {
        val model = ResourceModel.builder()
                .endpointGroupRegion(endpointGroupRegion)
                .listenerArn(listenerArn)
                .endpointGroupArn(endpointGroupArn)
                .healthCheckPort(-1)
                .thresholdCount(3)
                .build()

        every { proxy.injectCredentialsAndInvoke(ofType(), ofType<ProxyDescribeEndpointGroup>()) } throws(EndpointGroupNotFoundException("NOT FOUND"))

        val request = ResourceHandlerRequest.builder<ResourceModel>().desiredResourceState(model).build()
        val response = DeleteHandler().handleRequest(proxy, request, null, logger)

        assertNotNull(response)
        assertEquals(OperationStatus.FAILED, response.status)
        assertEquals(0, response.callbackDelaySeconds)
        assertNull(response.callbackContext)
        assertEquals("Endpoint Group not found.", response.message)
        assertNull(response.resourceModels)
        assertEquals(model, response.resourceModel)
    }

    @Test
    fun handleRequest_ThresholdTimeExceeded() {
        val endpointConfigurations = ArrayList<EndpointConfiguration>()
        endpointConfigurations.add(EndpointConfiguration.builder().endpointId("EPID1").build())
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
        val request = ResourceHandlerRequest.builder<ResourceModel>().desiredResourceState(model).build()
        val callbackContext = CallbackContext(0, pendingStabilization = true)
        val handler = DeleteHandler()

        val exception = assertThrows(RuntimeException::class.java) {
            handler.handleRequest(proxy, request, callbackContext, logger)
        }

        assertEquals("Timed out waiting for endpoint group to be deployed.", exception.message)
    }

    @Test
    fun handleRequest_AcceleratorDoesntExist() {
        val endpointConfigurations = ArrayList<EndpointConfiguration>()
        endpointConfigurations.add(EndpointConfiguration.builder().endpointId("EPID1").build())
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

        every { proxy.injectCredentialsAndInvoke(ofType(), ofType<ProxyDescribeAccelerator>()) } throws(AcceleratorNotFoundException("NOT FOUND"))

        val request = ResourceHandlerRequest.builder<ResourceModel>().desiredResourceState(model).build()
        val callbackContext = CallbackContext(10, pendingStabilization = true)
        val response = DeleteHandler().handleRequest(proxy, request, callbackContext, logger)

        assertNotNull(response)
        assertEquals(OperationStatus.SUCCESS, response.status)
        assertNull(response.callbackContext)
        assertNull(response.resourceModel)
        assertNull(response.message)
        assertNull(response.errorCode)
        assertNull(response.resourceModels)
    }
}
