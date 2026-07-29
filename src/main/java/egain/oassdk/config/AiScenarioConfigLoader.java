package egain.oassdk.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import egain.oassdk.core.exceptions.OASSDKException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Loads {@link AiScenarioConfig} from classpath defaults, optional user YAML, and {@link TestConfig} overrides.
 *
 * <p>Precedence (later wins for scalar overrides): classpath defaults → user YAML → TestConfig additionalProperties.
 */
public final class AiScenarioConfigLoader {

    public static final String DEFAULT_RESOURCE = "ai-scenario-defaults.yaml";
    public static final String ADDITIONAL_PROP_KEY = "aiScenario";
    public static final String AI_CONFIG_PATH_KEY = "aiScenario.configPath";
    public static final String AI_MODEL_KEY = "aiScenario.activeModel";
    public static final String JIRA_JQL_KEY = "aiScenario.jira.jql";

    private static final ObjectMapper YAML_MAPPER;

    static {
        YAMLFactory yamlFactory = new YAMLFactory();
        yamlFactory.configure(YAMLGenerator.Feature.WRITE_DOC_START_MARKER, false);
        YAML_MAPPER = new ObjectMapper(yamlFactory);
    }

    private AiScenarioConfigLoader() {
    }

    public static AiScenarioConfig loadDefaults() throws OASSDKException {
        try (InputStream in = AiScenarioConfigLoader.class.getClassLoader().getResourceAsStream(DEFAULT_RESOURCE)) {
            if (in == null) {
                throw new OASSDKException("Missing classpath resource: " + DEFAULT_RESOURCE);
            }
            return parseRoot(YAML_MAPPER.readTree(in));
        } catch (IOException e) {
            throw new OASSDKException("Failed to load " + DEFAULT_RESOURCE + ": " + e.getMessage(), e);
        }
    }

    public static AiScenarioConfig loadFromFile(Path path) throws OASSDKException {
        if (path == null || !Files.isRegularFile(path)) {
            throw new OASSDKException("AI scenario config file not found: " + path);
        }
        try {
            return parseRoot(YAML_MAPPER.readTree(Files.readString(path)));
        } catch (IOException e) {
            throw new OASSDKException("Failed to load AI scenario config from " + path + ": " + e.getMessage(), e);
        }
    }

    /**
     * Resolve effective config for test generation.
     */
    public static AiScenarioConfig resolve(TestConfig testConfig) throws OASSDKException {
        AiScenarioConfig config = loadDefaults();

        Map<String, Object> extra = testConfig != null ? testConfig.getAdditionalProperties() : null;
        if (extra != null) {
            Object pathObj = extra.get(AI_CONFIG_PATH_KEY);
            if (pathObj != null && !pathObj.toString().isBlank()) {
                config = loadFromFile(Path.of(pathObj.toString().trim()));
            }

            Object nested = extra.get(ADDITIONAL_PROP_KEY);
            if (nested instanceof AiScenarioConfig typed) {
                config = merge(config, typed);
            } else if (nested instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> asMap = (Map<String, Object>) map;
                config = merge(config, YAML_MAPPER.convertValue(asMap, AiScenarioConfig.class));
            }

            Object model = extra.get(AI_MODEL_KEY);
            if (model != null && !model.toString().isBlank()) {
                config.setActiveModel(model.toString().trim().toLowerCase(Locale.ROOT));
            }
            Object jql = extra.get(JIRA_JQL_KEY);
            if (jql != null && !jql.toString().isBlank()) {
                config.getScenarioSource().getJira().setJql(jql.toString().trim());
            }
        }
        return config;
    }

    static AiScenarioConfig parseRoot(JsonNode root) throws OASSDKException {
        if (root == null || root.isNull()) {
            throw new OASSDKException("Empty AI scenario configuration");
        }
        JsonNode node = root.has("aiScenario") ? root.get("aiScenario") : root;
        try {
            AiScenarioConfig config = YAML_MAPPER.treeToValue(node, AiScenarioConfig.class);
            if (config.getModels() == null || config.getModels().isEmpty()) {
                throw new OASSDKException("aiScenario.models must define at least one model");
            }
            return config;
        } catch (IOException e) {
            throw new OASSDKException("Invalid AI scenario configuration: " + e.getMessage(), e);
        }
    }

    static AiScenarioConfig merge(AiScenarioConfig base, AiScenarioConfig override) {
        if (override == null) {
            return base;
        }
        AiScenarioConfig merged = new AiScenarioConfig();
        merged.setEnabled(override.isEnabled());
        merged.setActiveModel(override.getActiveModel() != null ? override.getActiveModel() : base.getActiveModel());

        Map<String, AiScenarioConfig.ModelConfig> models = new LinkedHashMap<>(base.getModels());
        if (override.getModels() != null && !override.getModels().isEmpty()) {
            models.putAll(override.getModels());
        }
        merged.setModels(models);

        if (override.getScenarioSource() != null) {
            merged.setScenarioSource(override.getScenarioSource());
        } else {
            merged.setScenarioSource(base.getScenarioSource());
        }
        if (override.getGeneration() != null) {
            merged.setGeneration(override.getGeneration());
        } else {
            merged.setGeneration(base.getGeneration());
        }
        return merged;
    }
}
