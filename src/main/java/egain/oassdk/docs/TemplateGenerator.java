package egain.oassdk.docs;

import egain.oassdk.Util;
import egain.oassdk.core.exceptions.GenerationException;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates documentation using FreeMarker templates
 * <p>
 * This class replaces hardcoded documentation strings with template-based generation
 * using FreeMarker for maintainable, customizable documentation.
 */
public class TemplateGenerator {

    private final Configuration freemarkerConfig;

    public TemplateGenerator() {
        this.freemarkerConfig = new Configuration(Configuration.VERSION_2_3_32);
        this.freemarkerConfig.setClassForTemplateLoading(TemplateGenerator.class, "/templates");
        this.freemarkerConfig.setDefaultEncoding("UTF-8");
    }

    /**
     * Generate test documentation from template
     */
    public void generateTestDocumentation(Map<String, Object> spec, String outputDir, TestDocConfig config) throws GenerationException {
        if (outputDir == null) {
            throw new IllegalArgumentException("Output directory cannot be null");
        }
        try {
            Files.createDirectories(Paths.get(outputDir));

            Map<String, Object> dataModel = createTestDocumentationDataModel(spec, config);
            String content = processTemplate("test-documentation.ftl", dataModel);
            Files.write(Paths.get(outputDir, "TEST_DOCUMENTATION.md"), content.getBytes(StandardCharsets.UTF_8));

        } catch (IOException | TemplateException e) {
            throw new GenerationException("Failed to generate test documentation: " + e.getMessage(), e);
        }
    }

    /**
     * Generate project documentation from template
     */
    public void generateProjectDocumentation(Map<String, Object> spec, String outputDir, ProjectDocConfig config) throws GenerationException {
        if (outputDir == null) {
            throw new IllegalArgumentException("Output directory cannot be null");
        }
        try {
            Files.createDirectories(Paths.get(outputDir));

            Map<String, Object> dataModel = createProjectDocumentationDataModel(spec, config);
            String content = processTemplate("project-documentation.ftl", dataModel);
            Files.write(Paths.get(outputDir, "PROJECT_DOCUMENTATION.md"), content.getBytes(StandardCharsets.UTF_8));

        } catch (IOException | TemplateException e) {
            throw new GenerationException("Failed to generate project documentation: " + e.getMessage(), e);
        }
    }

    /**
     * Create data model for test documentation
     */
    private Map<String, Object> createTestDocumentationDataModel(Map<String, Object> spec, TestDocConfig config) {
        Map<String, Object> dataModel = new HashMap<>();

        // API information
        Map<String, Object> info = egain.oassdk.Util.asStringObjectMap(spec.get("info"));
        dataModel.put("apiTitle", info != null ? info.get("title") : "API");
        dataModel.put("apiVersion", info != null ? info.get("version") : "1.0.0");
        dataModel.put("apiDescription", info != null ? info.get("description") : "Generated API");

        // Test configuration
        dataModel.put("testConfig", config);

        // Test types
        List<Map<String, Object>> testTypes = new ArrayList<>();

        Map<String, Object> unitTests = new HashMap<>();
        unitTests.put("name", "Unit Tests");
        unitTests.put("purpose", "Test individual components in isolation");
        unitTests.put("location", "src/test/java/");
        unitTests.put("framework", "JUnit 5");
        unitTests.put("coverage", "Controllers, Services, Models");
        unitTests.put("enabled", config.isUnitTestsEnabled());
        testTypes.add(unitTests);

        Map<String, Object> integrationTests = new HashMap<>();
        integrationTests.put("name", "Integration Tests");
        integrationTests.put("purpose", "Test component interactions");
        integrationTests.put("location", "src/test/java/integration/");
        integrationTests.put("framework", "Jersey Test");
        integrationTests.put("coverage", "API endpoints, Database interactions");
        integrationTests.put("enabled", config.isIntegrationTestsEnabled());
        testTypes.add(integrationTests);

        Map<String, Object> nfrTests = new HashMap<>();
        nfrTests.put("name", "NFR Tests");
        nfrTests.put("purpose", "Test non-functional requirements");
        nfrTests.put("location", "src/test/java/nfr/");
        nfrTests.put("framework", "Custom + JUnit 5");
        nfrTests.put("coverage", "Performance, Scalability, Reliability");
        nfrTests.put("enabled", config.isNfrTestsEnabled());
        testTypes.add(nfrTests);

        Map<String, Object> performanceTests = new HashMap<>();
        performanceTests.put("name", "Performance Tests");
        performanceTests.put("purpose", "Test system performance under load");
        performanceTests.put("location", "src/test/java/performance/");
        performanceTests.put("framework", "JMeter, Gatling");
        performanceTests.put("coverage", "Response time, Throughput, Resource usage");
        performanceTests.put("enabled", config.isPerformanceTestsEnabled());
        testTypes.add(performanceTests);

        Map<String, Object> securityTests = new HashMap<>();
        securityTests.put("name", "Security Tests");
        securityTests.put("purpose", "Test security vulnerabilities");
        securityTests.put("location", "src/test/java/security/");
        securityTests.put("framework", "OWASP ZAP, Custom");
        securityTests.put("coverage", "Authentication, Authorization, Input validation");
        securityTests.put("enabled", config.isSecurityTestsEnabled());
        testTypes.add(securityTests);

        Map<String, Object> schemathesisTests = new HashMap<>();
        schemathesisTests.put("name", "Schemathesis");
        schemathesisTests.put("purpose", "Property-based contract testing against a running API (st CLI)");
        schemathesisTests.put("location", "schemathesis/ (generated bundle next to other test outputs)");
        schemathesisTests.put("framework", "Schemathesis (st)");
        schemathesisTests.put("coverage", "OpenAPI conformance, status codes, headers, schema coverage");
        schemathesisTests.put("enabled", true);
        testTypes.add(schemathesisTests);

        dataModel.put("testTypes", testTypes);

        // Test commands
        List<String> testCommands = new ArrayList<>();
        testCommands.add("mvn test");
        testCommands.add("mvn test -Dtest=*UnitTest");
        testCommands.add("mvn test -Dtest=*IntegrationTest");
        testCommands.add("mvn test -Dtest=*NFRTest");
        testCommands.add("mvn test -Dtest=*PerformanceTest");
        testCommands.add("mvn test -Dtest=*SecurityTest");
        testCommands.add("# Schemathesis (after: oas-sdk tests <spec> -t schemathesis -o <dir>)");
        testCommands.add("cd schemathesis && chmod +x run-schemathesis.sh && ./run-schemathesis.sh");
        dataModel.put("testCommands", testCommands);

        // Mock data info
        dataModel.put("mockDataLocation", "src/test/resources/mock-data/");
        dataModel.put("postmanCollection", "API.postman_collection.json");
        dataModel.put("postmanEnvironment", "API-Environment.postman_environment.json");

        return dataModel;
    }

    /**
     * Create data model for project documentation
     */
    private Map<String, Object> createProjectDocumentationDataModel(Map<String, Object> spec, ProjectDocConfig config) {
        Map<String, Object> dataModel = new HashMap<>();

        // API information
        Map<String, Object> info = egain.oassdk.Util.asStringObjectMap(spec.get("info"));
        dataModel.put("apiTitle", info != null ? info.get("title") : "API");
        dataModel.put("apiVersion", info != null ? info.get("version") : "1.0.0");
        dataModel.put("apiDescription", info != null ? info.get("description") : "Generated API");

        // Project configuration
        dataModel.put("projectConfig", config);

        // Project features
        List<String> features = new ArrayList<>();
        features.add("REST API implementation");
        features.add("Comprehensive test suite");
        features.add("SLA enforcement");
        features.add("Monitoring and observability");
        features.add("Documentation");
        dataModel.put("features", features);

        // Prerequisites
        List<String> prerequisites = new ArrayList<>();
        prerequisites.add("Java 21 or higher");
        prerequisites.add("Maven 3.6 or higher");
        prerequisites.add("Docker (optional)");
        dataModel.put("prerequisites", prerequisites);

        // Installation steps
        List<String> installationSteps = new ArrayList<>();
        installationSteps.add("Clone the repository");
        installationSteps.add("Run `mvn clean install`");
        installationSteps.add("Start the application: `mvn jersey:run`");
        dataModel.put("installationSteps", installationSteps);

        // Configuration files
        List<String> configFiles = new ArrayList<>();
        configFiles.add("src/main/resources/application.yml");
        configFiles.add("src/main/resources/sla.yml");
        dataModel.put("configFiles", configFiles);

        // API endpoints
        List<String> endpoints = new ArrayList<>();
        Map<String, Object> paths = Util.asStringObjectMap(spec.get("paths"));
        if (paths != null) {
            endpoints.addAll(paths.keySet());
        }
        dataModel.put("endpoints", endpoints);

        // Monitoring endpoints
        List<Map<String, String>> monitoringEndpoints = new ArrayList<>();
        Map<String, String> healthCheck = new HashMap<>();
        healthCheck.put("endpoint", "/actuator/health");
        healthCheck.put("description", "Health checks");
        monitoringEndpoints.add(healthCheck);

        Map<String, String> metrics = new HashMap<>();
        metrics.put("endpoint", "/actuator/metrics");
        metrics.put("description", "Metrics");
        monitoringEndpoints.add(metrics);

        Map<String, String> prometheus = new HashMap<>();
        prometheus.put("endpoint", "/actuator/prometheus");
        prometheus.put("description", "Prometheus");
        monitoringEndpoints.add(prometheus);

        dataModel.put("monitoringEndpoints", monitoringEndpoints);

        // SLA enforcement features
        List<String> slaFeatures = new ArrayList<>();
        slaFeatures.add("Rate limiting");
        slaFeatures.add("Response time monitoring");
        slaFeatures.add("Error rate tracking");
        slaFeatures.add("Availability monitoring");
        dataModel.put("slaFeatures", slaFeatures);

        // Docker commands
        List<String> dockerCommands = new ArrayList<>();
        dockerCommands.add("docker build -t api-service .");
        dockerCommands.add("docker run -p 8080:8080 api-service");
        dockerCommands.add("docker-compose up -d");
        dataModel.put("dockerCommands", dockerCommands);

        return dataModel;
    }

    /**
     * Process a FreeMarker template
     */
    private String processTemplate(String templateName, Map<String, Object> dataModel) throws IOException, TemplateException {
        Template template = freemarkerConfig.getTemplate(templateName);
        StringWriter writer = new StringWriter();
        template.process(dataModel, writer);
        return writer.toString();
    }

    /**
     * Test documentation configuration
     */
    public static class TestDocConfig {
        private boolean unitTestsEnabled = true;
        private boolean integrationTestsEnabled = true;
        private boolean nfrTestsEnabled = true;
        private boolean performanceTestsEnabled = true;
        private boolean securityTestsEnabled = true;
        private String testDataLocation = "src/test/resources/mock-data/";
        private String postmanCollection = "API.postman_collection.json";
        private String postmanEnvironment = "API-Environment.postman_environment.json";

        // Getters and setters
        public boolean isUnitTestsEnabled() {
            return unitTestsEnabled;
        }

        public void setUnitTestsEnabled(boolean unitTestsEnabled) {
            this.unitTestsEnabled = unitTestsEnabled;
        }

        public boolean isIntegrationTestsEnabled() {
            return integrationTestsEnabled;
        }

        public void setIntegrationTestsEnabled(boolean integrationTestsEnabled) {
            this.integrationTestsEnabled = integrationTestsEnabled;
        }

        public boolean isNfrTestsEnabled() {
            return nfrTestsEnabled;
        }

        public void setNfrTestsEnabled(boolean nfrTestsEnabled) {
            this.nfrTestsEnabled = nfrTestsEnabled;
        }

        public boolean isPerformanceTestsEnabled() {
            return performanceTestsEnabled;
        }

        public void setPerformanceTestsEnabled(boolean performanceTestsEnabled) {
            this.performanceTestsEnabled = performanceTestsEnabled;
        }

        public boolean isSecurityTestsEnabled() {
            return securityTestsEnabled;
        }

        public void setSecurityTestsEnabled(boolean securityTestsEnabled) {
            this.securityTestsEnabled = securityTestsEnabled;
        }

        public String getTestDataLocation() {
            return testDataLocation;
        }

        public void setTestDataLocation(String testDataLocation) {
            this.testDataLocation = testDataLocation;
        }

        public String getPostmanCollection() {
            return postmanCollection;
        }

        public void setPostmanCollection(String postmanCollection) {
            this.postmanCollection = postmanCollection;
        }

        public String getPostmanEnvironment() {
            return postmanEnvironment;
        }

        public void setPostmanEnvironment(String postmanEnvironment) {
            this.postmanEnvironment = postmanEnvironment;
        }
    }

    /**
     * Project documentation configuration
     */
    public static class ProjectDocConfig {
        private String projectName = "Generated API Project";
        private String projectDescription = "API project generated by OAS SDK";
        private String javaVersion = "21";
        private String mavenVersion = "3.6";
        private String dockerImage = "api-service";
        private String dockerPort = "8080";
        private String contactEmail = "api@example.com";
        private String license = "MIT";
        private String licenseUrl = "https://opensource.org/licenses/MIT";

        // Getters and setters
        public String getProjectName() {
            return projectName;
        }

        public void setProjectName(String projectName) {
            this.projectName = projectName;
        }

        public String getProjectDescription() {
            return projectDescription;
        }

        public void setProjectDescription(String projectDescription) {
            this.projectDescription = projectDescription;
        }

        public String getJavaVersion() {
            return javaVersion;
        }

        public void setJavaVersion(String javaVersion) {
            this.javaVersion = javaVersion;
        }

        public String getMavenVersion() {
            return mavenVersion;
        }

        public void setMavenVersion(String mavenVersion) {
            this.mavenVersion = mavenVersion;
        }

        public String getDockerImage() {
            return dockerImage;
        }

        public void setDockerImage(String dockerImage) {
            this.dockerImage = dockerImage;
        }

        public String getDockerPort() {
            return dockerPort;
        }

        public void setDockerPort(String dockerPort) {
            this.dockerPort = dockerPort;
        }

        public String getContactEmail() {
            return contactEmail;
        }

        public void setContactEmail(String contactEmail) {
            this.contactEmail = contactEmail;
        }

        public String getLicense() {
            return license;
        }

        public void setLicense(String license) {
            this.license = license;
        }

        public String getLicenseUrl() {
            return licenseUrl;
        }

        public void setLicenseUrl(String licenseUrl) {
            this.licenseUrl = licenseUrl;
        }
    }
}
