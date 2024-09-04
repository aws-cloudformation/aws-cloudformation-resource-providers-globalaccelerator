package software.amazon.globalaccelerator.crossaccountattachment

import com.amazonaws.services.globalaccelerator.model.Resource

fun getCrossAccountAttachmentResources(resources: List<Resource>?): List<software.amazon.globalaccelerator.crossaccountattachment.Resource>? {
    return resources?.map {
        software.amazon.globalaccelerator.crossaccountattachment.Resource.builder()
                .apply { if(it.region != null) region(it.region) }
                .apply { if(it.endpointId != null) endpointId(it.endpointId) }
                .apply { if(it.cidr != null) cidr(it.cidr) }
                .build()

    }
}
