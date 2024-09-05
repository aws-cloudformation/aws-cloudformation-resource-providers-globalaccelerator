package software.amazon.globalaccelerator.crossaccountattachment

import com.amazonaws.services.globalaccelerator.model.Attachment
import com.amazonaws.services.globalaccelerator.model.AttachmentNotFoundException
import com.amazonaws.services.globalaccelerator.model.DeleteCrossAccountAttachmentResult
import com.amazonaws.services.globalaccelerator.model.DescribeCrossAccountAttachmentResult
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy
import software.amazon.cloudformation.proxy.Logger
import software.amazon.cloudformation.proxy.ResourceHandlerRequest
import software.amazon.cloudformation.proxy.OperationStatus
import software.amazon.cloudformation.proxy.HandlerErrorCode

@ExtendWith(MockKExtension::class)
class DeleteHandlerTest {
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

    @Test
    fun handleRequest_DeleteAttachment() {
        val describeAttachmentResult = DescribeCrossAccountAttachmentResult().withCrossAccountAttachment(Attachment().withAttachmentArn(attachmentARN))
        every { proxy.injectCredentialsAndInvoke(ofType(), ofType<ProxyDescribeCrossAccountAttachment>()) } returns describeAttachmentResult
        every { proxy.injectCredentialsAndInvoke(ofType(), ofType<ProxyDeleteCrossAccountAttachment>()) } returns DeleteCrossAccountAttachmentResult()

        val resource2 = Resource()
        resource2.endpointId = endpointArn
        resource2.region = endpointRegion

        val principals: List<String> = listOf(accountPrincipal, acceleratorPrincipal)

        val resources = listOf(resource2)
        val model = ResourceModel.builder()
            .attachmentArn(attachmentARN)
            .principals(principals)
            .resources(resources)
            .name(attachmentName)
            .build()
        val request = ResourceHandlerRequest.builder<ResourceModel>().previousResourceState(model).desiredResourceState(model).build()
        val response = DeleteHandler().handleRequest(proxy, request, null, logger)

        assertNotNull(response)
        assertNull(response.result)
        assertNull(response.message)
        assertNull(response.errorCode)
        assertNotNull(response.resourceModel)
    }

    @Test
    fun handleRequest_DeleteAttachment_Not_Found() {
        val describeAttachmentResult = DescribeCrossAccountAttachmentResult().withCrossAccountAttachment(Attachment().withAttachmentArn(attachmentARN))
        every { proxy.injectCredentialsAndInvoke(ofType(), ofType<ProxyDescribeCrossAccountAttachment>()) } returns describeAttachmentResult
        every { proxy.injectCredentialsAndInvoke(ofType(), ofType<ProxyDeleteCrossAccountAttachment>()) } returns DeleteCrossAccountAttachmentResult()
        every { proxy.injectCredentialsAndInvoke(ofType(), ofType<ProxyDescribeCrossAccountAttachment>()) } throws AttachmentNotFoundException("NOT FOUND")

        val resource2 = Resource()
        resource2.endpointId = endpointArn
        resource2.region = endpointRegion

        val principals: List<String> = listOf(accountPrincipal, acceleratorPrincipal)

        val resources = listOf(resource2)
        val model = ResourceModel.builder()
            .attachmentArn(attachmentARN)
            .principals(principals)
            .resources(resources)
            .name(attachmentName)
            .build()
        val request = ResourceHandlerRequest.builder<ResourceModel>().previousResourceState(model).desiredResourceState(model).build()
        val response = DeleteHandler().handleRequest(proxy, request, null, logger)

        assertNotNull(response)
        assertEquals(OperationStatus.FAILED, response.status)
        assertEquals(HandlerErrorCode.NotFound, response.errorCode)
        assertNotNull(response.resourceModel)
        assertEquals(response.resourceModel, model)
    }
}
