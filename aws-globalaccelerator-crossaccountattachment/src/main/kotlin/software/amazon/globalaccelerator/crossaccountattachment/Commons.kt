package software.amazon.globalaccelerator.crossaccountattachment

import com.amazonaws.services.globalaccelerator.model.Resource

fun getCrossAccountAttachmentResources(resources: List<Resource>?): List<software.amazon.globalaccelerator.crossaccountattachment.Resource>? {
    return resources?.map {
        software.amazon.globalaccelerator.crossaccountattachment.Resource.builder()
                .endpointId(it.endpointId)
                .region(it.region)
                .build()
    }
}
