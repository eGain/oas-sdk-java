package egain.oassdk.testgenerators.scenario;

import egain.oassdk.config.AiScenarioConfig;
import egain.oassdk.config.AiScenarioConfigLoader;
import egain.oassdk.config.TestConfig;
import egain.oassdk.testgenerators.TestGenerator;
import egain.oassdk.testgenerators.TestGeneratorFactory;
import egain.oassdk.testgenerators.common.TestProfileSupport;
import egain.oassdk.testgenerators.scenario.llm.LlmClient;
import egain.oassdk.testgenerators.scenario.source.ScenarioSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AiScenarioTestGeneratorTest {

    @Test
    void factory_resolvesScenarioType() {
        TestGeneratorFactory factory = new TestGeneratorFactory();
        TestGenerator generator = factory.getGenerator("scenario");
        assertInstanceOf(AiScenarioTestGenerator.class, generator);
        assertInstanceOf(AiScenarioTestGenerator.class, factory.getGenerator("ai-scenario"));
        assertTrue(List.of(factory.getSupportedTestTypes()).contains("scenario"));
    }

    @Test
    void smokeProfile_excludesScenario() {
        Map<String, Object> extra = new HashMap<>();
        extra.put("testProfile", "smoke");
        TestConfig config = TestConfig.builder().additionalProperties(extra).build();
        List<String> filtered = TestProfileSupport.filterTestTypes(
                List.of("integration", "scenario", "unit"), config);
        assertFalse(filtered.contains("scenario"));
        assertTrue(filtered.contains("integration"));
    }

    @Test
    void generate_withStubs_writesJsonAndJava(@TempDir Path temp) throws Exception {
        ScenarioSource source = request -> List.of(
                ScenarioDocument.builder()
                        .id("1")
                        .key("EGS-9")
                        .title("Ping scenario")
                        .description("Call ping endpoint")
                        .labels(List.of("api-scenario"))
                        .build()
        );
        LlmClient llm = request -> """
                {
                  "scenarioId": "EGS-9",
                  "title": "Ping scenario",
                  "steps": [
                    {"method": "GET", "path": "/ping", "expectedStatus": 200}
                  ]
                }
                """;

        AiScenarioConfig aiConfig = AiScenarioConfigLoader.loadDefaults();
        AiScenarioTestGenerator generator = new AiScenarioTestGenerator();
        generator.setScenarioSourceOverride(source);
        generator.setLlmClientOverride(llm);
        generator.setAiConfigOverride(aiConfig);

        Map<String, Object> spec = minimalSpec();
        TestConfig testConfig = TestConfig.builder().build();
        generator.generate(spec, temp.toString(), testConfig, "junit5");

        Path json = temp.resolve("scenario/scenarios/EGS-9.json");
        assertTrue(Files.isRegularFile(json));
        String jsonText = Files.readString(json);
        assertTrue(jsonText.contains("/ping"));

        Path javaDir = temp.resolve("scenario/src/test/java/com/example/api");
        assertTrue(Files.isDirectory(javaDir));
        try (var stream = Files.list(javaDir)) {
            List<Path> javaFiles = stream.filter(p -> p.getFileName().toString().endsWith("ScenarioIT.java")).toList();
            assertEquals(1, javaFiles.size());
            String java = Files.readString(javaFiles.get(0));
            assertTrue(java.contains("HttpClient"));
            assertTrue(java.contains("/ping"));
            assertTrue(java.contains("assertEquals(200"));
        }
        assertTrue(Files.isRegularFile(temp.resolve("scenario/pom.xml")));
    }

    private static Map<String, Object> minimalSpec() {
        Map<String, Object> ping = new LinkedHashMap<>();
        ping.put("operationId", "ping");
        ping.put("summary", "Ping");
        Map<String, Object> pingItem = new LinkedHashMap<>();
        pingItem.put("get", ping);
        Map<String, Object> paths = new LinkedHashMap<>();
        paths.put("/ping", pingItem);
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("title", "Demo API");
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("info", info);
        spec.put("paths", paths);
        return spec;
    }
}
