package software.amazon.globalaccelerator.endpointgroup

import com.amazonaws.AmazonWebServiceResult
import com.amazonaws.ResponseMetadata
import com.amazonaws.services.globalaccelerator.model.*
import junit.framework.Assert.assertEquals
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
                        .withEndpointGroupArn("ENDPOINT_GROUP_ARN")
                        .withHealthCheckPath("/MYPATH")
                        .withHealthCheckPort(200)
                        .withEndpointGroupRegion("us-west-2")
                        .withHealthCheckProtocol("HTTP")
                        .withHealthCheckIntervalSeconds(10)
                        .withThresholdCount(4)
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

        assertThat(response).isNotNull()
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS)
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0)
        assertThat(response.getCallbackContext()).isNotNull()
        assertThat(response.getResourceModels()).isNull()
        assertThat(response.getMessage()).isNull()

        // want to confirm data was updated correctly
        assertThat(response.getResourceModel()).isNotNull()
        assertThat(response.getResourceModel().getEndpointGroupArn()).isEqualTo("ENDPOINT_GROUP_ARN")
        assertThat(response.getResourceModel().getHealthCheckPath()).isEqualTo("/MYPATH")
        assertThat(response.getResourceModel().getHealthCheckPort()).isEqualTo(200)
        assertThat(response.getResourceModel().getEndpointGroupRegion()).isEqualTo("us-west-2")
        assertThat(response.getResourceModel().getHealthCheckProtocol()).isEqualTo("HTTP")
        assertThat(response.getResourceModel().getHealthCheckIntervalSeconds()).isEqualTo(10)
        assertThat(response.getResourceModel().getThresholdCount()).isEqualTo(4)
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
                .trafficDialPercentage(100)
                .healthCheckPath("/HEALTH")
                .endpointConfigurations(endpointConfigurations)
                .build()

        // call the handler
        val request = ResourceHandlerRequest.builder<ResourceModel>().desiredResourceState(model).build()
        val callbackContext = CallbackContext(5)
        val handler = CreateHandler()
        val response = handler.handleRequest(proxy!!, request, callbackContext, logger!!)

        // validate expectations
        assertThat(response).isNotNull()
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS)
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(1)
        assertThat(response.getCallbackContext()).isNotNull()
        assertThat(response.callbackContext!!.stabilizationRetriesRemaining).isEqualTo(4)
        assertThat(response.getResourceModels()).isNull()
        assertThat(response.getMessage()).isNull()

        // confirm the model did not mutate
        assertThat(response.getResourceModel()).isEqualTo(model)
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
                .trafficDialPercentage(100)
                .healthCheckPath("/HEALTH")
                .endpointConfigurations(endpointConfigurations)
                .build()

        // call the handler
        val request = ResourceHandlerRequest.builder<ResourceModel>().desiredResourceState(model).build()
        val callbackContext = CallbackContext(5)
        val handler = CreateHandler()
        val response = handler.handleRequest(proxy!!, request, callbackContext, logger!!)

        // validate expectations
        assertThat(response).isNotNull()
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS)
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0)
        assertThat(response.getCallbackContext()).isNull()
        assertThat(response.getResourceModels()).isNull()
        assertThat(response.getMessage()).isNull()

        // confirm the model did not mutate
        assertThat(response.getResourceModel()).isEqualTo(model)
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
                .trafficDialPercentage(100)
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
        assertEquals("Timed out waiting for endpoint group to be deployed.", exception.message)
    }
}
