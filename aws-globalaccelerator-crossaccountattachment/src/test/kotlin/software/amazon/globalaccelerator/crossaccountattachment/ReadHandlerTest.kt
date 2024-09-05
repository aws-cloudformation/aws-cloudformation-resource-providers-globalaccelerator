package software.amazon.globalaccelerator.crossaccountattachment

import com.amazonaws.services.globalaccelerator.model.DescribeCrossAccountAttachmentResult
import com.amazonaws.services.globalaccelerator.model.Attachment
import com.amazonaws.services.globalaccelerator.model.AttachmentNotFoundException
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
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
import software.amazon.cloudformation.proxy.HandlerErrorCode


@ExtendWith(MockKExtension::class)
class ReadHandlerTest {
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
    private val attachmentARN = "ATTACHMENT_ARN"

    private fun createTestResourceModel(): ResourceModel? {
        val resource2 = Resource()
        resource2.endpointId = endpointArn
        resource2.region = endpointRegion

        val principals: List<String> = listOf(accountPrincipal, acceleratorPrincipal)
        val resources = listOf(resource2)

        return software.amazon.globalaccelerator.crossaccountattachment.ResourceModel.builder()
            .attachmentArn(attachmentARN)
            .name(attachmentName)
            .principals(principals)
            .resources(resources)
            .build()
    }

    @Test
    fun handleRequest_returnsAttachment() {
        val resource2 = com.amazonaws.services.globalaccelerator.model.Resource().withEndpointId(endpointArn).withRegion(endpointRegion)
        val principals: List<String> = listOf(accountPrincipal, acceleratorPrincipal)
        val resources = listOf(resource2)

        val model = createTestResourceModel()
        val describeAttachmentResult = DescribeCrossAccountAttachmentResult()
            .withCrossAccountAttachment(Attachment()
                .withAttachmentArn(attachmentARN)
                .withName(attachmentName)
                .withPrincipals(principals)
                .withResources(resources)
            )
        every { proxy.injectCredentialsAndInvoke(ofType(), ofType<ProxyDescribeCrossAccountAttachment>()) } returns describeAttachmentResult

        val request = ResourceHandlerRequest.builder<software.amazon.globalaccelerator.crossaccountattachment.ResourceModel>().desiredResourceState(model).build()
        val response = ReadHandler().handleRequest(proxy, request, null, logger)
        assertNotNull(response)
        assertEquals(OperationStatus.SUCCESS, response.status)
        assertNull(response.callbackContext)
        assertNull(response.resourceModels)
        assertNull(response.message)
        assertNotNull(response.resourceModel)
        assertEquals(attachmentARN, response.resourceModel.attachmentArn)
        assertEquals(endpointArn, response.resourceModel.resources[0].endpointId)
        assertEquals(endpointRegion, response.resourceModel.resources[0].region)
        assertEquals(attachmentName, response.resourceModel.name)
        assertEquals(principals, response.resourceModel.principals)
    }

    @Test
    fun handleRequest_nonExistingAttachment() {
        val model = createTestResourceModel()
        every { proxy.injectCredentialsAndInvoke(ofType(), ofType<ProxyDescribeCrossAccountAttachment>()) } throws AttachmentNotFoundException("NOT FOUND")

        val request = ResourceHandlerRequest.builder<software.amazon.globalaccelerator.crossaccountattachment.ResourceModel>().desiredResourceState(model).build()
        val response = ReadHandler().handleRequest(proxy, request, null, logger)
        assertNotNull(response)
        assertEquals(OperationStatus.FAILED, response.status)
        assertEquals(HandlerErrorCode.NotFound, response.errorCode)
        assertNotNull(response.resourceModel)
        assertEquals(response.resourceModel, model)
    }
}
