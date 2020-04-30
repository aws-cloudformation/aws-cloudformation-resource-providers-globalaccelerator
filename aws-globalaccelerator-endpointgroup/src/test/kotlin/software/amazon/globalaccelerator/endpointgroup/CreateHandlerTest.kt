package software.amazon.globalaccelerator.endpointgroup

import com.amazonaws.AmazonWebServiceResult
import com.amazonaws.ResponseMetadata
import com.amazonaws.services.globalaccelerator.model.*
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
import org.mockito.Mockito
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock

@ExtendWith(MockitoExtension::class)
class CreateHandlerTest {

    @Mock
    private var proxy: AmazonWebServicesClientProxy? = null

    @Mock
    private var logger: Logger? = null

    @BeforeEach
    fun setup() {
        proxy = mock(AmazonWebServicesClientProxy::class.java)
        logger = mock(Logger::class.java)
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
    fun handleRequest_InitialCreate_ReturnsInProgress() {
        // return an accelerator that is IN_PROGRESS
        val describeAcceleratorResult = DescribeAcceleratorResult()
                .withAccelerator(Accelerator()
                        .withStatus(AcceleratorStatus.IN_PROGRESS))

        doReturn(describeAcceleratorResult).`when`(proxy!!).injectCredentialsAndInvoke(any(DescribeAcceleratorRequest::class.java), any<java.util.function.Function<DescribeAcceleratorRequest, AmazonWebServiceResult<ResponseMetadata>>>())

        // return a listener
        val describeListenerResult = DescribeListenerResult()
                .withListener(Listener()
                        .withListenerArn("arn:aws:globalaccelerator::474880776455:accelerator/abcd1234/listener/12341234"))
        doReturn(describeListenerResult).`when`(proxy!!).injectCredentialsAndInvoke(any(DescribeListenerRequest::class.java), any<java.util.function.Function<DescribeListenerRequest, AmazonWebServiceResult<ResponseMetadata>>>())

        // the endpoint group create result
        val createEndpointGroupResult = CreateEndpointGroupResult()
                .withEndpointGroup(EndpointGroup()
                        .withEndpointGroupArn("ENDPOINTGROUP_ARN")
                        .withHealthCheckPath("/MYPATH")
                        .withHealthCheckPort(200)
                        .withEndpointGroupRegion("us-west-2")
                        .withHealthCheckProtocol("HTTP")
                        .withHealthCheckIntervalSeconds(10)
                        .withThresholdCount(4)
                        .withTrafficDialPercentage(100.0f)
                        .withEndpointDescriptions(createEndpointDescription()))
        doReturn(createEndpointGroupResult).`when`(proxy!!).injectCredentialsAndInvoke(any(CreateEndpointGroupRequest::class.java), any<java.util.function.Function<CreateEndpointGroupRequest, AmazonWebServiceResult<ResponseMetadata>>>())

        // create the that will go to our handler
        val model = ResourceModel.builder()
                .endpointGroupRegion("us-west-2")
                .listenerArn("arn:aws:globalaccelerator::474880776455:accelerator/abcd1234/listener/12341234")
                .healthCheckPort(-1)
                .thresholdCount(3)
                .build()

        val request = ResourceHandlerRequest.builder<ResourceModel>().desiredResourceState(model).build()
        val handler = CreateHandler()
        val response = handler.handleRequest(proxy!!, request, null, logger!!)

        Assertions.assertNotNull(response)
        Assertions.assertEquals(response.status, OperationStatus.IN_PROGRESS)
        Assertions.assertEquals(response.callbackDelaySeconds, 0)
        Assertions.assertNotNull(response.callbackContext)
        Assertions.assertNotNull(response.resourceModel)
        Assertions.assertNull(response.message)
        Assertions.assertNull(response.resourceModels)
        Assertions.assertEquals(response.resourceModel.endpointGroupArn, "ENDPOINTGROUP_ARN")
        Assertions.assertFalse(response.callbackContext!!.pendingStabilization)

        Assertions.assertEquals(response.resourceModel.healthCheckPath, "/MYPATH")
        Assertions.assertEquals(response.resourceModel.healthCheckPort, 200)
        Assertions.assertEquals(response.resourceModel.endpointGroupRegion, "us-west-2")
        Assertions.assertEquals(response.resourceModel.healthCheckProtocol, "HTTP")
        Assertions.assertEquals(response.resourceModel.healthCheckIntervalSeconds, 10)
        Assertions.assertEquals(response.resourceModel.thresholdCount, 4)
        Assertions.assertEquals(response.resourceModel.trafficDialPercentage, 100.0)
    }

    @Test
    fun handleRequest_AwaitAcceleratorToGoInSync_ReturnsInProgress() {
        // return an accelerator that is IN_PROGRESS
        val describeAcceleratorResult = DescribeAcceleratorResult()
                .withAccelerator(Accelerator()
                        .withStatus(AcceleratorStatus.IN_PROGRESS))
        doReturn(describeAcceleratorResult).`when`(proxy!!).injectCredentialsAndInvoke(any(DescribeAcceleratorRequest::class.java), any<java.util.function.Function<DescribeAcceleratorRequest, AmazonWebServiceResult<ResponseMetadata>>>())

        // Create the model we will provide to our handler
        val endpointConfigurations = ArrayList<EndpointConfiguration>()
        endpointConfigurations.add(EndpointConfiguration.builder().endpointId("EPID1").build())
        endpointConfigurations.add(EndpointConfiguration.builder().endpointId("EPID2").build())
        val model = ResourceModel.builder()
                .endpointGroupRegion("us-west-2")
                .listenerArn("arn:aws:globalaccelerator::474880776455:accelerator/abcd1234/listener/12341234")
                .endpointGroupArn("ENDPOINT_GROUP")
                .healthCheckPort(20)
                .thresholdCount(3)
                .trafficDialPercentage(100.0)
                .healthCheckPath("/HEALTH")
                .endpointConfigurations(endpointConfigurations)
                .build()

        // call the handler
        val request = ResourceHandlerRequest.builder<ResourceModel>().desiredResourceState(model).build()
        val callbackContext = CallbackContext(5)
        val handler = CreateHandler()
        val response = handler.handleRequest(proxy!!, request, callbackContext, logger!!)

        Assertions.assertNotNull(response)
        Assertions.assertEquals(response.status, OperationStatus.IN_PROGRESS)
        Assertions.assertEquals(response.callbackDelaySeconds, 1)
        Assertions.assertEquals(response.callbackContext!!.stabilizationRetriesRemaining, 4)
        Assertions.assertNotNull(response.callbackContext)
        Assertions.assertNull(response.message)
        Assertions.assertNull(response.resourceModels)
        Assertions.assertEquals(response.resourceModel, model)
    }

    @Test
    fun handleRequest_AcceleratorIsInSync_ReturnsSuccess() {
        // return an accelerator that is IN_PROGRESS
        val describeAcceleratorResult = DescribeAcceleratorResult()
                .withAccelerator(Accelerator()
                        .withStatus(AcceleratorStatus.DEPLOYED))
        doReturn(describeAcceleratorResult).`when`(proxy!!).injectCredentialsAndInvoke(any(DescribeAcceleratorRequest::class.java), any<java.util.function.Function<DescribeAcceleratorRequest, AmazonWebServiceResult<ResponseMetadata>>>())

        // Create the model we will provide to our handler
        val endpointConfigurations = ArrayList<EndpointConfiguration>()
        endpointConfigurations.add(EndpointConfiguration.builder().endpointId("EPID1").build())
        endpointConfigurations.add(EndpointConfiguration.builder().endpointId("EPID2").build())
        val model = ResourceModel.builder()
                .endpointGroupRegion("us-west-2")
                .listenerArn("arn:aws:globalaccelerator::474880776455:accelerator/abcd1234/listener/12341234")
                .endpointGroupArn("ENDPOINT_GROUP")
                .healthCheckPort(20)
                .thresholdCount(3)
                .trafficDialPercentage(100.0)
                .healthCheckPath("/HEALTH")
                .endpointConfigurations(endpointConfigurations)
                .build()

        // call the handler
        val request = ResourceHandlerRequest.builder<ResourceModel>().desiredResourceState(model).build()
        val callbackContext = CallbackContext(5)
        val handler = CreateHandler()
        val response = handler.handleRequest(proxy!!, request, callbackContext, logger!!)

        Assertions.assertNotNull(response)
        Assertions.assertEquals(response.getStatus(), OperationStatus.SUCCESS)
        Assertions.assertEquals(response.getCallbackDelaySeconds(), 0)
        Assertions.assertNull(response.getCallbackContext())
        Assertions.assertNull(response.getMessage())
        Assertions.assertNull(response.getResourceModels())
        Assertions.assertEquals(response.getResourceModel(), model)

    }

    @Test
    fun handleRequest_ThresholdTimeExceeded() {
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
                .trafficDialPercentage(100.0)
                .healthCheckPath("/HEALTH")
                .endpointConfigurations(endpointConfigurations)
                .build()

        // call the handler
        val request = ResourceHandlerRequest.builder<ResourceModel>().desiredResourceState(model).build()
        val callbackContext = CallbackContext(0)
        val handler = CreateHandler()

        val exception = Assertions.assertThrows(RuntimeException::class.java) {
            handler.handleRequest(proxy!!, request, callbackContext, logger!!)
        }
        Assertions.assertEquals("Timed out waiting for endpoint group to be deployed.", exception.message)
    }
}
