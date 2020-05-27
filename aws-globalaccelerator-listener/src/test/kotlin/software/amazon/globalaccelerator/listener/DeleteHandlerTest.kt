package software.amazon.globalaccelerator.listener

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

import org.junit.jupiter.api.Assertions
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
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
    fun handleRequest_DeletesListener() {

        doReturn(DescribeListenerResult().withListener(Listener().withListenerArn("TEST_LISTENER_ARN")))
                .`when`(proxy!!).injectCredentialsAndInvoke(ArgumentMatchers.any(DescribeListenerRequest::class.java), ArgumentMatchers.any<Function<DescribeListenerRequest, AmazonWebServiceResult<ResponseMetadata>>>())

        doReturn(DeleteListenerResult()).`when`(proxy!!).injectCredentialsAndInvoke(ArgumentMatchers.any(DeleteListenerRequest::class.java), ArgumentMatchers.any<Function<DeleteListenerRequest, AmazonWebServiceResult<ResponseMetadata>>>())

        val handler = DeleteHandler()
        val model = ResourceModel.builder()
                .listenerArn("TEST_LISTENER_ARN")
                .build()

        val request = ResourceHandlerRequest.builder<ResourceModel>()
                .desiredResourceState(model)
                .previousResourceState(model)
                .build()

        val response = handler.handleRequest(proxy!!, request, null, logger!!)

        Assertions.assertNotNull(response)
        Assertions.assertEquals(response.status, OperationStatus.SUCCESS)
        Assertions.assertNull(response.callbackContext)
        Assertions.assertEquals(response.resourceModel, model)
        Assertions.assertNotNull(response.resourceModel)
        Assertions.assertNull(response.message)
        Assertions.assertNull(response.errorCode)
        Assertions.assertNull(response.resourceModels)
    }
}
