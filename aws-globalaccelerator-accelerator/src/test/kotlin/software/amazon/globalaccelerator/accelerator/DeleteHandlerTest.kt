package software.amazon.globalaccelerator.accelerator

import com.amazonaws.AmazonWebServiceResult
import com.amazonaws.ResponseMetadata
import com.amazonaws.services.globalaccelerator.model.Accelerator
import com.amazonaws.services.globalaccelerator.model.AcceleratorStatus
import com.amazonaws.services.globalaccelerator.model.DeleteAcceleratorResult
import com.amazonaws.services.globalaccelerator.model.DescribeAcceleratorRequest
import com.amazonaws.services.globalaccelerator.model.DescribeAcceleratorResult
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy
import software.amazon.cloudformation.proxy.Logger
import software.amazon.cloudformation.proxy.OperationStatus
import software.amazon.cloudformation.proxy.ResourceHandlerRequest
import java.util.function.Function

@ExtendWith(MockKExtension::class)
class DeleteHandlerTest {
    @MockK
    lateinit var proxy: AmazonWebServicesClientProxy

    @MockK(relaxed = true)
    lateinit var logger: Logger

    @BeforeEach
    fun setup() = MockKAnnotations.init(this)

    @Test
    fun handleRequest_AcceleratorAlreadyDisabledAndInSync() {
        val result = DescribeAcceleratorResult()
                .withAccelerator(Accelerator()
                .withAcceleratorArn("TEST_ARN")
                .withEnabled(false)
                .withStatus(AcceleratorStatus.DEPLOYED))
        every { proxy.injectCredentialsAndInvoke(ofType(), ofType<ProxyDescribeAccelerator>()) } returns result
        every { proxy.injectCredentialsAndInvoke(ofType(), ofType<ProxyDeleteAccelerator>()) } returns DeleteAcceleratorResult()


        val handler = DeleteHandler()
        val model = ResourceModel.builder()
                .acceleratorArn("TEST_ARN")
                .build()
        val request = ResourceHandlerRequest.builder<ResourceModel>()
                .desiredResourceState(model)
                .previousResourceState(model)
                .build()
        val response = handler.handleRequest(proxy, request, null, logger)
        assertNotNull(response)
        assertEquals(OperationStatus.IN_PROGRESS, response.status)
        assertNotNull(response.callbackContext)
        assertEquals(1, response.callbackDelaySeconds)
        assertNotNull(response.resourceModel)
        assertNull(response.resourceModels)
        assertNull(response.message)
        assertNull(response.errorCode)
    }
}
