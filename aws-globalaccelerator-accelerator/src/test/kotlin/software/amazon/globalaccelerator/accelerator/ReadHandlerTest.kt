package software.amazon.globalaccelerator.accelerator

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy
import software.amazon.cloudformation.proxy.Logger
import software.amazon.cloudformation.proxy.OperationStatus
import software.amazon.cloudformation.proxy.ResourceHandlerRequest

@ExtendWith(MockitoExtension::class)
class ReadHandlerTest {
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
    fun handleRequest_SimpleSuccess() {
        val handler = ReadHandler()
        val model = ResourceModel.builder().build()
        val request = ResourceHandlerRequest.builder<ResourceModel>()
                .desiredResourceState(model)
                .build()
        val response = handler.handleRequest(proxy!!, request, null, logger!!)
        assertNotNull(response)
        assertEquals(OperationStatus.SUCCESS, response.status)
        assertNull(response.callbackContext)
        assertEquals(0, response.callbackDelaySeconds)
        assertEquals(request.desiredResourceState, response.resourceModel)
        assertNull(response.resourceModels)
        assertNull(response.message)
        assertNull(response.errorCode)
    }
}
