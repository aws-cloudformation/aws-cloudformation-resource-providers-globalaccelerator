package software.amazon.globalaccelerator.crossaccountattachment

import com.amazonaws.services.globalaccelerator.model.Attachment
import com.amazonaws.services.globalaccelerator.model.AttachmentNotFoundException
import com.amazonaws.services.globalaccelerator.model.DescribeCrossAccountAttachmentResult
import com.amazonaws.services.globalaccelerator.model.UpdateCrossAccountAttachmentResult
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
class UpdateHandlerTest {
    @MockK
    lateinit var proxy: AmazonWebServicesClientProxy

    @MockK(relaxed = true)
    lateinit var logger: Logger

    @BeforeEach
    fun setup() = MockKAnnotations.init(this)

    private val endpointRegion = "us-west-2"
    private val acceleratorPrincipal = "arn:aws:globalaccelerator::474880776455:accelerator/abcd1234"
    private val acceleratorPrincipalTwo = "arn:aws:globalaccelerator::474880776455:accelerator/ddddddddd"
    private val accountPrincipal = "474880776455"
    private val endpointArn = "us-east-2b.my-load-balancer-1234567890abcdef.elb.us-east-2.amazonaws.com"
    private val endpointArnTwo = "arn:aws:elasticloadbalancing:us-east-2:123456789012:loadbalancer/net/my-load-balancer/1234567890123456"
    private val endpointArnThree = "arn:aws:elasticloadbalancing:us-east-2:123456789012:loadbalancer/net/my-load-balancer-Two/1234567890"
    private val cidrOne = "1.1.1.0/24"
    private val attachmentName = "Test-Attachment-Name"
    private val attachmentNameTwo = "Test-Attachment-Name"
    private val attachmentNameThree = "Test-Attachment-Name"
    private val attachmentARN = "ATTACHMENT_ARN"
    private val attachmentARNTwo = "ATTACHMENT_ARN_2"
    private val attachmentARNThree = "ATTACHMENT_ARN_3"

    private fun createTestResourceModel(attachmentArn: String, attachmentName: String): ResourceModel {
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

    private fun createTestCidrResourceModel(attachmentArn: String, attachmentName: String): ResourceModel {
        val resource= Resource()
        resource.cidr = cidrOne

        val principals: List<String> = listOf(accountPrincipal)
        val resources = mutableListOf(resource)

        return software.amazon.globalaccelerator.crossaccountattachment.ResourceModel.builder()
            .attachmentArn(attachmentArn)
            .name(attachmentName)
            .principals(principals)
            .resources(resources)
            .build()
    }

    @Test
    fun handleRequest_returnsSuccess_UpdatedResourcesAndPrincipals() {
        var initialAttachmentModel = createTestResourceModel(attachmentARN, attachmentName)
        var initialPrincipals = listOf(accountPrincipal, acceleratorPrincipal)
        var initialResource = listOf(com.amazonaws.services.globalaccelerator.model.Resource().withEndpointId(endpointArn).withRegion(endpointRegion))
        var updatedAttachmentModel = createTestResourceModel(attachmentARN, attachmentNameTwo)

        var updatedPrincipals = listOf(accountPrincipal, acceleratorPrincipalTwo)

        val resource3 = Resource()
        resource3.endpointId = endpointArnTwo
        resource3.region = endpointRegion
        var updatedResources = listOf(resource3)

        val convertedUpdatedResources = listOf(com.amazonaws.services.globalaccelerator.model.Resource().withEndpointId(endpointArnTwo).withRegion(endpointRegion))

        updatedAttachmentModel.principals = updatedPrincipals
        updatedAttachmentModel.resources = updatedResources

        val describeAttachmentResult = DescribeCrossAccountAttachmentResult().withCrossAccountAttachment(Attachment().withAttachmentArn(attachmentARN).withName(attachmentName).withPrincipals(initialPrincipals).withResources(initialResource))
        every { proxy.injectCredentialsAndInvoke(ofType(), ofType<ProxyDescribeCrossAccountAttachment>()) } returns describeAttachmentResult

        val updateAttachmentResult = UpdateCrossAccountAttachmentResult().withCrossAccountAttachment(Attachment().withAttachmentArn(attachmentARN).withName(attachmentNameTwo).withPrincipals(updatedPrincipals).withResources(convertedUpdatedResources))
        every { proxy.injectCredentialsAndInvoke(ofType(), ofType<ProxyUpdateCrossAccountAttachment>()) } returns updateAttachmentResult

        val request = ResourceHandlerRequest.builder<ResourceModel>()
            .desiredResourceState(updatedAttachmentModel)
            .previousResourceState(initialAttachmentModel)
            .build()

        val response = UpdateHandler().handleRequest(proxy, request, null, logger)

        assertNotNull(response)
        assertEquals(OperationStatus.SUCCESS, response.status)
        assertNotNull(response.resourceModel)
        assertNull(response.callbackContext)
        assertEquals(response.resourceModel.name, attachmentNameTwo)
        assertEquals(response.resourceModel.attachmentArn, attachmentARN)
        assertEquals(response.resourceModel.principals, updatedPrincipals)
        assertEquals(response.resourceModel.resources, updatedResources)
    }

    @Test
    fun handleRequest_resend_same_resources() {
        var initialAttachmentModel = createTestCidrResourceModel(attachmentARN, attachmentName)
        var updatedAttachmentModel = createTestResourceModel(attachmentARN, attachmentName)

        val convertedInitalResources = listOf(com.amazonaws.services.globalaccelerator.model.Resource().withCidr(cidrOne))
        val convertedUpdatedResources = listOf(com.amazonaws.services.globalaccelerator.model.Resource().withCidr(cidrOne))

        val describeAttachmentResult = DescribeCrossAccountAttachmentResult().withCrossAccountAttachment(Attachment().withAttachmentArn(attachmentARN).withName(attachmentName).withPrincipals(initialAttachmentModel.principals).withResources(convertedInitalResources))
        every { proxy.injectCredentialsAndInvoke(ofType(), ofType<ProxyDescribeCrossAccountAttachment>()) } returns describeAttachmentResult

        val updateAttachmentResult = UpdateCrossAccountAttachmentResult().withCrossAccountAttachment(Attachment().withAttachmentArn(attachmentARN).withName(attachmentName).withPrincipals(updatedAttachmentModel.principals).withResources(convertedUpdatedResources))
        every { proxy.injectCredentialsAndInvoke(ofType(), ofType<ProxyUpdateCrossAccountAttachment>()) } returns updateAttachmentResult

        val request = ResourceHandlerRequest.builder<ResourceModel>()
            .desiredResourceState(updatedAttachmentModel)
            .previousResourceState(initialAttachmentModel)
            .build()

        val response = UpdateHandler().handleRequest(proxy, request, null, logger)

        assertNotNull(response)
        assertEquals(OperationStatus.SUCCESS, response.status)
        assertNotNull(response.resourceModel)
        assertNull(response.callbackContext)
        assertEquals(response.resourceModel.name, attachmentName)
        assertEquals(response.resourceModel.attachmentArn, attachmentARN)
    }

    @Test
    fun handleRequest_returnsSuccess_NoResourcesToResources() {
        var initialAttachmentModel = createTestResourceModel(attachmentARN, attachmentName)
        initialAttachmentModel.resources = emptyList()
        var initialPrincipals = listOf(accountPrincipal, acceleratorPrincipal)
        var updatedAttachmentModel = createTestResourceModel(attachmentARN, attachmentNameTwo)

        var updatedPrincipals = listOf(accountPrincipal, acceleratorPrincipalTwo)

        val resource3 = Resource()
        resource3.endpointId = endpointArnTwo
        resource3.region = endpointRegion
        var updatedResources = listOf(resource3)

        val convertedUpdatedResources = listOf(com.amazonaws.services.globalaccelerator.model.Resource().withEndpointId(endpointArnTwo).withRegion(endpointRegion))

        updatedAttachmentModel.principals = updatedPrincipals
        updatedAttachmentModel.resources = updatedResources

        val describeAttachmentResult = DescribeCrossAccountAttachmentResult().withCrossAccountAttachment(Attachment().withAttachmentArn(attachmentARN).withName(attachmentName).withPrincipals(initialPrincipals).withResources(emptyList()))
        every { proxy.injectCredentialsAndInvoke(ofType(), ofType<ProxyDescribeCrossAccountAttachment>()) } returns describeAttachmentResult

        val updateAttachmentResult = UpdateCrossAccountAttachmentResult().withCrossAccountAttachment(Attachment().withAttachmentArn(attachmentARN).withName(attachmentNameTwo).withPrincipals(updatedPrincipals).withResources(convertedUpdatedResources))
        every { proxy.injectCredentialsAndInvoke(ofType(), ofType<ProxyUpdateCrossAccountAttachment>()) } returns updateAttachmentResult

        val request = ResourceHandlerRequest.builder<ResourceModel>()
            .desiredResourceState(updatedAttachmentModel)
            .previousResourceState(initialAttachmentModel)
            .build()

        val response = UpdateHandler().handleRequest(proxy, request, null, logger)

        assertNotNull(response)
        assertEquals(OperationStatus.SUCCESS, response.status)
        assertNotNull(response.resourceModel)
        assertNull(response.callbackContext)
        assertEquals(response.resourceModel.name, attachmentNameTwo)
        assertEquals(response.resourceModel.attachmentArn, attachmentARN)
        assertEquals(response.resourceModel.principals, updatedPrincipals)
        assertEquals(response.resourceModel.resources, updatedResources)
    }

    @Test
    fun handleRequest_returnsSuccess_NoPrincipalsUpdatedPrincipals() {
        var initialAttachmentModel = createTestResourceModel(attachmentARN, attachmentName)
        //var initialPrincipals = listOf(accountPrincipal, acceleratorPrincipal)
        var initialResource = listOf(com.amazonaws.services.globalaccelerator.model.Resource().withEndpointId(endpointArn).withRegion(endpointRegion))
        var updatedAttachmentModel = createTestResourceModel(attachmentARN, attachmentNameTwo)
        var updatedPrincipals = listOf(accountPrincipal, acceleratorPrincipalTwo)

        initialAttachmentModel.principals = null

        val resource3 = Resource()
        resource3.endpointId = endpointArnTwo
        resource3.region = endpointRegion
        var updatedResources = listOf(resource3)

        val convertedUpdatedResources = listOf(com.amazonaws.services.globalaccelerator.model.Resource().withEndpointId(endpointArnTwo).withRegion(endpointRegion))

        updatedAttachmentModel.principals = updatedPrincipals
        updatedAttachmentModel.resources = updatedResources

        val describeAttachmentResult = DescribeCrossAccountAttachmentResult().withCrossAccountAttachment(Attachment().withAttachmentArn(attachmentARN).withName(attachmentName).withResources(initialResource))
        every { proxy.injectCredentialsAndInvoke(ofType(), ofType<ProxyDescribeCrossAccountAttachment>()) } returns describeAttachmentResult

        val updateAttachmentResult = UpdateCrossAccountAttachmentResult().withCrossAccountAttachment(Attachment().withAttachmentArn(attachmentARN).withName(attachmentNameTwo).withPrincipals(updatedPrincipals).withResources(convertedUpdatedResources))
        every { proxy.injectCredentialsAndInvoke(ofType(), ofType<ProxyUpdateCrossAccountAttachment>()) } returns updateAttachmentResult

        val request = ResourceHandlerRequest.builder<ResourceModel>()
            .desiredResourceState(updatedAttachmentModel)
            .previousResourceState(initialAttachmentModel)
            .build()

        val response = UpdateHandler().handleRequest(proxy, request, null, logger)

        assertNotNull(response)
        assertEquals(OperationStatus.SUCCESS, response.status)
        assertNotNull(response.resourceModel)
        assertNull(response.callbackContext)
        assertEquals(response.resourceModel.name, attachmentNameTwo)
        assertEquals(response.resourceModel.attachmentArn, attachmentARN)
        assertEquals(response.resourceModel.principals, updatedPrincipals)
        assertEquals(response.resourceModel.resources, updatedResources)
    }

    @Test
    fun handleRequest_returnsSuccess_ResourcesToNoResources() {
        val initialAttachmentModel = createTestResourceModel(attachmentARN, attachmentName)
        val initialPrincipals = listOf(accountPrincipal, acceleratorPrincipal)
        val initialResource = listOf(com.amazonaws.services.globalaccelerator.model.Resource().withEndpointId(endpointArn).withRegion(endpointRegion))
        val updatedAttachmentModel = createTestResourceModel(attachmentARN, attachmentNameTwo)

        val updatedPrincipals = listOf(accountPrincipal, acceleratorPrincipalTwo)

        val resource3 = Resource()
        resource3.endpointId = endpointArnTwo
        resource3.region = endpointRegion

        updatedAttachmentModel.principals = updatedPrincipals
        updatedAttachmentModel.resources = null

        val describeAttachmentResult = DescribeCrossAccountAttachmentResult().withCrossAccountAttachment(Attachment().withAttachmentArn(attachmentARN).withName(attachmentName).withPrincipals(initialPrincipals).withResources(initialResource))
        every { proxy.injectCredentialsAndInvoke(ofType(), ofType<ProxyDescribeCrossAccountAttachment>()) } returns describeAttachmentResult

        val updateAttachmentResult = UpdateCrossAccountAttachmentResult().withCrossAccountAttachment(Attachment().withAttachmentArn(attachmentARN).withName(attachmentNameTwo).withPrincipals(updatedPrincipals))
        every { proxy.injectCredentialsAndInvoke(ofType(), ofType<ProxyUpdateCrossAccountAttachment>()) } returns updateAttachmentResult

        val request = ResourceHandlerRequest.builder<ResourceModel>()
            .desiredResourceState(updatedAttachmentModel)
            .previousResourceState(initialAttachmentModel)
            .build()

        val response = UpdateHandler().handleRequest(proxy, request, null, logger)

        assertNotNull(response)
        assertEquals(OperationStatus.SUCCESS, response.status)
        assertNotNull(response.resourceModel)
        assertNull(response.callbackContext)
        assertEquals(response.resourceModel.name, attachmentNameTwo)
        assertEquals(response.resourceModel.attachmentArn, attachmentARN)
        assertEquals(response.resourceModel.principals, updatedPrincipals)
        assertEquals(response.resourceModel.resources.size, 0)
    }

    @Test
    fun handleRequest_returnsSuccess_PrincipalsToNoPrincipals() {
        var initialAttachmentModel = createTestResourceModel(attachmentARN, attachmentName)
        var initialPrincipals = listOf(accountPrincipal, acceleratorPrincipal)
        var initialResource = listOf(com.amazonaws.services.globalaccelerator.model.Resource().withEndpointId(endpointArn).withRegion(endpointRegion))
        var updatedAttachmentModel = createTestResourceModel(attachmentARN, attachmentNameTwo)

        val resource3 = Resource()
        resource3.endpointId = endpointArnTwo
        resource3.region = endpointRegion
        var updatedResources = listOf(resource3)

        val convertedUpdatedResources = listOf(com.amazonaws.services.globalaccelerator.model.Resource().withEndpointId(endpointArnTwo).withRegion(endpointRegion))

        updatedAttachmentModel.principals = null
        updatedAttachmentModel.resources = updatedResources

        val describeAttachmentResult = DescribeCrossAccountAttachmentResult().withCrossAccountAttachment(Attachment().withAttachmentArn(attachmentARN).withName(attachmentName).withPrincipals(initialPrincipals).withResources(initialResource))
        every { proxy.injectCredentialsAndInvoke(ofType(), ofType<ProxyDescribeCrossAccountAttachment>()) } returns describeAttachmentResult

        val updateAttachmentResult = UpdateCrossAccountAttachmentResult().withCrossAccountAttachment(Attachment().withAttachmentArn(attachmentARN).withName(attachmentNameTwo).withResources(convertedUpdatedResources))
        every { proxy.injectCredentialsAndInvoke(ofType(), ofType<ProxyUpdateCrossAccountAttachment>()) } returns updateAttachmentResult

        val request = ResourceHandlerRequest.builder<ResourceModel>()
            .desiredResourceState(updatedAttachmentModel)
            .previousResourceState(initialAttachmentModel)
            .build()

        val response = UpdateHandler().handleRequest(proxy, request, null, logger)

        assertNotNull(response)
        assertEquals(OperationStatus.SUCCESS, response.status)
        assertNotNull(response.resourceModel)
        assertNull(response.callbackContext)
        assertEquals(response.resourceModel.name, attachmentNameTwo)
        assertEquals(response.resourceModel.attachmentArn, attachmentARN)
        assertEquals(response.resourceModel.principals, null)
        assertEquals(response.resourceModel.resources, updatedResources)
    }

    @Test
    fun handleRequest_UpdateAttachment_NoAttachment() {
        val desiredModel = createTestResourceModel(attachmentARN, attachmentName)
        every { proxy.injectCredentialsAndInvoke(ofType(), ofType<ProxyDescribeCrossAccountAttachment>()) } throws AttachmentNotFoundException("NOT FOUND")

        val request = ResourceHandlerRequest.builder<software.amazon.globalaccelerator.crossaccountattachment.ResourceModel>()
            .desiredResourceState(desiredModel)
            .build()

        val response = UpdateHandler().handleRequest(proxy, request, null, logger)
        assertNotNull(response)
        assertEquals(OperationStatus.FAILED, response.status)
        assertEquals(HandlerErrorCode.NotFound, response.errorCode)
        assertNotNull(response.resourceModel)
        assertEquals(response.resourceModel, desiredModel)
    }

    @Test
    fun handleRequest_UpdateAttachment_ResourceDelta_ResourceToResource_completeSwap() {
        val resourceOne = com.amazonaws.services.globalaccelerator.model.Resource()
        resourceOne.endpointId = endpointArn
        resourceOne.region = endpointRegion

        val resourceTwo = com.amazonaws.services.globalaccelerator.model.Resource()
        resourceTwo.endpointId = endpointArnTwo
        resourceTwo.region = endpointRegion

        val principals = listOf(accountPrincipal, acceleratorPrincipal)
        val existingResources = listOf(resourceOne, resourceTwo)
        val existingAttachment = Attachment().withAttachmentArn(attachmentARN).withName(attachmentName). withPrincipals(principals).withResources(existingResources)

        val resourceThree = Resource()
        resourceThree.endpointId = endpointArnThree
        resourceThree.region = endpointRegion

        var updatedModel = createTestResourceModel(attachmentARN, attachmentName)
        updatedModel.principals = principals
        updatedModel.resources = listOf(resourceThree)

        val resourceRequest = UpdateHandler().buildResourceRequest(updatedModel, existingAttachment, logger)

        assertNotNull(resourceRequest)
        assertEquals(resourceRequest.addResources.size, 1)
        assertEquals(resourceRequest.removeResources.size, 2)
        assertEquals(resourceRequest.addResources[0].endpointId, resourceThree.endpointId)
        assertEquals(resourceRequest.removeResources, listOf(resourceOne, resourceTwo))
    }

    fun handleRequest_UpdateAttachment_ResourceDelta_ResourceToResource_cidr_completeSwap() {
        val resourceOne = com.amazonaws.services.globalaccelerator.model.Resource()
        resourceOne.cidr = "1.2.3.4"

        val resourceTwo = com.amazonaws.services.globalaccelerator.model.Resource()
        resourceTwo.cidr = "1.2.3.5"

        val principals = listOf(accountPrincipal, acceleratorPrincipal)
        val existingResources = listOf(resourceOne, resourceTwo)
        val existingAttachment = Attachment().withAttachmentArn(attachmentARN).withName(attachmentName). withPrincipals(principals).withResources(existingResources)

        val resourceThree = Resource()
        resourceThree.cidr = "1.2.3.6"

        var updatedModel = createTestResourceModel(attachmentARN, attachmentName)
        updatedModel.principals = principals
        updatedModel.resources = listOf(resourceThree)

        val resourceRequest = UpdateHandler().buildResourceRequest(updatedModel, existingAttachment, logger)

        assertNotNull(resourceRequest)
        assertEquals(resourceRequest.addResources.size, 1)
        assertEquals(resourceRequest.removeResources.size, 2)
        assertEquals(resourceRequest.addResources[0].endpointId, resourceThree.endpointId)
        assertEquals(resourceRequest.removeResources, listOf(resourceOne, resourceTwo))
    }


    fun handleRequest_UpdateAttachment_ResourceDelta_ResourceToResource_mixed_completeSwap() {
        val resourceOne = com.amazonaws.services.globalaccelerator.model.Resource()
        resourceOne.endpointId = endpointArn
        resourceOne.region = endpointRegion

        val resourceTwo = com.amazonaws.services.globalaccelerator.model.Resource()
        resourceTwo.endpointId = endpointArnTwo
        resourceTwo.region = endpointRegion

        val resourceFour = com.amazonaws.services.globalaccelerator.model.Resource()
        resourceFour.cidr = "1.2.3.4"

        val principals = listOf(accountPrincipal, acceleratorPrincipal)
        val existingResources = listOf(resourceOne, resourceTwo, resourceFour)
        val existingAttachment = Attachment().withAttachmentArn(attachmentARN).withName(attachmentName). withPrincipals(principals).withResources(existingResources)

        val resourceThree = Resource()
        resourceThree.endpointId = endpointArnThree
        resourceThree.region = endpointRegion

        val resourceFive = Resource()
        resourceFive.cidr = "1.3.4.5"

        var updatedModel = createTestResourceModel(attachmentARN, attachmentName)
        updatedModel.principals = principals
        updatedModel.resources = listOf(resourceThree, resourceFive)

        val resourceRequest = UpdateHandler().buildResourceRequest(updatedModel, existingAttachment, logger)

        assertNotNull(resourceRequest)
        assertEquals(resourceRequest.addResources.size, 2)
        assertEquals(resourceRequest.removeResources.size, 3)
        assertEquals(resourceRequest.addResources[0].endpointId, resourceThree.endpointId)
        assertEquals(resourceRequest.addResources[1].cidr, resourceFive.cidr)
        assertEquals(resourceRequest.removeResources, listOf(resourceOne, resourceTwo, resourceFour))
    }

    @Test
    fun handleRequest_UpdateAttachment_ResourceDelta_ResourceToResource_PartialSwap() {
        val resourceOne = com.amazonaws.services.globalaccelerator.model.Resource()
        resourceOne.endpointId = endpointArn
        resourceOne.region = endpointRegion

        val resourceTwo = com.amazonaws.services.globalaccelerator.model.Resource()
        resourceTwo.endpointId = endpointArnTwo
        resourceTwo.region = endpointRegion

        val principals = listOf(accountPrincipal, acceleratorPrincipal)
        val existingResources = listOf(resourceOne, resourceTwo)
        val existingAttachment = Attachment().withAttachmentArn(attachmentARN).withName(attachmentName). withPrincipals(principals).withResources(existingResources)

        val resourceTwoConverted = Resource()
        resourceTwoConverted.endpointId = endpointArnTwo
        resourceTwoConverted.region = endpointRegion

        val resourceThree = Resource()
        resourceThree.endpointId = endpointArnThree
        resourceThree.region = endpointRegion

        var updatedModel = createTestResourceModel(attachmentARN, attachmentName)
        updatedModel.principals = principals
        updatedModel.resources = listOf(resourceTwoConverted, resourceThree)

        val resourceRequest = UpdateHandler().buildResourceRequest(updatedModel, existingAttachment, logger)

        assertNotNull(resourceRequest)
        assertEquals(resourceRequest.addResources.size, 1)
        assertEquals(resourceRequest.removeResources.size, 1)
        assertEquals(resourceRequest.addResources[0].endpointId, resourceThree.endpointId)
        assertEquals(resourceRequest.removeResources, listOf(resourceOne))
    }

    @Test
    fun handleRequest_UpdateAttachment_ResourceDelta_ResourceToResource_ToEmpty() {
        val resourceOne = com.amazonaws.services.globalaccelerator.model.Resource()
        resourceOne.endpointId = endpointArn
        resourceOne.region = endpointRegion

        val resourceTwo = com.amazonaws.services.globalaccelerator.model.Resource()
        resourceTwo.endpointId = endpointArnTwo
        resourceTwo.region = endpointRegion

        val principals = listOf(accountPrincipal, acceleratorPrincipal)
        val existingResources = listOf(resourceOne, resourceTwo)
        val existingAttachment = Attachment().withAttachmentArn(attachmentARN).withName(attachmentName). withPrincipals(principals).withResources(existingResources)


        var updatedModel = createTestResourceModel(attachmentARN, attachmentName)
        updatedModel.principals = principals
        updatedModel.resources = null

        val resourceRequest = UpdateHandler().buildResourceRequest(updatedModel, existingAttachment, logger)

        assertNotNull(resourceRequest)
        assertEquals(resourceRequest.addResources.size, 0)
        assertEquals(resourceRequest.removeResources.size, 2)
        assertEquals(resourceRequest.removeResources, listOf(resourceOne, resourceTwo))
    }

    @Test
    fun handleRequest_UpdateAttachment_PrincipalDelta_PrincipalToPrincipal_CompleteSwap() {
        val resourceOne = com.amazonaws.services.globalaccelerator.model.Resource()
        resourceOne.endpointId = endpointArn
        resourceOne.region = endpointRegion

        val resourceOneConverted = Resource()
        resourceOneConverted.endpointId = endpointArn
        resourceOneConverted.region = endpointRegion

        val principals = listOf(accountPrincipal, acceleratorPrincipal)
        val existingResources = listOf(resourceOne)
        val existingAttachment = Attachment().withAttachmentArn(attachmentARN).withName(attachmentName). withPrincipals(principals).withResources(existingResources)

        val updatedPrincipals = listOf(acceleratorPrincipalTwo)

        var updatedModel = createTestResourceModel(attachmentARN, attachmentName)
        updatedModel.principals = updatedPrincipals
        updatedModel.resources = listOf(resourceOneConverted)

        val principalRequest = UpdateHandler().buildPrincipalRequest(updatedModel, existingAttachment, logger)

        assertNotNull(principalRequest)
        assertEquals(principalRequest.addPrincipals.size, 1)
        assertEquals(principalRequest.removePrincipals.size, 2)
        assertEquals(principalRequest.addPrincipals[0], acceleratorPrincipalTwo)
        assertEquals(principalRequest.removePrincipals, principals)
    }

    @Test
    fun handleRequest_UpdateAttachment_PrincipalDelta_PrincipalToPrincipal_PartialSwap() {
        val resourceOne = com.amazonaws.services.globalaccelerator.model.Resource()
        resourceOne.endpointId = endpointArn
        resourceOne.region = endpointRegion

        val resourceOneConverted = Resource()
        resourceOneConverted.endpointId = endpointArn
        resourceOneConverted.region = endpointRegion

        val principals = listOf(accountPrincipal, acceleratorPrincipal)
        val existingResources = listOf(resourceOne)
        val existingAttachment = Attachment().withAttachmentArn(attachmentARN).withName(attachmentName). withPrincipals(principals).withResources(existingResources)

        val updatedPrincipals = listOf(accountPrincipal, acceleratorPrincipalTwo)

        var updatedModel = createTestResourceModel(attachmentARN, attachmentName)
        updatedModel.principals = updatedPrincipals
        updatedModel.resources = listOf(resourceOneConverted)

        val principalRequest = UpdateHandler().buildPrincipalRequest(updatedModel, existingAttachment, logger)

        assertNotNull(principalRequest)
        assertEquals(principalRequest.addPrincipals.size, 1)
        assertEquals(principalRequest.removePrincipals.size, 1)
        assertEquals(principalRequest.addPrincipals[0], acceleratorPrincipalTwo)
        assertEquals(principalRequest.removePrincipals[0], acceleratorPrincipal)
    }

    @Test
    fun handleRequest_UpdateAttachment_PrincipalDelta_PrincipalToPrincipal_ToEmpty() {
        val resourceOne = com.amazonaws.services.globalaccelerator.model.Resource()
        resourceOne.endpointId = endpointArn
        resourceOne.region = endpointRegion

        val resourceOneConverted = Resource()
        resourceOneConverted.endpointId = endpointArn
        resourceOneConverted.region = endpointRegion

        val principals = listOf(accountPrincipal, acceleratorPrincipal)
        val existingResources = listOf(resourceOne)
        val existingAttachment = Attachment().withAttachmentArn(attachmentARN).withName(attachmentName). withPrincipals(principals).withResources(existingResources)

        val updatedPrincipals = null

        var updatedModel = createTestResourceModel(attachmentARN, attachmentName)
        updatedModel.principals = updatedPrincipals
        updatedModel.resources = listOf(resourceOneConverted)

        val principalRequest = UpdateHandler().buildPrincipalRequest(updatedModel, existingAttachment, logger)

        assertNotNull(principalRequest)
        assertEquals(principalRequest.addPrincipals.size, 0)
        assertEquals(principalRequest.removePrincipals.size, 2)
        assertEquals(principalRequest.removePrincipals, listOf(accountPrincipal, acceleratorPrincipal))
    }

    @Test
    fun handleRequest_UpdateAttachment_InvalidTag() {
        val desiredModel = createTestResourceModel(attachmentARN, attachmentName)
        desiredModel.tags = listOf(Tag.builder().key("Key1").value("Value1?2").build())
        var describeAttachmentResult = DescribeCrossAccountAttachmentResult().withCrossAccountAttachment(Attachment().withAttachmentArn(attachmentARN).withName(attachmentName))
        var updateAttachmentResult = UpdateCrossAccountAttachmentResult().withCrossAccountAttachment(Attachment().withAttachmentArn(attachmentARN).withName(attachmentName))

        every { proxy.injectCredentialsAndInvoke(ofType(), ofType<ProxyDescribeCrossAccountAttachment>()) } returns describeAttachmentResult
        every { proxy.injectCredentialsAndInvoke(ofType(), ofType<ProxyUpdateCrossAccountAttachment>()) } returns updateAttachmentResult
        every { proxy.injectCredentialsAndInvoke(ofType(), ofType<ProxyTagCrossAccountAttachment>()) } returns null

        val request = ResourceHandlerRequest.builder<ResourceModel>()
            .desiredResourceState(desiredModel)
            .previousResourceState(desiredModel)
            .build()

        val response = UpdateHandler().handleRequest(proxy, request, null, logger)
        assertNotNull(response)
        assertEquals(OperationStatus.FAILED, response.status)
        assertEquals(HandlerErrorCode.InvalidRequest, response.errorCode)
    }

    @Test
    fun handleRequest_UpdateAttachment_TagsDrift() {
        val desiredModel = createTestResourceModel(attachmentARN, attachmentName)
        val previousModel = createTestResourceModel(attachmentARN, attachmentName)
        previousModel.tags = listOf(Tag.builder().key("Key1").value("Value1").build())

        var describeAttachmentResult = DescribeCrossAccountAttachmentResult().withCrossAccountAttachment(Attachment().withAttachmentArn(attachmentARN).withName(attachmentName).withPrincipals(emptyList()).withResources(emptyList()))
        var updateAttachmentResult = UpdateCrossAccountAttachmentResult().withCrossAccountAttachment(Attachment().withAttachmentArn(attachmentARN).withName(attachmentName))

        every { proxy.injectCredentialsAndInvoke(ofType(), ofType<ProxyDescribeCrossAccountAttachment>()) } returns describeAttachmentResult
        every { proxy.injectCredentialsAndInvoke(ofType(), ofType<ProxyUpdateCrossAccountAttachment>()) } returns updateAttachmentResult
        every { proxy.injectCredentialsAndInvoke(ofType(), ofType<ProxyTagCrossAccountAttachment>()) } returns null
        every { proxy.injectCredentialsAndInvoke(ofType(), ofType<ProxyUntagCrossAccountAttachment>()) } returns null

        val request = ResourceHandlerRequest.builder<ResourceModel>()
            .desiredResourceState(desiredModel)
            .previousResourceState(previousModel)
            .build()
        val response = UpdateHandler().handleRequest(proxy, request, null, logger)

        assertNotNull(response)
        assertEquals(OperationStatus.SUCCESS, response.status)
        assertNotNull(response.resourceModel)
        assertEquals(desiredModel.name, response.resourceModel.name)
    }
}
