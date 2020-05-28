package software.amazon.globalaccelerator.arns

/**
 * Listener ARN validation
 */
class ListenerArn(arn: String?) : AcceleratorArn(arn?.split(LISTENER_SEPARATOR)?.get(0)) {

    val listenerId: String
    val listenerArn: String
        get() = acceleratorArn + LISTENER_SEPARATOR + listenerId

    companion object {
        private const val LISTENER_SEPARATOR = "/listener/"
        internal fun validateListenerArn(arn: String) {
            val splitListenerArn = arn.split(LISTENER_SEPARATOR)
            if (splitListenerArn.size != 2 || !splitListenerArn[1].matches(regex = Regex("[\\w]+"))) {
                throw RuntimeException(String.format("Invalid listener arn %s", arn))
            }
        }
    }

    init {
        validateListenerArn(arn!!)
        this.listenerId = arn.split(LISTENER_SEPARATOR)[1]
    }

    override fun toString(): String {
        return listenerArn
    }
}
