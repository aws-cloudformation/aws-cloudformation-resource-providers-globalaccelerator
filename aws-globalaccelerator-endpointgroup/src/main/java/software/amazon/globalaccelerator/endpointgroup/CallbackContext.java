package software.amazon.globalaccelerator.endpointgroup;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class CallbackContext {
    private Integer stabilizationRetriesRemaining;
}
