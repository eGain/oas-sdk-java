package egain.oassdk.testgenerators.nfr;

import egain.oassdk.Util;
import egain.oassdk.config.TestConfig;
import egain.oassdk.core.exceptions.GenerationException;
import egain.oassdk.testgenerators.common.TestCodegenSupport;
import egain.oassdk.testgenerators.common.TestMavenSupport;
import egain.oassdk.testgenerators.common.TestOutputLayout;
import egain.oassdk.testgenerators.common.TestSpecUtils;
import egain.oassdk.testgenerators.ConfigurableTestGenerator;
import egain.oassdk.testgenerators.TestGenerator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

/**
 * Non-Functional Requirements (NFR) test generator.
 * Emits JUnit 5 + RestAssured tests for performance, scalability, reliability, and availability
 * (concurrent load, response time and success rate, health-style checks).
 */
public class NFRTestGenerator implements TestGenerator, ConfigurableTestGenerator {

    private TestConfig config;

    @Override
    public void generate(Map<String, Object> spec, String outputDir, TestConfig config, String testFramework) throws GenerationException {
        this.config = config;

        try {
            // Create output directory structure
            Path outputPath = Paths.get(outputDir, "nfr");
            Files.createDirectories(outputPath);

            // Extract API information
            String apiTitle = TestSpecUtils.getApiTitle(spec);
            String baseUrl = TestSpecUtils.resolveBaseUrl(spec, config);
            String basePackage = "com.example.api";
            if (config != null && config.getAdditionalProperties() != null) {
                Object packageNameObj = config.getAdditionalProperties().get("packageName");
                if (packageNameObj != null) {
                    basePackage = packageNameObj.toString();
                }
            }

            // Generate NFR test classes
            generateNFRTestClasses(spec, outputPath.toString(), basePackage, apiTitle, baseUrl);

            // Generate NFR configuration
            generateNFRConfiguration(outputPath.toString(), baseUrl);
            generatePomXml(outputPath.toString(), basePackage);

        } catch (Exception e) {
            throw new GenerationException("Failed to generate NFR tests: " + e.getMessage(), e);
        }
    }

    /**
     * Generate NFR test classes
     */
    private void generateNFRTestClasses(Map<String, Object> spec, String outputDir, String basePackage, String apiTitle, String baseUrl) throws IOException {
        Map<String, Object> paths = Util.asStringObjectMap(spec.get("paths"));
        if (paths == null || paths.isEmpty()) {
            return;
        }

        String packageDir = TestOutputLayout.testJavaDir(outputDir, basePackage);
        Files.createDirectories(Paths.get(packageDir));

        // Generate comprehensive NFR test class
        String className = "NFRTest";
        String testClassContent = generateNFRTestClass(basePackage, className, spec, baseUrl);
        Files.write(Paths.get(packageDir, className + ".java"), testClassContent.getBytes());
    }

    /**
     * Generate NFR test class
     */
    private String generateNFRTestClass(String basePackage, String className, Map<String, Object> spec, String baseUrl) {
        StringBuilder sb = new StringBuilder();

        // Package declaration
        sb.append("package ").append(basePackage).append(";\n\n");

        // Imports
        sb.append("import org.junit.jupiter.api.*;\n");
        sb.append("import org.junit.jupiter.api.DisplayName;\n");
        sb.append("import static org.junit.jupiter.api.Assertions.*;\n");
        sb.append("import static io.restassured.RestAssured.given;\n\n");
        sb.append("import io.restassured.http.ContentType;\n");
        sb.append("import io.restassured.response.Response;\n");
        sb.append("import java.util.*;\n");
        sb.append("import java.util.concurrent.*;\n");
        sb.append(TestCodegenSupport.supportImport(basePackage));

        // Class declaration
        sb.append("/**\n");
        sb.append(" * Non-Functional Requirements (NFR) Tests (generated from OpenAPI).\n");
        sb.append(" * <p>Requires {@code io.rest-assured:rest-assured} (5.x) and JUnit 5.\n");
        sb.append(" * Override base URL with env {@code API_BASE_URL}; optional {@code API_TOKEN} for Authorization.\n");
        sb.append(" * <ul>\n");
        sb.append(" * <li>Performance: response time on representative GET operations.</li>\n");
        sb.append(" * <li>Scalability: parallel RestAssured calls simulate concurrent users; throughput, latency, and success rate.</li>\n");
        sb.append(" * <li>Reliability: repeated requests to bound error rate under load.</li>\n");
        sb.append(" * <li>Availability: health-style GET expects non-server-error status.</li>\n");
        sb.append(" * </ul>\n");
        sb.append(" */\n");
        sb.append("@DisplayName(\"NFR Tests\")\n");
        sb.append("public class ").append(className).append(" {\n\n");

        // Constants
        sb.append("    private static final int MAX_RESPONSE_TIME_MS = TestEnv.getInt(\"nfr.maxResponseTimeMs\", 2000);\n");
        sb.append("    private static final int CONCURRENT_USERS = TestEnv.getInt(\"nfr.concurrentUsers\", 10);\n");
        sb.append("    private static final int REQUESTS_PER_USER = TestEnv.getInt(\"nfr.requestsPerUser\", 10);\n");
        sb.append("    private static final double MAX_ERROR_RATE = 0.01;\n\n");

        sb.append(TestCodegenSupport.restAssuredInit()).append("\n");

        sb.append("    private static String authToken() {\n");
        sb.append("        return TestAuth.rawToken();\n");
        sb.append("    }\n\n");

        // Performance tests
        generatePerformanceTests(sb, spec);

        // Scalability tests
        generateScalabilityTests(sb, spec);

        // Reliability tests
        generateReliabilityTests(sb, spec);

        // Availability tests
        generateAvailabilityTests(sb, spec);

        // Helper methods
        sb.append("    /**\n");
        sb.append("     * Execute concurrent GET requests; returns response times in ms, or -1 on failure.\n");
        sb.append("     */\n");
        sb.append("    private List<Long> executeConcurrentGets(String path, int count) {\n");
        sb.append("        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_USERS);\n");
        sb.append("        List<CompletableFuture<Long>> futures = new ArrayList<>();\n");
        sb.append("        \n");
        sb.append("        for (int i = 0; i < count; i++) {\n");
        sb.append("            futures.add(CompletableFuture.supplyAsync(() -> {\n");
        sb.append("                try {\n");
        sb.append("                    Response response = given()\n");
        sb.append("                        .accept(ContentType.JSON)\n");
        sb.append("                        .header(\"Authorization\", authToken())\n");
        sb.append("                        .when()\n");
        sb.append("                        .get(path);\n");
        sb.append("                    return response.getTime();\n");
        sb.append("                } catch (Exception e) {\n");
        sb.append("                    return -1L; // Error indicator\n");
        sb.append("                }\n");
        sb.append("            }, executor));\n");
        sb.append("        }\n");
        sb.append("        \n");
        sb.append("        List<Long> responseTimes = new ArrayList<>();\n");
        sb.append("        for (CompletableFuture<Long> future : futures) {\n");
        sb.append("            try {\n");
        sb.append("                responseTimes.add(future.get());\n");
        sb.append("            } catch (Exception e) {\n");
        sb.append("                responseTimes.add(-1L);\n");
        sb.append("            }\n");
        sb.append("        }\n");
        sb.append("        \n");
        sb.append("        executor.shutdown();\n");
        sb.append("        return responseTimes;\n");
        sb.append("    }\n\n");

        sb.append("}\n");

        return sb.toString();
    }

    /**
     * Generate performance tests
     */
    private void generatePerformanceTests(StringBuilder sb, Map<String, Object> spec) {
        sb.append("    // ========== Performance Tests ==========\n\n");

        Map<String, Object> paths = Util.asStringObjectMap(spec.get("paths"));
        if (paths != null) {
            for (Map.Entry<String, Object> pathEntry : paths.entrySet()) {
                String path = pathEntry.getKey();
                Map<String, Object> pathItem = Util.asStringObjectMap(pathEntry.getValue());

                if (pathItem == null) continue;

                if (pathItem.containsKey("get")) {
                    Map<String, Object> operation = Util.asStringObjectMap(pathItem.get("get"));
                    String summary = (String) operation.get("summary");
                    String display = summary != null ? summary : "GET " + path;

                    sb.append("    @Test\n");
                    sb.append("    @DisplayName(\"Performance: ").append(escapeJavaString(display))
                            .append(" - Response Time\")\n");
                    sb.append("    void testPerformance_ResponseTime_").append(sanitizePath(path)).append("() {\n");
                    sb.append("        Response response = given()\n");
                    sb.append("            .accept(ContentType.JSON)\n");
                    sb.append("            .header(\"Authorization\", authToken())\n");
                    sb.append("            .when()\n");
                    sb.append("            .get(\"").append(escapeJavaString(path)).append("\");\n\n");
                    sb.append("        long responseTime = response.getTime();\n\n");
                    sb.append("        assertTrue(response.getStatusCode() < 500,\n");
                    sb.append("            \"Unexpected server error, status \" + response.getStatusCode());\n");
                    sb.append("        assertTrue(responseTime < MAX_RESPONSE_TIME_MS,\n");
                    sb.append("            \"Response time should be less than \" + MAX_RESPONSE_TIME_MS + \"ms, but was \" + responseTime + \"ms\");\n");
                    sb.append("    }\n\n");
                }
            }
        }
    }

    /**
     * Generate scalability tests
     */
    private void generateScalabilityTests(StringBuilder sb, Map<String, Object> spec) {
        sb.append("    // ========== Scalability Tests ==========\n\n");

        Map<String, Object> paths = Util.asStringObjectMap(spec.get("paths"));
        if (paths != null && !paths.isEmpty()) {
            String firstPath = paths.keySet().iterator().next();
            String escapedPath = escapeJavaString(firstPath);

            sb.append("    @Test\n");
            sb.append("    @DisplayName(\"Scalability: Concurrent Request Handling\")\n");
            sb.append("    void testScalability_ConcurrentRequests() {\n");
            sb.append("        String path = \"").append(escapedPath).append("\";\n");
            sb.append("        int totalRequests = CONCURRENT_USERS * REQUESTS_PER_USER;\n");
            sb.append("        List<Long> responseTimes = executeConcurrentGets(path, totalRequests);\n\n");
            sb.append("        long successCount = responseTimes.stream().filter(rt -> rt > 0).count();\n");
            sb.append("        double successRate = (double) successCount / totalRequests;\n");
            sb.append("        \n");
            sb.append("        assertTrue(successRate >= (1.0 - MAX_ERROR_RATE),\n");
            sb.append("            \"Success rate should be at least \" + (1.0 - MAX_ERROR_RATE) + \", but was \" + successRate);\n");
            sb.append("        \n");
            sb.append("        double avgResponseTime = responseTimes.stream()\n");
            sb.append("            .filter(rt -> rt > 0)\n");
            sb.append("            .mapToLong(Long::longValue)\n");
            sb.append("            .average()\n");
            sb.append("            .orElse(0.0);\n");
            sb.append("        \n");
            sb.append("        assertTrue(avgResponseTime < MAX_RESPONSE_TIME_MS * 2,\n");
            sb.append("            \"Average response time under load should be reasonable, but was \" + avgResponseTime + \"ms\");\n");
            sb.append("    }\n\n");
        }
    }

    /**
     * Generate reliability tests
     */
    private void generateReliabilityTests(StringBuilder sb, Map<String, Object> spec) {
        sb.append("    // ========== Reliability Tests ==========\n\n");

        sb.append("    @Test\n");
        sb.append("    @DisplayName(\"Reliability: Error Rate Under Normal Load\")\n");
        sb.append("    void testReliability_ErrorRate() {\n");
        sb.append("        int requestCount = 100;\n");
        sb.append("        int errorCount = 0;\n");
        sb.append("        \n");
        sb.append("        for (int i = 0; i < requestCount; i++) {\n");
        sb.append("            try {\n");
        sb.append("                Response response = given()\n");
        sb.append("                    .header(\"Authorization\", authToken())\n");
        sb.append("                    .when()\n");
        sb.append("                    .get(\"/\");\n");
        sb.append("                if (response.getStatusCode() >= 500) {\n");
        sb.append("                    errorCount++;\n");
        sb.append("                }\n");
        sb.append("            } catch (Exception e) {\n");
        sb.append("                errorCount++;\n");
        sb.append("            }\n");
        sb.append("        }\n\n");
        sb.append("        double errorRate = (double) errorCount / requestCount;\n");
        sb.append("        assertTrue(errorRate <= MAX_ERROR_RATE,\n");
        sb.append("            \"Error rate should be at most \" + MAX_ERROR_RATE + \", but was \" + errorRate);\n");
        sb.append("    }\n\n");
    }

    /**
     * Generate availability tests
     */
    private void generateAvailabilityTests(StringBuilder sb, Map<String, Object> spec) {
        sb.append("    // ========== Availability Tests ==========\n\n");

        sb.append("    @Test\n");
        sb.append("    @DisplayName(\"Availability: Health Check\")\n");
        sb.append("    void testAvailability_HealthCheck() {\n");
        sb.append("        Response response = given()\n");
        sb.append("            .header(\"Authorization\", authToken())\n");
        sb.append("            .when()\n");
        sb.append("            .get(\"/\");\n\n");
        sb.append("        assertTrue(response.getStatusCode() < 500,\n");
        sb.append("            \"Service should be available (status code < 500), but got \" + response.getStatusCode());\n");
        sb.append("    }\n\n");
    }

    /**
     * Generate NFR configuration
     */
    private void generatePomXml(String outputDir, String basePackage) throws IOException {
        String pom = TestMavenSupport.pomHeader("api-nfr-tests", basePackage)
                + TestMavenSupport.standardRestAssuredTestDependencies()
                + TestMavenSupport.buildSectionWithTestSupport();
        Files.write(Paths.get(outputDir, "pom.xml"), pom.getBytes());
    }

    private void generateNFRConfiguration(String outputDir, String baseUrl) throws IOException {
        String configContent = "# NFR Test Configuration\n" +
                "# Generated from OpenAPI specification\n\n" +
                "base.url=" + baseUrl + "\n" +
                "max.response.time.ms=2000\n" +
                "concurrent.users=10\n" +
                "requests.per.user=10\n" +
                "max.error.rate=0.01\n" +
                "timeout.seconds=30\n";

        Files.write(Paths.get(outputDir, "nfr-config.properties"), configContent.getBytes());
    }

    private static String escapeJavaString(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String sanitizePath(String path) {
        return path.replaceAll("[^a-zA-Z0-9]", "_");
    }

    @Override
    public String getName() {
        return "NFR Test Generator";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getTestType() {
        return "nfr";
    }

    @Override
    public void setConfig(TestConfig config) {
        this.config = config;
    }

    @Override
    public TestConfig getConfig() {
        return this.config;
    }
}
