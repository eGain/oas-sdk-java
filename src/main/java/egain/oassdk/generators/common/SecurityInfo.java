package egain.oassdk.generators.common;

import java.util.ArrayList;
import java.util.List;

/**
 * Security requirements extracted from an OpenAPI operation.
 */
public final class SecurityInfo {

    public final boolean hasRequirements;
    public final List<String> scopes = new ArrayList<>();

    public SecurityInfo(boolean hasRequirements) {
        this.hasRequirements = hasRequirements;
    }
}
