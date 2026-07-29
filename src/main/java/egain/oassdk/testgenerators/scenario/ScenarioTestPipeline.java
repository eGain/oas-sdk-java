package egain.oassdk.testgenerators.scenario;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import egain.oassdk.config.AiScenarioConfig;
import egain.oassdk.core.exceptions.OASSDKException;
import egain.oassdk.testgenerators.scenario.llm.LlmClient;
import egain.oassdk.testgenerators.scenario.llm.LlmRequest;
import egain.oassdk.testgenerators.scenario.source.ScenarioSource;
import egain.oassdk.testgenerators.scenario.source.ScenarioSourceRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Fetches scenarios, calls the LLM, validates steps against OpenAPI, returns structured scenarios.
 */
public final class ScenarioTestPipeline {

    private static final Logger LOG = Logger.getLogger(ScenarioTestPipeline.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private final ScenarioSource scenarioSource;
    private final LlmClient llmClient;
    private final AiScenarioConfig config;

    public ScenarioTestPipeline(ScenarioSource scenarioSource, LlmClient llmClient, AiScenarioConfig config) {
        this.scenarioSource = Objects.requireNonNull(scenarioSource, "scenarioSource");
        this.llmClient = Objects.requireNonNull(llmClient, "llmClient");
        this.config = Objects.requireNonNull(config, "config");
    }

    public List<GeneratedScenario> run(Map<String, Object> spec) throws OASSDKException {
        OpenApiOperationIndex index = OpenApiOperationIndex.fromSpec(spec);
        if (index.getOperations().isEmpty()) {
            throw new OASSDKException("OpenAPI spec has no operations to map scenarios onto");
        }

        ScenarioSourceRequest request = new ScenarioSourceRequest(
                config.getScenarioSource().getJira(), null);
        List<ScenarioDocument> documents = scenarioSource.fetch(request);
        if (documents.isEmpty()) {
            throw new OASSDKException("No scenarios returned from source type="
                    + config.getScenarioSource().getType());
        }

        AiScenarioConfig.ModelConfig model = config.resolveActiveModel();
        double temperature = model != null ? model.getTemperature() : 0.2;
        int maxTokens = model != null ? model.getMaxTokens() : 4096;
        boolean failOnUnmapped = config.getGeneration().isFailOnUnmappedSteps();
        boolean mapOps = config.getGeneration().isMapToOpenApiOperations();

        List<GeneratedScenario> results = new ArrayList<>();
        for (ScenarioDocument document : documents) {
            String sourceKey = document.getKey() != null ? document.getKey() : document.getId();
            LlmRequest llmRequest = new LlmRequest(
                    ScenarioJsonParser.systemPrompt(),
                    ScenarioJsonParser.userPrompt(document, index),
                    temperature,
                    maxTokens);
            String completion = llmClient.complete(llmRequest);
            GeneratedScenario scenario = ScenarioJsonParser.parse(completion, sourceKey);
            if (scenario.getSourceKey() == null) {
                scenario.setSourceKey(sourceKey);
            }
            if (mapOps) {
                List<GeneratedScenario.ScenarioStep> resolved = new ArrayList<>();
                for (GeneratedScenario.ScenarioStep step : scenario.getSteps()) {
                    resolved.add(index.resolveStep(step, failOnUnmapped));
                }
                scenario.setSteps(resolved);
            }
            results.add(scenario);
            LOG.info(() -> "Generated scenario for " + sourceKey + " with " + scenario.getSteps().size() + " steps");
        }
        return results;
    }

    public static ObjectMapper jsonMapper() {
        return MAPPER;
    }
}
