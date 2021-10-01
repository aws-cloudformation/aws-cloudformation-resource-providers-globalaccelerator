package software.amazon.globalaccelerator.endpointgroup

import com.amazonaws.services.globalaccelerator.model.EndpointGroup
import com.amazonaws.services.globalaccelerator.model.ListEndpointGroupsRequest
import com.amazonaws.services.globalaccelerator.model.ListEndpointGroupsResult
import com.amazonaws.services.globalaccelerator.model.EndpointDescription
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.slot
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

@ExtendWith(MockKExtension::class)
class ListHandlerTest {
    @MockK
    lateinit var proxy: AmazonWebServicesClientProxy

    @MockK(relaxed = true)
    lateinit var logger: Logger

    @BeforeEach
    fun setup() = MockKAnnotations.init(this)

    private val endpointGroupRegion = "us-west-2"
    private val listenerArn = "arn:aws:globalaccelerator::444607872184:accelerator/88127aa5-01d8-484c-80a0-349daaefce1d/listener/ee7358c2"
    private val endpointGroupArn1 = "arn:aws:globalaccelerator::444607872184:accelerator/88127aa5-01d8-484c-80a0-349daaefce1d/listener/ee7358c2/endpoint-group/de69a4b45005"
    private val endpointId1_1 = "EPID1"
    private val endpointId1_2 = "EPID2"
    private val portOverrides1 = listOf(PortOverride(80, 8080))

    private val endpointGroupArn2 = "arn:aws:globalaccelerator::444607872184:accelerator/88127aa5-01d8-484c-80a0-349daaefce1d/listener/ee8675c2endpoint-group/de69a4b45665"
    private val endpointId2_1 = "EPID3"
    private val endpointId2_2 = "EPID4"
    private val portOverrides2 = listOf(PortOverride(842, 4242))

    private fun createTestResourceModel(arn: String, endpointId1: String, endpointId2: String,
                                        portOverrides: List<PortOverride>): ResourceModel {
        val endpointConfigurations = ArrayList<EndpointConfiguration>()
        endpointConfigurations.add(EndpointConfiguration.builder().endpointId(endpointId1).build())
        endpointConfigurations.add(EndpointConfiguration.builder().endpointId(endpointId2).build())
        return ResourceModel.builder()
                .endpointGroupArn(arn)
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

    private fun createEndpointDescription(endpointId1: String, endpointId2: String): List<EndpointDescription> {
        val array = ArrayList<EndpointDescription>()
        array.add(EndpointDescription()
                .withClientIPPreservationEnabled(true)
                .withHealthState("HS1")
                .withEndpointId(endpointId1)
                .withWeight(100)
                .withHealthReason("Reason1"))
        array.add(EndpointDescription()
                .withClientIPPreservationEnabled(false)
                .withHealthState("HS2")
                .withEndpointId(endpointId2)
                .withWeight(100)
                .withHealthReason("Reason2"))
        return array
    }

    @Test
    fun handleRequest_returnsMappedEndpointGroups() {
        val model1 = createTestResourceModel(endpointGroupArn1, endpointId1_1, endpointId1_2, portOverrides1)
        val model2 = createTestResourceModel(endpointGroupArn2, endpointId2_1, endpointId2_2, portOverrides2)
        val returnedPortOverrides1 = com.amazonaws.services.globalaccelerator.model.PortOverride()
                .withListenerPort(portOverrides1[0].listenerPort)
                .withEndpointPort(portOverrides1[0].endpointPort)
        val returnedPortOverrides2 = com.amazonaws.services.globalaccelerator.model.PortOverride()
                .withListenerPort(portOverrides2[0].listenerPort)
                .withEndpointPort(portOverrides2[0].endpointPort)
        val endpointGroups = mutableListOf(
                EndpointGroup()
                        .withEndpointGroupArn(endpointGroupArn1)
                        .withEndpointGroupRegion(endpointGroupRegion)
                        .withTrafficDialPercentage(50.0f)
                        .withEndpointDescriptions(createEndpointDescription(endpointId1_1, endpointId1_2))
                        .withPortOverrides(returnedPortOverrides1),
                EndpointGroup()
                        .withEndpointGroupArn(endpointGroupArn2)
                        .withEndpointGroupRegion(endpointGroupRegion)
                        .withTrafficDialPercentage(50.0f)
                        .withEndpointDescriptions(createEndpointDescription(endpointId2_1, endpointId2_2))
                        .withPortOverrides(returnedPortOverrides2)
        )
        val listEndpointGroupsResult = ListEndpointGroupsResult().withEndpointGroups(endpointGroups)
        every { proxy.injectCredentialsAndInvoke(ofType(), ofType<ProxyListEndpointGroups>()) } returns listEndpointGroupsResult
        val request = ResourceHandlerRequest.builder<ResourceModel>().desiredResourceState(model1).build()
        val response = ListHandler().handleRequest(proxy, request, null, logger)
        assertNotNull(response)
        assertEquals(OperationStatus.SUCCESS, response.status)
        assertNull(response.callbackContext)
        assertNull(response.resourceModel)
        assertNull(response.message)
        assertNotNull(response.resourceModels)
        assertNull(response.nextToken)
        assertEquals(2, response.resourceModels.size)
        assertEquals(listenerArn, response.resourceModels[0].listenerArn)
        assertEquals(model1.endpointGroupArn, response.resourceModels[0].endpointGroupArn)
        assertEquals(model1.endpointGroupRegion, response.resourceModels[0].endpointGroupRegion)
        assertEquals(1, response.resourceModels[0].portOverrides.size)
        assertEquals(portOverrides1[0].listenerPort, response.resourceModels[0].portOverrides[0].listenerPort)
        assertEquals(portOverrides1[0].endpointPort, response.resourceModels[0].portOverrides[0].endpointPort)
        assertEquals(listenerArn, response.resourceModels[1].listenerArn)
        assertEquals(model2.endpointGroupArn, response.resourceModels[1].endpointGroupArn)
        assertEquals(model2.endpointGroupRegion, response.resourceModels[1].endpointGroupRegion)
        assertEquals(1, response.resourceModels[1].portOverrides.size)
        assertEquals(portOverrides2[0].listenerPort, response.resourceModels[1].portOverrides[0].listenerPort)
        assertEquals(portOverrides2[0].endpointPort, response.resourceModels[1].portOverrides[0].endpointPort)
    }

    @Test
    fun handleRequest_noEndpointGroups() {
        val model = createTestResourceModel(endpointGroupArn1, endpointId1_1, endpointId1_2, portOverrides1)
        val listEndpointGroupsResult = ListEndpointGroupsResult().withEndpointGroups(emptyList())
        every { proxy.injectCredentialsAndInvoke(ofType(), ofType<ProxyListEndpointGroups>()) } returns listEndpointGroupsResult
        val request = ResourceHandlerRequest.builder<ResourceModel>().desiredResourceState(model).build()
        val response = ListHandler().handleRequest(proxy, request, null, logger)
        assertNotNull(response)
        assertEquals(OperationStatus.SUCCESS, response.status)
        assertNotNull(response.resourceModels)
        assertEquals(0, response.resourceModels.size)
        assertNull(response.nextToken)
    }

    @Test
    fun handleRequest_returnsMappedEndpointGroupsWithToken() {
	val sentNextToken = "next_token"
	val expectedNextToken = "This_token_is_expected"
	val model = createTestResourceModel(endpointGroupArn1, endpointId1_1, endpointId1_2, portOverrides1)
	val returnedPortOverrides = com.amazonaws.services.globalaccelerator.model.PortOverride()
        	.withListenerPort(portOverrides1[0].listenerPort)
        	.withEndpointPort(portOverrides1[0].endpointPort)
	val endpointGroups = mutableListOf(
		EndpointGroup()
			.withEndpointGroupArn(endpointGroupArn1)
			.withEndpointGroupRegion(endpointGroupRegion)
			.withTrafficDialPercentage(50.0f)
			.withEndpointDescriptions(createEndpointDescription(endpointId1_1, endpointId1_2))
			.withPortOverrides(returnedPortOverrides)
	)

	val listEndpointGroupsRequestSlot = slot<ListEndpointGroupsRequest>()
	val listEndpointGroupsResult = ListEndpointGroupsResult()
        	.withEndpointGroups(endpointGroups)
        	.withNextToken(expectedNextToken)
	every { proxy.injectCredentialsAndInvoke(capture(listEndpointGroupsRequestSlot), ofType<ProxyListEndpointGroups>()) } returns listEndpointGroupsResult

	val request = ResourceHandlerRequest.builder<ResourceModel>().desiredResourceState(model).nextToken(sentNextToken).build()
	val response = ListHandler().handleRequest(proxy, request, null, logger)
	assertNotNull(response)
	assertEquals(OperationStatus.SUCCESS, response.status)
	assertEquals(sentNextToken, listEndpointGroupsRequestSlot.captured.nextToken)
	assertNull(response.callbackContext)
	assertNull(response.resourceModel)
	assertNull(response.message)
	assertNotNull(response.resourceModels)
	assertNotNull(response.nextToken)
	assertEquals(expectedNextToken, response.nextToken)
	assertEquals(1, response.resourceModels.size)
	assertEquals(endpointGroupArn1, response.resourceModels[0].endpointGroupArn)
   }
}
