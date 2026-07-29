package egain.oassdk.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AiScenarioConfigLoaderTest {

    @Test
    void loadDefaults_containsThreeModelsAndJiraSource() throws Exception {
        AiScenarioConfig config = AiScenarioConfigLoader.loadDefaults();
        assertTrue(config.isEnabled());
        assertEquals("openai", config.getActiveModel());
        assertEquals(3, config.getModels().size());
        assertNotNull(config.getModels().get("openai"));
        assertNotNull(config.getModels().get("anthropic"));
        assertNotNull(config.getModels().get("gemini"));
        assertEquals("gpt-4o", config.getModels().get("openai").getModel());
        assertEquals("jira", config.getScenarioSource().getType());
        assertNotNull(config.resolveActiveModel());
        assertEquals("openai", config.resolveActiveModel().getProvider());
    }

    @Test
    void resolve_appliesCliOverrides(@TempDir Path temp) throws Exception {
        Path custom = temp.resolve("custom-ai.yaml");
        Files.writeString(custom, """
                aiScenario:
                  enabled: true
                  activeModel: gemini
                  models:
                    gemini:
                      provider: gemini
                      model: gemini-2.0-flash
                      baseUrl: https://generativelanguage.googleapis.com/v1beta
                      apiKeyEnv: GOOGLE_API_KEY
                      temperature: 0.1
                      maxTokens: 2048
                    openai:
                      provider: openai
                      model: gpt-4o
                      baseUrl: https://api.openai.com/v1
                      apiKeyEnv: OPENAI_API_KEY
                    anthropic:
                      provider: anthropic
                      model: claude-sonnet-4-20250514
                      baseUrl: https://api.anthropic.com
                      apiKeyEnv: ANTHROPIC_API_KEY
                  scenarioSource:
                    type: jira
                    jira:
                      jql: "project = CUSTOM"
                  generation:
                    outputSubdir: scenario
                    writeScenarioJson: true
                    mapToOpenApiOperations: true
                    failOnUnmappedSteps: true
                """);

        Map<String, Object> extra = new HashMap<>();
        extra.put(AiScenarioConfigLoader.AI_CONFIG_PATH_KEY, custom.toString());
        extra.put(AiScenarioConfigLoader.AI_MODEL_KEY, "anthropic");
        extra.put(AiScenarioConfigLoader.JIRA_JQL_KEY, "project = OVERRIDE");

        TestConfig testConfig = TestConfig.builder().additionalProperties(extra).build();
        AiScenarioConfig resolved = AiScenarioConfigLoader.resolve(testConfig);

        assertEquals("anthropic", resolved.getActiveModel());
        assertEquals("project = OVERRIDE", resolved.getScenarioSource().getJira().getJql());
        assertEquals("anthropic", resolved.resolveActiveModel().getProvider());
    }
}
