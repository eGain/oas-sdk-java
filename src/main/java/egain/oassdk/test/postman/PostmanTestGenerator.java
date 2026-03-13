package egain.oassdk.test.postman;

import egain.oassdk.Util;
import egain.oassdk.config.TestConfig;
import egain.oassdk.core.exceptions.GenerationException;
import egain.oassdk.testgenerators.ConfigurableTestGenerator;
import egain.oassdk.testgenerators.TestGenerator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Generates Postman test scripts for each API
 */
public class PostmanTestGenerator implements TestGenerator, ConfigurableTestGenerator {

    private TestConfig config;

    /**
     * Generate Postman test scripts
     *
     * @param spec      OpenAPI specification
     * @param outputDir Output directory
     * @throws GenerationException if generation fails
     */
    public void generateTestScripts(Map<String, Object> spec, String outputDir) throws GenerationException {
        try {
            Files.createDirectories(Paths.get(outputDir));

            // Generate Postman collection
            generatePostmanCollection(spec, outputDir);

            // Generate environment file
            generateEnvironmentFile(spec, outputDir);

            // Generate test scripts for each API
            generateIndividualTestScripts(spec, outputDir);

            // Generate Newman test runner
            generateNewmanTestRunner(spec, outputDir);

            // Generate CI/CD integration
            generateCICDIntegration(spec, outputDir);

        } catch (Exception e) {
            throw new GenerationException("Failed to generate Postman test scripts: " + e.getMessage(), e);
        }
    }

    // Interface implementation methods
    @Override
    public void generate(Map<String, Object> spec, String outputDir, TestConfig config, String testFramework) throws GenerationException {
        this.config = config;
        generateTestScripts(spec, outputDir);
    }

    @Override
    public String getName() {
        return "Postman Test Generator";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public void setConfig(TestConfig config) {
        this.config = config;
    }

    @Override
    public TestConfig getConfig() {
        return this.config;
    }

    @Override
    public String getTestType() {
        return "postman";
    }

    /**
     * Generate Postman collection
     */
    private void generatePostmanCollection(Map<String, Object> spec, String outputDir) throws IOException {
        String apiTitle = getAPITitle(spec);
        String apiVersion = getAPIVersion(spec);
        String apiDescription = getAPIDescription(spec);

        Map<String, Object> collection = new HashMap<>();
        collection.put("info", Map.of(
                "name", apiTitle + " API Tests",
                "description", apiDescription,
                "version", apiVersion,
                "schema", "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
        ));

        // Generate items (requests) from paths
        List<Map<String, Object>> items = generateCollectionItems(spec);
        collection.put("item", items);

        // Generate variables
        List<Map<String, Object>> variables = generateVariables(spec);
        collection.put("variable", variables);

        // Convert to JSON
        String json = convertToJson(collection);

        // Write to file
        String fileName = apiTitle.replaceAll("[^a-zA-Z0-9]", "-") + "-API.postman_collection.json";
        Files.write(Paths.get(outputDir, fileName), json.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Generate collection items from API paths
     */
    private List<Map<String, Object>> generateCollectionItems(Map<String, Object> spec) {
        List<Map<String, Object>> items = new ArrayList<>();
        Map<String, Object> paths = Util.asStringObjectMap(spec.get("paths"));

        if (paths == null) return items;

        for (Map.Entry<String, Object> pathEntry : paths.entrySet()) {
            String path = pathEntry.getKey();
            Map<String, Object> pathItem = Util.asStringObjectMap(pathEntry.getValue());

            if (pathItem == null) continue;

            // Group operations by tag
            Map<String, List<Map<String, Object>>> taggedOperations = groupOperationsByTag(pathItem, path);

            for (Map.Entry<String, List<Map<String, Object>>> tagEntry : taggedOperations.entrySet()) {
                String tag = tagEntry.getKey();
                List<Map<String, Object>> operations = tagEntry.getValue();

                Map<String, Object> folder = new HashMap<>();
                folder.put("name", tag);
                folder.put("item", operations);
                items.add(folder);
            }
        }

        return items;
    }

    /**
     * Group operations by tag
     */
    private Map<String, List<Map<String, Object>>> groupOperationsByTag(Map<String, Object> pathItem, String path) {
        Map<String, List<Map<String, Object>>> taggedOperations = new HashMap<>();

        String[] methods = {"get", "post", "put", "delete", "patch", "head", "options", "trace"};
        for (String method : methods) {
            if (pathItem.containsKey(method)) {
                Map<String, Object> operation = Util.asStringObjectMap(pathItem.get(method));
                if (operation == null) continue;

                String tag = getOperationTag(operation);
                Map<String, Object> request = generatePostmanRequest(method, path, operation);

                taggedOperations.computeIfAbsent(tag, k -> new ArrayList<>()).add(request);
            }
        }

        return taggedOperations;
    }

    /**
     * Generate Postman request
     */
    private Map<String, Object> generatePostmanRequest(String method, String path, Map<String, Object> operation) {
        String operationId = (String) operation.get("operationId");
        String summary = (String) operation.get("summary");
        String description = (String) operation.get("description");

        Map<String, Object> request = new HashMap<>();
        request.put("name", summary != null ? summary : operationId != null ? operationId : method.toUpperCase() + " " + path);
        request.put("request", Map.of(
                "method", method.toUpperCase(),
                "header", generateHeaders(operation),
                "url", generateUrl(path, operation),
                "body", generateBody(operation),
                "description", description != null ? description : ""
        ));

        // Add tests
        request.put("event", generateTests(operation));

        return request;
    }

    /**
     * Generate headers
     */
    private List<Map<String, Object>> generateHeaders(Map<String, Object> operation) {
        List<Map<String, Object>> headers = new ArrayList<>();

        // Add common headers
        headers.add(Map.of("key", "Content-Type", "value", "application/json"));
        headers.add(Map.of("key", "Accept", "value", "application/json"));

        // Add operation-specific headers
        if (operation.containsKey("parameters")) {
            List<Map<String, Object>> parameters = Util.asStringObjectMapList(operation.get("parameters"));
            for (Map<String, Object> param : parameters) {
                if ("header".equals(param.get("in"))) {
                    headers.add(Map.of(
                            "key", param.get("name"),
                            "value", "{{" + param.get("name") + "}}",
                            "description", param.get("description")
                    ));
                }
            }
        }

        return headers;
    }

    /**
     * Generate URL
     */
    private Map<String, Object> generateUrl(String path, Map<String, Object> operation) {
        Map<String, Object> url = new HashMap<>();
        url.put("raw", "{{base_url}}" + path);
        url.put("host", List.of("{{base_url}}"));
        url.put("path", Arrays.asList(path.split("/")));

        // Add query parameters
        List<Map<String, Object>> queryParams = new ArrayList<>();
        if (operation.containsKey("parameters")) {
            List<Map<String, Object>> parameters = Util.asStringObjectMapList(operation.get("parameters"));
            for (Map<String, Object> param : parameters) {
                if ("query".equals(param.get("in"))) {
                    queryParams.add(Map.of(
                            "key", param.get("name"),
                            "value", "{{" + param.get("name") + "}}",
                            "description", param.get("description")
                    ));
                }
            }
        }

        if (!queryParams.isEmpty()) {
            url.put("query", queryParams);
        }

        return url;
    }

    /**
     * Generate request body
     */
    private Map<String, Object> generateBody(Map<String, Object> operation) {
        if (!operation.containsKey("requestBody")) {
            return Map.of("mode", "raw", "raw", "");
        }

        Map<String, Object> requestBody = Util.asStringObjectMap(operation.get("requestBody"));
        Map<String, Object> content = Util.asStringObjectMap(requestBody.get("content"));

        if (content == null || !content.containsKey("application/json")) {
            return Map.of("mode", "raw", "raw", "");
        }

        Map<String, Object> schema = Util.asStringObjectMap(content.get("application/json"));
        String example = generateExampleFromSchema(schema);

        return Map.of(
                "mode", "raw",
                "raw", example,
                "options", Map.of("raw", Map.of("language", "json"))
        );
    }

    /**
     * Generate tests
     */
    private List<Map<String, Object>> generateTests(Map<String, Object> operation) {
        List<Map<String, Object>> events = new ArrayList<>();

        Map<String, Object> testEvent = new HashMap<>();
        testEvent.put("listen", "test");
        testEvent.put("script", Map.of(
                "type", "text/javascript",
                "exec", generateTestScript(operation)
        ));

        events.add(testEvent);
        return events;
    }

    /**
     * Generate test script
     */
    private List<String> generateTestScript(Map<String, Object> operation) {
        List<String> script = new ArrayList<>();

        script.add("// Test response status");
        script.add("pm.test(\"Status code is 200\", function () {");
        script.add("    pm.response.to.have.status(200);");
        script.add("});");
        script.add("");

        script.add("// Test response time");
        script.add("pm.test(\"Response time is less than 2000ms\", function () {");
        script.add("    pm.expect(pm.response.responseTime).to.be.below(2000);");
        script.add("});");
        script.add("");

        script.add("// Test response headers");
        script.add("pm.test(\"Content-Type is application/json\", function () {");
        script.add("    pm.expect(pm.response.headers.get(\"Content-Type\")).to.include(\"application/json\");");
        script.add("});");
        script.add("");

        // Add response validation if schema is available
        if (operation.containsKey("responses")) {
            Map<String, Object> responses = Util.asStringObjectMap(operation.get("responses"));
            if (responses.containsKey("200")) {
                Map<String, Object> response200 = Util.asStringObjectMap(responses.get("200"));
                if (response200.containsKey("content")) {
                    script.add("// Test response body structure");
                    script.add("pm.test(\"Response has required fields\", function () {");
                    script.add("    const jsonData = pm.response.json();");
                    script.add("    // Add specific field validations here");
                    script.add("});");
                }
            }
        }

        return script;
    }

    /**
     * Generate variables
     */
    private List<Map<String, Object>> generateVariables(Map<String, Object> spec) {
        List<Map<String, Object>> variables = new ArrayList<>();

        // Base URL
        variables.add(Map.of(
                "key", "base_url",
                "value", "http://localhost:8080",
                "type", "string"
        ));

        // Add server URLs if available
        if (spec.containsKey("servers")) {
            List<Map<String, Object>> servers = Util.asStringObjectMapList(spec.get("servers"));
            if (servers != null && !servers.isEmpty()) {
                String serverUrl = (String) servers.getFirst().get("url");
                if (serverUrl != null) {
                    variables.add(Map.of(
                            "key", "base_url",
                            "value", serverUrl,
                            "type", "string"
                    ));
                }
            }
        }

        return variables;
    }

    /**
     * Generate environment file
     */
    private void generateEnvironmentFile(Map<String, Object> spec, String outputDir) throws IOException {
        String apiTitle = getAPITitle(spec);

        Map<String, Object> environment = new HashMap<>();
        environment.put("id", UUID.randomUUID().toString());
        environment.put("name", apiTitle + " Environment");
        environment.put("values", generateVariables(spec));

        String json = convertToJson(environment);
        String fileName = apiTitle.replaceAll("[^a-zA-Z0-9]", "-") + "-Environment.postman_environment.json";
        Files.write(Paths.get(outputDir, fileName), json.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Generate individual test scripts
     */
    private void generateIndividualTestScripts(Map<String, Object> spec, String outputDir) throws IOException {
        Map<String, Object> paths = Util.asStringObjectMap(spec.get("paths"));
        if (paths == null) return;

        for (Map.Entry<String, Object> pathEntry : paths.entrySet()) {
            String path = pathEntry.getKey();
            Map<String, Object> pathItem = Util.asStringObjectMap(pathEntry.getValue());

            if (pathItem == null) continue;

            String[] methods = {"get", "post", "put", "delete", "patch"};
            for (String method : methods) {
                if (pathItem.containsKey(method)) {
                    Map<String, Object> operation = Util.asStringObjectMap(pathItem.get(method));
                    generateIndividualTestScript(path, method, operation, outputDir);
                }
            }
        }
    }

    /**
     * Generate individual test script
     */
    private void generateIndividualTestScript(String path, String method, Map<String, Object> operation, String outputDir) throws IOException {
        String operationId = (String) operation.get("operationId");
        String fileName = (operationId != null ? operationId : method + "_" + path.replaceAll("[^a-zA-Z0-9]", "_")) + "_test.js";

        List<String> testScript = generateTestScript(operation);
        String content = String.join("\n", testScript);

        Files.write(Paths.get(outputDir, fileName), content.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Generate Newman test runner
     */
    private void generateNewmanTestRunner(Map<String, Object> spec, String outputDir) throws IOException {
        String apiTitle = getAPITitle(spec);
        String collectionFile = apiTitle.replaceAll("[^a-zA-Z0-9]", "-") + "-API.postman_collection.json";
        String environmentFile = apiTitle.replaceAll("[^a-zA-Z0-9]", "-") + "-Environment.postman_environment.json";

        String newmanScript = """
                #!/bin/bash
                
                set -e
                
                echo "Running Postman tests with Newman..."
                
                # Check if Newman is installed
                if ! command -v newman &> /dev/null; then
                    echo "Installing Newman..."
                    npm install -g newman
                fi
                
                # Run basic tests
                echo "Running basic API tests..."
                newman run %s -e %s --reporters cli,json --reporter-json-export basic-test-results.json
                
                # Run with different environments
                echo "Running tests with different environments..."
                newman run %s -e %s --env-var "base_url=http://staging.example.com" --reporters cli,json --reporter-json-export staging-test-results.json
                
                # Run performance tests
                echo "Running performance tests..."
                newman run %s -e %s --reporters cli,json --reporter-json-export performance-test-results.json --timeout-request 5000
                
                # Generate report
                echo "Generating test report..."
                newman run %s -e %s --reporters html --reporter-html-export test-report.html
                
                echo "All tests completed!"
                echo "Results saved to: test-report.html"
                """.formatted(collectionFile, environmentFile, collectionFile, environmentFile, collectionFile, environmentFile, collectionFile, environmentFile);

        Files.write(Paths.get(outputDir, "run-newman-tests.sh"), newmanScript.getBytes(StandardCharsets.UTF_8));

        // Make script executable
        Path scriptPath = Paths.get(outputDir, "run-newman-tests.sh");
        scriptPath.toFile().setExecutable(true);
    }

    /**
     * Generate CI/CD integration
     */
    private void generateCICDIntegration(Map<String, Object> spec, String outputDir) throws IOException {
        // Generate GitHub Actions workflow
        String githubActions = """
                name: Postman API Testing
                
                on:
                  push:
                    branches: [ main, develop ]
                  pull_request:
                    branches: [ main ]
                  schedule:
                    - cron: '0 2 * * *'  # Run daily at 2 AM
                
                jobs:
                  postman-tests:
                    runs-on: ubuntu-latest
                
                    steps:
                    - uses: actions/checkout@v3
                
                    - name: Set up Node.js
                      uses: actions/setup-node@v3
                      with:
                        node-version: '18'
                
                    - name: Install Newman
                      run: npm install -g newman
                
                    - name: Run Postman tests
                      run: ./run-newman-tests.sh
                
                    - name: Upload test results
                      uses: actions/upload-artifact@v3
                      if: always()
                      with:
                        name: postman-results
                        path: |
                          basic-test-results.json
                          staging-test-results.json
                          performance-test-results.json
                          test-report.html
                """;

        Files.write(Paths.get(outputDir, "github-actions.yml"), githubActions.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Helper methods
     */
    private String getAPITitle(Map<String, Object> spec) {
        Map<String, Object> info = Util.asStringObjectMap(spec.get("info"));
        String title = info != null ? (String) info.get("title") : null;
        return title != null ? title : "API";
    }

    private String getAPIVersion(Map<String, Object> spec) {
        Map<String, Object> info = Util.asStringObjectMap(spec.get("info"));
        String version = info != null ? (String) info.get("version") : null;
        return version != null ? version : "1.0.0";
    }

    private String getAPIDescription(Map<String, Object> spec) {
        Map<String, Object> info = Util.asStringObjectMap(spec.get("info"));
        String desc = info != null ? (String) info.get("description") : null;
        return desc != null ? desc : "Generated API";
    }

    private String getOperationTag(Map<String, Object> operation) {
        List<String> tags = Util.asStringList(operation.get("tags"));
        return tags != null && !tags.isEmpty() ? tags.getFirst() : "Default";
    }

    private String generateExampleFromSchema(Map<String, Object> schema) {
        // Simple example generation - in a real implementation, this would be more sophisticated
        return "{\n  \"example\": \"value\"\n}";
    }

    private String convertToJson(Map<String, Object> data) {
        // Simple JSON conversion - in a real implementation, use a proper JSON library
        StringBuilder json = new StringBuilder();
        json.append("{\n");

        boolean first = true;
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            if (!first) json.append(",\n");
            json.append("  \"").append(entry.getKey()).append("\": ");

            Object value = entry.getValue();
            switch (value) {
                case String s -> json.append("\"").append(value).append("\"");
                case List<?> list -> json.append(convertListToJson(list));
                case Map<?, ?> map -> json.append(convertMapToJson(map));
                case null, default -> json.append(value);
            }

            first = false;
        }

        json.append("\n}");
        return json.toString();
    }

    private String convertListToJson(List<?> list) {
        StringBuilder json = new StringBuilder();
        json.append("[\n");

        boolean first = true;
        for (Object item : list) {
            if (!first) json.append(",\n");
            json.append("    ");

            if (item instanceof String) {
                json.append("\"").append(item).append("\"");
            } else if (item instanceof Map) {
                json.append(convertMapToJson((Map<?, ?>) item));
            } else {
                json.append(item);
            }

            first = false;
        }

        json.append("\n  ]");
        return json.toString();
    }

    private String convertMapToJson(Map<?, ?> map) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");

        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!first) json.append(",\n");
            json.append("    \"").append(entry.getKey()).append("\": ");

            Object value = entry.getValue();
            switch (value) {
                case String s -> json.append("\"").append(value).append("\"");
                case List<?> list -> json.append(convertListToJson(list));
                case Map<?, ?> map1 -> json.append(convertMapToJson(map1));
                case null, default -> json.append(value);
            }

            first = false;
        }

        json.append("\n  }");
        return json.toString();
    }
}
