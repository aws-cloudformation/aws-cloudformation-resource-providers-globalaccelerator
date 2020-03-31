package software.amazon.globalaccelerator.accelerator

import com.amazonaws.AmazonWebServiceResult
import com.amazonaws.ResponseMetadata
import com.amazonaws.services.globalaccelerator.model.Accelerator
import com.amazonaws.services.globalaccelerator.model.AcceleratorStatus
import com.amazonaws.services.globalaccelerator.model.DescribeAcceleratorRequest
import com.amazonaws.services.globalaccelerator.model.DescribeAcceleratorResult
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

@ExtendWith(MockitoExtension::class)
class DeleteHandlerTest {
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
    fun handleRequest_AcceleratorAlreadyDisabledAndInSync() {
        Mockito.doReturn(DescribeAcceleratorResult()
                        .withAccelerator(Accelerator()
                                .withAcceleratorArn("TEST_ARN")
                                .withEnabled(false)
                                .withStatus(AcceleratorStatus.DEPLOYED)))
                .`when`(proxy!!).injectCredentialsAndInvoke(ArgumentMatchers.any(DescribeAcceleratorRequest::class.java), ArgumentMatchers.any<Function<DescribeAcceleratorRequest, AmazonWebServiceResult<ResponseMetadata>>>())
        val handler = DeleteHandler()
        val model = ResourceModel.builder()
                .acceleratorArn("TEST_ARN")
                .build()
        val request = ResourceHandlerRequest.builder<ResourceModel>()
                .desiredResourceState(model)
                .previousResourceState(model)
                .build()
        val response = handler.handleRequest(proxy!!, request, null, logger!!)
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
