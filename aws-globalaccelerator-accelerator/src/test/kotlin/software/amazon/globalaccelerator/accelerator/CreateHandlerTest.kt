package software.amazon.globalaccelerator.accelerator

import com.amazonaws.AmazonWebServiceResult
import com.amazonaws.ResponseMetadata
import com.amazonaws.services.globalaccelerator.model.Accelerator
import com.amazonaws.services.globalaccelerator.model.AcceleratorStatus
import com.amazonaws.services.globalaccelerator.model.CreateAcceleratorRequest
import com.amazonaws.services.globalaccelerator.model.CreateAcceleratorResult
import com.amazonaws.services.globalaccelerator.model.DescribeAcceleratorRequest
import com.amazonaws.services.globalaccelerator.model.DescribeAcceleratorResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy
import software.amazon.cloudformation.proxy.Logger
import software.amazon.cloudformation.proxy.OperationStatus
import software.amazon.cloudformation.proxy.ResourceHandlerRequest
import java.util.*
import java.util.function.Function

@ExtendWith(MockitoExtension::class)
class CreateHandlerTest {
    @Mock
    private var proxy: AmazonWebServicesClientProxy? = null

    @Mock
    private var logger: Logger? = null

    @BeforeEach
    fun setup() {
        proxy = Mockito.mock(AmazonWebServicesClientProxy::class.java)
        logger = Mockito.mock(Logger::class.java)
    }

    @Test
    fun handleRequest_InitialStateCreatesAccelerator() {
        val result = CreateAcceleratorResult()
                .withAccelerator(Accelerator()
                        .withStatus(AcceleratorStatus.IN_PROGRESS)
                        .withEnabled(true)
                        .withAcceleratorArn("ACCELERATOR_ARN"))
        Mockito.doReturn(result).`when`(proxy!!).injectCredentialsAndInvoke(ArgumentMatchers.any(CreateAcceleratorRequest::class.java), ArgumentMatchers.any<Function<CreateAcceleratorRequest, AmazonWebServiceResult<ResponseMetadata>>>())

        // create the input
        val handler = CreateHandler()
        val tags = ArrayList<Tag>()
        tags.add(Tag.builder().key("K1").value("V1").build())
        val model = ResourceModel.builder().enabled(true).name("AcceleratorTest").tags(tags).build()
        val request = ResourceHandlerRequest.builder<ResourceModel>().desiredResourceState(model).build()
        val response = handler.handleRequest(proxy!!, request, null, logger!!)
        assertNotNull(response)
        assertEquals(OperationStatus.IN_PROGRESS, response.status)
        assertEquals(0, response.callbackDelaySeconds)
        assertNotNull(response.callbackContext)
        assertNotNull(response.resourceModel)
        assertEquals("ACCELERATOR_ARN", response.resourceModel.acceleratorArn)
        assertNull(response.resourceModels)
        assertNull(response.message)
    }

    @Test
    fun handleRequest_PendingDeployedReturnsInProgress() {
        val result = DescribeAcceleratorResult()
                .withAccelerator(Accelerator()
                        .withStatus(AcceleratorStatus.IN_PROGRESS)
                        .withEnabled(true)
                        .withAcceleratorArn("ACCELERATOR_ARN"))
        Mockito.doReturn(result).`when`(proxy!!).injectCredentialsAndInvoke(ArgumentMatchers.any(DescribeAcceleratorRequest::class.java), ArgumentMatchers.any<Function<DescribeAcceleratorRequest, AmazonWebServiceResult<ResponseMetadata>>>())

        // create the input
        val handler = CreateHandler()
        val tags = ArrayList<Tag>()
        tags.add(Tag.builder().key("K1").value("V1").build())
        val model = ResourceModel.builder()
                .enabled(true)
                .name("AcceleratorTest")
                .tags(tags)
                .acceleratorArn("ACCELERATOR_ARN").build()
        val request = ResourceHandlerRequest.builder<ResourceModel>().desiredResourceState(model).build()
        val callbackContext = CallbackContext(10)
        val response = handler.handleRequest(proxy!!, request, callbackContext, logger!!)
        assertNotNull(response)
        assertEquals(OperationStatus.IN_PROGRESS, response.status)
        assertEquals(1, response.callbackDelaySeconds)
        assertNotNull(response.callbackContext)
        assertEquals(response.callbackContext!!.stabilizationRetriesRemaining, callbackContext.stabilizationRetriesRemaining - 1)
        assertNotNull(response.resourceModel)
        assertEquals("ACCELERATOR_ARN", response.resourceModel.acceleratorArn)
        assertNull(response.resourceModels)
        assertNull(response.message)
    }

    @Test
    fun handleRequest_DeployedReturnsSuccess() {
        val result = DescribeAcceleratorResult()
                .withAccelerator(Accelerator()
                        .withStatus(AcceleratorStatus.DEPLOYED)
                        .withEnabled(true)
                        .withAcceleratorArn("ACCELERATOR_ARN"))
        Mockito.doReturn(result).`when`(proxy!!).injectCredentialsAndInvoke(ArgumentMatchers.any(DescribeAcceleratorRequest::class.java), ArgumentMatchers.any<Function<DescribeAcceleratorRequest, AmazonWebServiceResult<ResponseMetadata>>>())

        // create the input
        val handler = CreateHandler()
        val tags = ArrayList<Tag>()
        tags.add(Tag.builder().key("K1").value("V1").build())
        val model = ResourceModel.builder()
                .enabled(true)
                .name("AcceleratorTest")
                .tags(tags)
                .acceleratorArn("ACCELERATOR_ARN").build()
        val request = ResourceHandlerRequest.builder<ResourceModel>().desiredResourceState(model).build()
        val callbackContext = CallbackContext(10)
        val response = handler.handleRequest(proxy!!, request, callbackContext, logger!!)
        assertNotNull(response)
        assertEquals(OperationStatus.SUCCESS, response.status)
        assertEquals(0, response.callbackDelaySeconds)
        assertNotNull(response.resourceModel)
        assertEquals("ACCELERATOR_ARN", response.resourceModel.acceleratorArn)
        assertNull(response.resourceModels)
        assertNull(response.message)
    }

    @Test
    fun handleRequest_StabilizationTooLongFails() {
        // create the input
        val handler = CreateHandler()
        val model = ResourceModel.builder()
                .acceleratorArn("ACCELERATOR_ARN").build()
        val request = ResourceHandlerRequest.builder<ResourceModel>().desiredResourceState(model).build()
        val callbackContext = CallbackContext(0)
        val exception = assertThrows(RuntimeException::class.java) {
            handler.handleRequest(proxy!!, request, callbackContext, logger!!)
        }
        assertEquals("Timed out waiting for global accelerator to be deployed.", exception.message)
    }
}
