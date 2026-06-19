package egain.oassdk.testgenerators.common;

import java.util.HashMap;
import java.util.Map;

/**
 * Minimal valid OpenAPI specs for generator and integration tests.
 */
public final class OpenApiTestFixtures {

    private OpenApiTestFixtures() {
    }

    /** Empty paths, suitable for generator smoke tests. */
    public static Map<String, Object> minimalSpec() {
        return minimalSpec("Test API", "1.0.0");
    }

    public static Map<String, Object> minimalSpec(String title, String version) {
        Map<String, Object> spec = new HashMap<>();
        Map<String, Object> info = new HashMap<>();
        info.put("title", title);
        info.put("version", version);
        spec.put("info", info);
        spec.put("paths", new HashMap<>());
        return spec;
    }

    /** Minimal spec with a default server URL. */
    public static Map<String, Object> minimalSpecWithServer(String baseUrl) {
        Map<String, Object> spec = minimalSpec();
        spec.put("servers", java.util.List.of(Map.of("url", baseUrl)));
        return spec;
    }
}
