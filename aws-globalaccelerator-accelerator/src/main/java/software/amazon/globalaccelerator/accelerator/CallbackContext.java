package software.amazon.globalaccelerator.accelerator;

import com.amazonaws.services.globalaccelerator.model.Accelerator;

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
