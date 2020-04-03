package software.amazon.globalaccelerator.accelerator

import com.amazonaws.services.globalaccelerator.model.CreateAcceleratorRequest
import com.amazonaws.services.globalaccelerator.model.CreateAcceleratorResult
import com.amazonaws.services.globalaccelerator.model.DeleteAcceleratorRequest
import com.amazonaws.services.globalaccelerator.model.DeleteAcceleratorResult
import com.amazonaws.services.globalaccelerator.model.DescribeAcceleratorRequest
import com.amazonaws.services.globalaccelerator.model.DescribeAcceleratorResult
import com.amazonaws.services.globalaccelerator.model.UpdateAcceleratorRequest
import com.amazonaws.services.globalaccelerator.model.UpdateAcceleratorResult
import java.util.function.Function

typealias ProxyDescribeAccelerator = Function<DescribeAcceleratorRequest, DescribeAcceleratorResult>
typealias ProxyUpdateAccelerator = Function<UpdateAcceleratorRequest, UpdateAcceleratorResult>
typealias ProxyCreateAccelerator = Function<CreateAcceleratorRequest, CreateAcceleratorResult>
typealias ProxyDeleteAccelerator = Function<DeleteAcceleratorRequest, DeleteAcceleratorResult>
