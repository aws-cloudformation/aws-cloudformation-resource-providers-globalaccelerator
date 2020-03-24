package software.amazon.globalaccelerator.listener;

import java.util.Map;
import org.json.JSONObject;
import org.json.JSONTokener;

class Configuration extends BaseConfiguration {

    public Configuration() {
        super("aws-globalaccelerator-listener.json");
    }
}
