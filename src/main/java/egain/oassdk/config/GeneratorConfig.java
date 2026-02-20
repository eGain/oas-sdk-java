package egain.oassdk.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration for code generation
 */
public class GeneratorConfig {

    private String language;
    private String framework;
    private String packageName;
    private String version;
    private String outputDir;
    private String templatesDir;
    private boolean customTemplates;
    private Map<String, Object> additionalProperties;

    // API filtering
    private List<String> includePaths;  // Paths to include (null = include all)
    private Map<String, List<String>> includeOperations;  // Operations to include per path (null = include all)

    // External file reference search paths
    private List<String> searchPaths;  // Additional paths to search for external file references

    // ZIP-based spec loading: when set, specs and $ref resolution are read from this ZIP (entry paths use forward slashes)
    private String specZipPath;

    /**
     * Default constructor
     */
    public GeneratorConfig() {
        this.language = "java";
        this.framework = "jersey";
        this.packageName = "com.example.api";
        this.version = "1.0.0";
        this.outputDir = "./generated";
        this.templatesDir = null;
        this.customTemplates = false;
        this.additionalProperties = new HashMap<>();
        this.includePaths = null;
        this.includeOperations = null;
        this.searchPaths = null;
        this.specZipPath = null;
    }

    /**
     * Constructor with parameters
     */
    public GeneratorConfig(String language, String framework, String packageName, String version,
                           String outputDir, String templatesDir, boolean customTemplates,
                           Map<String, Object> additionalProperties) {
        this.language = language;
        this.framework = framework;
        this.packageName = packageName;
        this.version = version;
        this.outputDir = outputDir;
        this.templatesDir = templatesDir;
        this.customTemplates = customTemplates;
        this.additionalProperties = additionalProperties != null ? additionalProperties : new HashMap<>();
        this.includePaths = null;
        this.includeOperations = null;
        this.searchPaths = null;
        this.specZipPath = null;
    }

    // Getters and Setters
    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getFramework() {
        return framework;
    }

    public void setFramework(String framework) {
        this.framework = framework;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getOutputDir() {
        return outputDir;
    }

    public void setOutputDir(String outputDir) {
        this.outputDir = outputDir;
    }

    public String getTemplatesDir() {
        return templatesDir;
    }

    public void setTemplatesDir(String templatesDir) {
        this.templatesDir = templatesDir;
    }

    public boolean isCustomTemplates() {
        return customTemplates;
    }

    public void setCustomTemplates(boolean customTemplates) {
        this.customTemplates = customTemplates;
    }

    public Map<String, Object> getAdditionalProperties() {
        return additionalProperties != null ? new HashMap<>(additionalProperties) : null;
    }

    public void setAdditionalProperties(Map<String, Object> additionalProperties) {
        this.additionalProperties = additionalProperties != null ? new HashMap<>(additionalProperties) : null;
    }

    public List<String> getIncludePaths() {
        return includePaths != null ? new ArrayList<>(includePaths) : null;
    }

    public void setIncludePaths(List<String> includePaths) {
        this.includePaths = includePaths != null ? new ArrayList<>(includePaths) : null;
    }

    public Map<String, List<String>> getIncludeOperations() {
        if (includeOperations == null) {
            return null;
        }
        Map<String, List<String>> copy = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : includeOperations.entrySet()) {
            copy.put(entry.getKey(), entry.getValue() != null ? new ArrayList<>(entry.getValue()) : null);
        }
        return copy;
    }

    public void setIncludeOperations(Map<String, List<String>> includeOperations) {
        if (includeOperations == null) {
            this.includeOperations = null;
            return;
        }
        this.includeOperations = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : includeOperations.entrySet()) {
            this.includeOperations.put(entry.getKey(), entry.getValue() != null ? new ArrayList<>(entry.getValue()) : null);
        }
    }

    public List<String> getSearchPaths() {
        return searchPaths != null ? new ArrayList<>(searchPaths) : null;
    }

    public void setSearchPaths(List<String> searchPaths) {
        this.searchPaths = searchPaths != null ? new ArrayList<>(searchPaths) : null;
    }

    /**
     * Path to a ZIP file containing OpenAPI YAML/JSON specs. When set, loadSpec() expects entry paths
     * inside the ZIP (e.g. published/core/infomgr/v4/api.yaml); all $ref resolution stays inside the ZIP.
     * Avoids path/separator differences between Windows and Mac.
     */
    public String getSpecZipPath() {
        return specZipPath;
    }

    public void setSpecZipPath(String specZipPath) {
        this.specZipPath = specZipPath;
    }

    /**
     * Builder class for GeneratorConfig
     */
    public static class Builder {
        private String language = "java";
        private String framework = "jersey";
        private String packageName = "com.example.api";
        private String version = "1.0.0";
        private String outputDir = "./generated";
        private String templatesDir = null;
        private boolean customTemplates = false;
        private Map<String, Object> additionalProperties = new HashMap<>();
        private List<String> includePaths = null;
        private Map<String, List<String>> includeOperations = null;
        private List<String> searchPaths = null;
        private String specZipPath = null;

        public Builder language(String language) {
            this.language = language;
            return this;
        }

        public Builder framework(String framework) {
            this.framework = framework;
            return this;
        }

        public Builder packageName(String packageName) {
            this.packageName = packageName;
            return this;
        }

        public Builder version(String version) {
            this.version = version;
            return this;
        }

        public Builder outputDir(String outputDir) {
            this.outputDir = outputDir;
            return this;
        }

        public Builder templatesDir(String templatesDir) {
            this.templatesDir = templatesDir;
            return this;
        }

        public Builder customTemplates(boolean customTemplates) {
            this.customTemplates = customTemplates;
            return this;
        }

        public Builder additionalProperties(Map<String, Object> additionalProperties) {
            this.additionalProperties = additionalProperties != null ? new HashMap<>(additionalProperties) : null;
            return this;
        }

        public Builder includePaths(List<String> includePaths) {
            this.includePaths = includePaths != null ? new ArrayList<>(includePaths) : null;
            return this;
        }

        public Builder includeOperations(Map<String, List<String>> includeOperations) {
            if (includeOperations == null) {
                this.includeOperations = null;
                return this;
            }
            this.includeOperations = new HashMap<>();
            for (Map.Entry<String, List<String>> entry : includeOperations.entrySet()) {
                this.includeOperations.put(entry.getKey(), entry.getValue() != null ? new ArrayList<>(entry.getValue()) : null);
            }
            return this;
        }

        public Builder searchPaths(List<String> searchPaths) {
            this.searchPaths = searchPaths != null ? new ArrayList<>(searchPaths) : null;
            return this;
        }

        public Builder specZipPath(String specZipPath) {
            this.specZipPath = specZipPath;
            return this;
        }

        public GeneratorConfig build() {
            GeneratorConfig config = new GeneratorConfig(language, framework, packageName, version,
                    outputDir, templatesDir, customTemplates, additionalProperties);
            config.setIncludePaths(includePaths);
            config.setIncludeOperations(includeOperations);
            config.setSearchPaths(searchPaths);
            config.setSpecZipPath(specZipPath);
            return config;
        }
    }

    /**
     * Create a new builder
     *
     * @return Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String toString() {
        return "GeneratorConfig{" +
                "language='" + language + '\'' +
                ", framework='" + framework + '\'' +
                ", packageName='" + packageName + '\'' +
                ", version='" + version + '\'' +
                ", outputDir='" + outputDir + '\'' +
                ", templatesDir='" + templatesDir + '\'' +
                ", customTemplates=" + customTemplates +
                ", additionalProperties=" + additionalProperties +
                ", includePaths=" + includePaths +
                ", includeOperations=" + includeOperations +
                ", searchPaths=" + searchPaths +
                ", specZipPath=" + specZipPath +
                '}';
    }
}
