package egain.oassdk.generators.python;

import egain.oassdk.config.GeneratorConfig;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Shared mutable state for Python code generation (FastAPI and Flask).
 */
public final class PythonGenerationContext {

    private final Map<String, Object> spec;
    private GeneratorConfig config;
    private final Map<Object, String> inlinedSchemas = new IdentityHashMap<>();
    private Map<String, Map<String, Object>> securitySchemes = Map.of();

    public PythonGenerationContext(Map<String, Object> spec, GeneratorConfig config) {
        this.spec = spec;
        this.config = config;
    }

    public Map<String, Object> getSpec() {
        return spec;
    }

    public GeneratorConfig getConfig() {
        return config;
    }

    public void setConfig(GeneratorConfig config) {
        this.config = config;
    }

    public Map<Object, String> getInlinedSchemas() {
        return inlinedSchemas;
    }

    public Map<String, Map<String, Object>> getSecuritySchemes() {
        return securitySchemes;
    }

    public void setSecuritySchemes(Map<String, Map<String, Object>> securitySchemes) {
        this.securitySchemes = securitySchemes != null ? securitySchemes : Map.of();
    }
}
