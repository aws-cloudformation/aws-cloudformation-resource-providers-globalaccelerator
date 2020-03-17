package software.amazon.globalaccelerator.accelerator;

import java.util.Map;
import org.json.JSONObject;
import org.json.JSONTokener;

class Configuration extends BaseConfiguration {

    public Configuration() {
        super("aws-globalaccelerator-accelerator.json");
    }
}
