package software.amazon.globalaccelerator.arns;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import lombok.EqualsAndHashCode;

/**
 * Class for ListenerArn
 */
@EqualsAndHashCode(callSuper = true)
public class ListenerArn extends AcceleratorArn {

    private String listenerId;

    private static final String LISTENER_SEPARATOR = "/listener/";

    /**
     * Constructs an instance of ListenerArn from a string.
     * @param listenerArn
     * @return
     */
    public ListenerArn(final String listenerArn) {
        super(listenerArn.split(LISTENER_SEPARATOR)[0]);
        validateListenerArn(listenerArn);
        this.listenerId = listenerArn.split(LISTENER_SEPARATOR)[1];
    }

    /**
     * Converts to String
     * @return
     */
    @Override
    public String toString() {
        return getListenerArn();
    }

    /**
     * Gets listener arn.
     * @return
     */
    public String getListenerArn() {
        return getAcceleratorArn() + LISTENER_SEPARATOR + listenerId;
    }

    /**
     * Gets listener id.
     * @return
     */
    public String getListenerId() {
        return listenerId;
    }

    /**
     * Validates a listener arn if it matches the format of <GLB_ARN>/listener/<LISTENER_ID>
     * @param listenerArn
     * @return
     */
    @VisibleForTesting
    static void validateListenerArn(final String listenerArn) {
        if (Strings.isNullOrEmpty(listenerArn)) {
            throw new RuntimeException("ListenerArn must not be null or empty");
        }

        final String[] splitListenerArn = listenerArn.split(LISTENER_SEPARATOR);
        if (splitListenerArn.length != 2 || !splitListenerArn[1].matches("[\\w]+")) {
            throw new RuntimeException(String.format("Invalid listener arn %s", listenerArn));
        }
    }
}
