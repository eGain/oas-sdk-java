package egain.oassdk.testgenerators.scenario;

import egain.oassdk.config.AiScenarioConfig;
import egain.oassdk.config.AiScenarioConfigLoader;
import egain.oassdk.config.TestConfig;
import egain.oassdk.core.exceptions.GenerationException;
import egain.oassdk.core.exceptions.OASSDKException;
import egain.oassdk.testgenerators.ConfigurableTestGenerator;
import egain.oassdk.testgenerators.TestGenerator;
import egain.oassdk.testgenerators.common.TestMavenSupport;
import egain.oassdk.testgenerators.common.TestOutputLayout;
import egain.oassdk.testgenerators.common.TestSpecUtils;
import egain.oassdk.testgenerators.scenario.llm.LlmClient;
import egain.oassdk.testgenerators.scenario.llm.LlmClientFactory;
import egain.oassdk.testgenerators.scenario.source.ScenarioSource;
import egain.oassdk.testgenerators.scenario.source.ScenarioSourceFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Generates Java/JUnit scenario tests from Jira (default) via a configured LLM.
 */
public class AiScenarioTestGenerator implements TestGenerator, ConfigurableTestGenerator {

    private TestConfig config;
    private ScenarioSource scenarioSourceOverride;
    private LlmClient llmClientOverride;
    private AiScenarioConfig aiConfigOverride;

    @Override
    public void setConfig(TestConfig config) {
        this.config = config;
    }

    @Override
    public TestConfig getConfig() {
        return config;
    }

    /**
     * Test seam: inject scenario source (skips factory).
     */
    public void setScenarioSourceOverride(ScenarioSource scenarioSourceOverride) {
        this.scenarioSourceOverride = scenarioSourceOverride;
    }

    /**
     * Test seam: inject LLM client (skips factory).
     */
    public void setLlmClientOverride(LlmClient llmClientOverride) {
        this.llmClientOverride = llmClientOverride;
    }

    /**
     * Test seam: inject resolved AI config.
     */
    public void setAiConfigOverride(AiScenarioConfig aiConfigOverride) {
        this.aiConfigOverride = aiConfigOverride;
    }

    @Override
    public void generate(Map<String, Object> spec, String outputDir, TestConfig config, String testFramework)
            throws GenerationException {
        this.config = config;
        try {
            AiScenarioConfig aiConfig = aiConfigOverride != null
                    ? aiConfigOverride
                    : AiScenarioConfigLoader.resolve(config);
            if (!aiConfig.isEnabled()) {
                throw new GenerationException("AI scenario generation is disabled in configuration");
            }

            ScenarioSource source = scenarioSourceOverride != null
                    ? scenarioSourceOverride
                    : ScenarioSourceFactory.create(aiConfig);
            LlmClient llm = llmClientOverride != null
                    ? llmClientOverride
                    : LlmClientFactory.create(aiConfig);

            ScenarioTestPipeline pipeline = new ScenarioTestPipeline(source, llm, aiConfig);
            List<GeneratedScenario> scenarios = pipeline.run(spec);

            String subdir = aiConfig.getGeneration().getOutputSubdir();
            if (subdir == null || subdir.isBlank()) {
                subdir = "scenario";
            }
            Path moduleDir = Paths.get(outputDir, subdir);
            Files.createDirectories(moduleDir);

            String basePackage = resolvePackage(config);
            String packageDir = TestOutputLayout.testJavaDir(moduleDir.toString(), basePackage);
            Files.createDirectories(Paths.get(packageDir));

            if (aiConfig.getGeneration().isWriteScenarioJson()) {
                Path scenariosDir = moduleDir.resolve("scenarios");
                Files.createDirectories(scenariosDir);
                for (GeneratedScenario scenario : scenarios) {
                    String fileKey = scenario.getSourceKey() != null ? scenario.getSourceKey() : scenario.getScenarioId();
                    String safe = fileKey != null ? fileKey.replaceAll("[^A-Za-z0-9._-]", "_") : "scenario";
                    Path jsonPath = scenariosDir.resolve(safe + ".json");
                    Files.writeString(jsonPath, ScenarioTestPipeline.jsonMapper().writeValueAsString(scenario),
                            StandardCharsets.UTF_8);
                }
            }

            for (GeneratedScenario scenario : scenarios) {
                String className = ScenarioJavaTestEmitter.toClassName(scenario.getSourceKey(), scenario.getTitle());
                String java = ScenarioJavaTestEmitter.emit(basePackage, className, scenario);
                Files.writeString(Paths.get(packageDir, className + ".java"), java, StandardCharsets.UTF_8);
            }

            String baseUrl = TestSpecUtils.resolveBaseUrl(spec, config);
            writePom(moduleDir.toString(), basePackage);
            writeReadme(moduleDir, aiConfig, scenarios.size(), baseUrl);

        } catch (GenerationException e) {
            throw e;
        } catch (OASSDKException e) {
            throw new GenerationException("AI scenario generation failed: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new GenerationException("Failed to generate AI scenario tests: " + e.getMessage(), e);
        }
    }

    private static String resolvePackage(TestConfig config) {
        String basePackage = "com.example.api";
        if (config != null && config.getAdditionalProperties() != null) {
            Object packageNameObj = config.getAdditionalProperties().get("packageName");
            if (packageNameObj != null && !packageNameObj.toString().isBlank()) {
                basePackage = packageNameObj.toString().trim();
            }
        }
        return basePackage;
    }

    private static void writePom(String moduleDir, String basePackage) throws Exception {
        StringBuilder pom = new StringBuilder();
        pom.append(TestMavenSupport.pomHeader("scenario-tests", basePackage));
        pom.append("    <dependencies>\n");
        pom.append(TestMavenSupport.junitDependency());
        pom.append("    </dependencies>\n");
        pom.append(TestMavenSupport.buildSectionWithTestSupport());
        Files.writeString(Paths.get(moduleDir, "pom.xml"), pom.toString(), StandardCharsets.UTF_8);
    }

    private static void writeReadme(Path moduleDir, AiScenarioConfig aiConfig, int count, String baseUrl)
            throws Exception {
        Objects.requireNonNull(aiConfig);
        String content = """
                # AI Scenario Tests

                Generated %d scenario test(s) from source `%s` using model `%s`.

                ## Prerequisites

                - Running API at base URL (default from OpenAPI servers / TestEnv): `%s`
                - Shared test-support module at `../test-support`

                ## Run

                ```bash
                mvn test
                ```

                Scenario JSON artifacts (if enabled) are under `scenarios/`.
                """.formatted(
                count,
                aiConfig.getScenarioSource().getType(),
                aiConfig.getActiveModel(),
                baseUrl != null ? baseUrl : "http://localhost:8080");
        Files.writeString(moduleDir.resolve("README.md"), content, StandardCharsets.UTF_8);
    }

    @Override
    public String getName() {
        return "AI Scenario Test Generator";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getTestType() {
        return "scenario";
    }
}
