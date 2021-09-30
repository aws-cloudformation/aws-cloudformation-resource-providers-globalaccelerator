package software.amazon.globalaccelerator.listener

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
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy
import software.amazon.cloudformation.proxy.HandlerErrorCode
import software.amazon.cloudformation.proxy.Logger
import software.amazon.cloudformation.proxy.OperationStatus
import software.amazon.cloudformation.proxy.ResourceHandlerRequest


@ExtendWith(MockKExtension::class)
class DeleteHandlerTest {
    @MockK
    lateinit var proxy: AmazonWebServicesClientProxy

    @MockK(relaxed = true)
    lateinit var logger: Logger

    @BeforeEach
    fun setup() = MockKAnnotations.init(this)

    private val acceleratorArn = "arn:aws:globalaccelerator::444607872184:accelerator/88127aa5-01d8-484c-80a0-349daaefce1d"
    private val listenerArn = "arn:aws:globalaccelerator::444607872184:accelerator/88127aa5-01d8-484c-80a0-349daaefce1d/listener/ee7358c2"

    @Test
    fun handleRequest_DeleteListener() {
        val model = ResourceModel.builder()
                .listenerArn(listenerArn)
                .acceleratorArn(acceleratorArn)
                .build()
        val request = ResourceHandlerRequest.builder<ResourceModel>()
                .desiredResourceState(model)
                .previousResourceState(model)
                .build()
        val context = CallbackContext(stabilizationRetriesRemaining = 10, pendingStabilization = false)
        val describeListenerResult = DescribeListenerResult().withListener(Listener().withListenerArn(listenerArn))

        every { proxy.injectCredentialsAndInvoke(ofType(), ofType<ProxyDescribeListener>()) } returns describeListenerResult

        every { proxy.injectCredentialsAndInvoke(ofType(), ofType<ProxyDeleteListener>()) } returns DeleteListenerResult()

        val response = DeleteHandler().handleRequest(proxy, request, context, logger)

        assertNotNull(response)
        assertEquals(OperationStatus.IN_PROGRESS, response.status)
        assertEquals(model, response.resourceModel)
        assertNotNull(response.resourceModel)
        assertNull(response.message)
        assertNull(response.errorCode)
        assertNull(response.resourceModels)
    }

    @Test
    fun handleRequest_AlreadyDeletedListener() {
        val model = ResourceModel.builder()
                .listenerArn(listenerArn)
                .build()
        val request = ResourceHandlerRequest.builder<ResourceModel>()
                .desiredResourceState(model)
                .previousResourceState(model)
                .build()

        every { proxy.injectCredentialsAndInvoke(ofType(), ofType<ProxyDescribeListener>()) } throws(ListenerNotFoundException("NOT FOUND"))

        val response = DeleteHandler().handleRequest(proxy, request, null, logger)

        assertNotNull(response)
        assertEquals(OperationStatus.FAILED, response.status)
        assertNull(response.callbackContext)
        assertEquals(model, response.resourceModel)
        assertNotNull(response.resourceModel)
        assertEquals("Listener not found.", response.message)
        assertEquals(HandlerErrorCode.NotFound, response.errorCode)
        assertNull(response.resourceModels)
    }

    @Test
    fun handleRequest_DeleteInProgressListener() {
        val describeAcceleratorResult = DescribeAcceleratorResult()
                .withAccelerator(Accelerator()
                        .withAcceleratorArn(acceleratorArn)
                        .withStatus(AcceleratorStatus.IN_PROGRESS))
        every { proxy.injectCredentialsAndInvoke(ofType(), ofType<ProxyDescribeAccelerator>()) } returns describeAcceleratorResult

        val model = ResourceModel.builder()
                .listenerArn(listenerArn)
                .acceleratorArn(acceleratorArn)
                .build()
        val request = ResourceHandlerRequest.builder<ResourceModel>()
                .desiredResourceState(model)
                .previousResourceState(model)
                .build()

        val context = CallbackContext(stabilizationRetriesRemaining = 10, pendingStabilization = true)
        val response = DeleteHandler().handleRequest(proxy, request, context, logger)

        assertNotNull(response)
        assertEquals(OperationStatus.IN_PROGRESS, response.status)
        assertEquals(model, response.resourceModel)
        assertNotNull(response.resourceModel)
        assertNull(response.message)
        assertNull(response.errorCode)
        assertNull(response.resourceModels)
    }

    @Test
    fun handleRequest_ListenerDoesNotExist_AcceleratorInProgress_ReturnsInProgress() {
        val describeAcceleratorResult = DescribeAcceleratorResult()
                .withAccelerator(Accelerator()
                        .withStatus(AcceleratorStatus.IN_PROGRESS))
        every { proxy.injectCredentialsAndInvoke(ofType(), ofType<ProxyDescribeAccelerator>()) } returns describeAcceleratorResult

        val model = ResourceModel.builder()
                .listenerArn(listenerArn)
                .acceleratorArn(acceleratorArn)
                .build()
        val request = ResourceHandlerRequest.builder<ResourceModel>()
                .desiredResourceState(model)
                .previousResourceState(model)
                .build()

        val context = CallbackContext(stabilizationRetriesRemaining = 10, pendingStabilization = true)
        val response = DeleteHandler().handleRequest(proxy, request, context, logger)

        assertNotNull(response)
        assertEquals(OperationStatus.IN_PROGRESS, response.status)
        assertEquals(model, response.resourceModel)
        assertNotNull(response.resourceModel)
        assertNull(response.message)
        assertNull(response.errorCode)
        assertNull(response.resourceModels)
    }

    @Test
    fun handleRequest_ThresholdTimeExceeded() {
        val model = ResourceModel.builder()
                .listenerArn(listenerArn)
                .acceleratorArn(acceleratorArn)
                .build()

        val request = ResourceHandlerRequest.builder<ResourceModel>()
                .desiredResourceState(model)
                .previousResourceState(model)
                .build()
        val context = CallbackContext(stabilizationRetriesRemaining = 0, pendingStabilization = true)

        val handler = DeleteHandler()
        val exception = assertThrows(RuntimeException::class.java) {
            handler.handleRequest(proxy, request, context, logger)
        }

        assertEquals("Timed out waiting for listener to be deployed.", exception.message)
    }

    @Test
    fun handleRequest_AcceleratorDoesntExist() {
        val model = ResourceModel.builder()
                .listenerArn("TEST_LISTENER_ARN")
                .acceleratorArn("TEST_ACCELERATOR_ARN")
                .build()

        val request = ResourceHandlerRequest.builder<ResourceModel>()
                .desiredResourceState(model)
                .previousResourceState(model)
                .build()

        every { proxy.injectCredentialsAndInvoke(ofType(), ofType<ProxyDescribeAccelerator>()) } throws(AcceleratorNotFoundException("NOT FOUND"))

        val context = CallbackContext(stabilizationRetriesRemaining = 10, pendingStabilization = true)
        val response = DeleteHandler().handleRequest(proxy, request, context, logger)

        assertNotNull(response)
        assertEquals(OperationStatus.SUCCESS, response.status)
        assertNull(response.callbackContext)
        assertNull(response.resourceModel)
        assertNull(response.message)
        assertNull(response.errorCode)
        assertNull(response.resourceModels)
    }
}

