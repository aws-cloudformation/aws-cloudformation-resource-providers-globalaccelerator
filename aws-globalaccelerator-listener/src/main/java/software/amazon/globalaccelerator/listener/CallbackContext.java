package software.amazon.globalaccelerator.listener;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;

@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class CallbackContext {
    private Integer stabilizationRetriesRemaining;
    private HashMap<String, String> callbackMap;
}
