package egain.oassdk.generators.common;

import java.util.Map;

/**
 * An HTTP operation on an OpenAPI path item.
 */
public record PathOperation(String path, String method, Map<String, Object> operation) {
}
