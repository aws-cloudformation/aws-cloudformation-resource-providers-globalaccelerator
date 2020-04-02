package software.amazon.globalaccelerator.arns

import com.google.common.base.Strings
import java.util.regex.Pattern

/**
 * Class for accelerator arn
 */
open class AcceleratorArn

(arn: String) {

    val acceleratorArn: String
    val awsAccountId: String
    val uuid: String

    init {
        if (Strings.isNullOrEmpty(arn)) {
            throw RuntimeException(String.format("Invalid global accelerator arn %s", arn))
        }

        val pattern = Pattern.compile(EXPECTED_ARN_REGEX)
        val matcher = pattern.matcher(arn)
        if (!matcher.find() || matcher.groupCount() !== 4) {
            throw RuntimeException(String.format("Invalid global accelerator arn %s", arn))
        }

        val serviceName = matcher.group(1)
        val resourceName = matcher.group(3)

        if (matchesName(serviceName, resourceName)) {
            this.acceleratorArn = arn
            this.awsAccountId = matcher.group(2)
            this.uuid = matcher.group(4)
        } else {
            throw RuntimeException(String.format("Invalid accelerator arn provided %s", arn))
        }
    }

    @Override
    override fun toString(): String {
        return acceleratorArn
    }

    private fun matchesName(serviceName: String, resourceName: String): Boolean {
        return SERVICE_NAME.equals(serviceName) && RESOURCE_NAME.equals(resourceName)
    }

    companion object {
        private val SERVICE_NAME = "globalaccelerator"
        private val RESOURCE_NAME = "accelerator"
        private val EXPECTED_ARN_REGEX = "^arn:aws:(\\w+)::(\\d{12}):(\\w+)/([0-9a-f-]+)$"
    }
}
