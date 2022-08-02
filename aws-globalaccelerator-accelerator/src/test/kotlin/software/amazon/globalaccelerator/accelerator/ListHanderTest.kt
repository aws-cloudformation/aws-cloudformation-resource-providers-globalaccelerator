package software.amazon.globalaccelerator.accelerator

import com.amazonaws.services.globalaccelerator.model.Accelerator
import com.amazonaws.services.globalaccelerator.model.AcceleratorStatus
import com.amazonaws.services.globalaccelerator.model.ListAcceleratorsRequest
import com.amazonaws.services.globalaccelerator.model.ListAcceleratorsResult
import com.amazonaws.services.globalaccelerator.model.IpSet
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.slot
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

    private val ipFamily = "IPV4"
    private val acceleratorArn1 = "arn:aws:globalaccelerator::444607872184:accelerator/88127aa5-01d8-484c-80a0-349daaefce1d"
    private val ipAddresses1 = listOf("10.10.10.1", "10.10.10.2")
    private val ipSet1 = IpSet().withIpAddresses(ipAddresses1).withIpFamily("IPV4")
    private val name1 = "Name1"
    private val dnsName1 = "DNS_NAME_HERE"
    private val acceleratorArn2 = "arn:aws:globalaccelerator::444607872184:accelerator/86753aa5-01d8-484c-80a0-349daaefce09"
    private val ipAddresses2 = listOf("10.10.42.1", "10.10.42.2")
    private val ipSet2 = IpSet().withIpAddresses(ipAddresses2).withIpFamily("IPV4")
    private val name2 = "Name2"
    private val dnsName2 = "DNS_NAME_HERE_TWO"
    private val dualStackAddressType = "DUAL_STACK"
    private val acceleratorArn3 = "arn:aws:globalaccelerator::444607872184:accelerator/86753aa5-01d8-484c-80a0-349daaef4242"
    private val ipAddresses3 = listOf("10.10.84.1", "10.10.84.2")
    private val ipSet3 = IpSet().withIpAddresses(ipAddresses3).withIpFamily("IPV4")
    private val ipv6Addresses = listOf("2600:9000:a16f:94b4:4352:4d16:11d5:561b", "2600:9000:a1f9:b20f:f92:8279:1e86:ae75")
    private val ipSet4 = IpSet().withIpAddresses(ipv6Addresses).withIpFamily("IPV6")
    private val name3 = "Name2"
    private val dnsName3 = "DNS_NAME_HERE_TWO"
    private val dualStackDnsName = "DUAL_STACK_DNS_NAME_HERE"

    private fun createTestResourceModel(arn: String, ipAddresses: List<String>, name: String, dnsName: String): ResourceModel {
        return ResourceModel.builder()
                .acceleratorArn(arn)
                .enabled(true)
                .name(name)
                .dnsName(dnsName)
                .ipAddresses(ipAddresses)
                .ipAddressType(ipFamily)
                .build()
    }

    @Test
    fun handleRequest_returnsMappedAccelerators() {
        val model1 = createTestResourceModel(acceleratorArn1, ipAddresses1, name1, dnsName1)
        val model2 = createTestResourceModel(acceleratorArn2, ipAddresses2, name2, dnsName2)
        val model3 = createTestResourceModel(acceleratorArn3, ipAddresses3, name3, dnsName3)
        model3.dualStackDnsName = dualStackDnsName
        model3.ipv6Addresses = ipv6Addresses
        model3.ipAddressType = dualStackAddressType

        val accelerators = mutableListOf(
                Accelerator()
                        .withAcceleratorArn(acceleratorArn1)
                        .withStatus(AcceleratorStatus.IN_PROGRESS.toString())
                        .withEnabled(true)
                        .withIpAddressType(ipFamily)
                        .withName(name1)
                        .withDnsName(dnsName1)
                        .withIpSets(ipSet1),
                Accelerator()
                        .withAcceleratorArn(acceleratorArn2)
                        .withStatus(AcceleratorStatus.IN_PROGRESS.toString())
                        .withEnabled(true)
                        .withIpAddressType(ipFamily)
                        .withName(name2)
                        .withDnsName(dnsName2)
                        .withIpSets(ipSet2),
                Accelerator()
                        .withAcceleratorArn(acceleratorArn3)
                        .withStatus(AcceleratorStatus.IN_PROGRESS.toString())
                        .withEnabled(true)
                        .withIpAddressType(dualStackAddressType)
                        .withName(name3)
                        .withDnsName(dnsName3)
                        .withDualStackDnsName(dualStackDnsName)
                        .withIpSets(arrayListOf(ipSet3, ipSet4))
        )

        val listAcceleratorsResult = ListAcceleratorsResult()
                .withAccelerators(accelerators)
        every { proxy.injectCredentialsAndInvoke(ofType(), ofType<ProxyListAccelerators>()) } returns listAcceleratorsResult

        val request = ResourceHandlerRequest.builder<ResourceModel>().build()
        val response = ListHandler().handleRequest(proxy, request, null, logger)

        assertNotNull(response)
        assertEquals(OperationStatus.SUCCESS, response.status)
        assertNull(response.callbackContext)
        assertNull(response.resourceModel)
        assertNull(response.message)
        assertNotNull(response.resourceModels)
        assertNull(response.nextToken)
        assertEquals(3, response.resourceModels.size)

        assertEquals(acceleratorArn1, response.resourceModels[0].acceleratorArn)
        assertEquals(model1.ipAddressType, response.resourceModels[0].ipAddressType)
        assertEquals(model1.name, response.resourceModels[0].name)
        assertEquals(model1.dnsName, response.resourceModels[0].dnsName)
        assertNull(response.resourceModels[0].dualStackDnsName)
        assertTrue(response.resourceModels[0].enabled)
        assertEquals(model1.ipAddresses, response.resourceModels[0].ipAddresses)
        assertEquals(model1.ipAddresses, response.resourceModels[0].ipv4Addresses)
        assertEquals(arrayListOf<String>(), response.resourceModels[0].ipv6Addresses)

        assertEquals(acceleratorArn2, response.resourceModels[1].acceleratorArn)
        assertEquals(model2.ipAddressType, response.resourceModels[1].ipAddressType)
        assertEquals(model2.name, response.resourceModels[1].name)
        assertEquals(model2.dnsName, response.resourceModels[1].dnsName)
        assertNull(response.resourceModels[1].dualStackDnsName)
        assertTrue(response.resourceModels[1].enabled)
        assertEquals(model2.ipAddresses, response.resourceModels[1].ipAddresses)
        assertEquals(model2.ipAddresses, response.resourceModels[1].ipv4Addresses)
        assertEquals(arrayListOf<String>(), response.resourceModels[1].ipv6Addresses)

        assertEquals(acceleratorArn3, response.resourceModels[2].acceleratorArn)
        assertEquals(model3.ipAddressType, response.resourceModels[2].ipAddressType)
        assertEquals(model3.name, response.resourceModels[2].name)
        assertEquals(model3.dnsName, response.resourceModels[2].dnsName)
        assertEquals(model3.dualStackDnsName, response.resourceModels[2].dualStackDnsName)
        assertTrue(response.resourceModels[2].enabled)
        assertEquals(model3.ipAddresses, response.resourceModels[2].ipAddresses)
        assertEquals(model3.ipAddresses, response.resourceModels[2].ipv4Addresses)
        assertEquals(model3.ipv6Addresses, response.resourceModels[2].ipv6Addresses)
   }

    @Test
    fun handleRequest_noAccelerators() {
        val listAcceleratorsResult = ListAcceleratorsResult().withAccelerators(emptyList())
        every { proxy.injectCredentialsAndInvoke(ofType(), ofType<ProxyListAccelerators>()) } returns listAcceleratorsResult

        val request = ResourceHandlerRequest.builder<ResourceModel>().build()
        val response = ListHandler().handleRequest(proxy, request, null, logger)

        assertNotNull(response)
        assertEquals(OperationStatus.SUCCESS, response.status)
        assertNotNull(response.resourceModels)
        assertEquals(0, response.resourceModels.size)
        assertNull(response.nextToken)
    }

    @Test
    fun handleRequest_returnsMappedAcceleratorsWithToken() {
        val sentNextToken = "next_token"
        val expectedNextToken = "This_token_is_expected"
        val accelerators = mutableListOf(
                Accelerator()
                        .withAcceleratorArn(acceleratorArn1)
                        .withStatus(AcceleratorStatus.IN_PROGRESS.toString())
                        .withEnabled(true)
                        .withIpAddressType(ipFamily)
                        .withName(name1)
                        .withDnsName(dnsName1)
                        .withIpSets(ipSet1)
        )

        val listAcceleratorsRequestSlot = slot<ListAcceleratorsRequest>()
        val listAcceleratorsResult = ListAcceleratorsResult()
                .withAccelerators(accelerators)
                .withNextToken(expectedNextToken)
        every { proxy.injectCredentialsAndInvoke(capture(listAcceleratorsRequestSlot), ofType<ProxyListAccelerators>()) } returns listAcceleratorsResult

        val request = ResourceHandlerRequest.builder<ResourceModel>().nextToken(sentNextToken).build()
        val response = ListHandler().handleRequest(proxy, request, null, logger)

        assertNotNull(response)
        assertEquals(OperationStatus.SUCCESS, response.status)
        assertEquals(sentNextToken, listAcceleratorsRequestSlot.captured.nextToken)
        assertNull(response.callbackContext)
        assertNull(response.resourceModel)
        assertNull(response.message)
        assertNotNull(response.resourceModels)
        assertNotNull(response.nextToken)
        assertEquals(expectedNextToken, response.nextToken)
        assertEquals(1, response.resourceModels.size)
        assertEquals(acceleratorArn1, response.resourceModels[0].acceleratorArn)
    }
}
