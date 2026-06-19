package egain.oassdk.testgenerators;

import egain.oassdk.config.TestConfig;

/**
 * Interface for test generators that can be configured.
 *
 * <p>Factory wiring seam: {@link egain.oassdk.testgenerators.TestGeneratorFactory} detects
 * {@code ConfigurableTestGenerator} implementations and calls {@link #setConfig(TestConfig)}
 * before {@link egain.oassdk.testgenerators.TestGenerator#generate}.
 */
public interface ConfigurableTestGenerator {

    /**
     * Set test configuration
     *
     * @param config Test configuration
     */
    void setConfig(TestConfig config);

    /**
     * Get test configuration
     *
     * @return Test configuration
     */
    TestConfig getConfig();
}
