package egain.oassdk.testgenerators.common;

import egain.oassdk.Util;

import java.util.List;
import java.util.Map;

/**
 * Shared OpenAPI spec metadata helpers for test generators.
 */
public final class TestSpecUtils {

    private TestSpecUtils() {
    }

    public static String getApiTitle(Map<String, Object> spec) {
        if (spec == null) {
            return "API";
        }
        Map<String, Object> info = Util.asStringObjectMap(spec.get("info"));
        if (info == null) {
            return "API";
        }
        Object title = info.get("title");
        return title != null ? title.toString() : "API";
    }

    public static String getBaseUrl(Map<String, Object> spec) {
        if (spec != null && spec.containsKey("servers")) {
            List<Map<String, Object>> servers = Util.asStringObjectMapList(spec.get("servers"));
            if (servers != null && !servers.isEmpty()) {
                String url = (String) servers.get(0).get("url");
                if (url != null) {
                    return url;
                }
            }
        }
        return "http://localhost:8080";
    }
}
