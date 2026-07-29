package egain.oassdk.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Configuration for AI-driven scenario test generation (Jira source + LLM providers).
 */
public final class AiScenarioConfig {

    private boolean enabled = true;
    private String activeModel = "openai";
    private Map<String, ModelConfig> models = new LinkedHashMap<>();
    private ScenarioSourceConfig scenarioSource = new ScenarioSourceConfig();
    private GenerationConfig generation = new GenerationConfig();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getActiveModel() {
        return activeModel;
    }

    public void setActiveModel(String activeModel) {
        this.activeModel = activeModel;
    }

    public Map<String, ModelConfig> getModels() {
        return models == null ? Map.of() : Map.copyOf(models);
    }

    public void setModels(Map<String, ModelConfig> models) {
        this.models = models != null ? new LinkedHashMap<>(models) : new LinkedHashMap<>();
    }

    public ScenarioSourceConfig getScenarioSource() {
        return scenarioSource;
    }

    public void setScenarioSource(ScenarioSourceConfig scenarioSource) {
        this.scenarioSource = scenarioSource != null ? scenarioSource : new ScenarioSourceConfig();
    }

    public GenerationConfig getGeneration() {
        return generation;
    }

    public void setGeneration(GenerationConfig generation) {
        this.generation = generation != null ? generation : new GenerationConfig();
    }

    public ModelConfig resolveActiveModel() {
        if (models == null || models.isEmpty()) {
            return null;
        }
        String key = activeModel != null ? activeModel.trim().toLowerCase(Locale.ROOT) : "openai";
        ModelConfig direct = models.get(key);
        if (direct != null) {
            return direct;
        }
        for (Map.Entry<String, ModelConfig> entry : models.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(key)) {
                return entry.getValue();
            }
            ModelConfig cfg = entry.getValue();
            if (cfg != null && cfg.getProvider() != null
                    && cfg.getProvider().equalsIgnoreCase(key)) {
                return cfg;
            }
        }
        return null;
    }

    public static final class ModelConfig {
        private String provider;
        private String model;
        private String baseUrl;
        private String apiKeyEnv;
        private double temperature = 0.2;
        private int maxTokens = 4096;

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKeyEnv() {
            return apiKeyEnv;
        }

        public void setApiKeyEnv(String apiKeyEnv) {
            this.apiKeyEnv = apiKeyEnv;
        }

        public double getTemperature() {
            return temperature;
        }

        public void setTemperature(double temperature) {
            this.temperature = temperature;
        }

        public int getMaxTokens() {
            return maxTokens;
        }

        public void setMaxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
        }
    }

    public static final class ScenarioSourceConfig {
        private String type = "jira";
        private JiraConfig jira = new JiraConfig();

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public JiraConfig getJira() {
            return jira;
        }

        public void setJira(JiraConfig jira) {
            this.jira = jira != null ? jira : new JiraConfig();
        }
    }

    public static final class JiraConfig {
        private String baseUrlEnv = "JIRA_BASE_URL";
        private String userEnv = "JIRA_USER";
        private String tokenEnv = "JIRA_API_TOKEN";
        private String baseUrl;
        private String user;
        private String token;
        private String jql =
                "project = DEMO AND type = Story AND labels = api-scenario ORDER BY created DESC";
        private int maxIssues = 25;
        private List<String> fields = List.of("summary", "description", "labels");

        public String getBaseUrlEnv() {
            return baseUrlEnv;
        }

        public void setBaseUrlEnv(String baseUrlEnv) {
            this.baseUrlEnv = baseUrlEnv;
        }

        public String getUserEnv() {
            return userEnv;
        }

        public void setUserEnv(String userEnv) {
            this.userEnv = userEnv;
        }

        public String getTokenEnv() {
            return tokenEnv;
        }

        public void setTokenEnv(String tokenEnv) {
            this.tokenEnv = tokenEnv;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getUser() {
            return user;
        }

        public void setUser(String user) {
            this.user = user;
        }

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }

        public String getJql() {
            return jql;
        }

        public void setJql(String jql) {
            this.jql = jql;
        }

        public int getMaxIssues() {
            return maxIssues;
        }

        public void setMaxIssues(int maxIssues) {
            this.maxIssues = maxIssues;
        }

        public List<String> getFields() {
            return fields == null ? List.of() : List.copyOf(fields);
        }

        public void setFields(List<String> fields) {
            this.fields = fields != null ? new ArrayList<>(fields) : new ArrayList<>();
        }

        public String resolveBaseUrl() {
            return firstNonBlank(baseUrl, env(baseUrlEnv));
        }

        public String resolveUser() {
            return firstNonBlank(user, env(userEnv));
        }

        public String resolveToken() {
            return firstNonBlank(token, env(tokenEnv));
        }

        private static String env(String name) {
            if (name == null || name.isBlank()) {
                return null;
            }
            return System.getenv(name.trim());
        }

        private static String firstNonBlank(String a, String b) {
            if (a != null && !a.isBlank()) {
                return a.trim();
            }
            if (b != null && !b.isBlank()) {
                return b.trim();
            }
            return null;
        }
    }

    public static final class GenerationConfig {
        private String outputSubdir = "scenario";
        private boolean writeScenarioJson = true;
        private boolean mapToOpenApiOperations = true;
        private boolean failOnUnmappedSteps = true;

        public String getOutputSubdir() {
            return outputSubdir;
        }

        public void setOutputSubdir(String outputSubdir) {
            this.outputSubdir = outputSubdir;
        }

        public boolean isWriteScenarioJson() {
            return writeScenarioJson;
        }

        public void setWriteScenarioJson(boolean writeScenarioJson) {
            this.writeScenarioJson = writeScenarioJson;
        }

        public boolean isMapToOpenApiOperations() {
            return mapToOpenApiOperations;
        }

        public void setMapToOpenApiOperations(boolean mapToOpenApiOperations) {
            this.mapToOpenApiOperations = mapToOpenApiOperations;
        }

        public boolean isFailOnUnmappedSteps() {
            return failOnUnmappedSteps;
        }

        public void setFailOnUnmappedSteps(boolean failOnUnmappedSteps) {
            this.failOnUnmappedSteps = failOnUnmappedSteps;
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final AiScenarioConfig config = new AiScenarioConfig();

        public Builder enabled(boolean enabled) {
            config.setEnabled(enabled);
            return this;
        }

        public Builder activeModel(String activeModel) {
            config.setActiveModel(activeModel);
            return this;
        }

        public Builder models(Map<String, ModelConfig> models) {
            config.setModels(models);
            return this;
        }

        public Builder scenarioSource(ScenarioSourceConfig scenarioSource) {
            config.setScenarioSource(scenarioSource);
            return this;
        }

        public Builder generation(GenerationConfig generation) {
            config.setGeneration(generation);
            return this;
        }

        public AiScenarioConfig build() {
            Objects.requireNonNull(config.getScenarioSource(), "scenarioSource");
            Objects.requireNonNull(config.getGeneration(), "generation");
            return config;
        }
    }
}
