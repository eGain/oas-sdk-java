package egain.oassdk.testgenerators;

import egain.oassdk.config.TestConfig;
import egain.oassdk.testgenerators.integration.IntegrationTestGenerator;
import egain.oassdk.testgenerators.mock.MockDataGenerator;
import egain.oassdk.testgenerators.nfr.NFRTestGenerator;
import egain.oassdk.testgenerators.nodejs.JestIntegrationTestGenerator;
import egain.oassdk.testgenerators.nodejs.JestUnitTestGenerator;
import egain.oassdk.testgenerators.performance.PerformanceTestGenerator;
import egain.oassdk.testgenerators.postman.PostmanTestGenerator;
import egain.oassdk.testgenerators.python.PytestIntegrationTestGenerator;
import egain.oassdk.testgenerators.python.PytestUnitTestGenerator;
import egain.oassdk.testgenerators.schemathesis.SchemathesisTestGenerator;
import egain.oassdk.testgenerators.security.SecurityTestGenerator;
import egain.oassdk.testgenerators.sequence.SequenceChainTestGenerator;
import egain.oassdk.testgenerators.unit.UnitTestGenerator;

import java.util.Locale;


/**
 * Factory for creating test generators based on language, framework, and test type
 */
public class TestGeneratorFactory {

    /**
     * Get test generator for specific test type with language/framework awareness
     *
     * @param testType Type of test (unit, integration, nfr, performance, security, postman, schemathesis, sequence, mock_data)
     * @param config   Test configuration containing language and framework information
     * @return Test generator instance
     * @throws IllegalArgumentException if test type or language/framework combination is not supported
     */
    public TestGenerator getGenerator(String testType, TestConfig config) {
        // Determine language and framework from config
        String language = config != null && config.getLanguage() != null ? config.getLanguage().toLowerCase(Locale.ROOT) : "java";
        String framework = config != null && config.getFramework() != null ? config.getFramework().toLowerCase(Locale.ROOT) : "junit5";

        // Route based on test type and language
        String testTypeLower = testType.toLowerCase(Locale.ROOT);

        TestGenerator generator;
        switch (testTypeLower) {
            case "unit":
                generator = getUnitTestGenerator(language, framework, config);
                break;

            case "integration":
                generator = getIntegrationTestGenerator(language, framework, config);
                break;

            case "nfr":
                generator = new NFRTestGenerator();
                break;

            case "performance":
                generator = new PerformanceTestGenerator();
                break;

            case "security":
                generator = new SecurityTestGenerator();
                break;

            case "postman":
                generator = new PostmanTestGenerator();
                break;

            case "schemathesis":
                generator = new SchemathesisTestGenerator();
                break;

            case "sequence":
                generator = new SequenceChainTestGenerator();
                break;

            case "mock_data":
            case "mockdata":
                generator = new MockDataGenerator();
                break;

            default:
                throw new IllegalArgumentException("Unsupported test type: " + testType);
        }

        // Set config if generator supports it
        if (generator instanceof ConfigurableTestGenerator && config != null) {
            ((ConfigurableTestGenerator) generator).setConfig(config);
        }

        return generator;
    }

    /**
     * Get unit test generator based on language and framework
     */
    private TestGenerator getUnitTestGenerator(String language, String framework, TestConfig config) {
        switch (language) {
            case "java":
                return new UnitTestGenerator();

            case "python":
                return new PytestUnitTestGenerator();

            case "nodejs":
            case "javascript":
                return new JestUnitTestGenerator();

            default:
                throw new IllegalArgumentException("Unsupported language for unit tests: " + language);
        }
    }

    /**
     * Get integration test generator based on language and framework
     */
    private TestGenerator getIntegrationTestGenerator(String language, String framework, TestConfig config) {
        switch (language) {
            case "java":
                return new IntegrationTestGenerator();

            case "python":
                return new PytestIntegrationTestGenerator();

            case "nodejs":
            case "javascript":
                return new JestIntegrationTestGenerator();

            default:
                throw new IllegalArgumentException("Unsupported language for integration tests: " + language);
        }
    }

    /**
     * Get test generator for specific test type (legacy method for backwards compatibility)
     *
     * @param testType Type of test (unit, integration, nfr, performance, security, postman, schemathesis, sequence, mock_data)
     * @return Test generator instance
     * @throws IllegalArgumentException if test type is not supported
     */
    public TestGenerator getGenerator(String testType) {
        // Use default Java/JUnit5 for backwards compatibility
        TestConfig defaultConfig = TestConfig.builder()
                .language("java")
                .framework("junit5")
                .build();
        return getGenerator(testType, defaultConfig);
    }

    /**
     * Get list of supported test types
     *
     * @return Array of supported test types
     */
    public String[] getSupportedTestTypes() {
        return new String[]{
                "unit",
                "integration",
                "nfr",
                "performance",
                "security",
                "postman",
                "schemathesis",
                "sequence",
                "mock_data"
        };
    }

    /**
     * Check if test type is supported
     *
     * @param testType Test type
     * @return true if supported
     */
    public boolean isSupported(String testType) {
        try {
            getGenerator(testType);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
