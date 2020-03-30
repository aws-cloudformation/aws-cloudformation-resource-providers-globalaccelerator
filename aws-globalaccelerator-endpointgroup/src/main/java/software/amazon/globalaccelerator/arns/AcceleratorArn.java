package software.amazon.globalaccelerator.arns;

import com.google.common.base.Strings;
import lombok.EqualsAndHashCode;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Class for accelerator arn
 */
@EqualsAndHashCode
public class AcceleratorArn {

    private final String acceleratorArn;
    private final String awsAccountId;
    private final String uuid;

    private static final String SERVICE_NAME = "globalaccelerator";
    private static final String RESOURCE_NAME = "accelerator";
    private static final String EXPECTED_ARN_REGEX = "^arn:aws:(\\w+)::(\\d{12}):(\\w+)/([0-9a-f-]+)$";

    /**
     * Constructs a accelerator arn.
     * @param arn
     */
    public AcceleratorArn(final String arn) {
        if (Strings.isNullOrEmpty(arn)) {
            throw new RuntimeException(String.format("Invalid global accelerator arn %s", arn));
        }

        final Pattern pattern = Pattern.compile(EXPECTED_ARN_REGEX);
        final Matcher matcher = pattern.matcher(arn);
        if (!matcher.find() || matcher.groupCount() != 4) {
            throw new RuntimeException(String.format("Invalid global accelerator arn %s", arn));
        }

        final String serviceName = matcher.group(1);
        final String resourceName = matcher.group(3);

        if (matchesName(serviceName, resourceName)) {
            this.acceleratorArn = arn;
            this.awsAccountId = matcher.group(2);
            this.uuid = matcher.group(4);
        } else {
            throw new RuntimeException(String.format("Invalid accelerator arn provided %s", arn));
        }
    }

    /**
     * Converts to a string.
     * @return
     */
    @Override
    public String toString() {
        return acceleratorArn;
    }

    /**
     * Gets accelerator arn.
     * @return
     */
    public String getAcceleratorArn() {
        return acceleratorArn;
    }

    /**
     * Gets the accelerator aws account id.
     * @return
     */
    public String getAwsAccountId() {
        return awsAccountId;
    }

    /**
     * Gets the uuid.
     * @return
     */
    public String getUuid() {
        return this.uuid;
    }

    private boolean matchesName(final String serviceName, final String resourceName) {
        return SERVICE_NAME.equals(serviceName) && RESOURCE_NAME.equals(resourceName);
    }
}
