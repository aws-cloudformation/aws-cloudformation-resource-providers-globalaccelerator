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
    /**
     * The number of attempts remaining before we timeout the stabilization wait
     */
    private Integer stabilizationRetriesRemaining;

    /**
     * Indicates that primary work has completed and we are just waiting
     * for the accelerator to become deployed
     */
    private boolean pendingStabilization;
}
