package software.amazon.globalaccelerator.endpointgroup

import com.amazonaws.services.globalaccelerator.model.EndpointGroup
import com.amazonaws.services.globalaccelerator.model.DescribeEndpointGroupRequest
import com.amazonaws.services.globalaccelerator.model.DescribeEndpointGroupResult
import com.amazonaws.services.globalaccelerator.model.EndpointDescription
import com.amazonaws.services.globalaccelerator.model.EndpointGroupNotFoundException
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

    private val endpointGroupRegion = "us-west-2"
    private val listenerArn = "arn:aws:globalaccelerator::474880776455:accelerator/abcd1234/listener/12341234"
    private val endpointGroupArn = "arn:aws:globalaccelerator::444607872184:accelerator/88127aa5-01d8-484c-80a0-349daaefce1d/listener/ee7358c2/endpoint-group/de69a4b45005"
    private val portOverrides = listOf(PortOverride(80, 8080))


    private fun createTestResourceModel(): ResourceModel {
        val endpointConfigurations = ArrayList<EndpointConfiguration>()
        endpointConfigurations.add(EndpointConfiguration.builder().endpointId("EPID1").build())
        endpointConfigurations.add(EndpointConfiguration.builder().endpointId("EPID2").build())
        return ResourceModel.builder()
                .endpointGroupArn(endpointGroupArn)
                .endpointGroupRegion(endpointGroupRegion)
                .healthCheckPort(10)
                .thresholdCount(100)
                .endpointConfigurations(endpointConfigurations)
                .healthCheckPath("/Health")
                .trafficDialPercentage(100.0)
                .healthCheckIntervalSeconds(10)
                .healthCheckProtocol("TCP")
                .listenerArn(listenerArn)
                .portOverrides(portOverrides)
                .build()
    }

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
    fun handleRequest_returnsMappedEndpointGroup() {
        val model = createTestResourceModel()
        val returnedPortOverrides = com.amazonaws.services.globalaccelerator.model.PortOverride().withListenerPort(80).withEndpointPort(8080)
        val describeEndpointGroupResult = DescribeEndpointGroupResult()
                .withEndpointGroup(EndpointGroup()
                        .withEndpointGroupArn(endpointGroupArn)
                        .withEndpointGroupRegion(endpointGroupRegion)
                        .withTrafficDialPercentage(50.0f)
                        .withEndpointDescriptions(createEndpointDescription())
                        .withPortOverrides(returnedPortOverrides))

        every { proxy.injectCredentialsAndInvoke(ofType(), ofType<ProxyDescribeEndpointGroup>()) } returns describeEndpointGroupResult
        val request = ResourceHandlerRequest.builder<ResourceModel>().desiredResourceState(model).build()
        val response = ReadHandler().handleRequest(proxy, request, null, logger)

        assertNotNull(response)
        assertEquals(OperationStatus.SUCCESS, response.status)
        assertNotNull(response.resourceModel)
        assertNull(response.callbackContext)
        assertEquals(endpointGroupArn, response.resourceModel.endpointGroupArn)
        assertEquals(endpointGroupRegion, response.resourceModel.endpointGroupRegion)
        assertEquals(1, response.resourceModel.portOverrides.size)
        assertEquals(portOverrides[0].listenerPort, response.resourceModel.portOverrides[0].listenerPort)
        assertEquals(portOverrides[0].endpointPort, response.resourceModel.portOverrides[0].endpointPort)
        assertEquals(listenerArn, response.resourceModel.listenerArn)
    }

    @Test
    fun handleRequest_nonExistingEndpointGroup() {
        val model = createTestResourceModel()
        every { proxy.injectCredentialsAndInvoke(ofType(), ofType<ProxyDescribeEndpointGroup>()) } throws(EndpointGroupNotFoundException("NOT FOUND"))
        val request = ResourceHandlerRequest.builder<ResourceModel>().desiredResourceState(model).build()
        val response = ReadHandler().handleRequest(proxy, request, null, logger)

        assertNotNull(response)
        assertEquals(OperationStatus.FAILED, response.status)
        assertEquals(HandlerErrorCode.NotFound, response.errorCode)
        assertNull(response.resourceModel)
    }
}

