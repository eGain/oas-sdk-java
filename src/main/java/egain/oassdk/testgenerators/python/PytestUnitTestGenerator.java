package egain.oassdk.testgenerators.python;

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
 * Python pytest unit test generator
 * Generates pytest unit tests for API endpoints based on OpenAPI specification
 */
public class PytestUnitTestGenerator implements TestGenerator, ConfigurableTestGenerator {

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

            // Generate test modules for each endpoint
            generateTestModules(spec, outputPath.toString(), apiTitle);

            // Generate conftest.py for pytest fixtures
            generateConftest(outputPath.toString());

            // Generate pytest.ini with coverage configuration
            generatePytestConfig(outputPath.toString());

            // Generate requirements.txt
            generateRequirements(outputPath.toString());

        } catch (Exception e) {
            throw new GenerationException("Failed to generate pytest unit tests: " + e.getMessage(), e);
        }
    }

    /**
     * Generate test modules for all endpoints
     */
    private void generateTestModules(Map<String, Object> spec, String outputDir, String apiTitle) throws IOException {
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

        // Create __init__.py to make it a package
        Files.write(Paths.get(outputDir, "__init__.py"), "".getBytes(StandardCharsets.UTF_8));

        // Generate test module for each tag
        for (Map.Entry<String, List<OperationInfo>> tagEntry : operationsByTag.entrySet()) {
            String tag = tagEntry.getKey();
            List<OperationInfo> operations = tagEntry.getValue();

            String moduleName = "test_" + toSnakeCase(tag) + "_unit";

            String testModuleContent = generateTestModule(moduleName, tag, operations, spec);
            Files.write(Paths.get(outputDir, moduleName + ".py"), testModuleContent.getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * Generate a test module for a group of operations
     */
    private String generateTestModule(String moduleName, String tag, List<OperationInfo> operations, Map<String, Object> spec) {
        StringBuilder sb = new StringBuilder();

        // Module docstring
        sb.append("\"\"\"Unit tests for ").append(tag).append(" API endpoints\n\n");
        sb.append("Generated from OpenAPI specification\n");
        sb.append("\"\"\"\n\n");

        // Imports
        sb.append("import pytest\n");
        sb.append("from unittest.mock import Mock, patch, MagicMock\n");
        sb.append("from typing import Dict, Any\n\n");

        // Test class
        sb.append("class Test").append(toPascalCase(tag)).append("Unit:\n");
        sb.append("    \"\"\"Unit tests for ").append(tag).append(" endpoints\"\"\"\n\n");

        // Generate test methods for each operation
        for (OperationInfo opInfo : operations) {
            generateTestMethods(sb, opInfo, spec);
        }

        return sb.toString();
    }

    /**
     * Generate test methods for an operation
     */
    private void generateTestMethods(StringBuilder sb, OperationInfo opInfo, Map<String, Object> spec) {
        Map<String, Object> operation = opInfo.operation;
        String operationId = (String) operation.get("operationId");
        String summary = (String) operation.get("summary");
        String method = opInfo.method.toLowerCase();
        String path = opInfo.path;

        // Get operation name for test method
        String testMethodName = operationId != null
                ? toSnakeCase(operationId)
                : toSnakeCase(method + "_" + sanitizePath(path));

        // Extract parameters
        List<Map<String, Object>> parameters = operation.containsKey("parameters")
                ? Util.asStringObjectMapList(operation.get("parameters"))
                : new ArrayList<>();

        // Extract responses
        Map<String, Object> responses = operation.containsKey("responses")
                ? Util.asStringObjectMap(operation.get("responses"))
                : new HashMap<>();

        // Test: Valid request
        sb.append("    def test_").append(testMethodName).append("_valid_request(self):\n");
        sb.append("        \"\"\"Test ").append(summary != null ? summary : method.toUpperCase() + " " + path).append(" - Valid Request\"\"\"\n");
        sb.append("        # Arrange\n");
        sb.append("        # Note: This is a unit test template. Replace with actual implementation:\n");
        sb.append("        # 1. Mock the HTTP client or service layer\n");
        sb.append("        # 2. Call the method under test\n");
        sb.append("        # 3. Verify the response\n");

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
            sb.append("        path_params = {\n");
            for (Map.Entry<String, String> entry : pathParams.entrySet()) {
                sb.append("            '").append(entry.getKey()).append("': '").append(entry.getValue()).append("',\n");
            }
            sb.append("        }\n");
        }

        if (!queryParams.isEmpty()) {
            sb.append("        query_params = {\n");
            for (Map.Entry<String, String> entry : queryParams.entrySet()) {
                sb.append("            '").append(entry.getKey()).append("': '").append(entry.getValue()).append("',\n");
            }
            sb.append("        }\n");
        }

        sb.append("        \n");
        sb.append("        # Act & Assert\n");
        sb.append("        # TODO: Implement test logic\n");
        sb.append("        assert True, 'Test placeholder - implement validation logic'\n");
        sb.append("        \n\n");

        // Test: Missing required parameters
        for (Map<String, Object> param : parameters) {
            String paramName = (String) param.get("name");
            Boolean required = param.containsKey("required") ? (Boolean) param.get("required") : false;
            String paramIn = (String) param.get("in");

            if (required && "query".equals(paramIn)) {
                sb.append("    def test_").append(testMethodName).append("_missing_").append(toSnakeCase(paramName)).append("(self):\n");
                sb.append("        \"\"\"Test ").append(summary != null ? summary : method.toUpperCase() + " " + path)
                        .append(" - Missing Required Parameter: ").append(paramName).append("\"\"\"\n");
                sb.append("        # Arrange - Missing required parameter: ").append(paramName).append("\n");
                sb.append("        # Act & Assert\n");
                sb.append("        # Verify that the API returns 400 Bad Request\n");
                sb.append("        assert True, 'Test placeholder - implement validation logic'\n");
                sb.append("        \n\n");
            }

            // Test invalid parameter values
            Map<String, Object> schema = param.containsKey("schema")
                    ? Util.asStringObjectMap(param.get("schema"))
                    : new HashMap<>();

            if (schema.containsKey("type")) {
                String type = (String) schema.get("type");
                if ("string".equals(type) && schema.containsKey("pattern")) {
                    sb.append("    @pytest.mark.parametrize('invalid_value', ['invalid', 'test123', ''])\n");
                    sb.append("    def test_").append(testMethodName).append("_invalid_").append(toSnakeCase(paramName)).append("_format(self, invalid_value):\n");
                    sb.append("        \"\"\"Test ").append(summary != null ? summary : method.toUpperCase() + " " + path)
                            .append(" - Invalid ").append(paramName).append(" Format\"\"\"\n");
                    sb.append("        # Arrange\n");
                    sb.append("        # Invalid ").append(paramName).append(" value\n");
                    sb.append("        # Act & Assert\n");
                    sb.append("        # Verify that the API returns 400 Bad Request for invalid format\n");
                    sb.append("        assert invalid_value is not None, 'Test value should not be None'\n");
                    sb.append("        \n\n");
                }
            }
        }

        // Test: Response status codes
        for (String statusCode : responses.keySet()) {
            if (!"default".equals(statusCode)) {
                sb.append("    def test_").append(testMethodName).append("_status_").append(statusCode).append("(self):\n");
                sb.append("        \"\"\"Test ").append(summary != null ? summary : method.toUpperCase() + " " + path)
                        .append(" - Response Status ").append(statusCode).append("\"\"\"\n");
                sb.append("        # Arrange\n");
                sb.append("        # Setup request for ").append(statusCode).append(" response\n");
                sb.append("        # Act & Assert\n");
                sb.append("        # Verify response status is ").append(statusCode).append("\n");
                sb.append("        assert True, 'Test placeholder - implement status code validation'\n");
                sb.append("        \n\n");
            }
        }
    }

    /**
     * Generate conftest.py for pytest fixtures
     */
    private void generateConftest(String outputDir) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("\"\"\"Pytest configuration and fixtures for unit tests\"\"\"\n\n");
        sb.append("import pytest\n");
        sb.append("from unittest.mock import Mock, MagicMock\n\n");

        sb.append("@pytest.fixture\n");
        sb.append("def mock_client():\n");
        sb.append("    \"\"\"Provide a mock HTTP client\"\"\"\n");
        sb.append("    return Mock()\n\n");

        sb.append("@pytest.fixture\n");
        sb.append("def mock_response():\n");
        sb.append("    \"\"\"Provide a mock HTTP response\"\"\"\n");
        sb.append("    response = Mock()\n");
        sb.append("    response.status_code = 200\n");
        sb.append("    response.json.return_value = {}\n");
        sb.append("    return response\n");

        Files.write(Paths.get(outputDir, "conftest.py"), sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Generate requirements.txt for pytest
     */
    private void generateRequirements(String outputDir) throws IOException {
        String requirements = "# Test dependencies\n" +
                "pytest>=7.0.0\n" +
                "pytest-cov>=4.0.0\n" +
                "pytest-mock>=3.10.0\n";

        Files.write(Paths.get(outputDir, "requirements.txt"), requirements.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Generate pytest.ini with code coverage configuration
     */
    private void generatePytestConfig(String outputDir) throws IOException {
        String configContent = "# Pytest configuration with code coverage\n" +
                "# Generated from OpenAPI specification\n\n" +
                "[pytest]\n" +
                "addopts = -v --tb=short --strict-markers --cov=. --cov-report=term-missing --cov-report=html:coverage_html\n" +
                "testpaths = .\n" +
                "python_files = test_*.py\n" +
                "python_classes = Test*\n" +
                "python_functions = test_*\n" +
                "markers =\n" +
                "    unit: Unit tests\n";

        Files.write(Paths.get(outputDir, "pytest.ini"), configContent.getBytes(StandardCharsets.UTF_8));
    }

    // Helper methods
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

    private String toSnakeCase(String name) {
        if (name == null || name.isEmpty()) {
            return "default";
        }
        // Convert PascalCase/camelCase to snake_case
        String result = name.replaceAll("([a-z])([A-Z])", "$1_$2");
        // Replace non-alphanumeric characters with underscores
        result = result.replaceAll("[^a-zA-Z0-9]+", "_");
        return result.toLowerCase(Locale.ROOT);
    }

    private String toPascalCase(String name) {
        if (name == null || name.isEmpty()) {
            return "Default";
        }
        String[] parts = name.split("[^a-zA-Z0-9]+");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                sb.append(Character.toUpperCase(part.charAt(0)));
                if (part.length() > 1) {
                    sb.append(part.substring(1).toLowerCase(Locale.ROOT));
                }
            }
        }
        return sb.toString();
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
        return "Pytest Unit Test Generator";
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

