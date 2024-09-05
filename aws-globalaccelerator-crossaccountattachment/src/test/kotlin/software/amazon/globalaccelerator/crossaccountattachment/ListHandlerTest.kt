package software.amazon.globalaccelerator.crossaccountattachment

import com.amazonaws.services.globalaccelerator.model.Attachment
import com.amazonaws.services.globalaccelerator.model.ListCrossAccountAttachmentsResult
import com.amazonaws.services.globalaccelerator.model.ListCrossAccountAttachmentsRequest
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy
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

    private val endpointRegion = "us-west-2"
    private val acceleratorPrincipal = "arn:aws:globalaccelerator::474880776455:accelerator/abcd1234"
    private val accountPrincipal = "474880776455"
    private val endpointArn = "us-east-2b.my-load-balancer-1234567890abcdef.elb.us-east-2.amazonaws.com"
    private val attachmentName = "Test-Attachment-Name"
    private val attachmentNameTwo = "Test-Attachment-Name"
    private val attachmentNameThree = "Test-Attachment-Name"
    private val attachmentARN = "ATTACHMENT_ARN"
    private val attachmentARNTwo = "ATTACHMENT_ARN_2"
    private val attachmentARNThree = "ATTACHMENT_ARN_3"

    private fun createTestResourceModel(attachmentArn: String, attachmentName: String): ResourceModel? {
        val resource2 = Resource()
        resource2.endpointId = endpointArn
        resource2.region = endpointRegion

        val principals: List<String> = listOf(accountPrincipal, acceleratorPrincipal)
        val resources = listOf(resource2)

        return software.amazon.globalaccelerator.crossaccountattachment.ResourceModel.builder()
            .attachmentArn(attachmentArn)
            .name(attachmentName)
            .principals(principals)
            .resources(resources)
            .build()
    }

    @Test
    fun handleRequest_returnsMappedAttachments() {

        val resource2 = com.amazonaws.services.globalaccelerator.model.Resource().withEndpointId(endpointArn).withRegion(endpointRegion)
        val principals: List<String> = listOf(accountPrincipal, acceleratorPrincipal)
        val resources = listOf(resource2)

        val attachments = mutableListOf(
            Attachment()
                .withAttachmentArn(attachmentARN)
                .withName(attachmentName)
                .withPrincipals(principals)
                .withResources(resources),
            Attachment()
                .withAttachmentArn(attachmentARNTwo)
                .withName(attachmentNameTwo)
                .withPrincipals(principals)
                .withResources(resources),
            Attachment()
                .withAttachmentArn(attachmentARNThree)
                .withName(attachmentNameThree)
                .withPrincipals(principals)
                .withResources(resources)
        )


        val listAttachmentResult = ListCrossAccountAttachmentsResult()
            .withCrossAccountAttachments(attachments)
        every { proxy.injectCredentialsAndInvoke(ofType(), ofType<ProxyListCrossAccountAttachment>()) } returns listAttachmentResult

        val request = ResourceHandlerRequest.builder<software.amazon.globalaccelerator.crossaccountattachment.ResourceModel>().build()
        val response = software.amazon.globalaccelerator.crossaccountattachment.ListHandler().handleRequest(proxy, request, null, logger)

        assertNotNull(response)
        assertEquals(OperationStatus.SUCCESS, response.status)
        assertNull(response.callbackContext)
        assertNull(response.resourceModel)
        assertNull(response.message)
        assertNotNull(response.resourceModels)
        assertNull(response.nextToken)
        assertEquals(3, response.resourceModels.size)

        assertEquals(attachmentARN, response.resourceModels[0].attachmentArn)
        assertEquals(attachmentName, response.resourceModels[0].name)

        assertEquals(attachmentARNTwo, response.resourceModels[1].attachmentArn)
        assertEquals(attachmentNameTwo, response.resourceModels[1].name)

        assertEquals(attachmentARNThree, response.resourceModels[2].attachmentArn)
        assertEquals(attachmentNameThree, response.resourceModels[2].name)
    }

    @Test
    fun handleRequest_noAttachments() {
        val listAttachmentResult = ListCrossAccountAttachmentsResult().withCrossAccountAttachments(emptyList())
        every { proxy.injectCredentialsAndInvoke(ofType(), ofType<ProxyListCrossAccountAttachment>()) } returns listAttachmentResult

        val request = ResourceHandlerRequest.builder<software.amazon.globalaccelerator.crossaccountattachment.ResourceModel>().build()
        val response = ListHandler().handleRequest(proxy, request, null, logger)

        assertNotNull(response)
        assertEquals(OperationStatus.SUCCESS, response.status)
        assertNotNull(response.resourceModels)
        assertEquals(0, response.resourceModels.size)
        assertNull(response.nextToken)
    }

    @Test
    fun handleRequest_returnsAttachmentsWithToken() {
        val sentNextToken = "next_token"
        val expectedNextToken = "This_token_is_expected"
        val resource2 = com.amazonaws.services.globalaccelerator.model.Resource().withEndpointId(endpointArn).withRegion(endpointRegion)
        val principals: List<String> = listOf(accountPrincipal, acceleratorPrincipal)
        val resources = listOf(resource2)
        val attachments = mutableListOf(
            Attachment()
                .withAttachmentArn(attachmentARN)
                .withName(attachmentName)
                .withPrincipals(principals)
                .withResources(resources)
        )

        val listAttachmentsRequestSlot = slot<ListCrossAccountAttachmentsRequest>()
        val listAttachmentResult = ListCrossAccountAttachmentsResult()
            .withCrossAccountAttachments(attachments)
            .withNextToken(expectedNextToken)
        every { proxy.injectCredentialsAndInvoke(capture(listAttachmentsRequestSlot), ofType<ProxyListCrossAccountAttachment>()) } returns listAttachmentResult

        val request = ResourceHandlerRequest.builder<software.amazon.globalaccelerator.crossaccountattachment.ResourceModel>().nextToken(sentNextToken).build()
        val response = ListHandler().handleRequest(proxy, request, null, logger)

        assertNotNull(response)
        assertEquals(OperationStatus.SUCCESS, response.status)
        assertEquals(sentNextToken, listAttachmentsRequestSlot.captured.nextToken)
        assertNull(response.callbackContext)
        assertNull(response.resourceModel)
        assertNull(response.message)
        assertNotNull(response.resourceModels)
        assertNotNull(response.nextToken)
        assertEquals(expectedNextToken, response.nextToken)
        assertEquals(1, response.resourceModels.size)
        assertEquals(attachmentARN, response.resourceModels[0].attachmentArn)
    }
}
