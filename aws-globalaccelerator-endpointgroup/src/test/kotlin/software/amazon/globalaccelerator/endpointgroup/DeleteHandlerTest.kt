package software.amazon.globalaccelerator.endpointgroup

import com.amazonaws.AmazonWebServiceResult
import com.amazonaws.ResponseMetadata
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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.junit.jupiter.api.Assertions
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy
import software.amazon.cloudformation.proxy.Logger
import software.amazon.cloudformation.proxy.OperationStatus
import software.amazon.cloudformation.proxy.ResourceHandlerRequest
import java.util.ArrayList
import java.util.function.Function

@ExtendWith(MockitoExtension::class)
class DeleteHandlerTest {

    @Mock
    private var proxy: AmazonWebServicesClientProxy? = null

    @Mock
    private var logger: Logger? = null

    @BeforeEach
    fun setup() {
        proxy = mock(AmazonWebServicesClientProxy::class.java)
        logger = mock(Logger::class.java)
    }

    @Test
    fun handleRequest_ResourceStillExists_DeletesAndReturnsInProgress() {
        val describeEndpointGroupResult = DescribeEndpointGroupResult()
                .withEndpointGroup(EndpointGroup()
                        .withEndpointGroupArn("ENDPOINT_GROUP_ARN"))
        val model = ResourceModel.builder()
                .endpointGroupRegion("us-west-2")
                .listenerArn("arn:aws:globalaccelerator::474880776455:accelerator/abcd1234/listener/12341234")
                .endpointGroupArn("ENDPOINT_GROUP_ARN")
                .healthCheckPort(-1)
                .thresholdCount(3)
                .build()
        val request = ResourceHandlerRequest.builder<ResourceModel>().desiredResourceState(model).build()
        val handler = DeleteHandler()

        doReturn(DeleteEndpointGroupResult()).`when`(proxy!!).injectCredentialsAndInvoke(any(DeleteEndpointGroupRequest::class.java), any<java.util.function.Function<DeleteEndpointGroupRequest, AmazonWebServiceResult<ResponseMetadata>>>())
        doReturn(describeEndpointGroupResult).`when`(proxy!!).injectCredentialsAndInvoke(any(DescribeEndpointGroupRequest::class.java), any<java.util.function.Function<DescribeEndpointGroupRequest, AmazonWebServiceResult<ResponseMetadata>>>())

        val response = handler.handleRequest(proxy!!, request, null, logger!!)

        Assertions.assertNotNull(response)
        Assertions.assertEquals(response.status, OperationStatus.IN_PROGRESS)
        Assertions.assertEquals(response.callbackDelaySeconds, 0)
        Assertions.assertNotNull(response.callbackContext)
        Assertions.assertNull(response.message)
        Assertions.assertNull(response.resourceModels)
        Assertions.assertEquals(response.resourceModel, model)
    }

    @Test
    fun handleRequest_ResourceDoesNotExists_AcceleratorInProgress_ReturnsInProgress() {
        val describeAcceleratorResult = DescribeAcceleratorResult()
                .withAccelerator(Accelerator()
                        .withStatus(AcceleratorStatus.IN_PROGRESS))
        val model = ResourceModel.builder()
                .endpointGroupRegion("us-west-2")
                .listenerArn("arn:aws:globalaccelerator::474880776455:accelerator/abcd1234/listener/12341234")
                .endpointGroupArn("ENDPOINT_GROUP_ARN")
                .healthCheckPort(-1)
                .thresholdCount(3)
                .build()
        val request = ResourceHandlerRequest.builder<ResourceModel>().desiredResourceState(model).build()
        val handler = DeleteHandler()
        val context = CallbackContext(2, pendingStabilization = true)

        doReturn(describeAcceleratorResult).`when`(proxy!!).injectCredentialsAndInvoke(any(DescribeAcceleratorRequest::class.java), any<java.util.function.Function<DescribeAcceleratorRequest, AmazonWebServiceResult<ResponseMetadata>>>())

        val response = handler.handleRequest(proxy!!, request, context, logger!!)

        Assertions.assertNotNull(response)
        Assertions.assertEquals(response.status, OperationStatus.IN_PROGRESS)
        Assertions.assertEquals(response.callbackDelaySeconds, 1)
        Assertions.assertNotNull(response.callbackContext)
        Assertions.assertNull(response.message)
        Assertions.assertNull(response.resourceModels)
        Assertions.assertEquals(response.resourceModel, model)
    }

    @Test
    fun handleRequest_ResourceDoesNotExist_AcceleratorDeployed_ReturnsSuccess() {
        val model = ResourceModel.builder()
                .endpointGroupRegion("us-west-2")
                .listenerArn("arn:aws:globalaccelerator::474880776455:accelerator/abcd1234/listener/12341234")
                .endpointGroupArn("ENDPOINT_GROUP_ARN")
                .healthCheckPort(-1)
                .thresholdCount(3)
                .build()
        val request = ResourceHandlerRequest.builder<ResourceModel>().desiredResourceState(model).build()
        val handler = DeleteHandler()
        val context = CallbackContext(2)

        doThrow(EndpointGroupNotFoundException("NOT FOUND")).`when`(proxy)!!.injectCredentialsAndInvoke(any(DescribeEndpointGroupRequest::class.java),
                any<Function<DescribeEndpointGroupRequest, AmazonWebServiceResult<ResponseMetadata>>>())

        val response = handler.handleRequest(proxy!!, request, context, logger!!)

        Assertions.assertNotNull(response)
        Assertions.assertEquals(response.status, OperationStatus.SUCCESS)
        Assertions.assertEquals(response.callbackDelaySeconds, 0)
        Assertions.assertNull(response.callbackContext)
        Assertions.assertNull(response.message)
        Assertions.assertNull(response.resourceModels)
        Assertions.assertEquals(response.resourceModel, model)
    }

    @Test
    fun handleRequest_ThresholdTimeExceeded() {
        val endpointConfigurations = ArrayList<EndpointConfiguration>()
        endpointConfigurations.add(EndpointConfiguration.builder().endpointId("EPID1").build())
        endpointConfigurations.add(EndpointConfiguration.builder().endpointId("EPID2").build())
        val model = ResourceModel.builder()
                .endpointGroupRegion("us-west-2")
                .listenerArn("arn:aws:globalaccelerator::474880776455:accelerator/abcd1234/listener/12341234")
                .endpointGroupArn("ENDPOINT_GROUP_ARN")
                .healthCheckPort(20)
                .thresholdCount(3)
                .trafficDialPercentage(100.0)
                .healthCheckPath("/HEALTH")
                .endpointConfigurations(endpointConfigurations)
                .build()
        val request = ResourceHandlerRequest.builder<ResourceModel>().desiredResourceState(model).build()
        val callbackContext = CallbackContext(0, pendingStabilization = true)
        val handler = DeleteHandler()

        val exception = Assertions.assertThrows(RuntimeException::class.java) {
            handler.handleRequest(proxy!!, request, callbackContext, logger!!)
        }

        Assertions.assertEquals("Timed out waiting for endpoint group to be deployed.", exception.message)
    }

    @Test
    fun handleRequest_AcceleratorDoesntExist() {

        val endpointConfigurations = ArrayList<EndpointConfiguration>()
        endpointConfigurations.add(EndpointConfiguration.builder().endpointId("EPID1").build())
        endpointConfigurations.add(EndpointConfiguration.builder().endpointId("EPID2").build())
        val model = ResourceModel.builder()
                .endpointGroupRegion("us-west-2")
                .listenerArn("arn:aws:globalaccelerator::474880776455:accelerator/abcd1234/listener/12341234")
                .endpointGroupArn("ENDPOINT_GROUP_ARN")
                .healthCheckPort(20)
                .thresholdCount(3)
                .trafficDialPercentage(100.0)
                .healthCheckPath("/HEALTH")
                .endpointConfigurations(endpointConfigurations)
                .build()
        val request = ResourceHandlerRequest.builder<ResourceModel>().desiredResourceState(model).build()
        val callbackContext = CallbackContext(10, pendingStabilization = true)
        val handler = DeleteHandler()

        doThrow(AcceleratorNotFoundException("NOT FOUND")).`when`(proxy)!!.injectCredentialsAndInvoke(any(DescribeAcceleratorRequest::class.java),
                any<Function<DescribeAcceleratorRequest, AmazonWebServiceResult<ResponseMetadata>>>())

        val response = handler.handleRequest(proxy!!, request, callbackContext, logger!!)

        Assertions.assertNotNull(response)
        Assertions.assertEquals(response.status, OperationStatus.SUCCESS)
        Assertions.assertNull(response.callbackContext)
        Assertions.assertEquals(response.resourceModel, model)
        Assertions.assertNotNull(response.resourceModel)
        Assertions.assertNull(response.message)
        Assertions.assertNull(response.errorCode)
        Assertions.assertNull(response.resourceModels)
    }

}
