package software.amazon.globalaccelerator.accelerator

import com.amazonaws.services.globalaccelerator.model.Accelerator
import com.amazonaws.services.globalaccelerator.model.AcceleratorNotFoundException
import com.amazonaws.services.globalaccelerator.model.AcceleratorStatus
import com.amazonaws.services.globalaccelerator.model.DescribeAcceleratorResult
import com.amazonaws.services.globalaccelerator.model.UpdateAcceleratorResult
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Assertions
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
class UpdateHandlerTest {

    @MockK
    lateinit var proxy: AmazonWebServicesClientProxy

    @MockK(relaxed = true)
    lateinit var logger: Logger



    private val ACCELERATOR_ARN = "arn:aws:globalaccelerator::444607872184:accelerator/88127aa5-01d8-484c-80a0-349daaefce1d"
    private fun createTestResourceModel(): ResourceModel {
        val t1 = Tag.builder().key("Key1").value("Value1").build()
        val listOfTags = listOf(t1)
        val ipAddresses = listOf("10.10.10.1", "10.10.10.2")
        return ResourceModel.builder()
                .acceleratorArn(ACCELERATOR_ARN)
                .enabled(true)
                .name("ACCELERATOR_NAME_2")
                .tags(listOfTags)
                .ipAddressType("IPV4")
                .ipAddresses(ipAddresses)
                .build()
    }

    @BeforeEach
    fun setup() = MockKAnnotations.init(this)

    @Test
    fun handleRequest_NonExistingAccelerator() {
        val handler = UpdateHandler()
        val desiredModel = createTestResourceModel()
        every { proxy.injectCredentialsAndInvoke(ofType(), ofType<ProxyDescribeAccelerator>()) } throws AcceleratorNotFoundException("Not found")
        val request = ResourceHandlerRequest.builder<ResourceModel>()
                .desiredResourceState(desiredModel)
                .build()
        val response = handler.handleRequest(proxy, request, null, logger)
        assertNotNull(response)
        assertEquals(OperationStatus.FAILED, response.status)
        assertEquals(HandlerErrorCode.NotFound, response.errorCode)
        assertNull(response.resourceModel)
    }

    @Test
    fun handleRequest_UpdateAccelerator_PendingReturnsInProgress() {
        val handler = UpdateHandler()
        val desiredModel = createTestResourceModel()
        val describeAcceleratorResult = DescribeAcceleratorResult()
                .withAccelerator(Accelerator()
                        .withAcceleratorArn(ACCELERATOR_ARN)
                        .withStatus(AcceleratorStatus.IN_PROGRESS.toString()))
        val updateAcceleratorResult = UpdateAcceleratorResult()
                .withAccelerator(Accelerator()
                        .withAcceleratorArn(ACCELERATOR_ARN))

        every { proxy.injectCredentialsAndInvoke(ofType(), ofType<ProxyDescribeAccelerator>()) } returns describeAcceleratorResult
        every { proxy.injectCredentialsAndInvoke(ofType(), ofType<ProxyUpdateAccelerator>()) } returns updateAcceleratorResult
        every { proxy.injectCredentialsAndInvoke(ofType(), ofType<ProxyTagResourceRequest>()) } returns null

        val request = ResourceHandlerRequest.builder<ResourceModel>()
                .desiredResourceState(desiredModel)
                .build()
        val response = handler.handleRequest(proxy, request, null, logger)
        assertNotNull(response)
        assertEquals(OperationStatus.IN_PROGRESS, response.status)
        assertEquals(0, response.callbackDelaySeconds)
        assertNotNull(response.resourceModel)
        assertEquals(ACCELERATOR_ARN, response.resourceModel.acceleratorArn)
        assertEquals(desiredModel.enabled, response.resourceModel.enabled)
        assertEquals(desiredModel.name, response.resourceModel.name)
        assertEquals(desiredModel.ipAddressType, response.resourceModel.ipAddressType)
        assertTrue(response.callbackContext?.pendingStabilization!!)
    }

    @Test
    fun handleRequest_UpdateAccelerator_DeployedReturnsSuccess() {
        val handler = UpdateHandler()
        val desiredModel = createTestResourceModel()
        val describeAcceleratorResult = DescribeAcceleratorResult()
                .withAccelerator(Accelerator()
                        .withAcceleratorArn(ACCELERATOR_ARN)
                        .withStatus(AcceleratorStatus.DEPLOYED.toString()))

        every { proxy.injectCredentialsAndInvoke(ofType(), ofType<ProxyDescribeAccelerator>()) } returns describeAcceleratorResult

        val request = ResourceHandlerRequest.builder<ResourceModel>()
                .desiredResourceState(desiredModel)
                .build()
        val context = CallbackContext(100, true)
        val response = handler.handleRequest(proxy, request, context, logger)
        assertNotNull(response)
        assertEquals(OperationStatus.SUCCESS, response.status)
        assertEquals(0, response.callbackDelaySeconds)
        assertNull(response.callbackContext)
        assertNotNull(response.resourceModel)
        assertEquals(ACCELERATOR_ARN, response.resourceModel.acceleratorArn)
        assertEquals(desiredModel.enabled, response.resourceModel.enabled)
        assertEquals(desiredModel.name, response.resourceModel.name)
        assertEquals(desiredModel.ipAddressType, response.resourceModel.ipAddressType)
    }

    @Test
    fun testStabilizationTimeout() {
        val handler = UpdateHandler()
        val desiredModel = createTestResourceModel()
        val describeAcceleratorResult = DescribeAcceleratorResult()
                .withAccelerator(Accelerator()
                        .withAcceleratorArn(ACCELERATOR_ARN)
                        .withStatus(AcceleratorStatus.IN_PROGRESS.toString()))
        every { proxy.injectCredentialsAndInvoke(ofType(), ofType<ProxyDescribeAccelerator>()) } returns describeAcceleratorResult

        val request = ResourceHandlerRequest.builder<ResourceModel>()
                .desiredResourceState(desiredModel)
                .build()
        val context = CallbackContext(0, true)
        val exception = Assertions.assertThrows(RuntimeException::class.java) {
            handler.handleRequest(proxy, request, context, logger)
        }
        assertEquals(HandlerCommons.TIMED_OUT_MESSAGE, exception.message)
    }

    @Test
    fun handleRequest_UpdateAccelerator_InvalidTag() {
        val handler = UpdateHandler()
        val desiredModel = createTestResourceModel()
        desiredModel.tags = listOf(Tag.builder().key("Key1").value("Value1?2").build())
        val describeAcceleratorResult = DescribeAcceleratorResult()
                .withAccelerator(Accelerator()
                        .withAcceleratorArn(ACCELERATOR_ARN)
                        .withStatus(AcceleratorStatus.IN_PROGRESS.toString()))
        val updateAcceleratorResult = UpdateAcceleratorResult()
                .withAccelerator(Accelerator()
                        .withAcceleratorArn(ACCELERATOR_ARN))

        every { proxy.injectCredentialsAndInvoke(ofType(), ofType<ProxyDescribeAccelerator>()) } returns describeAcceleratorResult
        every { proxy.injectCredentialsAndInvoke(ofType(), ofType<ProxyUpdateAccelerator>()) } returns updateAcceleratorResult
        every { proxy.injectCredentialsAndInvoke(ofType(), ofType<ProxyTagResourceRequest>()) } returns null

        val request = ResourceHandlerRequest.builder<ResourceModel>()
                .desiredResourceState(desiredModel)
                .build()
        val response = handler.handleRequest(proxy, request, null, logger)
        assertNotNull(response)
        assertEquals(OperationStatus.FAILED, response.status)
        assertEquals(HandlerErrorCode.InvalidRequest, response.errorCode)
    }

    @Test
    fun handleRequest_UpdateAccelerator_MissingTags() {
        val handler = UpdateHandler()
        val desiredModel = ResourceModel.builder()
                .acceleratorArn(ACCELERATOR_ARN)
                .enabled(true)
                .name("ACCELERATOR_NAME_2")
                .ipAddressType("IPV4")
                .ipAddresses(listOf("10.10.10.1", "10.10.10.2"))
                .build()

        val describeAcceleratorResult = DescribeAcceleratorResult()
                .withAccelerator(Accelerator()
                        .withAcceleratorArn(ACCELERATOR_ARN)
                        .withStatus(AcceleratorStatus.IN_PROGRESS.toString()))
        val updateAcceleratorResult = UpdateAcceleratorResult()
                .withAccelerator(Accelerator()
                        .withAcceleratorArn(ACCELERATOR_ARN))

        every { proxy.injectCredentialsAndInvoke(ofType(), ofType<ProxyDescribeAccelerator>()) } returns describeAcceleratorResult
        every { proxy.injectCredentialsAndInvoke(ofType(), ofType<ProxyUpdateAccelerator>()) } returns updateAcceleratorResult
        every { proxy.injectCredentialsAndInvoke(ofType(), ofType<ProxyTagResourceRequest>()) } returns null

        val request = ResourceHandlerRequest.builder<ResourceModel>()
                .desiredResourceState(desiredModel)
                .build()
        val response = handler.handleRequest(proxy, request, null, logger)
        assertNotNull(response)
        assertEquals(OperationStatus.IN_PROGRESS, response.status)
        assertEquals(0, response.callbackDelaySeconds)
        assertNotNull(response.resourceModel)
        assertEquals(ACCELERATOR_ARN, response.resourceModel.acceleratorArn)
        assertEquals(desiredModel.enabled, response.resourceModel.enabled)
        assertEquals(desiredModel.name, response.resourceModel.name)
        assertEquals(desiredModel.ipAddressType, response.resourceModel.ipAddressType)
        assertTrue(response.callbackContext?.pendingStabilization!!)
    }
}
