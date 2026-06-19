package egain.oassdk.generators.csharp;

import egain.oassdk.config.GeneratorConfig;
import egain.oassdk.core.exceptions.GenerationException;
import egain.oassdk.generators.CodeGenerator;
import egain.oassdk.generators.ConfigurableGenerator;

import java.util.Map;

/**
 * ASP.NET Core code generator
 */
public class ASPNETGenerator implements CodeGenerator, ConfigurableGenerator {

    private GeneratorConfig config;

    @Override
    public void generate(Map<String, Object> spec, String outputDir, GeneratorConfig config, String packageName) throws GenerationException {
        this.config = config;
        // ASP.NET Core code generation is not yet implemented
        // This generator would create C# ASP.NET Core applications from OpenAPI specifications
        throw new GenerationException("ASP.NET Core generator not yet implemented");
    }

    @Override
    public String getName() {
        return "ASP.NET Core Generator";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getLanguage() {
        return "csharp";
    }

    @Override
    public String getFramework() {
        return "aspnet";
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
