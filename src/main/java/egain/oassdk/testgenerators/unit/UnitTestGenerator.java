package egain.oassdk.testgenerators.unit;

import egain.oassdk.Util;
import egain.oassdk.config.TestConfig;
import egain.oassdk.core.exceptions.GenerationException;
import egain.oassdk.testgenerators.ConfigurableTestGenerator;
import egain.oassdk.testgenerators.TestGenerator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Unit test generator
 * Generates JUnit 5 unit tests for API endpoints based on OpenAPI specification
 */
public class UnitTestGenerator implements TestGenerator, ConfigurableTestGenerator {

    private TestConfig config;

    @Override
    public void generate(Map<String, Object> spec, String outputDir, TestConfig config, String testFramework) throws GenerationException {
        this.config = config;

        try {
            // Create output directory structure
            Path outputPath = Paths.get(outputDir, "unit");
            Files.createDirectories(outputPath);

            // Extract API information
            String apiTitle = getAPITitle(spec);

            // Get package name from additional properties or use default
            String basePackage = "com.example.api";
            if (config != null && config.getAdditionalProperties() != null) {
                Object packageNameObj = config.getAdditionalProperties().get("packageName");
                if (packageNameObj != null) {
                    basePackage = packageNameObj.toString();
                }
            }

            // Generate test classes for each endpoint
            generateTestClasses(spec, outputPath.toString(), basePackage, apiTitle);

            // Generate test utilities if needed
            generateTestUtilities(outputPath.toString(), basePackage);

        } catch (Exception e) {
            throw new GenerationException("Failed to generate unit tests: " + e.getMessage(), e);
        }
    }

    /**
     * Generate test classes for all endpoints
     */
    private void generateTestClasses(Map<String, Object> spec, String outputDir, String basePackage, String apiTitle) throws IOException {
        Map<String, Object> paths = Util.asStringObjectMap(spec.get("paths"));
        if (paths == null || paths.isEmpty()) {
            return;
        }

        // Group operations by tag for better organization
        Map<String, List<OperationInfo>> operationsByTag = new HashMap<>();

        for (Map.Entry<String, Object> pathEntry : paths.entrySet()) {
            String path = pathEntry.getKey();
            Map<String, Object> pathItem = Util.asStringObjectMap(pathEntry.getValue());

            if (pathItem == null) continue;

            String[] methods = {"get", "post", "put", "delete", "patch", "head", "options", "trace"};
            for (String method : methods) {
                if (pathItem.containsKey(method)) {
                    Map<String, Object> operation = Util.asStringObjectMap(pathItem.get(method));
                    if (operation == null) continue;

                    OperationInfo opInfo = new OperationInfo();
                    opInfo.path = path;
                    opInfo.method = method;
                    opInfo.operation = operation;

                    // Get tag for grouping
                    String tag = getOperationTag(operation);
                    operationsByTag.computeIfAbsent(tag, k -> new ArrayList<>()).add(opInfo);
                }
            }
        }

        // Generate test class for each tag
        for (Map.Entry<String, List<OperationInfo>> tagEntry : operationsByTag.entrySet()) {
            String tag = tagEntry.getKey();
            List<OperationInfo> operations = tagEntry.getValue();

            String className = toClassName(tag) + "ApiTest";
            String packageDir = outputDir + "/" + basePackage.replace(".", "/");
            Files.createDirectories(Paths.get(packageDir));

            String testClassContent = generateTestClass(basePackage, className, tag, operations, spec);
            Files.write(Paths.get(packageDir, className + ".java"), testClassContent.getBytes());
        }
    }

    /**
     * Generate a test class for a group of operations
     */
    private String generateTestClass(String basePackage, String className, String tag, List<OperationInfo> operations, Map<String, Object> spec) {
        StringBuilder sb = new StringBuilder();

        // Package declaration
        sb.append("package ").append(basePackage).append(";\n\n");

        // Imports
        sb.append("import org.junit.jupiter.api.*;\n");
        sb.append("import org.junit.jupiter.api.DisplayName;\n");
        sb.append("import org.junit.jupiter.params.ParameterizedTest;\n");
        sb.append("import org.junit.jupiter.params.provider.ValueSource;\n");
        sb.append("import org.junit.jupiter.params.provider.NullAndEmptySource;\n");
        sb.append("import static org.junit.jupiter.api.Assertions.*;\n");
        sb.append("import static org.mockito.Mockito.*;\n\n");
        sb.append("import java.util.*;\n");
        sb.append("import java.net.http.*;\n");
        sb.append("import java.net.URI;\n\n");

        // Class declaration
        sb.append("/**\n");
        sb.append(" * Unit tests for ").append(tag).append(" API endpoints\n");
        sb.append(" * Generated from OpenAPI specification\n");
        sb.append(" */\n");
        sb.append("@DisplayName(\"").append(tag).append(" API Tests\")\n");
        sb.append("public class ").append(className).append(" {\n\n");

        // Test setup
        sb.append("    private static final String BASE_URL = \"http://localhost:8080\";\n");
        sb.append("    private HttpClient httpClient;\n\n");

        sb.append("    @BeforeEach\n");
        sb.append("    public void setUp() {\n");
        sb.append("        httpClient = HttpClient.newHttpClient();\n");
        sb.append("    }\n\n");

        // Generate test methods for each operation
        for (OperationInfo opInfo : operations) {
            generateTestMethods(sb, opInfo, spec);
        }

        // Helper methods
        sb.append("    /**\n");
        sb.append("     * Helper method to build request URI\n");
        sb.append("     */\n");
        sb.append("    private URI buildUri(String path, Map<String, String> queryParams) {\n");
        sb.append("        StringBuilder uriBuilder = new StringBuilder(BASE_URL).append(path);\n");
        sb.append("        if (queryParams != null && !queryParams.isEmpty()) {\n");
        sb.append("            uriBuilder.append(\"?\");\n");
        sb.append("            queryParams.entrySet().stream()\n");
        sb.append("                .forEach(entry -> uriBuilder.append(entry.getKey())\n");
        sb.append("                    .append(\"=\").append(entry.getValue()).append(\"&\"));\n");
        sb.append("            uriBuilder.setLength(uriBuilder.length() - 1); // Remove trailing &\n");
        sb.append("        }\n");
        sb.append("        return URI.create(uriBuilder.toString());\n");
        sb.append("    }\n\n");

        sb.append("    /**\n");
        sb.append("     * Helper method to replace path parameters\n");
        sb.append("     */\n");
        sb.append("    private String replacePathParameters(String path, Map<String, String> pathParams) {\n");
        sb.append("        String result = path;\n");
        sb.append("        if (pathParams != null) {\n");
        sb.append("            for (Map.Entry<String, String> entry : pathParams.entrySet()) {\n");
        sb.append("                result = result.replace(\"{\" + entry.getKey() + \"}\", entry.getValue());\n");
        sb.append("            }\n");
        sb.append("        }\n");
        sb.append("        return result;\n");
        sb.append("    }\n\n");

        sb.append("}\n");

        return sb.toString();
    }

    /**
     * Generate test methods for an operation
     */
    private void generateTestMethods(StringBuilder sb, OperationInfo opInfo, Map<String, Object> spec) {
        Map<String, Object> operation = opInfo.operation;
        String operationId = (String) operation.get("operationId");
        String summary = (String) operation.get("summary");
        String method = opInfo.method.toUpperCase();
        String path = opInfo.path;

        // Get operation name for test method
        String testMethodName = operationId != null
                ? toMethodName(operationId)
                : toMethodName(method + "_" + sanitizePath(path));

        // Extract parameters
        List<Map<String, Object>> parameters = operation.containsKey("parameters")
                ? Util.asStringObjectMapList(operation.get("parameters"))
                : new ArrayList<>();

        // Extract responses
        Map<String, Object> responses = operation.containsKey("responses")
                ? Util.asStringObjectMap(operation.get("responses"))
                : new HashMap<>();

        // Test: Valid request
        sb.append("    @Test\n");
        sb.append("    @DisplayName(\"").append(summary != null ? summary : method + " " + path).append(" - Valid Request\")\n");
        sb.append("    public void test").append(capitalize(testMethodName)).append("_ValidRequest() {\n");
        sb.append("        // Arrange\n");

        // Build path parameters
        Map<String, String> pathParams = new HashMap<>();
        Map<String, String> queryParams = new HashMap<>();
        for (Map<String, Object> param : parameters) {
            String paramName = (String) param.get("name");
            String paramIn = (String) param.get("in");
            if ("path".equals(paramIn)) {
                String example = getParameterExample(param);
                pathParams.put(paramName, example);
            } else if ("query".equals(paramIn)) {
                String example = getParameterExample(param);
                queryParams.put(paramName, example);
            }
        }

        // Always declare pathParams, even if empty
        sb.append("        Map<String, String> pathParams = new HashMap<>();\n");
        if (!pathParams.isEmpty()) {
            for (Map.Entry<String, String> entry : pathParams.entrySet()) {
                sb.append("        pathParams.put(\"").append(entry.getKey()).append("\", \"").append(entry.getValue()).append("\");\n");
            }
        }

        // Always declare queryParams, even if empty
        sb.append("        Map<String, String> queryParams = new HashMap<>();\n");
        if (!queryParams.isEmpty()) {
            for (Map.Entry<String, String> entry : queryParams.entrySet()) {
                sb.append("        queryParams.put(\"").append(entry.getKey()).append("\", \"").append(entry.getValue()).append("\");\n");
            }
        }

        sb.append("        String path = replacePathParameters(\"").append(path).append("\", pathParams);\n");
        if (!queryParams.isEmpty()) {
            sb.append("        URI uri = buildUri(path, queryParams);\n");
        } else {
            sb.append("        URI uri = URI.create(BASE_URL + path);\n");
        }
        sb.append("        HttpRequest request = HttpRequest.newBuilder()\n");
        sb.append("            .uri(uri)\n");
        sb.append("            .method(\"").append(method).append("\", HttpRequest.BodyPublishers.noBody())\n");
        sb.append("            .header(\"Accept\", \"application/json\")\n");
        sb.append("            .build();\n\n");

        sb.append("        // Act & Assert\n");
        sb.append("        assertNotNull(request, \"Request should not be null\");\n");
        sb.append("        assertEquals(\"").append(method).append("\", request.method(), \"HTTP method should match\");\n");
        sb.append("        assertNotNull(uri, \"URI should not be null\");\n");
        sb.append("        assertTrue(uri.toString().startsWith(BASE_URL), \"URI should start with base URL\");\n");
        sb.append("        assertNotNull(path, \"Path should not be null\");\n");
        if (!pathParams.isEmpty()) {
            for (Map.Entry<String, String> entry : pathParams.entrySet()) {
                sb.append("        assertTrue(path.contains(\"").append(entry.getValue()).append("\"), \"Path should contain path parameter ").append(entry.getKey()).append(" value\");\n");
            }
        }
        if (!queryParams.isEmpty()) {
            for (String paramName : queryParams.keySet()) {
                sb.append("        assertTrue(uri.toString().contains(\"").append(paramName).append("=\"), \"URI should contain query parameter ").append(paramName).append("\");\n");
            }
        }
        sb.append("        assertNotNull(request.headers(), \"Request headers should not be null\");\n");
        sb.append("        assertTrue(request.headers().firstValue(\"Accept\").isPresent(), \"Request should have Accept header\");\n");
        sb.append("    }\n\n");

        // Test: Invalid parameters
        for (Map<String, Object> param : parameters) {
            String paramName = (String) param.get("name");
            Boolean required = param.containsKey("required") ? (Boolean) param.get("required") : false;
            String paramIn = (String) param.get("in");

            if (required && "query".equals(paramIn)) {
                // Test missing required parameter
                sb.append("    @Test\n");
                sb.append("    @DisplayName(\"").append(summary != null ? summary : method + " " + path)
                        .append(" - Missing Required Parameter: ").append(paramName).append("\")\n");
                sb.append("    public void test").append(capitalize(testMethodName)).append("_MissingRequiredParam_").append(capitalize(paramName)).append("() {\n");
                sb.append("        // Arrange\n");
                sb.append("        Map<String, String> pathParams = new HashMap<>();\n");
                // Add path params if any
                for (Map<String, Object> p : parameters) {
                    String pName = (String) p.get("name");
                    String pIn = (String) p.get("in");
                    if ("path".equals(pIn)) {
                        String example = getParameterExample(p);
                        sb.append("        pathParams.put(\"").append(pName).append("\", \"").append(example).append("\");\n");
                    }
                }
                sb.append("        Map<String, String> queryParams = new HashMap<>();\n");
                // Add other query params except the missing one
                for (Map<String, Object> p : parameters) {
                    String pName = (String) p.get("name");
                    String pIn = (String) p.get("in");
                    if ("query".equals(pIn) && !pName.equals(paramName)) {
                        String example = getParameterExample(p);
                        sb.append("        queryParams.put(\"").append(pName).append("\", \"").append(example).append("\");\n");
                    }
                }
                sb.append("        String path = replacePathParameters(\"").append(path).append("\", pathParams);\n");
                sb.append("        URI uri = buildUri(path, queryParams);\n");
                sb.append("        HttpRequest request = HttpRequest.newBuilder()\n");
                sb.append("            .uri(uri)\n");
                sb.append("            .method(\"").append(method).append("\", HttpRequest.BodyPublishers.noBody())\n");
                sb.append("            .header(\"Accept\", \"application/json\")\n");
                sb.append("            .build();\n\n");
                sb.append("        // Act & Assert\n");
                sb.append("        assertNotNull(request, \"Request should not be null\");\n");
                sb.append("        assertFalse(uri.toString().contains(\"").append(paramName).append("=\"), \"URI should not contain missing required parameter ").append(paramName).append("\");\n");
                sb.append("        // TODO: Mock HTTP client and verify 400 Bad Request response when ").append(paramName).append(" is missing\n");
                sb.append("    }\n\n");
            }

            // Test invalid parameter values
            Map<String, Object> schema = param.containsKey("schema")
                    ? Util.asStringObjectMap(param.get("schema"))
                    : new HashMap<>();

            if (schema.containsKey("type")) {
                String type = (String) schema.get("type");
                if ("string".equals(type) && schema.containsKey("pattern")) {
                    String pattern = (String) schema.get("pattern");
                    sb.append("    @ParameterizedTest\n");
                    sb.append("    @ValueSource(strings = {\"invalid\", \"test123\", \"\"})\n");
                    sb.append("    @DisplayName(\"").append(summary != null ? summary : method + " " + path)
                            .append(" - Invalid ").append(paramName).append(" Format\")\n");
                    sb.append("    public void test").append(capitalize(testMethodName)).append("_Invalid").append(capitalize(paramName)).append("Format(String invalidValue) {\n");
                    sb.append("        // Arrange\n");
                    sb.append("        Map<String, String> pathParams = new HashMap<>();\n");
                    // Add path params if any
                    for (Map<String, Object> p : parameters) {
                        String pName = (String) p.get("name");
                        String pIn = (String) p.get("in");
                        if ("path".equals(pIn)) {
                            String example = getParameterExample(p);
                            sb.append("        pathParams.put(\"").append(pName).append("\", \"").append(example).append("\");\n");
                        }
                    }
                    sb.append("        Map<String, String> queryParams = new HashMap<>();\n");
                    // Add all query params including the invalid one
                    for (Map<String, Object> p : parameters) {
                        String pName = (String) p.get("name");
                        String pIn = (String) p.get("in");
                        if ("query".equals(pIn)) {
                            if (pName.equals(paramName)) {
                                sb.append("        queryParams.put(\"").append(paramName).append("\", invalidValue);\n");
                            } else {
                                String example = getParameterExample(p);
                                sb.append("        queryParams.put(\"").append(pName).append("\", \"").append(example).append("\");\n");
                            }
                        }
                    }
                    sb.append("        String path = replacePathParameters(\"").append(path).append("\", pathParams);\n");
                    sb.append("        URI uri = buildUri(path, queryParams);\n");
                    sb.append("        HttpRequest request = HttpRequest.newBuilder()\n");
                    sb.append("            .uri(uri)\n");
                    sb.append("            .method(\"").append(method).append("\", HttpRequest.BodyPublishers.noBody())\n");
                    sb.append("            .header(\"Accept\", \"application/json\")\n");
                    sb.append("            .build();\n\n");
                    sb.append("        // Act & Assert\n");
                    sb.append("        assertNotNull(request, \"Request should not be null\");\n");
                    sb.append("        assertTrue(uri.toString().contains(\"").append(paramName).append("=\"), \"URI should contain parameter ").append(paramName).append("\");\n");
                    sb.append("        assertTrue(uri.toString().contains(invalidValue), \"URI should contain invalid value\");\n");
                    sb.append("        // TODO: Mock HTTP client and verify 400 Bad Request response for invalid ").append(paramName).append(" format (pattern: ").append(pattern).append(")\n");
                    sb.append("    }\n\n");
                }
            }
        }

        // Test: Response status codes
        for (String statusCode : responses.keySet()) {
            if (!"default".equals(statusCode)) {
                sb.append("    @Test\n");
                sb.append("    @DisplayName(\"").append(summary != null ? summary : method + " " + path)
                        .append(" - Response Status ").append(statusCode).append("\")\n");
                sb.append("    public void test").append(capitalize(testMethodName)).append("_Status").append(statusCode).append("() throws Exception {\n");
                sb.append("        // Arrange\n");
                // Build request similar to valid request
                sb.append("        Map<String, String> pathParams = new HashMap<>();\n");
                for (Map<String, Object> param : parameters) {
                    String paramName = (String) param.get("name");
                    String paramIn = (String) param.get("in");
                    if ("path".equals(paramIn)) {
                        String example = getParameterExample(param);
                        sb.append("        pathParams.put(\"").append(paramName).append("\", \"").append(example).append("\");\n");
                    }
                }
                sb.append("        Map<String, String> queryParams = new HashMap<>();\n");
                for (Map<String, Object> param : parameters) {
                    String paramName = (String) param.get("name");
                    String paramIn = (String) param.get("in");
                    if ("query".equals(paramIn)) {
                        String example = getParameterExample(param);
                        sb.append("        queryParams.put(\"").append(paramName).append("\", \"").append(example).append("\");\n");
                    }
                }
                sb.append("        String path = replacePathParameters(\"").append(path).append("\", pathParams);\n");
                if (!queryParams.isEmpty()) {
                    sb.append("        URI uri = buildUri(path, queryParams);\n");
                } else {
                    sb.append("        URI uri = URI.create(BASE_URL + path);\n");
                }
                sb.append("        HttpRequest request = HttpRequest.newBuilder()\n");
                sb.append("            .uri(uri)\n");
                sb.append("            .method(\"").append(method).append("\", HttpRequest.BodyPublishers.noBody())\n");
                sb.append("            .header(\"Accept\", \"application/json\")\n");
                sb.append("            .build();\n\n");
                sb.append("        // Act\n");
                sb.append("        // TODO: Mock HTTP client to return ").append(statusCode).append(" status\n");
                sb.append("        // HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());\n\n");
                sb.append("        // Assert\n");
                sb.append("        assertNotNull(request, \"Request should not be null\");\n");
                sb.append("        // assertEquals(").append(statusCode).append(", response.statusCode(), \"Response status should be ").append(statusCode).append("\");\n");
                sb.append("        // TODO: Add assertions for response body, headers, etc. based on OpenAPI response schema\n");
                sb.append("    }\n\n");
            }
        }
    }

    /**
     * Generate test utilities
     */
    private void generateTestUtilities(String outputDir, String basePackage) throws IOException {
        String packageDir = outputDir + "/" + basePackage.replace(".", "/");
        Files.createDirectories(Paths.get(packageDir));

        // Generate TestUtils class
        String utilsContent = generateTestUtilsClass(basePackage);
        Files.write(Paths.get(packageDir, "TestUtils.java"), utilsContent.getBytes());
    }

    /**
     * Generate TestUtils helper class
     */
    private String generateTestUtilsClass(String basePackage) {
        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(basePackage).append(";\n\n");
        sb.append("import java.util.*;\n");
        sb.append("import java.util.regex.Pattern;\n\n");
        sb.append("/**\n");
        sb.append(" * Utility class for unit tests\n");
        sb.append(" */\n");
        sb.append("public class TestUtils {\n\n");
        sb.append("    /**\n");
        sb.append("     * Generate valid test data based on schema\n");
        sb.append("     */\n");
        sb.append("    public static Object generateTestData(Map<String, Object> schema) {\n");
        sb.append("        // TODO: Implement test data generation based on schema\n");
        sb.append("        return null;\n");
        sb.append("    }\n\n");
        sb.append("    /**\n");
        sb.append("     * Validate response against schema\n");
        sb.append("     */\n");
        sb.append("    public static boolean validateResponse(Object response, Map<String, Object> schema) {\n");
        sb.append("        // TODO: Implement response validation against schema\n");
        sb.append("        return true;\n");
        sb.append("    }\n\n");
        sb.append("}\n");
        return sb.toString();
    }

    // Helper methods
    private String getAPITitle(Map<String, Object> spec) {
        Map<String, Object> info = Util.asStringObjectMap(spec.get("info"));
        return info != null ? (String) info.get("title") : "API";
    }

    private String getOperationTag(Map<String, Object> operation) {
        List<String> tags = Util.asStringList(operation.get("tags"));
        return tags != null && !tags.isEmpty() ? tags.get(0) : "Default";
    }

    private String getParameterExample(Map<String, Object> param) {
        if (param.containsKey("example")) {
            return String.valueOf(param.get("example"));
        }
        if (param.containsKey("schema")) {
            Map<String, Object> schema = Util.asStringObjectMap(param.get("schema"));
            if (schema.containsKey("example")) {
                return String.valueOf(schema.get("example"));
            }
            String type = (String) schema.get("type");
            if ("string".equals(type)) {
                return "test-value";
            } else if ("integer".equals(type)) {
                return "123";
            } else if ("boolean".equals(type)) {
                return "true";
            }
        }
        return "example";
    }

    private String toClassName(String name) {
        if (name == null || name.isEmpty()) {
            return "Default";
        }
        String[] parts = name.split("[^a-zA-Z0-9]+");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                sb.append(Character.toUpperCase(part.charAt(0)));
                if (part.length() > 1) {
                    sb.append(part.substring(1));
                }
            }
        }
        return sb.toString();
    }

    private String toMethodName(String name) {
        if (name == null || name.isEmpty()) {
            return "test";
        }
        String[] parts = name.split("[^a-zA-Z0-9]+");
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (String part : parts) {
            if (!part.isEmpty()) {
                if (first) {
                    sb.append(Character.toLowerCase(part.charAt(0)));
                    if (part.length() > 1) {
                        sb.append(part.substring(1));
                    }
                    first = false;
                } else {
                    sb.append(Character.toUpperCase(part.charAt(0)));
                    if (part.length() > 1) {
                        sb.append(part.substring(1));
                    }
                }
            }
        }
        return sb.toString();
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return Character.toUpperCase(str.charAt(0)) + (str.length() > 1 ? str.substring(1) : "");
    }

    private String sanitizePath(String path) {
        return path.replaceAll("[^a-zA-Z0-9]", "_");
    }

    /**
     * Inner class to hold operation information
     */
    private static class OperationInfo {
        String path;
        String method;
        Map<String, Object> operation;
    }

    @Override
    public String getName() {
        return "Unit Test Generator";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getTestType() {
        return "unit";
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
