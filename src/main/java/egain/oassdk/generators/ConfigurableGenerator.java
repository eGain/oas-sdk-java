package egain.oassdk.generators;

import egain.oassdk.config.GeneratorConfig;

/**
 * Interface for generators that can be configured.
 *
 * <p>Factory wiring seam: {@link GeneratorFactory#getGenerator(String, String, GeneratorConfig)}
 * detects {@code ConfigurableGenerator} implementations and calls {@link #setConfig(GeneratorConfig)}
 * before {@link CodeGenerator#generate}. Kept separate from {@link CodeGenerator} so stub or
 * stateless generators are not forced to carry configuration.
 */
public interface ConfigurableGenerator {

    /**
     * Set generator configuration
     *
     * @param config Generator configuration
     */
    void setConfig(GeneratorConfig config);

    /**
     * Get generator configuration
     *
     * @return Generator configuration
     */
    GeneratorConfig getConfig();
}
