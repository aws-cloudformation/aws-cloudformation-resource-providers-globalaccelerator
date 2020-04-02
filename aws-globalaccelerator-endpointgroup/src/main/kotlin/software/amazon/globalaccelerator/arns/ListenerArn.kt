package software.amazon.globalaccelerator.arns

import com.google.common.annotations.VisibleForTesting
import com.google.common.base.Strings
import lombok.EqualsAndHashCode

/**
 * Class for ListenerArn
 */
@EqualsAndHashCode(callSuper = true)
class ListenerArn
/**
 * Constructs an instance of ListenerArn from a string.
 * @param listenerArn
 * @return
 */
(listenerArn: String) : AcceleratorArn(listenerArn.split(LISTENER_SEPARATOR)[0]) {

    /**
     * Gets listener id.
     * @return
     */
    val listenerId: String

    /**
     * Gets listener arn.
     * @return
     */
    val listenerArn: String
        get() = acceleratorArn + LISTENER_SEPARATOR + listenerId

    init {
        validateListenerArn(listenerArn)
        this.listenerId = listenerArn.split(LISTENER_SEPARATOR)[1]
    }

    /**
     * Converts to String
     * @return
     */
    @Override
    override fun toString(): String {
        return listenerArn
    }

    companion object {

        private val LISTENER_SEPARATOR = "/listener/"

        /**
         * Validates a listener arn if it matches the format of <GLB_ARN>/listener/<LISTENER_ID>
         * @param listenerArn
         * @return
        </LISTENER_ID></GLB_ARN> */
        @VisibleForTesting
        internal fun validateListenerArn(listenerArn: String) {
            if (Strings.isNullOrEmpty(listenerArn)) {
                throw RuntimeException("ListenerArn must not be null or empty")
            }

            val splitListenerArn = listenerArn.split(LISTENER_SEPARATOR)
            if (splitListenerArn.size != 2 || !splitListenerArn[1].matches(regex = Regex("[\\w]+"))) {
                throw RuntimeException(String.format("Invalid listener arn %s", listenerArn))
            }
        }
    }
}
