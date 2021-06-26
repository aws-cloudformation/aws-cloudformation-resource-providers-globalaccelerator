package software.amazon.globalaccelerator.listener

import com.amazonaws.AmazonWebServiceResult
import com.amazonaws.ResponseMetadata
import com.amazonaws.services.globalaccelerator.model.Accelerator
import com.amazonaws.services.globalaccelerator.model.AcceleratorNotFoundException
import com.amazonaws.services.globalaccelerator.model.AcceleratorStatus
import com.amazonaws.services.globalaccelerator.model.DeleteListenerRequest
import com.amazonaws.services.globalaccelerator.model.DeleteListenerResult
import com.amazonaws.services.globalaccelerator.model.DescribeAcceleratorRequest
import com.amazonaws.services.globalaccelerator.model.DescribeAcceleratorResult
import com.amazonaws.services.globalaccelerator.model.DescribeListenerRequest
import com.amazonaws.services.globalaccelerator.model.DescribeListenerResult
import com.amazonaws.services.globalaccelerator.model.Listener
import com.amazonaws.services.globalaccelerator.model.ListenerNotFoundException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.junit.jupiter.api.Assertions
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.mockito.Mockito.doThrow
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy
import software.amazon.cloudformation.proxy.HandlerErrorCode
import software.amazon.cloudformation.proxy.Logger
import software.amazon.cloudformation.proxy.OperationStatus
import software.amazon.cloudformation.proxy.ResourceHandlerRequest
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
    fun handleRequest_DeleteListener() {
        val handler = DeleteHandler()
        val model = ResourceModel.builder()
                .listenerArn("TEST_LISTENER_ARN")
                .acceleratorArn("TEST_ACCELERATOR_ARN")
                .build()
        val request = ResourceHandlerRequest.builder<ResourceModel>()
                .desiredResourceState(model)
                .previousResourceState(model)
                .build()
        val context = CallbackContext(stabilizationRetriesRemaining = 10, pendingStabilization = false)
        val listener = DescribeListenerResult().withListener(Listener().withListenerArn("TEST_LISTENER_ARN"))

        doReturn(listener).`when`(proxy!!).injectCredentialsAndInvoke(any(DescribeListenerRequest::class.java),
                any<Function<DescribeListenerRequest, AmazonWebServiceResult<ResponseMetadata>>>())
        doReturn(DeleteListenerResult()).`when`(proxy!!).injectCredentialsAndInvoke(any(DeleteListenerRequest::class.java),
                any<Function<DeleteListenerRequest, AmazonWebServiceResult<ResponseMetadata>>>())

        val response = handler.handleRequest(proxy!!, request, context, logger!!)

        Assertions.assertNotNull(response)
        Assertions.assertEquals(response.status, OperationStatus.IN_PROGRESS)
        Assertions.assertEquals(response.resourceModel, model)
        Assertions.assertNotNull(response.resourceModel)
        Assertions.assertNull(response.message)
        Assertions.assertNull(response.errorCode)
        Assertions.assertNull(response.resourceModels)
    }

    @Test
    fun handleRequest_AlreadyDeletedListener() {
        val handler = DeleteHandler()
        val model = ResourceModel.builder()
                .listenerArn("TEST_LISTENER_ARN")
                .build()
        val request = ResourceHandlerRequest.builder<ResourceModel>()
                .desiredResourceState(model)
                .previousResourceState(model)
                .build()

        doThrow(ListenerNotFoundException("NOT FOUND")).`when`(proxy)!!.injectCredentialsAndInvoke(any(DescribeListenerRequest::class.java),
                any<Function<DescribeListenerRequest, AmazonWebServiceResult<ResponseMetadata>>>())

        val response = handler.handleRequest(proxy!!, request, null, logger!!)

        Assertions.assertNotNull(response)
        Assertions.assertEquals(response.status, OperationStatus.FAILED)
        Assertions.assertNull(response.callbackContext)
        Assertions.assertEquals(response.resourceModel, model)
        Assertions.assertNotNull(response.resourceModel)
        Assertions.assertEquals(response.message, "Listener Not Found")
        Assertions.assertEquals(response.errorCode, HandlerErrorCode.NotFound)
        Assertions.assertNull(response.resourceModels)
    }

    @Test
    fun handleRequest_DeleteInProgressListener() {
        val describeAcceleratorResult = DescribeAcceleratorResult()
                .withAccelerator(Accelerator()
                        .withAcceleratorArn("ACCELERATOR_ARN")
                        .withStatus(AcceleratorStatus.IN_PROGRESS))
        val handler = DeleteHandler()
        val model = ResourceModel.builder()
                .listenerArn("TEST_LISTENER_ARN")
                .acceleratorArn("TEST_ACCELERATOR_ARN")
                .build()
        val request = ResourceHandlerRequest.builder<ResourceModel>()
                .desiredResourceState(model)
                .previousResourceState(model)
                .build()
        val context = CallbackContext(stabilizationRetriesRemaining = 10, pendingStabilization = true)

        doReturn(describeAcceleratorResult).`when`(proxy!!).injectCredentialsAndInvoke(any(DescribeAcceleratorRequest::class.java), any<Function<DescribeAcceleratorRequest, AmazonWebServiceResult<ResponseMetadata>>>())

        val response = handler.handleRequest(proxy!!, request, context, logger!!)

        Assertions.assertNotNull(response)
        Assertions.assertEquals(response.status, OperationStatus.IN_PROGRESS)
        Assertions.assertEquals(response.resourceModel, model)
        Assertions.assertNotNull(response.resourceModel)
        Assertions.assertNull(response.message)
        Assertions.assertNull(response.errorCode)
        Assertions.assertNull(response.resourceModels)
    }

    @Test
    fun handleRequest_ListenerDoesNotExist_AcceleratorInProgress_ReturnsInProgress() {
        val describeAcceleratorResult = DescribeAcceleratorResult()
                .withAccelerator(Accelerator()
                        .withStatus(AcceleratorStatus.IN_PROGRESS))
        val handler = DeleteHandler()
        val model = ResourceModel.builder()
                .listenerArn("TEST_LISTENER_ARN")
                .acceleratorArn("TEST_ACCELERATOR_ARN")
                .build()
        val request = ResourceHandlerRequest.builder<ResourceModel>()
                .desiredResourceState(model)
                .previousResourceState(model)
                .build()
        val context = CallbackContext(stabilizationRetriesRemaining = 10, pendingStabilization = true)

        doReturn(describeAcceleratorResult).`when`(proxy!!).injectCredentialsAndInvoke(any(DescribeAcceleratorRequest::class.java), any<Function<DescribeAcceleratorRequest, AmazonWebServiceResult<ResponseMetadata>>>())

        val response = handler.handleRequest(proxy!!, request, context, logger!!)

        Assertions.assertNotNull(response)
        Assertions.assertEquals(response.status, OperationStatus.IN_PROGRESS)
        Assertions.assertEquals(response.resourceModel, model)
        Assertions.assertNotNull(response.resourceModel)
        Assertions.assertNull(response.message)
        Assertions.assertNull(response.errorCode)
        Assertions.assertNull(response.resourceModels)
    }

    @Test
    fun handleRequest_ThresholdTimeExceeded() {

        val handler = DeleteHandler()
        val model = ResourceModel.builder()
                .listenerArn("TEST_LISTENER_ARN")
                .acceleratorArn("TEST_ACCELERATOR_ARN")
                .build()
        val request = ResourceHandlerRequest.builder<ResourceModel>()
                .desiredResourceState(model)
                .previousResourceState(model)
                .build()
        val context = CallbackContext(stabilizationRetriesRemaining = 0, pendingStabilization = true)

        val exception = Assertions.assertThrows(RuntimeException::class.java) {
            handler.handleRequest(proxy!!, request, context, logger!!)
        }

        Assertions.assertEquals("Timed out waiting for listener to be deployed.", exception.message)
    }

    @Test
    fun handleRequest_AcceleratorDoesntExist() {

        val handler = DeleteHandler()
        val model = ResourceModel.builder()
                .listenerArn("TEST_LISTENER_ARN")
                .acceleratorArn("TEST_ACCELERATOR_ARN")
                .build()
        val request = ResourceHandlerRequest.builder<ResourceModel>()
                .desiredResourceState(model)
                .previousResourceState(model)
                .build()
        val context = CallbackContext(stabilizationRetriesRemaining = 10, pendingStabilization = true)

        doThrow(AcceleratorNotFoundException("NOT FOUND")).`when`(proxy)!!.injectCredentialsAndInvoke(any(DescribeAcceleratorRequest::class.java),
                any<Function<DescribeAcceleratorRequest, AmazonWebServiceResult<ResponseMetadata>>>())

        val response = handler.handleRequest(proxy!!, request, context, logger!!)

        Assertions.assertNotNull(response)
        Assertions.assertEquals(response.status, OperationStatus.SUCCESS)
        Assertions.assertNull(response.callbackContext)
        Assertions.assertNull(response.resourceModel)
        Assertions.assertNull(response.message)
        Assertions.assertNull(response.errorCode)
        Assertions.assertNull(response.resourceModels)
    }
}
