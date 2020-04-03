package software.amazon.globalaccelerator.listener

import com.amazonaws.AmazonWebServiceResult
import com.amazonaws.ResponseMetadata
import com.amazonaws.services.globalaccelerator.model.*
import com.amazonaws.services.globalaccelerator.model.PortRange
import junit.framework.Assert
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
import java.util.HashMap

import org.junit.jupiter.api.Assertions
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import java.util.function.Function

@ExtendWith(MockitoExtension::class)
class UpdateHandlerTest {

    @Mock
    private var proxy: AmazonWebServicesClientProxy? = null

    @Mock
    private var logger: Logger? = null

    @BeforeEach
    fun setup() {
        proxy = mock(AmazonWebServicesClientProxy::class.java)
        logger = mock(Logger::class.java)
    }

    /**
     * Sets up expectation that describe accelerator will be called on our test mock
     * @param acceleratorStatus the status the describe call should return
     */
    private fun expectDescribeAccelerator(acceleratorStatus: AcceleratorStatus) {
        // we expect a call to get the accelerator status
        val describeAcceleratorResult = DescribeAcceleratorResult()
                .withAccelerator(Accelerator()
                        .withAcceleratorArn("ACCELERATOR_ARN")
                        .withStatus(acceleratorStatus.toString()))
        doReturn(describeAcceleratorResult).`when`(proxy!!).injectCredentialsAndInvoke(ArgumentMatchers.any(DescribeAcceleratorRequest::class.java), ArgumentMatchers.any<Function<DescribeAcceleratorRequest, AmazonWebServiceResult<ResponseMetadata>>>())
    }

    private fun createTestResourceModel(): ResourceModel {
        val portRanges = ArrayList<software.amazon.globalaccelerator.listener.PortRange>()
        portRanges.add(software.amazon.globalaccelerator.listener.PortRange(80, 81))
        return ResourceModel.builder()
                .listenerArn("LISTENER_ARN")
                .acceleratorArn("ACCELERATOR_ARN")
                .clientAffinity("SOURCE_IP")
                .protocol("TCP")
                .portRanges(portRanges)
                .build()
    }

    @Test
    fun handleRequest_InitialUpdate_ReturnsInProgress() {
        expectDescribeAccelerator(AcceleratorStatus.IN_PROGRESS)

        // we expect a call to update
        val updateListenerResult = UpdateListenerResult()
                .withListener(Listener()
                        .withListenerArn("LISTENER_ARN")
                        .withClientAffinity(ClientAffinity.SOURCE_IP.toString())
                        .withProtocol(Protocol.TCP.toString())
                        .withPortRanges(PortRange().withFromPort(80).withToPort(81)))

        doReturn(updateListenerResult).`when`(proxy!!).injectCredentialsAndInvoke(ArgumentMatchers.any(UpdateListenerRequest::class.java), ArgumentMatchers.any<Function<UpdateListenerRequest, AmazonWebServiceResult<ResponseMetadata>>>())

        val model = createTestResourceModel()
        val request = ResourceHandlerRequest.builder<ResourceModel>().desiredResourceState(model).build()
        val response = UpdateHandler().handleRequest(proxy!!, request, null, logger!!)

        Assertions.assertNotNull(response)
        Assertions.assertEquals(response.getStatus(), OperationStatus.IN_PROGRESS)
        Assertions.assertEquals(response.getCallbackDelaySeconds(), 1)
        Assertions.assertNotNull(response.getCallbackContext())
        Assertions.assertNull(response.getResourceModels())
        Assertions.assertNull(response.getMessage())

        Assertions.assertEquals(response.getResourceModel(), model)
        Assertions.assertNotNull(response.getResourceModel())
    }

    @Test
    fun handleRequest_AcceleratorInProgress_ReturnsInProgress() {
        // intentionally do not provided expectation that UpdateListener is called.
        // strict mocking will cause us to throw if the handler calls update.

        // we expect a call to get the accelerator status
        expectDescribeAccelerator(AcceleratorStatus.IN_PROGRESS)

        val model = createTestResourceModel()
        val context = CallbackContext(stabilizationRetriesRemaining = 10, pendingStabilization = true)

        val request = ResourceHandlerRequest.builder<ResourceModel>().desiredResourceState(model).build()
        val response = UpdateHandler().handleRequest(proxy!!, request, context, logger!!)


        Assertions.assertNotNull(response)
        Assertions.assertEquals(response.getStatus(), OperationStatus.IN_PROGRESS)
        Assertions.assertEquals(response.getCallbackDelaySeconds(), 1)
        Assertions.assertNotNull(response.getCallbackContext())
        Assertions.assertNull(response.getResourceModels())
        Assertions.assertNull(response.getMessage())

        Assertions.assertEquals(response.getResourceModel(), model)
        Assertions.assertNotNull(response.getResourceModel())
    }

    @Test
    fun handleRequest_AcceleratorDoneDeploying_ReturnsSuccess() {
        expectDescribeAccelerator(AcceleratorStatus.DEPLOYED)

        // create the that will go to our handler
        val model = createTestResourceModel()
        val callbackMap = HashMap<String, String>()
        callbackMap.put(UpdateHandler.UPDATE_COMPLETED_KEY, "true")
        val context = CallbackContext(stabilizationRetriesRemaining = 10, pendingStabilization = true)

        val request = ResourceHandlerRequest.builder<ResourceModel>().desiredResourceState(model).build()
        val response = UpdateHandler().handleRequest(proxy!!, request, context, logger!!)


        Assertions.assertNotNull(response)
        Assertions.assertEquals(response.getStatus(), OperationStatus.SUCCESS)
        Assertions.assertNull(response.getResourceModels())
        Assertions.assertNull(response.getMessage())

        Assertions.assertEquals(response.getResourceModel(), model)
        Assertions.assertNotNull(response.getResourceModel())
    }

    @Test
    fun handleRequest_ThresholdTimeExceeded() {
        val model = createTestResourceModel()
        val callbackContext = CallbackContext(stabilizationRetriesRemaining = 0, pendingStabilization = true)

        val request = ResourceHandlerRequest.builder<ResourceModel>().desiredResourceState(model).build()
        val exception = Assertions.assertThrows(RuntimeException::class.java) {
            UpdateHandler().handleRequest(proxy!!, request, callbackContext, logger!!)
        }
        Assert.assertEquals("Timed out waiting for listener to be deployed.", exception.message)
    }
}
