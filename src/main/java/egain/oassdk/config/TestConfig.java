package egain.oassdk.config;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration for test generation
 */
public class TestConfig {

    private boolean unitTests;
    private boolean integrationTests;
    private boolean nfrTests;
    private boolean performanceTests;
    private boolean securityTests;
    private boolean scalabilityTests;
    private boolean reliabilityTests;
    private boolean complianceTests;
    private String testFramework;
    private String language;  // Language for test generation (java, python, nodejs)
    private String framework;  // Framework for test generation (junit5, pytest, jest)
    private boolean mockData;
    private boolean testUtilities;
    /** When true (default), Playwright API tests are included in test generation (eGain-oriented). */
    private boolean playwrightTests;
    private Map<String, Object> additionalProperties;

    /**
     * Default constructor
     */
    public TestConfig() {
        this.unitTests = true;
        this.integrationTests = true;
        this.nfrTests = true;
        this.performanceTests = true;
        this.securityTests = true;
        this.scalabilityTests = true;
        this.reliabilityTests = true;
        this.complianceTests = true;
        this.testFramework = "junit5";
        this.language = "java";
        this.framework = "junit5";
        this.mockData = true;
        this.testUtilities = true;
        this.playwrightTests = true;
        this.additionalProperties = new HashMap<>();
    }

    /**
     * Constructor with parameters
     */
    public TestConfig(boolean unitTests, boolean integrationTests, boolean nfrTests,
                      boolean performanceTests, boolean securityTests, boolean scalabilityTests,
                      boolean reliabilityTests, boolean complianceTests, String testFramework,
                      String language, String framework,
                      boolean mockData, boolean testUtilities, boolean playwrightTests,
                      Map<String, Object> additionalProperties) {
        this.unitTests = unitTests;
        this.integrationTests = integrationTests;
        this.nfrTests = nfrTests;
        this.performanceTests = performanceTests;
        this.securityTests = securityTests;
        this.scalabilityTests = scalabilityTests;
        this.reliabilityTests = reliabilityTests;
        this.complianceTests = complianceTests;
        this.testFramework = testFramework;
        this.language = language;
        this.framework = framework;
        this.mockData = mockData;
        this.testUtilities = testUtilities;
        this.playwrightTests = playwrightTests;
        this.additionalProperties = additionalProperties != null ? additionalProperties : new HashMap<>();
    }

    /**
     * Legacy constructor without {@code playwrightTests} (defaults to {@code true}).
     */
    public TestConfig(boolean unitTests, boolean integrationTests, boolean nfrTests,
                      boolean performanceTests, boolean securityTests, boolean scalabilityTests,
                      boolean reliabilityTests, boolean complianceTests, String testFramework,
                      String language, String framework,
                      boolean mockData, boolean testUtilities, Map<String, Object> additionalProperties) {
        this(unitTests, integrationTests, nfrTests, performanceTests, securityTests, scalabilityTests,
                reliabilityTests, complianceTests, testFramework, language, framework,
                mockData, testUtilities, true, additionalProperties);
    }

    // Getters and Setters
    public boolean isUnitTests() {
        return unitTests;
    }

    public void setUnitTests(boolean unitTests) {
        this.unitTests = unitTests;
    }

    public boolean isIntegrationTests() {
        return integrationTests;
    }

    public void setIntegrationTests(boolean integrationTests) {
        this.integrationTests = integrationTests;
    }

    public boolean isNfrTests() {
        return nfrTests;
    }

    public void setNfrTests(boolean nfrTests) {
        this.nfrTests = nfrTests;
    }

    public boolean isPerformanceTests() {
        return performanceTests;
    }

    public void setPerformanceTests(boolean performanceTests) {
        this.performanceTests = performanceTests;
    }

    public boolean isSecurityTests() {
        return securityTests;
    }

    public void setSecurityTests(boolean securityTests) {
        this.securityTests = securityTests;
    }

    public boolean isScalabilityTests() {
        return scalabilityTests;
    }

    public void setScalabilityTests(boolean scalabilityTests) {
        this.scalabilityTests = scalabilityTests;
    }

    public boolean isReliabilityTests() {
        return reliabilityTests;
    }

    public void setReliabilityTests(boolean reliabilityTests) {
        this.reliabilityTests = reliabilityTests;
    }

    public boolean isComplianceTests() {
        return complianceTests;
    }

    public void setComplianceTests(boolean complianceTests) {
        this.complianceTests = complianceTests;
    }

    public String getTestFramework() {
        return testFramework;
    }

    public void setTestFramework(String testFramework) {
        this.testFramework = testFramework;
    }

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

    public boolean isMockData() {
        return mockData;
    }

    public void setMockData(boolean mockData) {
        this.mockData = mockData;
    }

    public boolean isTestUtilities() {
        return testUtilities;
    }

    public void setTestUtilities(boolean testUtilities) {
        this.testUtilities = testUtilities;
    }

    public boolean isPlaywrightTests() {
        return playwrightTests;
    }

    public void setPlaywrightTests(boolean playwrightTests) {
        this.playwrightTests = playwrightTests;
    }

    public Map<String, Object> getAdditionalProperties() {
        return additionalProperties != null ? new HashMap<>(additionalProperties) : null;
    }

    public void setAdditionalProperties(Map<String, Object> additionalProperties) {
        this.additionalProperties = additionalProperties != null ? new HashMap<>(additionalProperties) : null;
    }

    /**
     * Builder class for TestConfig
     */
    public static class Builder {
        private boolean unitTests = true;
        private boolean integrationTests = true;
        private boolean nfrTests = true;
        private boolean performanceTests = true;
        private boolean securityTests = true;
        private boolean scalabilityTests = true;
        private boolean reliabilityTests = true;
        private boolean complianceTests = true;
        private String testFramework = "junit5";
        private String language = "java";
        private String framework = "junit5";
        private boolean mockData = true;
        private boolean testUtilities = true;
        private boolean playwrightTests = true;
        private Map<String, Object> additionalProperties = new HashMap<>();

        public Builder unitTests(boolean unitTests) {
            this.unitTests = unitTests;
            return this;
        }

        public Builder integrationTests(boolean integrationTests) {
            this.integrationTests = integrationTests;
            return this;
        }

        public Builder nfrTests(boolean nfrTests) {
            this.nfrTests = nfrTests;
            return this;
        }

        public Builder performanceTests(boolean performanceTests) {
            this.performanceTests = performanceTests;
            return this;
        }

        public Builder securityTests(boolean securityTests) {
            this.securityTests = securityTests;
            return this;
        }

        public Builder scalabilityTests(boolean scalabilityTests) {
            this.scalabilityTests = scalabilityTests;
            return this;
        }

        public Builder reliabilityTests(boolean reliabilityTests) {
            this.reliabilityTests = reliabilityTests;
            return this;
        }

        public Builder complianceTests(boolean complianceTests) {
            this.complianceTests = complianceTests;
            return this;
        }

        public Builder testFramework(String testFramework) {
            this.testFramework = testFramework;
            return this;
        }

        public Builder language(String language) {
            this.language = language;
            return this;
        }

        public Builder framework(String framework) {
            this.framework = framework;
            return this;
        }

        public Builder mockData(boolean mockData) {
            this.mockData = mockData;
            return this;
        }

        public Builder testUtilities(boolean testUtilities) {
            this.testUtilities = testUtilities;
            return this;
        }

        public Builder playwrightTests(boolean playwrightTests) {
            this.playwrightTests = playwrightTests;
            return this;
        }

        public Builder additionalProperties(Map<String, Object> additionalProperties) {
            this.additionalProperties = additionalProperties != null ? new HashMap<>(additionalProperties) : null;
            return this;
        }

        public TestConfig build() {
            return new TestConfig(unitTests, integrationTests, nfrTests, performanceTests,
                    securityTests, scalabilityTests, reliabilityTests, complianceTests,
                    testFramework, language, framework, mockData, testUtilities, playwrightTests,
                    additionalProperties);
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
        return "TestConfig{" +
                "unitTests=" + unitTests +
                ", integrationTests=" + integrationTests +
                ", nfrTests=" + nfrTests +
                ", performanceTests=" + performanceTests +
                ", securityTests=" + securityTests +
                ", scalabilityTests=" + scalabilityTests +
                ", reliabilityTests=" + reliabilityTests +
                ", complianceTests=" + complianceTests +
                ", testFramework='" + testFramework + '\'' +
                ", language='" + language + '\'' +
                ", framework='" + framework + '\'' +
                ", mockData=" + mockData +
                ", testUtilities=" + testUtilities +
                ", playwrightTests=" + playwrightTests +
                ", additionalProperties=" + additionalProperties +
                '}';
    }
}
