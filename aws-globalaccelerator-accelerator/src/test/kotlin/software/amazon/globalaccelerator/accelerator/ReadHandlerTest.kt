package software.amazon.globalaccelerator.accelerator

import com.amazonaws.services.globalaccelerator.model.Accelerator
import com.amazonaws.services.globalaccelerator.model.AcceleratorNotFoundException
import com.amazonaws.services.globalaccelerator.model.AcceleratorStatus
import com.amazonaws.services.globalaccelerator.model.DescribeAcceleratorResult
import com.amazonaws.services.globalaccelerator.model.IpSet
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
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
class ReadHandlerTest {
    @MockK
    lateinit var proxy: AmazonWebServicesClientProxy

    @MockK(relaxed = true)
    lateinit var logger: Logger

    @BeforeEach
    fun setup() = MockKAnnotations.init(this)

    private val ACCELERATOR_ARN = "arn:aws:globalaccelerator::444607872184:accelerator/88127aa5-01d8-484c-80a0-349daaefce1d"
    private var ipSet: IpSet? = null
    private fun createTestResourceModel(): ResourceModel {
        val ipAddresses = listOf("10.10.10.1", "10.10.10.2")
        ipSet = IpSet().withIpAddresses(ipAddresses)
        return ResourceModel.builder()
                .acceleratorArn(ACCELERATOR_ARN)
                .enabled(true)
                .name("Name")
                .ipAddresses(ipAddresses)
                .ipAddressType("IPV4")
                .build()
    }

    @Test
    fun handleRequest_returnsMappedAccelerator() {
        val model = createTestResourceModel()
        val describeAcceleratorResult = DescribeAcceleratorResult()
                .withAccelerator(Accelerator()
                        .withAcceleratorArn(ACCELERATOR_ARN)
                        .withStatus(AcceleratorStatus.IN_PROGRESS.toString())
                        .withEnabled(true)
                        .withIpAddressType("IPV4")
                        .withName("Name")
                        .withIpSets(ipSet)
                )
        every { proxy.injectCredentialsAndInvoke(ofType(), ofType<ProxyDescribeAccelerator>()) } returns describeAcceleratorResult

        val request = ResourceHandlerRequest.builder<ResourceModel>().desiredResourceState(model).build()
        val response = ReadHandler().handleRequest(proxy, request, null, logger)
        assertNotNull(response)
        assertEquals(OperationStatus.SUCCESS, response.status)
        assertNull(response.callbackContext)
        assertNull(response.resourceModels)
        assertNull(response.message)
        assertNotNull(response.resourceModel)
        assertEquals(ACCELERATOR_ARN, response.resourceModel.acceleratorArn)
        assertEquals("IPV4", response.resourceModel.ipAddressType)
        assertEquals("Name", response.resourceModel.name)
        assertTrue(response.resourceModel.enabled)
        assertEquals(model.ipAddresses, response.resourceModel.ipAddresses)
    }

    @Test
    fun handleRequest_nonExistingAccelerator() {
        val model = createTestResourceModel()
        every { proxy.injectCredentialsAndInvoke(ofType(), ofType<ProxyDescribeAccelerator>()) } throws AcceleratorNotFoundException("NOT FOUND")

        val request = ResourceHandlerRequest.builder<ResourceModel>().desiredResourceState(model).build()
        val response = ReadHandler().handleRequest(proxy, request, null, logger)
        assertNotNull(response)
        assertEquals(OperationStatus.FAILED, response.status)
        assertEquals(HandlerErrorCode.NotFound, response.errorCode)
        assertNull(response.resourceModel)
    }
}
