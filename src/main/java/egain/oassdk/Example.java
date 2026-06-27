package egain.oassdk;

import egain.oassdk.config.GeneratorConfig;
import egain.oassdk.config.TestConfig;
import egain.oassdk.core.exceptions.OASSDKException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Regenerates {@code generated4/} from {@code bundle-master-content.yaml}.
 * Includes {@code sequence-java} workflow chain tests (enumerated create→get→… sequences).
 * Run: {@code mvn -q -DskipTests package && java -cp target/oas-sdk-java-*-SNAPSHOT.jar egain.oassdk.Example}
 */
public class Example {

    private static final String REPO_ROOT = System.getProperty("user.dir");
    private static final String BUNDLE = REPO_ROOT + "/generated4/bundle-master-content.yaml";
    private static final String OUT = REPO_ROOT + "/generated4/";

    public static void main(String[] args) {
        try {
            GeneratorConfig config = GeneratorConfig.builder()
                    .language("java")
                    .framework("jersey")
                    .packageName("com.example.api")
                    .build();

            TestConfig testConfig = new TestConfig();
            Map<String, Object> testProps = new HashMap<>();
            testProps.put("auth.provider", "egain");
            testProps.put("test.baseUrl", "https://eg5843ain.ezdev.net/system/ws/knowledge/contentmgr/v4");
            testProps.put("packageName", "com.example.api");
            testProps.put("sequence.maxChainLength", 4);
            testConfig.setAdditionalProperties(testProps);

            try (OASSDK sdk = new OASSDK(config, testConfig, null)) {
                sdk.loadSpec(BUNDLE);

                sdk.generateApplication("java", "jersey", "com.example.api", OUT + "generated-app");

                sdk.generateTests(
                        List.of("unit", "integration", "nfr", "performance", "security",
                                "sequence-java", "postman", "schemathesis"),
                        OUT + "generated-tests");

                sdk.generateDocumentation(OUT + "generated-docs");
                sdk.generateMockData(OUT + "generated-mock-data");
            }

            System.out.println("Generation complete from bundle-master-content.yaml → " + OUT);

        } catch (OASSDKException e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
