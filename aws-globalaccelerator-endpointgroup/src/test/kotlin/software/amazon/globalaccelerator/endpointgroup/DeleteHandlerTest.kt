package software.amazon.globalaccelerator.endpointgroup

import com.amazonaws.AmazonWebServiceResult
import com.amazonaws.ResponseMetadata
import com.amazonaws.services.globalaccelerator.model.*
import junit.framework.Assert
import lombok.`val`
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy
import software.amazon.cloudformation.proxy.Logger
import software.amazon.cloudformation.proxy.OperationStatus
import software.amazon.cloudformation.proxy.ResourceHandlerRequest

import java.util.ArrayList

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.Assertions
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.*
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
        // return an accelerator that is IN_PROGRESS
        val describeAcceleratorResult = DescribeAcceleratorResult()
                .withAccelerator(Accelerator()
                        .withStatus(AcceleratorStatus.IN_PROGRESS))
        doReturn(describeAcceleratorResult).`when`(proxy!!).injectCredentialsAndInvoke(any(DescribeAcceleratorRequest::class.java), any<java.util.function.Function<DescribeAcceleratorRequest, AmazonWebServiceResult<ResponseMetadata>>>())

        // we expect delete to be called
        doReturn(DeleteEndpointGroupResult()).`when`(proxy!!).injectCredentialsAndInvoke(any(DeleteEndpointGroupRequest::class.java), any<java.util.function.Function<DeleteEndpointGroupRequest, AmazonWebServiceResult<ResponseMetadata>>>())

        // the endpoint group describe request
        val describeEndpointGroupResult = DescribeEndpointGroupResult()
                .withEndpointGroup(EndpointGroup()
                        .withEndpointGroupArn("ENDPOINT_GROUP_ARN"))
        doReturn(describeEndpointGroupResult).`when`(proxy!!).injectCredentialsAndInvoke(any(DescribeEndpointGroupRequest::class.java), any<java.util.function.Function<DescribeEndpointGroupRequest, AmazonWebServiceResult<ResponseMetadata>>>())

        // create the that will go to our handler
        val model = ResourceModel.builder()
                .endpointGroupRegion("us-west-2")
                .listenerArn("arn:aws:globalaccelerator::474880776455:accelerator/abcd1234/listener/12341234")
                .endpointGroupArn("ENDPOINT_GROUP_ARN")
                .healthCheckPort(-1)
                .thresholdCount(3)
                .build()

        val request = ResourceHandlerRequest.builder<ResourceModel>().desiredResourceState(model).build()
        val handler = DeleteHandler()
        val response = handler.handleRequest(proxy!!, request, null, logger!!)

        Assertions.assertNotNull(response)
        Assertions.assertEquals(response.getStatus(), OperationStatus.IN_PROGRESS)
        Assertions.assertEquals(response.getCallbackDelaySeconds(), 1)
        Assertions.assertNotNull(response.getCallbackContext())
        Assertions.assertNull(response.getMessage())
        Assertions.assertNull(response.getResourceModels())
        Assertions.assertEquals(response.getResourceModel(), model)
    }

    @Test
    fun handleRequest_ResourceDoesNotExists_AcceleratorInProgress_ReturnsInProgress() {
        // return an accelerator that is IN_PROGRESS
        val describeAcceleratorResult = DescribeAcceleratorResult()
                .withAccelerator(Accelerator()
                        .withStatus(AcceleratorStatus.IN_PROGRESS))
        doReturn(describeAcceleratorResult).`when`(proxy!!).injectCredentialsAndInvoke(any(DescribeAcceleratorRequest::class.java), any<java.util.function.Function<DescribeAcceleratorRequest, AmazonWebServiceResult<ResponseMetadata>>>())

        // the endpoint group describe request will return not found
        doThrow(EndpointGroupNotFoundException("NOT FOUND")).`when`(proxy)!!.injectCredentialsAndInvoke(any(DescribeEndpointGroupRequest::class.java),
                any<Function<DescribeEndpointGroupRequest, AmazonWebServiceResult<ResponseMetadata>>>())

        // create the that will go to our handler
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
        val response = handler.handleRequest(proxy!!, request, context, logger!!)

        Assertions.assertNotNull(response)
        Assertions.assertEquals(response.getStatus(), OperationStatus.IN_PROGRESS)
        Assertions.assertEquals(response.getCallbackDelaySeconds(), 1)
        Assertions.assertNotNull(response.getCallbackContext())
        Assertions.assertNull(response.getMessage())
        Assertions.assertNull(response.getResourceModels())
        Assertions.assertEquals(response.getResourceModel(), model)
    }

    @Test
    fun handleRequest_ResourceDoesNotExist_AcceleratorDeployed_ReturnsSuccess() {
        // return an accelerator that is IN_PROGRESS
        val describeAcceleratorResult = DescribeAcceleratorResult()
                .withAccelerator(Accelerator()
                        .withStatus(AcceleratorStatus.DEPLOYED))
        doReturn(describeAcceleratorResult).`when`(proxy!!).injectCredentialsAndInvoke(any(DescribeAcceleratorRequest::class.java), any<java.util.function.Function<DescribeAcceleratorRequest, AmazonWebServiceResult<ResponseMetadata>>>())

        // the endpoint group describe request will return not found
        doThrow(EndpointGroupNotFoundException("NOT FOUND")).`when`(proxy)!!.injectCredentialsAndInvoke(any(DescribeEndpointGroupRequest::class.java),
                any<Function<DescribeEndpointGroupRequest, AmazonWebServiceResult<ResponseMetadata>>>())

        // create the that will go to our handler
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
        val response = handler.handleRequest(proxy!!, request, context, logger!!)

        Assertions.assertNotNull(response)
        Assertions.assertEquals(response.getStatus(), OperationStatus.SUCCESS)
        Assertions.assertEquals(response.getCallbackDelaySeconds(), 0)
        Assertions.assertNull(response.getCallbackContext())
        Assertions.assertNull(response.getMessage())
        Assertions.assertNull(response.getResourceModels())
        Assertions.assertEquals(response.getResourceModel(), model)
    }

    @Test
    fun handleRequest_ResourceDoesNotExist_ThresholdTimeExceeded() {
        // the endpoint group describe request will return not found
        doThrow(EndpointGroupNotFoundException("NOT FOUND")).`when`(proxy)!!.injectCredentialsAndInvoke(any(DescribeEndpointGroupRequest::class.java),
                any<Function<DescribeEndpointGroupRequest, AmazonWebServiceResult<ResponseMetadata>>>())

        // Create the model we will provide to our handler
        val endpointConfigurations = ArrayList<EndpointConfiguration>()
        endpointConfigurations.add(EndpointConfiguration.builder().endpointId("EPID1").build())
        endpointConfigurations.add(EndpointConfiguration.builder().endpointId("EPID2").build())
        val model = ResourceModel.builder()
                .endpointGroupRegion("us-west-2")
                .listenerArn("arn:aws:globalaccelerator::474880776455:accelerator/abcd1234/listener/12341234")
                .endpointGroupArn("ENDPOINT_GROUP_ARN")
                .healthCheckPort(20)
                .thresholdCount(3)
                .trafficDialPercentage(100)
                .healthCheckPath("/HEALTH")
                .endpointConfigurations(endpointConfigurations)
                .build()

        // call the handler
        val request = ResourceHandlerRequest.builder<ResourceModel>().desiredResourceState(model).build()
        val callbackContext = CallbackContext(0)
        val handler = DeleteHandler()

        val exception = Assertions.assertThrows(RuntimeException::class.java) {
            handler.handleRequest(proxy!!, request, callbackContext, logger!!)
        }
        Assert.assertEquals("Timed out waiting for endpoint group to be deployed.", exception.message)
    }
}
