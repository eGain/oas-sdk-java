package egain.oassdk.testgenerators.performance;

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
 * Performance test generator
 * Generates load testing and performance benchmarking tests
 */
public class PerformanceTestGenerator implements TestGenerator, ConfigurableTestGenerator {

    private TestConfig config;

    @Override
    public void generate(Map<String, Object> spec, String outputDir, TestConfig config, String testFramework) throws GenerationException {
        this.config = config;

        try {
            // Create output directory structure
            Path outputPath = Paths.get(outputDir, "performance");
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

            // Generate performance test classes
            generatePerformanceTestClasses(spec, outputPath.toString(), basePackage, apiTitle, baseUrl);

            // Generate performance test configuration
            generatePerformanceConfiguration(outputPath.toString(), baseUrl);
            generatePomXml(outputPath.toString(), basePackage);

        } catch (Exception e) {
            throw new GenerationException("Failed to generate performance tests: " + e.getMessage(), e);
        }
    }

    /**
     * Generate performance test classes
     */
    private void generatePerformanceTestClasses(Map<String, Object> spec, String outputDir, String basePackage, String apiTitle, String baseUrl) throws IOException {
        Map<String, Object> paths = Util.asStringObjectMap(spec.get("paths"));
        if (paths == null || paths.isEmpty()) {
            return;
        }

        String packageDir = TestOutputLayout.testJavaDir(outputDir, basePackage);
        Files.createDirectories(Paths.get(packageDir));

        // Generate performance test class
        String className = "PerformanceTest";
        String testClassContent = generatePerformanceTestClass(basePackage, className, spec, baseUrl);
        Files.write(Paths.get(packageDir, className + ".java"), testClassContent.getBytes());
    }

    /**
     * Generate performance test class
     */
    private String generatePerformanceTestClass(String basePackage, String className, Map<String, Object> spec, String baseUrl) {
        StringBuilder sb = new StringBuilder();

        // Package declaration
        sb.append("package ").append(basePackage).append(";\n\n");

        // Imports
        sb.append("import org.junit.jupiter.api.*;\n");
        sb.append("import org.junit.jupiter.api.DisplayName;\n");
        sb.append("import static org.junit.jupiter.api.Assertions.*;\n\n");
        sb.append("import java.net.http.*;\n");
        sb.append("import java.net.URI;\n");
        sb.append("import java.time.Duration;\n");
        sb.append("import java.util.*;\n");
        sb.append("import java.util.concurrent.*;\n");
        sb.append("import java.util.stream.Collectors;\n");
        sb.append(TestCodegenSupport.supportImport(basePackage));

        // Class declaration
        sb.append("/**\n");
        sb.append(" * Performance Tests\n");
        sb.append(" * Generated from OpenAPI specification\n");
        sb.append(" * \n");
        sb.append(" * Tests for:\n");
        sb.append(" * - Load testing (concurrent requests)\n");
        sb.append(" * - Response time benchmarking\n");
        sb.append(" * - Throughput measurement\n");
        sb.append(" * - Stress testing\n");
        sb.append(" */\n");
        sb.append("@DisplayName(\"Performance Tests\")\n");
        sb.append("public class ").append(className).append(" {\n\n");

        // Constants
        sb.append("    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);\n");
        sb.append("    private static final int TARGET_RESPONSE_TIME_MS = TestEnv.getInt(\"performance.targetResponseTimeMs\", 1000);\n");
        sb.append("    private static final int MAX_RESPONSE_TIME_MS = TestEnv.getInt(\"performance.maxResponseTimeMs\", 2000);\n");
        sb.append("    private static final int LOAD_TEST_USERS = TestEnv.getInt(\"performance.loadTestUsers\", 50);\n");
        sb.append("    private static final int REQUESTS_PER_USER = TestEnv.getInt(\"performance.requestsPerUser\", 20);\n");
        sb.append("    private static final int STRESS_TEST_USERS = TestEnv.getInt(\"performance.stressTestUsers\", 100);\n");
        sb.append("    private static HttpClient httpClient;\n\n");

        sb.append("    @BeforeAll\n");
        sb.append("    static void setUpAll() {\n");
        sb.append("        httpClient = TestHttp.client();\n");
        sb.append("    }\n\n");

        sb.append("    private URI perfUri(String pathTemplate) {\n");
        sb.append("        String p = pathTemplate.replace(\"{folderID}\", TestEnv.folderId())\n");
        sb.append("                .replace(\"{promptID}\", TestEnv.get(\"test.prompt.id\", \"1\"));\n");
        sb.append("        return URI.create(TestEnv.baseUrl() + p);\n");
        sb.append("    }\n\n");

        // Load test
        generateLoadTest(sb, spec);

        // Response time test
        generateResponseTimeTest(sb, spec);

        // Throughput test
        generateThroughputTest(sb, spec);

        // Stress test
        generateStressTest(sb, spec);

        // Helper methods
        sb.append("    /**\n");
        sb.append("     * Execute load test with specified number of users and requests\n");
        sb.append("     */\n");
        sb.append("    private PerformanceMetrics executeLoadTest(HttpRequest request, int users, int requestsPerUser) {\n");
        sb.append("        ExecutorService executor = Executors.newFixedThreadPool(users);\n");
        sb.append("        List<CompletableFuture<RequestResult>> futures = new ArrayList<>();\n");
        sb.append("        \n");
        sb.append("        for (int i = 0; i < users * requestsPerUser; i++) {\n");
        sb.append("            futures.add(CompletableFuture.supplyAsync(() -> {\n");
        sb.append("                try {\n");
        sb.append("                    long startTime = System.currentTimeMillis();\n");
        sb.append("                    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());\n");
        sb.append("                    long endTime = System.currentTimeMillis();\n");
        sb.append("                    return new RequestResult(endTime - startTime, response.statusCode() < 500);\n");
        sb.append("                } catch (Exception e) {\n");
        sb.append("                    return new RequestResult(-1L, false);\n");
        sb.append("                }\n");
        sb.append("            }, executor));\n");
        sb.append("        }\n");
        sb.append("        \n");
        sb.append("        List<RequestResult> results = new ArrayList<>();\n");
        sb.append("        for (CompletableFuture<RequestResult> future : futures) {\n");
        sb.append("            try {\n");
        sb.append("                results.add(future.get());\n");
        sb.append("            } catch (Exception e) {\n");
        sb.append("                results.add(new RequestResult(-1L, false));\n");
        sb.append("            }\n");
        sb.append("        }\n");
        sb.append("        \n");
        sb.append("        executor.shutdown();\n");
        sb.append("        \n");
        sb.append("        return calculateMetrics(results);\n");
        sb.append("    }\n\n");

        sb.append("    /**\n");
        sb.append("     * Calculate performance metrics from results\n");
        sb.append("     */\n");
        sb.append("    private PerformanceMetrics calculateMetrics(List<RequestResult> results) {\n");
        sb.append("        long successCount = results.stream().filter(r -> r.success).count();\n");
        sb.append("        double successRate = (double) successCount / results.size();\n");
        sb.append("        \n");
        sb.append("        OptionalDouble avgResponseTime = results.stream()\n");
        sb.append("            .filter(r -> r.responseTime > 0)\n");
        sb.append("            .mapToLong(r -> r.responseTime)\n");
        sb.append("            .average();\n");
        sb.append("        \n");
        sb.append("        OptionalLong minResponseTime = results.stream()\n");
        sb.append("            .filter(r -> r.responseTime > 0)\n");
        sb.append("            .mapToLong(r -> r.responseTime)\n");
        sb.append("            .min();\n");
        sb.append("        \n");
        sb.append("        OptionalLong maxResponseTime = results.stream()\n");
        sb.append("            .filter(r -> r.responseTime > 0)\n");
        sb.append("            .mapToLong(r -> r.responseTime)\n");
        sb.append("            .max();\n");
        sb.append("        \n");
        sb.append("        return new PerformanceMetrics(\n");
        sb.append("            successRate,\n");
        sb.append("            avgResponseTime.orElse(0.0),\n");
        sb.append("            minResponseTime.orElse(0L),\n");
        sb.append("            maxResponseTime.orElse(0L)\n");
        sb.append("        );\n");
        sb.append("    }\n\n");

        sb.append("    /**\n");
        sb.append("     * Inner class for request result\n");
        sb.append("     */\n");
        sb.append("    private static class RequestResult {\n");
        sb.append("        long responseTime;\n");
        sb.append("        boolean success;\n");
        sb.append("        \n");
        sb.append("        RequestResult(long responseTime, boolean success) {\n");
        sb.append("            this.responseTime = responseTime;\n");
        sb.append("            this.success = success;\n");
        sb.append("        }\n");
        sb.append("    }\n\n");

        sb.append("    /**\n");
        sb.append("     * Inner class for performance metrics\n");
        sb.append("     */\n");
        sb.append("    private static class PerformanceMetrics {\n");
        sb.append("        double successRate;\n");
        sb.append("        double avgResponseTime;\n");
        sb.append("        long minResponseTime;\n");
        sb.append("        long maxResponseTime;\n");
        sb.append("        \n");
        sb.append("        PerformanceMetrics(double successRate, double avgResponseTime, long minResponseTime, long maxResponseTime) {\n");
        sb.append("            this.successRate = successRate;\n");
        sb.append("            this.avgResponseTime = avgResponseTime;\n");
        sb.append("            this.minResponseTime = minResponseTime;\n");
        sb.append("            this.maxResponseTime = maxResponseTime;\n");
        sb.append("        }\n");
        sb.append("    }\n\n");

        sb.append("}\n");

        return sb.toString();
    }

    /**
     * Generate load test
     */
    private void generateLoadTest(StringBuilder sb, Map<String, Object> spec) {
        sb.append("    @Test\n");
        sb.append("    @DisplayName(\"Load Test: Concurrent Users\")\n");
        sb.append("    void testLoad_ConcurrentUsers() throws Exception {\n");
        sb.append("        // Arrange\n");

        Map<String, Object> paths = Util.asStringObjectMap(spec.get("paths"));
        String testPath = "/";
        if (paths != null && !paths.isEmpty()) {
            testPath = paths.keySet().iterator().next();
        }

        sb.append("        URI uri = perfUri(\"").append(testPath).append("\");\n");
        sb.append("        HttpRequest request = HttpRequest.newBuilder()\n");
        sb.append("            .uri(uri)\n");
        sb.append("            .timeout(REQUEST_TIMEOUT)\n");
        sb.append("            .GET()\n");
        sb.append("            .header(\"Accept\", \"application/json\")\n");
        sb.append("            .build();\n\n");
        sb.append("        // Act\n");
        sb.append("        PerformanceMetrics metrics = executeLoadTest(request, LOAD_TEST_USERS, REQUESTS_PER_USER);\n\n");
        sb.append("        // Assert\n");
        sb.append("        assertTrue(metrics.successRate >= 0.95, \n");
        sb.append("            \"Success rate should be at least 95%, but was \" + (metrics.successRate * 100) + \"%\");\n");
        sb.append("        assertTrue(metrics.avgResponseTime < MAX_RESPONSE_TIME_MS, \n");
        sb.append("            \"Average response time should be less than \" + MAX_RESPONSE_TIME_MS + \"ms, but was \" + metrics.avgResponseTime + \"ms\");\n");
        sb.append("        \n");
        sb.append("        System.out.println(\"Load Test Results:\");\n");
        sb.append("        System.out.println(\"  Success Rate: \" + (metrics.successRate * 100) + \"%\");\n");
        sb.append("        System.out.println(\"  Avg Response Time: \" + metrics.avgResponseTime + \"ms\");\n");
        sb.append("        System.out.println(\"  Min Response Time: \" + metrics.minResponseTime + \"ms\");\n");
        sb.append("        System.out.println(\"  Max Response Time: \" + metrics.maxResponseTime + \"ms\");\n");
        sb.append("    }\n\n");
    }

    /**
     * Generate response time test
     */
    private void generateResponseTimeTest(StringBuilder sb, Map<String, Object> spec) {
        sb.append("    @Test\n");
        sb.append("    @DisplayName(\"Response Time: Single Request Benchmark\")\n");
        sb.append("    void testResponseTime_SingleRequest() throws Exception {\n");
        sb.append("        // Arrange\n");

        Map<String, Object> paths = Util.asStringObjectMap(spec.get("paths"));
        String testPath = "/";
        if (paths != null && !paths.isEmpty()) {
            testPath = paths.keySet().iterator().next();
        }

        sb.append("        URI uri = perfUri(\"").append(testPath).append("\");\n");
        sb.append("        HttpRequest request = HttpRequest.newBuilder()\n");
        sb.append("            .uri(uri)\n");
        sb.append("            .timeout(REQUEST_TIMEOUT)\n");
        sb.append("            .GET()\n");
        sb.append("            .header(\"Accept\", \"application/json\")\n");
        sb.append("            .build();\n\n");
        sb.append("        // Act - Measure response time over multiple requests\n");
        sb.append("        List<Long> responseTimes = new ArrayList<>();\n");
        sb.append("        for (int i = 0; i < 10; i++) {\n");
        sb.append("            long startTime = System.currentTimeMillis();\n");
        sb.append("            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());\n");
        sb.append("            long endTime = System.currentTimeMillis();\n");
        sb.append("            responseTimes.add(endTime - startTime);\n");
        sb.append("        }\n\n");
        sb.append("        // Assert\n");
        sb.append("        double avgResponseTime = responseTimes.stream().mapToLong(Long::longValue).average().orElse(0.0);\n");
        sb.append("        assertTrue(avgResponseTime < TARGET_RESPONSE_TIME_MS, \n");
        sb.append("            \"Average response time should be less than \" + TARGET_RESPONSE_TIME_MS + \"ms, but was \" + avgResponseTime + \"ms\");\n");
        sb.append("    }\n\n");
    }

    /**
     * Generate throughput test
     */
    private void generateThroughputTest(StringBuilder sb, Map<String, Object> spec) {
        sb.append("    @Test\n");
        sb.append("    @DisplayName(\"Throughput: Requests Per Second\")\n");
        sb.append("    void testThroughput_RequestsPerSecond() throws Exception {\n");
        sb.append("        // Arrange\n");

        Map<String, Object> paths = Util.asStringObjectMap(spec.get("paths"));
        String testPath = "/";
        if (paths != null && !paths.isEmpty()) {
            testPath = paths.keySet().iterator().next();
        }

        sb.append("        URI uri = perfUri(\"").append(testPath).append("\");\n");
        sb.append("        HttpRequest request = HttpRequest.newBuilder()\n");
        sb.append("            .uri(uri)\n");
        sb.append("            .timeout(REQUEST_TIMEOUT)\n");
        sb.append("            .GET()\n");
        sb.append("            .header(\"Accept\", \"application/json\")\n");
        sb.append("            .build();\n\n");
        sb.append("        // Act - Measure throughput\n");
        sb.append("        long startTime = System.currentTimeMillis();\n");
        sb.append("        int requestCount = 100;\n");
        sb.append("        int successCount = 0;\n");
        sb.append("        \n");
        sb.append("        for (int i = 0; i < requestCount; i++) {\n");
        sb.append("            try {\n");
        sb.append("                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());\n");
        sb.append("                if (response.statusCode() < 500) {\n");
        sb.append("                    successCount++;\n");
        sb.append("                }\n");
        sb.append("            } catch (Exception e) {\n");
        sb.append("                // Count as failure\n");
        sb.append("            }\n");
        sb.append("        }\n");
        sb.append("        \n");
        sb.append("        long endTime = System.currentTimeMillis();\n");
        sb.append("        long durationSeconds = (endTime - startTime) / 1000;\n");
        sb.append("        double requestsPerSecond = durationSeconds > 0 ? (double) successCount / durationSeconds : 0;\n\n");
        sb.append("        // Assert\n");
        sb.append("        assertTrue(requestsPerSecond > 0, \n");
        sb.append("            \"Throughput should be greater than 0 requests/second, but was \" + requestsPerSecond);\n");
        sb.append("        \n");
        sb.append("        System.out.println(\"Throughput: \" + requestsPerSecond + \" requests/second\");\n");
        sb.append("    }\n\n");
    }

    /**
     * Generate stress test
     */
    private void generateStressTest(StringBuilder sb, Map<String, Object> spec) {
        sb.append("    @Test\n");
        sb.append("    @DisplayName(\"Stress Test: High Load\")\n");
        sb.append("    void testStress_HighLoad() throws Exception {\n");
        sb.append("        // Arrange\n");

        Map<String, Object> paths = Util.asStringObjectMap(spec.get("paths"));
        String testPath = "/";
        if (paths != null && !paths.isEmpty()) {
            testPath = paths.keySet().iterator().next();
        }

        sb.append("        URI uri = perfUri(\"").append(testPath).append("\");\n");
        sb.append("        HttpRequest request = HttpRequest.newBuilder()\n");
        sb.append("            .uri(uri)\n");
        sb.append("            .timeout(REQUEST_TIMEOUT)\n");
        sb.append("            .GET()\n");
        sb.append("            .header(\"Accept\", \"application/json\")\n");
        sb.append("            .build();\n\n");
        sb.append("        // Act - Execute stress test\n");
        sb.append("        PerformanceMetrics metrics = executeLoadTest(request, STRESS_TEST_USERS, REQUESTS_PER_USER);\n\n");
        sb.append("        // Assert - Stress test may have lower success rate\n");
        sb.append("        assertTrue(metrics.successRate >= 0.80, \n");
        sb.append("            \"Success rate under stress should be at least 80%, but was \" + (metrics.successRate * 100) + \"%\");\n");
        sb.append("        \n");
        sb.append("        System.out.println(\"Stress Test Results:\");\n");
        sb.append("        System.out.println(\"  Success Rate: \" + (metrics.successRate * 100) + \"%\");\n");
        sb.append("        System.out.println(\"  Avg Response Time: \" + metrics.avgResponseTime + \"ms\");\n");
        sb.append("    }\n\n");
    }

    /**
     * Generate performance configuration
     */
    private void generatePomXml(String outputDir, String basePackage) throws IOException {
        String pom = TestMavenSupport.pomHeader("api-performance-tests", basePackage)
                + TestMavenSupport.standardTestSupportModuleDependencies()
                + TestMavenSupport.buildSectionWithTestSupport();
        Files.write(Paths.get(outputDir, "pom.xml"), pom.getBytes());
    }

    private void generatePerformanceConfiguration(String outputDir, String baseUrl) throws IOException {
        String configContent = "# Performance Test Configuration\n" +
                "# Generated from OpenAPI specification\n\n" +
                "base.url=" + baseUrl + "\n" +
                "target.response.time.ms=1000\n" +
                "max.response.time.ms=2000\n" +
                "load.test.users=50\n" +
                "requests.per.user=20\n" +
                "stress.test.users=100\n" +
                "timeout.seconds=30\n";

        Files.write(Paths.get(outputDir, "performance-config.properties"), configContent.getBytes());
    }

    @Override
    public String getName() {
        return "Performance Test Generator";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getTestType() {
        return "performance";
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
