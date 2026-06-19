package egain.oassdk.generators;

import egain.oassdk.config.GeneratorConfig;
import egain.oassdk.core.exceptions.GenerationException;

import java.util.Map;

/**
 * Interface for code generators
 */
public interface CodeGenerator {

    /**
     * Generate code from OpenAPI specification
     *
     * @param spec        Parsed OpenAPI specification
     * @param outputDir   Output directory for generated code
     * @param config      Generator configuration
     * @param packageName Package/namespace name
     * @throws GenerationException if generation fails
     */
    void generate(Map<String, Object> spec, String outputDir, GeneratorConfig config, String packageName) throws GenerationException;

    /**
     * Generate code from OpenAPI specification with default package name
     *
     * @param spec      Parsed OpenAPI specification
     * @param outputDir Output directory for generated code
     * @param config    Generator configuration
     * @throws GenerationException if generation fails
     */
    default void generate(Map<String, Object> spec, String outputDir, GeneratorConfig config) throws GenerationException {
        generate(spec, outputDir, config, null);
    }

    /**
     * Get generator name
     *
     * @return Generator name
     */
    String getName();

    /**
     * Get generator version
     *
     * @return Generator version
     */
    String getVersion();

    /**
     * Get supported language
     *
     * @return Programming language
     */
    String getLanguage();

    /**
     * Get supported framework
     *
     * @return Framework name
     */
    String getFramework();

    /**
     * Whether this generator produces code today. Stub generators return {@code false} and remain
     * discoverable via {@link GeneratorFactory} but are rejected by {@code generate} / {@code all}.
     *
     * @return {@code true} when {@link #generate} is fully implemented
     */
    default boolean isImplemented() {
        return true;
    }
}
