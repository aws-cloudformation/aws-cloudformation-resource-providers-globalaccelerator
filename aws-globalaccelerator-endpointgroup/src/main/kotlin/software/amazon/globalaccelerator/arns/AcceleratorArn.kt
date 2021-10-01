package software.amazon.globalaccelerator.arns

import com.google.common.base.Strings
import java.util.regex.Pattern

/**
 * Accelerator ARN validation
 */
open class AcceleratorArn(arn: String?) {
    val acceleratorArn: String
    val awsAccountId: String
    val uuid: String

    companion object {
        private const val SERVICE_NAME = "globalaccelerator"
        private const val RESOURCE_NAME = "accelerator"
        private const val EXPECTED_ARN_REGEX = "^arn:aws:(\\w+)::(\\d{12}):(\\w+)/([0-9a-f-]+)$"
    }

    init {
        if (Strings.isNullOrEmpty(arn)) {
            throw RuntimeException("Accelerator ARN cannot be null or empty $arn")
        }
        val pattern = Pattern.compile(EXPECTED_ARN_REGEX)
        val matcher = pattern.matcher(arn!!)
        if (!matcher.find() || matcher.groupCount() != 4) {
            throw RuntimeException(String.format("Invalid Accelerator ARN %s", arn))
        }
        if (verifyNames(matcher.group(1), matcher.group(3))) {
            this.acceleratorArn = arn
            this.awsAccountId = matcher.group(2)
            this.uuid = matcher.group(4)
        } else {
            throw RuntimeException(String.format("Invalid Accelerator ARN %s", arn))
        }
    }

    private fun verifyNames(serviceName: String, resourceName: String): Boolean {
        return SERVICE_NAME == serviceName && RESOURCE_NAME == resourceName
    }

    override fun toString(): String {
        return acceleratorArn
    }
}
