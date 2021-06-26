package software.amazon.globalaccelerator.endpointgroup

import com.amazonaws.AmazonWebServiceResult
import com.amazonaws.ResponseMetadata
import com.amazonaws.services.globalaccelerator.model.*
import org.junit.jupiter.api.Assertions
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

import org.mockito.ArgumentMatchers
import org.mockito.Mockito.mock
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.doThrow
import java.util.function.Function

@ExtendWith(MockitoExtension::class)
class ReadHandlerTest {

    @Mock
    private var proxy: AmazonWebServicesClientProxy? = null

    @Mock
    private var logger: Logger? = null

    private val portOverrides = listOf(PortOverride(80, 8080))

    @BeforeEach
    fun setup() {
        proxy = mock(AmazonWebServicesClientProxy::class.java)
        logger = mock(Logger::class.java)
    }

    private fun createTestResourceModel(): ResourceModel {
        val endpointConfigurations = ArrayList<EndpointConfiguration>()
        endpointConfigurations.add(EndpointConfiguration.builder().endpointId("EPID1").build())
        endpointConfigurations.add(EndpointConfiguration.builder().endpointId("EPID2").build())
        return ResourceModel.builder()
                .endpointGroupArn("ENDPOINTGROUP_ARN")
                .endpointGroupRegion("us-west-2")
                .healthCheckPort(10)
                .thresholdCount(100)
                .endpointConfigurations(endpointConfigurations)
                .healthCheckPath("/Health")
                .trafficDialPercentage(100.0)
                .healthCheckIntervalSeconds(10)
                .healthCheckProtocol("TCP")
                .listenerArn("arn:aws:globalaccelerator::474880776455:accelerator/abcd1234/listener/12341234")
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
                        .withEndpointGroupArn("ENDPOINTGROUP_ARN")
                        .withEndpointGroupRegion("us-west-2")
                        .withTrafficDialPercentage(50.0f)
                        .withEndpointDescriptions(createEndpointDescription())
                        .withPortOverrides(returnedPortOverrides))

        doReturn(describeEndpointGroupResult).`when`(proxy!!).injectCredentialsAndInvoke(ArgumentMatchers.any(DescribeEndpointGroupRequest::class.java), ArgumentMatchers.any<Function<DescribeEndpointGroupRequest, AmazonWebServiceResult<ResponseMetadata>>>())
        val request = ResourceHandlerRequest.builder<ResourceModel>().desiredResourceState(model).build()
        val response = ReadHandler().handleRequest(proxy!!, request, null, logger!!)

        Assertions.assertNotNull(response)
        Assertions.assertEquals(response.status, OperationStatus.SUCCESS)
        Assertions.assertNotNull(response.resourceModel)
        Assertions.assertNull(response.callbackContext)
        Assertions.assertEquals(response.resourceModel.endpointGroupArn, "ENDPOINTGROUP_ARN")
        Assertions.assertEquals(response.resourceModel.endpointGroupRegion, "us-west-2")
        Assertions.assertEquals(response.resourceModel.portOverrides.size, 1)
        Assertions.assertEquals(response.resourceModel.portOverrides[0].listenerPort, portOverrides[0].listenerPort)
        Assertions.assertEquals(response.resourceModel.portOverrides[0].endpointPort, portOverrides[0].endpointPort)
        Assertions.assertEquals(response.resourceModel.listenerArn, "arn:aws:globalaccelerator::474880776455:accelerator/abcd1234/listener/12341234")
    }

    @Test
    fun handleRequest_nonExistingEndpointGroup() {
        val model = createTestResourceModel()
        doThrow(EndpointGroupNotFoundException("NOT FOUND")).`when`(proxy)!!.injectCredentialsAndInvoke(ArgumentMatchers.any(DescribeEndpointGroupRequest::class.java),
                ArgumentMatchers.any<Function<DescribeEndpointGroupRequest, AmazonWebServiceResult<ResponseMetadata>>>())
        val request = ResourceHandlerRequest.builder<ResourceModel>().desiredResourceState(model).build()
        val response = ReadHandler().handleRequest(proxy!!, request, null, logger!!)

        Assertions.assertNotNull(response)
        Assertions.assertEquals(response.status, OperationStatus.FAILED)
        Assertions.assertEquals(response.errorCode, HandlerErrorCode.NotFound)
        Assertions.assertNull(response.resourceModel)
    }
}
