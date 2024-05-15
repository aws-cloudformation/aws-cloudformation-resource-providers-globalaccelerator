package software.amazon.globalaccelerator.crossaccountattachment

import com.amazonaws.services.globalaccelerator.model.Attachment
import com.amazonaws.services.globalaccelerator.model.CreateCrossAccountAttachmentResult
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy
import software.amazon.cloudformation.proxy.Logger
import software.amazon.cloudformation.proxy.ResourceHandlerRequest

@ExtendWith(MockKExtension::class)
class CreateHandlerTest {
    @MockK
    lateinit var proxy: AmazonWebServicesClientProxy

    @MockK(relaxed = true)
    lateinit var logger: Logger

    @BeforeEach
    fun setup() = MockKAnnotations.init(this)

    private val endpointRegion = "us-west-2"
    private val acceleratorPrincipal = "arn:aws:globalaccelerator::474880776455:accelerator/abcd1234"
    private val accountPrincipal = "474880776455"
    private val endpointGroupArn = "arn:aws:globalaccelerator::444607872184:accelerator/88127aa5-01d8-484c-80a0-349daaefce1d/listener/ee7358c2/endpoint-group/de69a4b45005"
    private val endpointArn = "us-east-2b.my-load-balancer-1234567890abcdef.elb.us-east-2.amazonaws.com"
    private val attachmentName = "Test-Attachment-Name"
    private val attachmentARN = "ATTACHMENT_ARN"
    private var principals: List<String> = listOf(accountPrincipal, acceleratorPrincipal)
    private val resource = com.amazonaws.services.globalaccelerator.model.Resource().withEndpointId(endpointArn).withRegion(endpointRegion)
    private var resources = listOf(resource)

    @Test
    fun handleRequest_CreateAttachment() {
        val result = CreateCrossAccountAttachmentResult().withCrossAccountAttachment(Attachment()
                .withAttachmentArn(attachmentARN)
                .withName(attachmentName)
                .withPrincipals(principals)
                .withResources(resources)
        )
        every { proxy.injectCredentialsAndInvoke(ofType(), ofType<ProxyCreateCrossAccountAttachment>()) } returns result

        val handler = CreateHandler()
        val model = ResourceModel.builder().name(attachmentName).build()
        val request = ResourceHandlerRequest.builder<ResourceModel>().desiredResourceState(model).build()
        val response = handler.handleRequest(proxy, request, null, logger)
        assertNotNull(response)
        assertNotNull(response.resourceModel)
        assertEquals(attachmentARN, response.resourceModel.attachmentArn)
        assertEquals(1, response.resourceModel.resources.size)
        assertEquals(2, response.resourceModel.principals.size)
    }

    @Test
    fun handleRequest_CreateAttachment_EndpointBelongsToOwner_Error() {
        val endpointArn = "arn:aws:ec2:us-west-1:224055702492:subnet/subnet-020657852fb4b8d66"
        val resource = Resource()
        resource.endpointId = endpointArn
        val resources = listOf(resource)
        every { proxy.injectCredentialsAndInvoke(ofType(), ofType<ProxyCreateCrossAccountAttachment>()) } throws RuntimeException("All resources must belong to attachment owner account.")

        val handler = CreateHandler()
        val model = ResourceModel.builder().name(attachmentName).resources(resources).build()
        val request = ResourceHandlerRequest.builder<ResourceModel>().desiredResourceState(model).build()

        val exception = Assertions.assertThrows(RuntimeException::class.java) {
            handler.handleRequest(proxy, request, null, logger)
        }

        assertEquals("All resources must belong to attachment owner account.", exception.message)
    }

    @Test
    fun handleRequest_CreateAttachment_MalformedPrincipal_Error() {
        val principal = "arn:aws:ec2:us-west-1:224055702492:subnet/subnet-020657852fb4b8d66"

        val endpointArn = "arn:aws:ec2:us-west-1:224055702492:subnet/subnet-020657852fb4b8d66"
        val resource = Resource()
        resource.endpointId = endpointArn
        val resources = listOf(resource)
        every { proxy.injectCredentialsAndInvoke(ofType(), ofType<ProxyCreateCrossAccountAttachment>()) } throws RuntimeException("Principals must not contain accountId and accelerator arn from the same account.")

        val handler = CreateHandler()
        val model = ResourceModel.builder().name(attachmentName).principals(listOf(principal)).resources(resources).build()
        val request = ResourceHandlerRequest.builder<ResourceModel>().desiredResourceState(model).build()
        val exception = Assertions.assertThrows(RuntimeException::class.java) {
            handler.handleRequest(proxy, request, null, logger)
        }

        assertEquals("Principals must not contain accountId and accelerator arn from the same account.", exception.message)
    }
}
