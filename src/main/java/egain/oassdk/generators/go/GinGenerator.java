package egain.oassdk.generators.go;

import egain.oassdk.config.GeneratorConfig;
import egain.oassdk.core.exceptions.GenerationException;
import egain.oassdk.generators.CodeGenerator;
import egain.oassdk.generators.ConfigurableGenerator;

import java.util.Map;

/**
 * Gin framework code generator
 */
public class GinGenerator implements CodeGenerator, ConfigurableGenerator {

    private GeneratorConfig config;

    @Override
    public void generate(Map<String, Object> spec, String outputDir, GeneratorConfig config, String packageName) throws GenerationException {
        this.config = config;
        // Gin code generation is not yet implemented
        // This generator would create Go Gin applications from OpenAPI specifications
        throw new GenerationException("Gin generator not yet implemented");
    }

    @Override
    public String getName() {
        return "Gin Generator";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getLanguage() {
        return "go";
    }

    @Override
    public String getFramework() {
        return "gin";
    }

    @Override
    public boolean isImplemented() {
        return false;
    }

    @Override
    public void setConfig(GeneratorConfig config) {
        this.config = config;
    }

    @Override
    public GeneratorConfig getConfig() {
        // Return a defensive copy if config is mutable
        // Since GeneratorConfig is designed to be immutable, returning reference is acceptable
        return this.config;
    }
}
