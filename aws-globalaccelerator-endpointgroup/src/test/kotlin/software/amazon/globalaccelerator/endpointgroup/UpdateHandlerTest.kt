package software.amazon.globalaccelerator.endpointgroup

import com.amazonaws.AmazonWebServiceResult
import com.amazonaws.ResponseMetadata
import com.amazonaws.services.globalaccelerator.AWSGlobalAccelerator
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
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy
import software.amazon.cloudformation.proxy.HandlerErrorCode
import software.amazon.cloudformation.proxy.Logger
import software.amazon.cloudformation.proxy.OperationStatus
import software.amazon.cloudformation.proxy.ResourceHandlerRequest
import java.util.function.Function
import kotlin.collections.ArrayList

@ExtendWith(MockitoExtension::class)
class UpdateHandlerTest {

    @Mock
    private var proxy: AmazonWebServicesClientProxy? = null

    @Mock
    private var logger: Logger? = null

    @Captor
    private lateinit var updateCaptor: ArgumentCaptor<UpdateEndpointGroupRequest>

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
                .listenerArn("LISTENER_ARN")
                .healthCheckPort(10)
                .thresholdCount(100)
                .endpointConfigurations(endpointConfigurations)
                .healthCheckPath("/Health")
                .trafficDialPercentage(100.0)
                .healthCheckIntervalSeconds(10)
                .healthCheckProtocol("TCP")
                .listenerArn("arn:aws:globalaccelerator::474880776455:accelerator/abcd1234/listener/12341234")
                .build()
    }

    @Test
    fun handleRequest_NonExistingEndpointGroup() {
        val handler = UpdateHandler()
        val desiredModel = createTestResourceModel()
        doThrow(EndpointGroupNotFoundException("NOT FOUND")).`when`(proxy)!!.injectCredentialsAndInvoke(any(DescribeEndpointGroupRequest::class.java),
                any<Function<DescribeEndpointGroupRequest, AmazonWebServiceResult<ResponseMetadata>>>())

        val request = ResourceHandlerRequest.builder<ResourceModel>()
                .desiredResourceState(desiredModel)
                .build()

        val response = handler.handleRequest(proxy!!, request, null, logger!!)
        Assertions.assertNotNull(response)
        Assertions.assertEquals(response.getStatus(), OperationStatus.FAILED)
        Assertions.assertEquals(response.getErrorCode(), HandlerErrorCode.NotFound)
        Assertions.assertNull(response.getResourceModel())
    }

    @Test
    fun handleRequest_UpdateEndpointGroup_PendingReturnsInProgress() {
        val handler = UpdateHandler()
        val previousModel = createTestResourceModel()
        val desiredModel = createTestResourceModel()

        val describeEndpointGroupResult = DescribeEndpointGroupResult()
                .withEndpointGroup(EndpointGroup()
                        .withEndpointGroupArn("ENDPOINTGROUP_ARN"))

        val updateEndpointGroupResult = UpdateEndpointGroupResult()
                .withEndpointGroup(EndpointGroup().withEndpointGroupArn("ENDPOINTGROUP_ARN"))

        doReturn(describeEndpointGroupResult).`when`(proxy!!).injectCredentialsAndInvoke(ArgumentMatchers.any(DescribeEndpointGroupRequest::class.java), ArgumentMatchers.any<Function<DescribeEndpointGroupRequest, AmazonWebServiceResult<ResponseMetadata>>>())
        doReturn(updateEndpointGroupResult).`when`(proxy!!).injectCredentialsAndInvoke(ArgumentMatchers.any(UpdateEndpointGroupRequest::class.java), ArgumentMatchers.any<Function<UpdateEndpointGroupRequest, AmazonWebServiceResult<ResponseMetadata>>>())

        val request = ResourceHandlerRequest.builder<ResourceModel>()
                .desiredResourceState(desiredModel)
                .previousResourceState(previousModel)
                .build()

        val response = handler.handleRequest(proxy!!, request, null, logger!!)

        Assertions.assertNotNull(response)
        Assertions.assertEquals(response.status, OperationStatus.IN_PROGRESS)
        Assertions.assertNotNull(response.resourceModel)
        Assertions.assertNotNull(response.callbackContext)
        Assertions.assertEquals(response.callbackDelaySeconds, 0)
        Assertions.assertEquals(response.resourceModel.endpointGroupArn, "ENDPOINTGROUP_ARN")
        Assertions.assertTrue(response.callbackContext!!.pendingStabilization)
    }

    @Test
    fun handleRequest_UpdateEndpointGroup_DeployedReturnsSuccess() {
        val handler = UpdateHandler()
        val desiredModel = createTestResourceModel()

        val describeEndpointGroupResult = DescribeEndpointGroupResult()
                .withEndpointGroup(EndpointGroup()
                        .withEndpointGroupArn("ENDPOINTGROUP_ARN"))

        val describeAcceleratorResult = DescribeAcceleratorResult()
                .withAccelerator(Accelerator()
                        .withAcceleratorArn("ACCELERATOR_ARN")
                        .withStatus(AcceleratorStatus.DEPLOYED.toString()))

        doReturn(describeEndpointGroupResult).`when`(proxy!!).injectCredentialsAndInvoke(ArgumentMatchers.any(DescribeEndpointGroupRequest::class.java), ArgumentMatchers.any<Function<DescribeEndpointGroupRequest, AmazonWebServiceResult<ResponseMetadata>>>())
        doReturn(describeAcceleratorResult).`when`(proxy!!).injectCredentialsAndInvoke(any(DescribeAcceleratorRequest::class.java), any<java.util.function.Function<DescribeAcceleratorRequest, AmazonWebServiceResult<ResponseMetadata>>>())

        val request = ResourceHandlerRequest.builder<ResourceModel>()
                .desiredResourceState(desiredModel)
                .build()

        val context = CallbackContext(100, true)

        val response = handler.handleRequest(proxy!!, request, context, logger!!)

        Assertions.assertNotNull(response)
        Assertions.assertEquals(response.status, OperationStatus.SUCCESS)
        Assertions.assertNotNull(response.resourceModel)
        Assertions.assertNull(response.callbackContext)
        Assertions.assertEquals(response.callbackDelaySeconds, 0)
        Assertions.assertEquals(response.resourceModel.endpointGroupArn, "ENDPOINTGROUP_ARN")
    }

    @Test
    fun testStabilizationTimeout() {
        val handler = UpdateHandler()
        val desiredModel = createTestResourceModel()

        val request = ResourceHandlerRequest.builder<ResourceModel>()
                .desiredResourceState(desiredModel)
                .build()

        val describeEndpointGroupResult = DescribeEndpointGroupResult()
                .withEndpointGroup(EndpointGroup()
                        .withEndpointGroupArn("ENDPOINTGROUP_ARN"))
        doReturn(describeEndpointGroupResult).`when`(proxy!!).injectCredentialsAndInvoke(ArgumentMatchers.any(DescribeEndpointGroupRequest::class.java), ArgumentMatchers.any<Function<DescribeEndpointGroupRequest, AmazonWebServiceResult<ResponseMetadata>>>())

        val context = CallbackContext(0, true)

        val exception = Assertions.assertThrows(RuntimeException::class.java) {
            handler.handleRequest(proxy!!, request, context, logger!!)
        }
        Assertions.assertEquals("Timed out waiting for endpoint group to be deployed.", exception.message)
    }

    @Test
    fun handleRequest_UpdateEndpointGroupWithPortOverrides() {
        val handler = UpdateHandler()
        val previousModel = createTestResourceModel()
        val desiredModel = createTestResourceModel()
        val portOverrides = listOf(PortOverride(80, 8080))
        desiredModel.portOverrides = portOverrides

        val describeEndpointGroupResult = DescribeEndpointGroupResult()
                .withEndpointGroup(EndpointGroup()
                        .withEndpointGroupArn("ENDPOINTGROUP_ARN"))

        val updateEndpointGroupResult = UpdateEndpointGroupResult()
                .withEndpointGroup(EndpointGroup().withEndpointGroupArn("ENDPOINTGROUP_ARN"))

        doReturn(describeEndpointGroupResult).`when`(proxy!!).injectCredentialsAndInvoke(ArgumentMatchers.any(DescribeEndpointGroupRequest::class.java), ArgumentMatchers.any<Function<DescribeEndpointGroupRequest, AmazonWebServiceResult<ResponseMetadata>>>())
        doReturn(updateEndpointGroupResult).`when`(proxy!!).injectCredentialsAndInvoke(ArgumentMatchers.any(UpdateEndpointGroupRequest::class.java), ArgumentMatchers.any<Function<UpdateEndpointGroupRequest, AmazonWebServiceResult<ResponseMetadata>>>())

        val request = ResourceHandlerRequest.builder<ResourceModel>()
                .desiredResourceState(desiredModel)
                .previousResourceState(previousModel)
                .build()

        val response = handler.handleRequest(proxy!!, request, null, logger!!)

        Assertions.assertNotNull(response)
        Assertions.assertEquals(response.status, OperationStatus.IN_PROGRESS)
        Assertions.assertNotNull(response.resourceModel)
        Assertions.assertNotNull(response.callbackContext)
        Assertions.assertEquals(response.callbackDelaySeconds, 0)
        Assertions.assertEquals(response.resourceModel.endpointGroupArn, "ENDPOINTGROUP_ARN")
        Assertions.assertTrue(response.callbackContext!!.pendingStabilization)
        Assertions.assertEquals(response.resourceModel.portOverrides.size, 1)
        Assertions.assertEquals(response.resourceModel.portOverrides[0].listenerPort, portOverrides[0].listenerPort)
        Assertions.assertEquals(response.resourceModel.portOverrides[0].endpointPort, portOverrides[0].endpointPort)
    }

    @Test
    fun handleRequest_UpdateEndpointGroupWithNullWithPreviousPortOverrides() {
        val handler = UpdateHandler()
        val portOverrides = listOf(PortOverride(80, 8080))
        val previousModel = createTestResourceModel()
        previousModel.portOverrides = portOverrides

        val desiredModel = createTestResourceModel()
        val describeEndpointGroupResult = DescribeEndpointGroupResult()
                .withEndpointGroup(EndpointGroup()
                        .withEndpointGroupArn("ENDPOINTGROUP_ARN"))

        val updateEndpointGroupResult = UpdateEndpointGroupResult()
                .withEndpointGroup(EndpointGroup().withEndpointGroupArn("ENDPOINTGROUP_ARN"))

        doReturn(describeEndpointGroupResult).`when`(proxy!!).injectCredentialsAndInvoke(ArgumentMatchers.any(DescribeEndpointGroupRequest::class.java), ArgumentMatchers.any<Function<DescribeEndpointGroupRequest, AmazonWebServiceResult<ResponseMetadata>>>())
        doReturn(updateEndpointGroupResult).`when`(proxy!!).injectCredentialsAndInvoke(ArgumentMatchers.any(UpdateEndpointGroupRequest::class.java), ArgumentMatchers.any<Function<UpdateEndpointGroupRequest, AmazonWebServiceResult<ResponseMetadata>>>())

        val request = ResourceHandlerRequest.builder<ResourceModel>()
                .desiredResourceState(desiredModel)
                .previousResourceState(previousModel)
                .build()

        val response = handler.handleRequest(proxy!!, request, null, logger!!)

        Assertions.assertNotNull(response)
        Assertions.assertEquals(response.status, OperationStatus.IN_PROGRESS)
        Assertions.assertNotNull(response.resourceModel)
        Assertions.assertNotNull(response.callbackContext)
        Assertions.assertEquals(response.callbackDelaySeconds, 0)
        Assertions.assertEquals(response.resourceModel.endpointGroupArn, "ENDPOINTGROUP_ARN")
        Assertions.assertTrue(response.callbackContext!!.pendingStabilization)
        Assertions.assertNull(response.resourceModel.portOverrides)
        verify(proxy, times(2))?.injectCredentialsAndInvoke<UpdateEndpointGroupRequest, UpdateEndpointGroupResult>(updateCaptor.capture(), any())
        val values = updateCaptor.allValues
        Assertions.assertEquals(values[1].portOverrides, ArrayList<String>()) // Empty array to clean port-overrides.
    }

    @Test
    fun handleRequest_UpdateEndpointGroupWithNullWithPreviousNullPortOverrides() {
        val handler = UpdateHandler()
        val previousModel = createTestResourceModel()

        val desiredModel = createTestResourceModel()
        val describeEndpointGroupResult = DescribeEndpointGroupResult()
                .withEndpointGroup(EndpointGroup()
                        .withEndpointGroupArn("ENDPOINTGROUP_ARN"))

        val updateEndpointGroupResult = UpdateEndpointGroupResult()
                .withEndpointGroup(EndpointGroup().withEndpointGroupArn("ENDPOINTGROUP_ARN"))

        doReturn(describeEndpointGroupResult).`when`(proxy!!).injectCredentialsAndInvoke(ArgumentMatchers.any(DescribeEndpointGroupRequest::class.java), ArgumentMatchers.any<Function<DescribeEndpointGroupRequest, AmazonWebServiceResult<ResponseMetadata>>>())
        doReturn(updateEndpointGroupResult).`when`(proxy!!).injectCredentialsAndInvoke(ArgumentMatchers.any(UpdateEndpointGroupRequest::class.java), ArgumentMatchers.any<Function<UpdateEndpointGroupRequest, AmazonWebServiceResult<ResponseMetadata>>>())

        val request = ResourceHandlerRequest.builder<ResourceModel>()
                .desiredResourceState(desiredModel)
                .previousResourceState(previousModel)
                .build()

        val response = handler.handleRequest(proxy!!, request, null, logger!!)

        Assertions.assertNotNull(response)
        Assertions.assertEquals(response.status, OperationStatus.IN_PROGRESS)
        Assertions.assertNotNull(response.resourceModel)
        Assertions.assertNotNull(response.callbackContext)
        Assertions.assertEquals(response.callbackDelaySeconds, 0)
        Assertions.assertEquals(response.resourceModel.endpointGroupArn, "ENDPOINTGROUP_ARN")
        Assertions.assertTrue(response.callbackContext!!.pendingStabilization)
        Assertions.assertNull(response.resourceModel.portOverrides)
        verify(proxy, times(2))?.injectCredentialsAndInvoke<UpdateEndpointGroupRequest, UpdateEndpointGroupResult>(updateCaptor.capture(), any())
        val values = updateCaptor.allValues
        Assertions.assertNull(values[1].portOverrides)
    }

    @Test
    fun handleRequest_UpdateEndpointGroupWithEmptyPortOverrides() {
        val handler = UpdateHandler()
        val portOverrides = ArrayList<PortOverride>()
        val previousModel = createTestResourceModel()

        val desiredModel = createTestResourceModel()
        desiredModel.portOverrides = portOverrides
        val describeEndpointGroupResult = DescribeEndpointGroupResult()
                .withEndpointGroup(EndpointGroup()
                        .withEndpointGroupArn("ENDPOINTGROUP_ARN"))

        val updateEndpointGroupResult = UpdateEndpointGroupResult()
                .withEndpointGroup(EndpointGroup().withEndpointGroupArn("ENDPOINTGROUP_ARN"))

        doReturn(describeEndpointGroupResult).`when`(proxy!!).injectCredentialsAndInvoke(ArgumentMatchers.any(DescribeEndpointGroupRequest::class.java), ArgumentMatchers.any<Function<DescribeEndpointGroupRequest, AmazonWebServiceResult<ResponseMetadata>>>())
        doReturn(updateEndpointGroupResult).`when`(proxy!!).injectCredentialsAndInvoke(ArgumentMatchers.any(UpdateEndpointGroupRequest::class.java), ArgumentMatchers.any<Function<UpdateEndpointGroupRequest, AmazonWebServiceResult<ResponseMetadata>>>())

        val request = ResourceHandlerRequest.builder<ResourceModel>()
                .desiredResourceState(desiredModel)
                .previousResourceState(previousModel)
                .build()

        val response = handler.handleRequest(proxy!!, request, null, logger!!)

        Assertions.assertNotNull(response)
        Assertions.assertEquals(response.status, OperationStatus.IN_PROGRESS)
        Assertions.assertNotNull(response.resourceModel)
        Assertions.assertNotNull(response.callbackContext)
        Assertions.assertEquals(response.callbackDelaySeconds, 0)
        Assertions.assertEquals(response.resourceModel.endpointGroupArn, "ENDPOINTGROUP_ARN")
        Assertions.assertTrue(response.callbackContext!!.pendingStabilization)
        Assertions.assertEquals(response.resourceModel.portOverrides.size, 0)
        verify(proxy, times(2))?.injectCredentialsAndInvoke<UpdateEndpointGroupRequest, UpdateEndpointGroupResult>(updateCaptor.capture(), any())
        val values = updateCaptor.allValues
        Assertions.assertEquals(values[1].portOverrides, ArrayList<String>()) // Empty array to clean port-overrides.
    }
}
