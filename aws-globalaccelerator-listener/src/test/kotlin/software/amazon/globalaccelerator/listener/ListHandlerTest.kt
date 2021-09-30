package software.amazon.globalaccelerator.listener

import com.amazonaws.services.globalaccelerator.model.Listener
import com.amazonaws.services.globalaccelerator.model.ListListenersResult
import com.amazonaws.services.globalaccelerator.model.Protocol
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
class ListHandlerTest {
    @MockK
    lateinit var proxy: AmazonWebServicesClientProxy

    @MockK(relaxed = true)
    lateinit var logger: Logger

    @BeforeEach
    fun setup() = MockKAnnotations.init(this)

    private val acceleratorArn = "arn:aws:globalaccelerator::444607872184:accelerator/88127aa5-01d8-484c-80a0-349daaefce1d"
    private val listenerArn1 = "arn:aws:globalaccelerator::444607872184:accelerator/88127aa5-01d8-484c-80a0-349daaefce1d/listener/ee7358c2"
    private val fromPort1 = 9090
    private val toPort1 = 9099
    private val protocol1 = "TCP"
    private val clientAffinity1 = "NONE"
    private val listenerArn2 = "arn:aws:globalaccelerator::444607872184:accelerator/88127aa5-01d8-484c-80a0-349daaefce1d/listener/ee8675c2"
    private val fromPort2 = 4242
    private val toPort2 = 4243
    private val protocol2 = "UDP"
    private val clientAffinity2 = "SOURCE_IP"


    private fun createTestResourceModel(arn: String, fromPort: Int, toPort: Int,
                                        protocol: String, clientAffinity: String): ResourceModel {
        return ResourceModel.builder()
                .listenerArn(arn)
                .acceleratorArn(acceleratorArn)
                .portRanges(listOf(PortRange(fromPort, toPort)))
                .protocol(protocol)
                .clientAffinity(clientAffinity)
                .build()
    }
    @Test
    fun handleRequest_returnsMappedListeners() {
        val model1 = createTestResourceModel(listenerArn1, fromPort1, toPort1, protocol1, clientAffinity1)
        val model2 = createTestResourceModel(listenerArn2, fromPort2, toPort2, protocol2, clientAffinity2)
        val listeners = mutableListOf(
                Listener()
                        .withListenerArn(listenerArn1)
                        .withPortRanges(listOf(com.amazonaws.services.globalaccelerator.model.PortRange().withFromPort(fromPort1).withToPort(toPort1)))
                        .withProtocol(protocol1)
                        .withClientAffinity(clientAffinity1),
                Listener()
                        .withListenerArn(listenerArn2)
                        .withPortRanges(listOf(com.amazonaws.services.globalaccelerator.model.PortRange().withFromPort(fromPort2).withToPort(toPort2)))
                        .withProtocol(protocol2)
                        .withClientAffinity(clientAffinity2)
        )
        val listListenersResult = ListListenersResult().withListeners(listeners)
        every { proxy.injectCredentialsAndInvoke(ofType(), ofType<ProxyListListeners>()) } returns listListenersResult
        val request = ResourceHandlerRequest.builder<ResourceModel>().desiredResourceState(model1).build()
        val response = ListHandler().handleRequest(proxy, request, null, logger)
        assertNotNull(response)
        assertEquals(OperationStatus.SUCCESS, response.status)
        assertNull(response.callbackContext)
        assertNull(response.resourceModel)
        assertNull(response.message)
        assertNotNull(response.resourceModels)
        assertNull(response.nextToken)
        assertEquals(2, response.resourceModels.size)
        assertEquals(acceleratorArn, response.resourceModels[0].acceleratorArn)
        assertEquals(model1.listenerArn, response.resourceModels[0].listenerArn)
        assertEquals(1, response.resourceModels[0].portRanges.size)
        assertEquals(model1.portRanges[0].fromPort, response.resourceModels[0].portRanges[0].fromPort)
        assertEquals(model1.portRanges[0].toPort, response.resourceModels[0].portRanges[0].toPort)
        assertEquals(model1.protocol, response.resourceModels[0].protocol)
        assertEquals(model1.clientAffinity, response.resourceModels[0].clientAffinity)
        assertEquals(acceleratorArn, response.resourceModels[1].acceleratorArn)
        assertEquals(model2.listenerArn, response.resourceModels[1].listenerArn)
        assertEquals(1, response.resourceModels[1].portRanges.size)
        assertEquals(model2.portRanges[0].fromPort, response.resourceModels[1].portRanges[0].fromPort)
        assertEquals(model2.portRanges[0].toPort, response.resourceModels[1].portRanges[0].toPort)
        assertEquals(model2.protocol, response.resourceModels[1].protocol)
        assertEquals(model2.clientAffinity, response.resourceModels[1].clientAffinity)

    }

    @Test
    fun handleRequest_noListeners() {
        val model = createTestResourceModel(listenerArn1, fromPort1, toPort1, protocol1, clientAffinity1)
        val listListenersResult = ListListenersResult().withListeners(emptyList())
        every { proxy.injectCredentialsAndInvoke(ofType(), ofType<ProxyListListeners>()) } returns listListenersResult
        val request = ResourceHandlerRequest.builder<ResourceModel>().desiredResourceState(model).build()
        val response = ListHandler().handleRequest(proxy, request, null, logger)
        assertNotNull(response)
        assertEquals(OperationStatus.SUCCESS, response.status)
        assertNotNull(response.resourceModels)
        assertEquals(0, response.resourceModels.size)
        assertNull(response.nextToken)
    }

    @Test
    fun handleRequest_returnsMappedListenersWithToken() {
        val expectedNextToken = "This_token_is_expected"
        val model = createTestResourceModel(listenerArn1, fromPort1, toPort1, protocol1, clientAffinity1)
        val listeners = mutableListOf(
                Listener()
                    .withListenerArn(listenerArn1)
                    .withPortRanges(listOf(com.amazonaws.services.globalaccelerator.model.PortRange().withFromPort(fromPort1).withToPort(toPort1)))
                    .withProtocol(protocol1)
                    .withClientAffinity(clientAffinity1)
        )
        val listListenersResult = ListListenersResult()
                .withListeners(listeners)
                .withNextToken(expectedNextToken)
        every { proxy.injectCredentialsAndInvoke(ofType(), ofType<ProxyListListeners>()) } returns listListenersResult
        val request = ResourceHandlerRequest.builder<ResourceModel>().desiredResourceState(model).nextToken("next_token").build()
        val response = ListHandler().handleRequest(proxy, request, null, logger)
        assertNotNull(response)
        assertEquals(OperationStatus.SUCCESS, response.status)
        assertNull(response.callbackContext)
        assertNull(response.resourceModel)
        assertNull(response.message)
        assertNotNull(response.resourceModels)
        assertNotNull(response.nextToken)
        assertEquals(expectedNextToken, response.nextToken)
        assertEquals(1, response.resourceModels.size)
        assertEquals(listenerArn1, response.resourceModels[0].listenerArn)
    }
}
