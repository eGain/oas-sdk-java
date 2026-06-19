package egain.oassdk.testgenerators.nodejs;

import egain.oassdk.Util;
import egain.oassdk.core.Constants;
import egain.oassdk.config.TestConfig;
import egain.oassdk.core.exceptions.GenerationException;
import egain.oassdk.testgenerators.common.TestSpecUtils;
import egain.oassdk.testgenerators.ConfigurableTestGenerator;
import egain.oassdk.testgenerators.TestGenerator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * NodeJS Jest unit test generator
 * Generates Jest unit tests for API endpoints based on OpenAPI specification
 */
public class JestUnitTestGenerator implements TestGenerator, ConfigurableTestGenerator {

    private TestConfig config;

    @Override
    public void generate(Map<String, Object> spec, String outputDir, TestConfig config, String testFramework) throws GenerationException {
        this.config = config;

        try {
            // Create output directory structure
            Path outputPath = Paths.get(outputDir, "unit");
            Files.createDirectories(outputPath);

            // Extract API information
            String apiTitle = TestSpecUtils.getApiTitle(spec);

            // Generate test files for each endpoint
            generateTestFiles(spec, outputPath.toString(), apiTitle);

            // Generate Jest configuration
            generateJestConfig(outputPath.toString());

            // Generate package.json
            generatePackageJson(outputPath.toString());

        } catch (Exception e) {
            throw new GenerationException("Failed to generate Jest unit tests: " + e.getMessage(), e);
        }
    }

    /**
     * Generate test files for all endpoints
     */
    private void generateTestFiles(Map<String, Object> spec, String outputDir, String apiTitle) throws IOException {
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

            String[] methods = Constants.HTTP_METHODS;
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

        // Generate test file for each tag
        for (Map.Entry<String, List<OperationInfo>> tagEntry : operationsByTag.entrySet()) {
            String tag = tagEntry.getKey();
            List<OperationInfo> operations = tagEntry.getValue();

            String fileName = toKebabCase(tag) + ".unit.test.js";

            String testFileContent = generateTestFile(fileName, tag, operations, spec);
            Files.write(Paths.get(outputDir, fileName), testFileContent.getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * Generate a test file for a group of operations
     */
    private String generateTestFile(String fileName, String tag, List<OperationInfo> operations, Map<String, Object> spec) {
        StringBuilder sb = new StringBuilder();

        // File header
        sb.append("/**\n");
        sb.append(" * Unit tests for ").append(tag).append(" API endpoints\n");
        sb.append(" * Generated from OpenAPI specification\n");
        sb.append(" */\n\n");

        // Test suite
        sb.append("describe('").append(tag).append(" Unit Tests', () => {\n\n");

        // Generate test cases for each operation
        for (OperationInfo opInfo : operations) {
            generateTestCases(sb, opInfo, spec);
        }

        sb.append("});\n");

        return sb.toString();
    }

    /**
     * Generate test cases for an operation
     */
    private void generateTestCases(StringBuilder sb, OperationInfo opInfo, Map<String, Object> spec) {
        Map<String, Object> operation = opInfo.operation;
        String operationId = (String) operation.get("operationId");
        String summary = (String) operation.get("summary");
        String method = opInfo.method.toLowerCase();
        String path = opInfo.path;

        // Get operation name for test
        String testName = operationId != null
                ? operationId
                : method + " " + path;

        // Extract parameters
        List<Map<String, Object>> parameters = Util.asStringObjectMapList(operation.get("parameters"));
        if (parameters == null) parameters = new ArrayList<>();

        // Extract responses
        Map<String, Object> responses = Util.asStringObjectMap(operation.get("responses"));
        if (responses == null) responses = new HashMap<>();

        // Test: Valid request
        sb.append("  describe('").append(summary != null ? summary : testName).append("', () => {\n");
        sb.append("    test('should handle valid request', () => {\n");
        sb.append("      // Arrange\n");
        sb.append("      // Note: This is a unit test template. Replace with actual implementation:\n");
        sb.append("      // 1. Mock the HTTP client or service layer\n");
        sb.append("      // 2. Call the method under test\n");
        sb.append("      // 3. Verify the response\n");

        // Build sample parameters
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

        if (!pathParams.isEmpty()) {
            sb.append("      const pathParams = {\n");
            for (Map.Entry<String, String> entry : pathParams.entrySet()) {
                sb.append("        ").append(entry.getKey()).append(": '").append(entry.getValue()).append("',\n");
            }
            sb.append("      };\n");
        }

        if (!queryParams.isEmpty()) {
            sb.append("      const queryParams = {\n");
            for (Map.Entry<String, String> entry : queryParams.entrySet()) {
                sb.append("        ").append(entry.getKey()).append(": '").append(entry.getValue()).append("',\n");
            }
            sb.append("      };\n");
        }

        sb.append("      \n");
        sb.append("      // Act & Assert\n");
        sb.append("      // TODO: Implement test logic\n");
        sb.append("      expect(true).toBe(true);\n");
        sb.append("    });\n\n");

        // Test: Missing required parameters
        for (Map<String, Object> param : parameters) {
            String paramName = (String) param.get("name");
            Boolean required = param.containsKey("required") ? (Boolean) param.get("required") : false;
            String paramIn = (String) param.get("in");

            if (required && "query".equals(paramIn)) {
                sb.append("    test('should reject missing required parameter: ").append(paramName).append("', () => {\n");
                sb.append("      // Arrange - Missing required parameter: ").append(paramName).append("\n");
                sb.append("      // Act & Assert\n");
                sb.append("      // Verify that the API returns 400 Bad Request\n");
                sb.append("      expect(true).toBe(true); // TODO: Implement validation logic\n");
                sb.append("    });\n\n");
            }

            // Test invalid parameter values
            Map<String, Object> schema = Util.asStringObjectMap(param.get("schema"));
            if (schema == null) schema = new HashMap<>();

            if (schema.containsKey("type")) {
                String type = (String) schema.get("type");
                if ("string".equals(type) && schema.containsKey("pattern")) {
                    sb.append("    test.each(['invalid', 'test123', ''])('should reject invalid ").append(paramName).append(" format: %s', (invalidValue) => {\n");
                    sb.append("      // Arrange - Invalid ").append(paramName).append(" value\n");
                    sb.append("      // Act & Assert\n");
                    sb.append("      // Verify that the API returns 400 Bad Request for invalid format\n");
                    sb.append("      expect(invalidValue).toBeDefined();\n");
                    sb.append("    });\n\n");
                }
            }
        }

        // Test: Response status codes
        for (String statusCode : responses.keySet()) {
            if (!"default".equals(statusCode)) {
                sb.append("    test('should return status ").append(statusCode).append("', () => {\n");
                sb.append("      // Arrange - Setup request for ").append(statusCode).append(" response\n");
                sb.append("      // Act & Assert\n");
                sb.append("      // Verify response status is ").append(statusCode).append("\n");
                sb.append("      expect(true).toBe(true); // TODO: Implement status code validation\n");
                sb.append("    });\n\n");
            }
        }

        sb.append("  });\n\n");
    }

    /**
     * Generate Jest configuration
     */
    private void generateJestConfig(String outputDir) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("module.exports = {\n");
        sb.append("  testEnvironment: 'node',\n");
        sb.append("  testMatch: ['**/*.unit.test.js'],\n");
        sb.append("  verbose: true,\n");
        sb.append("  collectCoverage: true,\n");
        sb.append("  coverageDirectory: 'coverage',\n");
        sb.append("  coverageReporters: ['text', 'text-summary', 'lcov', 'html'],\n");
        sb.append("  coverageThreshold: {\n");
        sb.append("    global: {\n");
        sb.append("      branches: 0,\n");
        sb.append("      functions: 0,\n");
        sb.append("      lines: 0,\n");
        sb.append("      statements: 0,\n");
        sb.append("    },\n");
        sb.append("  },\n");
        sb.append("};\n");

        Files.write(Paths.get(outputDir, "jest.config.js"), sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Generate package.json
     */
    private void generatePackageJson(String outputDir) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"name\": \"api-unit-tests\",\n");
        sb.append("  \"version\": \"1.0.0\",\n");
        sb.append("  \"description\": \"Unit tests for API\",\n");
        sb.append("  \"scripts\": {\n");
        sb.append("    \"test\": \"jest\",\n");
        sb.append("    \"test:watch\": \"jest --watch\",\n");
        sb.append("    \"test:coverage\": \"jest --coverage\"\n");
        sb.append("  },\n");
        sb.append("  \"devDependencies\": {\n");
        sb.append("    \"jest\": \"^29.0.0\"\n");
        sb.append("  }\n");
        sb.append("}\n");

        Files.write(Paths.get(outputDir, "package.json"), sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    // Helper methods
    private String getOperationTag(Map<String, Object> operation) {
        if (operation.get("tags") instanceof List<?> tags && !tags.isEmpty()) {
            return String.valueOf(tags.get(0));
        }
        return "Default";
    }

    private String getParameterExample(Map<String, Object> param) {
        if (param.containsKey("example")) {
            return String.valueOf(param.get("example"));
        }
        if (param.containsKey("schema")) {
            Map<String, Object> schema = Util.asStringObjectMap(param.get("schema"));
            if (schema != null && schema.containsKey("example")) {
                return String.valueOf(schema.get("example"));
            }
            if (schema != null) {
                String type = (String) schema.get("type");
                if ("string".equals(type)) {
                    return "test-value";
                } else if ("integer".equals(type)) {
                    return "123";
                } else if ("boolean".equals(type)) {
                    return "true";
                }
            }
        }
        return "example";
    }

    private String toKebabCase(String name) {
        if (name == null || name.isEmpty()) {
            return "default";
        }
        // Convert PascalCase/camelCase to kebab-case
        String result = name.replaceAll("([a-z])([A-Z])", "$1-$2");
        // Replace non-alphanumeric characters with hyphens
        result = result.replaceAll("[^a-zA-Z0-9]+", "-");
        return result.toLowerCase(Locale.ROOT);
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
        return "Jest Unit Test Generator";
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

