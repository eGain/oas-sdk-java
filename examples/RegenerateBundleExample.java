package egain.oassdk.examples;

import egain.oassdk.OASSDK;
import egain.oassdk.config.GeneratorConfig;
import egain.oassdk.config.TestConfig;
import egain.oassdk.core.exceptions.OASSDKException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Regenerates a full bundle (app + tests + docs) from a local OpenAPI file.
 *
 * <p>Environment overrides (optional):
 * <ul>
 *   <li>{@code OAS_SPEC_PATH} — OpenAPI YAML (default: {@code ./bundle-master-content.yaml} in cwd)</li>
 *   <li>{@code OAS_OUTPUT_DIR} — output root (default: {@code ./generated/})</li>
 *   <li>{@code OAS_BASE_URL} — {@code test.baseUrl} in generated test-env template</li>
 * </ul>
 *
 * <p>Run from repo root:
 * <pre>{@code
 * mvn -q -DskipTests package exec:java \
 *   -Dexec.classpathScope=compile \
 *   -Dexec.mainClass=egain.oassdk.examples.RegenerateBundleExample
 * }</pre>
 */
public class RegenerateBundleExample {

    public static void main(String[] args) {
        String repoRoot = System.getProperty("user.dir");
        String bundle = envOr("OAS_SPEC_PATH", repoRoot + "/bundle-master-content.yaml");
        String out = envOr("OAS_OUTPUT_DIR", repoRoot + "/generated/");
        if (!out.endsWith("/")) {
            out = out + "/";
        }
        String baseUrl = envOr("OAS_BASE_URL", "http://localhost:8080");

        try {
            GeneratorConfig config = GeneratorConfig.builder()
                    .language("java")
                    .framework("jersey")
                    .packageName("com.example.api")
                    .build();

            TestConfig testConfig = new TestConfig();
            Map<String, Object> testProps = new HashMap<>();
            testProps.put("auth.provider", "static");
            testProps.put("test.baseUrl", baseUrl);
            testProps.put("packageName", "com.example.api");
            testConfig.setAdditionalProperties(testProps);

            try (OASSDK sdk = new OASSDK(config, testConfig, null)) {
                sdk.loadSpec(bundle);
                sdk.generateApplication("java", "jersey", "com.example.api", out + "generated-app");
                sdk.generateTests(
                        List.of("contract", "integration", "lifecycle", "nfr", "performance", "security",
                                "postman", "schemathesis"),
                        out + "generated-tests");
                sdk.generateDocumentation(out + "generated-docs");
            }

            System.out.println("Generation complete → " + out);

        } catch (OASSDKException e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static String envOr(String key, String defaultValue) {
        String v = System.getenv(key);
        if (v != null && !v.isBlank()) {
            return v.trim();
        }
        return defaultValue;
    }
}
