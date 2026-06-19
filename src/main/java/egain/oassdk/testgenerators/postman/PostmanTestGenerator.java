package egain.oassdk.testgenerators.postman;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import egain.oassdk.Util;
import egain.oassdk.core.Constants;
import egain.oassdk.config.TestConfig;
import egain.oassdk.core.exceptions.GenerationException;
import egain.oassdk.testgenerators.common.TestSpecUtils;
import egain.oassdk.testgenerators.ConfigurableTestGenerator;
import egain.oassdk.testgenerators.IntegrationScenarioSupport;
import egain.oassdk.testgenerators.TestGenerator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 * Postman collection generator.
 * <p>Optional {@link TestConfig#getAdditionalProperties()} keys:
 * <ul>
 *   <li>{@code postmanNegativeTests} (Boolean, default {@code true}) — emit Negative-TCs folders</li>
 *   <li>{@code postmanNegativeTestsMaxPerOperation} (Number, default {@code 50}) — cap negative cases per operation</li>
 *   <li>{@code postmanAssertErrorExamples} (Boolean, default {@code false}) — assert {@code code} matches 4xx response examples when present</li>
 * </ul>
 */
public class PostmanTestGenerator implements TestGenerator, ConfigurableTestGenerator {

    private TestConfig config;
    private final ObjectMapper objectMapper;

    public PostmanTestGenerator() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.configure(SerializationFeature.INDENT_OUTPUT, false);
    }

    @Override
    public void generate(Map<String, Object> spec, String outputDir, TestConfig config, String testFramework) throws GenerationException {
        this.config = config;

        if (outputDir == null) {
            throw new IllegalArgumentException("Output directory cannot be null");
        }
        try {
            // Create output directory
            Files.createDirectories(Paths.get(outputDir));

            // Generate Postman collection
            generatePostmanCollection(spec, outputDir);

            // Generate environment file
            generateEnvironmentFile(spec, outputDir);

            // Generate test scripts
            generateTestScripts(spec, outputDir);

        } catch (Exception e) {
            String errorMsg = e.getMessage();
            if (errorMsg == null || errorMsg.isEmpty()) {
                errorMsg = e.getClass().getSimpleName() + " at " + e.getStackTrace()[0].toString();
            }
            throw new GenerationException("Failed to generate Postman tests: " + errorMsg, e);
        }
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
    public String getTestType() {
        return "postman";
    }

    @Override
    public void setConfig(TestConfig config) {
        this.config = config;
    }

    @Override
    public TestConfig getConfig() {
        return this.config;
    }

    /**
     * Generate Postman collection
     */
    private void generatePostmanCollection(Map<String, Object> spec, String outputDir) throws IOException {
        String apiTitle = TestSpecUtils.getApiTitle(spec);
        String apiVersion = getAPIVersion(spec);
        String apiDescription = getAPIDescription(spec);

        // Ensure all values are non-null for Map.of()
        String safeTitle = apiTitle != null ? apiTitle : "API";
        String safeVersion = apiVersion != null ? apiVersion : "1.0.0";
        String safeDescription = apiDescription != null ? apiDescription : "Generated API";

        Map<String, Object> collection = new HashMap<>();
        Map<String, Object> info = new HashMap<>();
        info.put("name", safeTitle + " API Tests");
        info.put("description", safeDescription);
        info.put("version", safeVersion);
        info.put("schema", "https://schema.getpostman.com/json/collection/v2.1.0/collection.json");
        collection.put("info", info);

        // Generate items (requests) from paths
        List<Map<String, Object>> items = generateCollectionItems(spec);
        collection.put("item", items);

        // Generate variables
        List<Map<String, Object>> variables = generateVariables(spec);
        collection.put("variable", variables);

        // Convert to JSON
        String json = convertToJson(collection);

        // Write to file
        String fileName = safeTitle.replaceAll("[^a-zA-Z0-9]", "-") + "-API.postman_collection.json";
        Files.write(Paths.get(outputDir, fileName), json.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Generate collection items from API paths
     */
    private List<Map<String, Object>> generateCollectionItems(Map<String, Object> spec) {
        List<Map<String, Object>> items = new ArrayList<>();
        Map<String, Object> paths = Util.asStringObjectMap(spec.get("paths"));

        if (paths == null) return items;

        // First, collect all operations grouped by tag across all paths
        Map<String, List<Map<String, Object>>> allTaggedOperations = new HashMap<>();

        for (Map.Entry<String, Object> pathEntry : paths.entrySet()) {
            String path = pathEntry.getKey();
            Map<String, Object> pathItem = Util.asStringObjectMap(pathEntry.getValue());

            if (pathItem == null) continue;

            // Get operations for this path grouped by tag
            Map<String, List<Map<String, Object>>> taggedOperations = groupOperationsByTag(pathItem, path, spec);

            // Merge into the global tag map
            for (Map.Entry<String, List<Map<String, Object>>> tagEntry : taggedOperations.entrySet()) {
                String tag = tagEntry.getKey();
                List<Map<String, Object>> operations = tagEntry.getValue();

                // Add operations to the existing tag list or create a new one
                allTaggedOperations.computeIfAbsent(tag, k -> new ArrayList<>()).addAll(operations);
            }
        }

        // Now create folders for each unique tag
        for (Map.Entry<String, List<Map<String, Object>>> tagEntry : allTaggedOperations.entrySet()) {
            String tag = tagEntry.getKey();
            List<Map<String, Object>> operations = tagEntry.getValue();

            Map<String, Object> folder = new HashMap<>();
            folder.put("name", tag);
            folder.put("item", operations);
            items.add(folder);
        }

        return items;
    }

    /**
     * Group operations by tag (each operation is a folder: happy path + optional Negative-TCs).
     */
    private Map<String, List<Map<String, Object>>> groupOperationsByTag(Map<String, Object> pathItem, String path,
                                                                        Map<String, Object> spec) {
        Map<String, List<Map<String, Object>>> taggedOperations = new HashMap<>();

        String[] methods = Constants.HTTP_METHODS;
        for (String method : methods) {
            if (pathItem.containsKey(method)) {
                Map<String, Object> operation = Util.asStringObjectMap(pathItem.get(method));
                if (operation == null) {
                    continue;
                }

                String tag = getOperationTag(operation);
                Map<String, Object> opFolder = buildOperationFolder(method, path, operation, spec);
                taggedOperations.computeIfAbsent(tag, k -> new ArrayList<>()).add(opFolder);
            }
        }

        return taggedOperations;
    }

    private boolean isPostmanNegativeTestsEnabled() {
        if (config == null || config.getAdditionalProperties() == null) {
            return true;
        }
        Object v = config.getAdditionalProperties().get("postmanNegativeTests");
        if (v instanceof Boolean b) {
            return b;
        }
        return true;
    }

    private int postmanNegativeTestsMaxPerOperation() {
        if (config == null || config.getAdditionalProperties() == null) {
            return 50;
        }
        Object v = config.getAdditionalProperties().get("postmanNegativeTestsMaxPerOperation");
        if (v instanceof Number n) {
            return Math.max(0, n.intValue());
        }
        return 50;
    }

    private boolean isPostmanAssertErrorExamples() {
        if (config == null || config.getAdditionalProperties() == null) {
            return false;
        }
        return Boolean.TRUE.equals(config.getAdditionalProperties().get("postmanAssertErrorExamples"));
    }

    private Map<String, Object> buildOperationFolder(String method, String path, Map<String, Object> operation,
                                                     Map<String, Object> spec) {
        String operationId = (String) operation.get("operationId");
        String summary = (String) operation.get("summary");
        String opName = summary != null ? summary : operationId != null ? operationId : method.toUpperCase() + " " + path;

        Map<String, Object> folder = new HashMap<>();
        folder.put("name", opName);

        List<Map<String, Object>> children = new ArrayList<>();
        children.add(buildHappyPathItem(method, path, operation, spec));

        if (isPostmanNegativeTestsEnabled()) {
            int maxNeg = postmanNegativeTestsMaxPerOperation();
            List<Map<String, Object>> positiveQuery = PostmanParameterSupport.buildPositiveQueryList(operation);
            int default4xx = preferred4xxStatus(operation);
            List<PostmanNegativeRequestFactory.NegativeCase> negatives = PostmanNegativeRequestFactory.buildCases(
                    path, operation, positiveQuery, maxNeg, default4xx);
            if (!negatives.isEmpty()) {
                Map<String, Object> negFolder = new HashMap<>();
                negFolder.put("name", "Negative-TCs");
                List<Map<String, Object>> negItems = new ArrayList<>();
                for (PostmanNegativeRequestFactory.NegativeCase nc : negatives) {
                    negItems.add(buildNegativeRequestItem(method, path, operation, nc, default4xx, spec));
                }
                negFolder.put("item", negItems);
                children.add(negFolder);
            }
        }

        folder.put("item", children);
        return folder;
    }

    private Map<String, Object> buildHappyPathItem(String method, String path, Map<String, Object> operation,
                                                   Map<String, Object> spec) {
        String description = (String) operation.get("description");
        String escapedDescription = escapeStringForJson(description != null ? description : "");

        Map<String, Object> item = new HashMap<>();
        item.put("name", "Happy path");

        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put("method", method.toUpperCase());
        requestMap.put("header", generateHeaders(operation));
        requestMap.put("url", buildHappyPathUrl(path, operation));
        requestMap.put("body", generateBody(operation, spec));
        requestMap.put("description", escapedDescription);
        item.put("request", requestMap);

        item.put("event", generateHappyPathTests(operation));
        return item;
    }

    private Map<String, Object> buildNegativeRequestItem(String method,
                                                         String path,
                                                         Map<String, Object> operation,
                                                         PostmanNegativeRequestFactory.NegativeCase nc,
                                                         int default4xx,
                                                         Map<String, Object> spec) {
        String description = (String) operation.get("description");
        String escapedDescription = escapeStringForJson(description != null ? description : "");

        Map<String, Object> item = new HashMap<>();
        item.put("name", nc.name);

        Map<String, String> resolvedPath = new LinkedHashMap<>();
        for (String pn : PostmanParameterSupport.pathParameterNames(path)) {
            resolvedPath.put(pn, nc.pathLiterals.getOrDefault(pn, "{{" + pn + "}}"));
        }

        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put("method", method.toUpperCase());
        requestMap.put("header", generateHeaders(operation));
        requestMap.put("url", PostmanParameterSupport.buildUrlObject(path, resolvedPath, nc.queryEntries));
        requestMap.put("body", generateBody(operation, spec));
        requestMap.put("description", escapedDescription);
        item.put("request", requestMap);

        int expected = nc.expectedStatusOverride != null ? nc.expectedStatusOverride : default4xx;
        item.put("event", generateNegativeTests(operation, expected));
        return item;
    }

    private Map<String, Object> buildHappyPathUrl(String path, Map<String, Object> operation) {
        Map<String, String> resolvedPath = new LinkedHashMap<>();
        for (String pn : PostmanParameterSupport.pathParameterNames(path)) {
            resolvedPath.put(pn, "{{" + pn + "}}");
        }
        List<Map<String, Object>> query = PostmanParameterSupport.buildPositiveQueryList(operation);
        return PostmanParameterSupport.buildUrlObject(path, resolvedPath, query);
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
            if (parameters != null) {
                for (Map<String, Object> param : parameters) {
                    if (param != null && "header".equals(param.get("in"))) {
                        Object name = param.get("name");
                        if (name != null) {
                            Object description = param.get("description");
                            headers.add(Map.of(
                                    "key", name.toString(),
                                    "value", "{{" + name + "}}",
                                    "description", description != null ? description.toString() : ""
                            ));
                        }
                    }
                }
            }
        }

        return headers;
    }

    /**
     * Generate request body
     */
    private Map<String, Object> generateBody(Map<String, Object> operation, Map<String, Object> spec) {
        if (!operation.containsKey("requestBody")) {
            return Map.of("mode", "raw", "raw", "");
        }

        String bodyJson = IntegrationScenarioSupport.generateRequestBodyFromSchemaRaw(operation, spec);
        String safeExample = bodyJson != null && !bodyJson.isBlank() ? bodyJson : "";

        Map<String, Object> bodyMap = new HashMap<>();
        bodyMap.put("mode", "raw");
        bodyMap.put("raw", safeExample);

        Map<String, Object> rawOptions = new HashMap<>();
        rawOptions.put("language", "json");
        Map<String, Object> options = new HashMap<>();
        options.put("raw", rawOptions);
        bodyMap.put("options", options);

        return bodyMap;
    }

    private List<Map<String, Object>> wrapTestScript(List<String> lines) {
        List<Map<String, Object>> events = new ArrayList<>();
        Map<String, Object> testEvent = new HashMap<>();
        testEvent.put("listen", "test");
        Map<String, Object> scriptMap = new HashMap<>();
        scriptMap.put("type", "text/javascript");
        scriptMap.put("exec", lines);
        testEvent.put("script", scriptMap);
        events.add(testEvent);
        return events;
    }

    private List<Map<String, Object>> generateHappyPathTests(Map<String, Object> operation) {
        return wrapTestScript(generateHappyPathTestScript(operation, preferred2xxStatus(operation)));
    }

    private List<Map<String, Object>> generateNegativeTests(Map<String, Object> operation, int expectedStatus) {
        return wrapTestScript(generateNegativeTestScript(operation, expectedStatus));
    }

    private int preferred2xxStatus(Map<String, Object> operation) {
        Map<String, Object> responses = operation != null ? Util.asStringObjectMap(operation.get("responses")) : null;
        if (responses == null) {
            return 200;
        }
        for (String code : List.of("200", "201", "202", "204")) {
            if (responses.containsKey(code)) {
                return Integer.parseInt(code);
            }
        }
        for (String key : responses.keySet()) {
            if (key != null && key.matches("\\d{3}")) {
                int c = Integer.parseInt(key);
                if (c >= 200 && c < 300) {
                    return c;
                }
            }
        }
        return 200;
    }

    /**
     * Default HTTP status for parameter-validation negatives: documented {@code 400} or {@code 422},
     * otherwise {@code 400}. Special cases (e.g. empty path segment) set
     * {@link PostmanNegativeRequestFactory.NegativeCase#expectedStatusOverride}.
     */
    private int preferred4xxStatus(Map<String, Object> operation) {
        Map<String, Object> responses = operation != null ? Util.asStringObjectMap(operation.get("responses")) : null;
        if (responses == null) {
            return 400;
        }
        if (responses.containsKey("400")) {
            return 400;
        }
        if (responses.containsKey("422")) {
            return 422;
        }
        return 400;
    }

    private List<String> generateHappyPathTestScript(Map<String, Object> operation, int successStatus) {
        List<String> script = new ArrayList<>();
        script.add("// Happy path — success response");
        script.add("pm.test(\"Status code is " + successStatus + "\", function () {");
        script.add("    pm.response.to.have.status(" + successStatus + ");");
        script.add("});");
        script.add("");
        script.add("pm.test(\"Response time is less than 2000ms\", function () {");
        script.add("    pm.expect(pm.response.responseTime).to.be.below(2000);");
        script.add("});");
        script.add("");
        script.add("pm.test(\"Content-Type is JSON\", function () {");
        script.add("    const ct = pm.response.headers.get(\"Content-Type\") || \"\";");
        script.add("    pm.expect(ct).to.match(/json/i);");
        script.add("});");
        script.add("");

        if (successStatus != 204 && operation.containsKey("responses")) {
            Map<String, Object> responses = Util.asStringObjectMap(operation.get("responses"));
            Map<String, Object> ok = Util.asStringObjectMap(responses.get(String.valueOf(successStatus)));
            if (ok != null && ok.containsKey("content")) {
                script.add("pm.test(\"Response parses as JSON\", function () {");
                script.add("    pm.response.json();");
                script.add("});");
            }
        }

        return script;
    }

    private List<String> generateNegativeTestScript(Map<String, Object> operation, int expectedStatus) {
        List<String> script = new ArrayList<>();
        script.add("// Negative case — client or validation error");
        script.add("pm.test(\"Status code is " + expectedStatus + "\", function () {");
        script.add("    pm.response.to.have.status(" + expectedStatus + ");");
        script.add("});");
        script.add("");
        script.add("pm.test(\"Content-Type is JSON\", function () {");
        script.add("    const ct = pm.response.headers.get(\"Content-Type\") || \"\";");
        script.add("    pm.expect(ct).to.match(/json/i);");
        script.add("});");
        script.add("");
        script.add("pm.test(\"Error body has code and developerMessage\", function () {");
        script.add("    const json = pm.response.json();");
        script.add("    pm.expect(json).to.have.property(\"code\");");
        script.add("    pm.expect(json.code).to.be.a(\"string\");");
        script.add("    pm.expect(json).to.have.property(\"developerMessage\");");
        script.add("    pm.expect(json.developerMessage).to.be.a(\"string\");");
        script.add("});");
        script.add("");
        script.add("pm.test(\"Error code matches eGain-style pattern\", function () {");
        script.add("    const json = pm.response.json();");
        script.add("    pm.expect(json.code).to.match(/^4\\d{2}-\\d+/);");
        script.add("});");

        if (isPostmanAssertErrorExamples()) {
            String exampleCode = extractExampleErrorCode(operation, expectedStatus);
            if (exampleCode != null && !exampleCode.isEmpty()) {
                script.add("");
                script.add("pm.test(\"Error code matches spec example\", function () {");
                script.add("    pm.expect(pm.response.json().code).to.eql(\"" + escapeForJsString(exampleCode) + "\");");
                script.add("});");
            }
        }

        return script;
    }

    private String escapeForJsString(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    @SuppressWarnings("unchecked")
    private String extractExampleErrorCode(Map<String, Object> operation, int status) {
        Map<String, Object> responses = Util.asStringObjectMap(operation.get("responses"));
        if (responses == null) {
            return null;
        }
        Map<String, Object> r = Util.asStringObjectMap(responses.get(String.valueOf(status)));
        if (r == null) {
            return null;
        }
        Map<String, Object> content = Util.asStringObjectMap(r.get("content"));
        if (content == null) {
            return null;
        }
        for (String mt : List.of("application/json", "application/problem+json")) {
            Map<String, Object> media = Util.asStringObjectMap(content.get(mt));
            if (media == null) {
                continue;
            }
            if (media.containsKey("example")) {
                Object ex = media.get("example");
                if (ex instanceof Map) {
                    Object code = ((Map<String, Object>) ex).get("code");
                    return code != null ? String.valueOf(code) : null;
                }
            }
            if (media.containsKey("examples")) {
                Map<String, Object> examples = Util.asStringObjectMap(media.get("examples"));
                if (examples != null && !examples.isEmpty()) {
                    Object first = examples.values().iterator().next();
                    if (first instanceof Map) {
                        Map<String, Object> exMap = Util.asStringObjectMap(first);
                        if (exMap != null && exMap.containsKey("value") && exMap.get("value") instanceof Map) {
                            Object code = ((Map<String, Object>) exMap.get("value")).get("code");
                            return code != null ? String.valueOf(code) : null;
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * Generate collection / environment variables (base_url + parameter defaults).
     */
    private List<Map<String, Object>> generateVariables(Map<String, Object> spec) {
        List<Map<String, Object>> variables = new ArrayList<>();
        Map<String, String> paramDefaults = collectParameterVariableDefaults(spec);

        String baseUrl = "http://localhost:8080";
        if (spec.containsKey("servers")) {
            List<Map<String, Object>> servers = Util.asStringObjectMapList(spec.get("servers"));
            if (servers != null && !servers.isEmpty()) {
                Map<String, Object> firstServer = servers.get(0);
                if (firstServer != null && firstServer.get("url") != null) {
                    baseUrl = String.valueOf(firstServer.get("url"));
                }
            }
        }

        variables.add(variableEntry("base_url", baseUrl));
        for (Map.Entry<String, String> e : paramDefaults.entrySet()) {
            variables.add(variableEntry(e.getKey(), e.getValue()));
        }

        return variables;
    }

    private Map<String, String> collectParameterVariableDefaults(Map<String, Object> spec) {
        Map<String, String> defaults = new LinkedHashMap<>();
        Map<String, Object> paths = Util.asStringObjectMap(spec.get("paths"));
        if (paths == null) {
            return defaults;
        }
        for (Map.Entry<String, Object> pathEntry : paths.entrySet()) {
            Map<String, Object> pathItem = Util.asStringObjectMap(pathEntry.getValue());
            if (pathItem == null) {
                continue;
            }
            for (String method : Constants.HTTP_METHODS) {
                if (!pathItem.containsKey(method)) {
                    continue;
                }
                Map<String, Object> operation = Util.asStringObjectMap(pathItem.get(method));
                if (operation == null || !operation.containsKey("parameters")) {
                    continue;
                }
                List<Map<String, Object>> parameters = Util.asStringObjectMapList(operation.get("parameters"));
                if (parameters == null) {
                    continue;
                }
                for (Map<String, Object> param : parameters) {
                    if (param == null) {
                        continue;
                    }
                    String in = (String) param.get("in");
                    if (!"query".equals(in) && !"path".equals(in) && !"header".equals(in)) {
                        continue;
                    }
                    String name = (String) param.get("name");
                    if (name == null || defaults.containsKey(name)) {
                        continue;
                    }
                    defaults.put(name, PostmanParameterSupport.defaultValueForParameter(param));
                }
            }
        }
        return defaults;
    }

    private Map<String, Object> variableEntry(String key, String value) {
        Map<String, Object> m = new HashMap<>();
        m.put("key", key);
        m.put("value", value != null ? value : "");
        m.put("type", "string");
        return m;
    }

    /**
     * Generate environment file
     */
    private void generateEnvironmentFile(Map<String, Object> spec, String outputDir) throws IOException {
        String apiTitle = TestSpecUtils.getApiTitle(spec);
        String safeTitle = apiTitle != null ? apiTitle : "API";

        Map<String, Object> environment = new HashMap<>();
        environment.put("id", UUID.randomUUID().toString());
        environment.put("name", safeTitle + " Environment");
        environment.put("values", generateVariables(spec));

        String json = convertToJson(environment);
        String fileName = safeTitle.replaceAll("[^a-zA-Z0-9]", "-") + "-Environment.postman_environment.json";
        Files.write(Paths.get(outputDir, fileName), json.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Generate test scripts
     */
    private void generateTestScripts(Map<String, Object> spec, String outputDir) throws IOException {
        String apiTitle = TestSpecUtils.getApiTitle(spec);
        String safeTitle = apiTitle != null ? apiTitle : "API";

        // Generate Newman test script
        String newmanScript = generateNewmanScript(safeTitle);
        Files.write(Paths.get(outputDir, "run-tests.sh"), newmanScript.getBytes(StandardCharsets.UTF_8));

        // Generate curl commands
        String curlScript = generateCurlScript(spec);
        Files.write(Paths.get(outputDir, "curl-commands.sh"), curlScript.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Generate Newman test script
     */
    private String generateNewmanScript(String apiTitle) {
        String safeTitle = apiTitle != null ? apiTitle : "API";
        String collectionFile = safeTitle.replaceAll("[^a-zA-Z0-9]", "-") + "-API.postman_collection.json";
        String environmentFile = safeTitle.replaceAll("[^a-zA-Z0-9]", "-") + "-Environment.postman_environment.json";

        return String.format("""
                #!/bin/bash
                
                # Install Newman if not already installed
                if ! command -v newman &> /dev/null; then
                    echo "Installing Newman..."
                    npm install -g newman
                fi
                
                # Run Postman collection tests
                echo "Running API tests with Newman..."
                newman run %s -e %s --reporters cli,json --reporter-json-export test-results.json
                
                # Check test results
                if [ $? -eq 0 ]; then
                    echo "All tests passed!"
                else
                    echo "Some tests failed. Check test-results.json for details."
                    exit 1
                fi
                """, collectionFile, environmentFile);
    }

    /**
     * Generate curl commands script
     */
    private String generateCurlScript(Map<String, Object> spec) {
        StringBuilder script = new StringBuilder();
        script.append("#!/bin/bash\n\n");
        script.append("# API Test Script - Generated from OpenAPI specification\n");
        script.append("BASE_URL=\"http://localhost:8080\"\n\n");

        Map<String, Object> paths = Util.asStringObjectMap(spec.get("paths"));
        if (paths != null) {
            for (Map.Entry<String, Object> pathEntry : paths.entrySet()) {
                String path = pathEntry.getKey();
                Map<String, Object> pathItem = Util.asStringObjectMap(pathEntry.getValue());

                if (pathItem == null) continue;

                String[] methods = {"get", "post", "put", "delete", "patch"};
                for (String method : methods) {
                    if (pathItem.containsKey(method)) {
                        Map<String, Object> operation = Util.asStringObjectMap(pathItem.get(method));
                        String summary = (String) operation.get("summary");

                        script.append("# ").append(summary != null ? summary : method.toUpperCase()).append(" ").append(path).append("\n");
                        script.append("curl -X ").append(method.toUpperCase()).append(" \"$BASE_URL").append(path).append("\" \\\n");
                        script.append("  -H \"Content-Type: application/json\" \\\n");
                        script.append("  -H \"Accept: application/json\"\n\n");
                    }
                }
            }
        }

        return script.toString();
    }

    /**
     * Helper methods
     */
    private String getAPIVersion(Map<String, Object> spec) {
        if (spec == null) {
            return "1.0.0";
        }
        Map<String, Object> info = Util.asStringObjectMap(spec.get("info"));
        if (info == null) {
            return "1.0.0";
        }
        Object version = info.get("version");
        return version != null ? version.toString() : "1.0.0";
    }

    private String getAPIDescription(Map<String, Object> spec) {
        if (spec == null) {
            return "Generated API";
        }
        Map<String, Object> info = Util.asStringObjectMap(spec.get("info"));
        if (info == null) {
            return "Generated API";
        }
        Object description = info.get("description");
        return description != null ? description.toString() : "Generated API";
    }

    private String getOperationTag(Map<String, Object> operation) {
        if (operation == null) {
            return "Default";
        }
        List<String> tags = Util.asStringList(operation.get("tags"));
        if (tags != null && !tags.isEmpty()) {
            String firstTag = tags.get(0);
            return firstTag != null ? firstTag : "Default";
        }
        return "Default";
    }

    private String convertToJson(Map<String, Object> data) {
        try {
            // Use Jackson ObjectMapper for proper JSON serialization with escaping
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(data);
        } catch (Exception e) {
            // Fallback to manual conversion if Jackson fails
            return convertToJsonManual(data);
        }
    }

    /**
     * Manual JSON conversion fallback (with proper string escaping)
     */
    private String convertToJsonManual(Map<String, Object> data) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");

        boolean first = true;
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            if (!first) json.append(",\n");
            json.append("  \"").append(escapeJsonString(entry.getKey())).append("\": ");

            Object value = entry.getValue();
            switch (value) {
                case String s -> json.append("\"").append(escapeJsonString(s)).append("\"");
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

            if (item instanceof String str) {
                json.append("\"").append(escapeJsonString(str)).append("\"");
            } else if (item instanceof Map<?, ?> map) {
                json.append(convertMapToJson(map));
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
            String key = entry.getKey() != null ? entry.getKey().toString() : "null";
            json.append("    \"").append(escapeJsonString(key)).append("\": ");

            Object value = entry.getValue();
            switch (value) {
                case String s -> json.append("\"").append(escapeJsonString(s)).append("\"");
                case List<?> list -> json.append(convertListToJson(list));
                case Map<?, ?> map1 -> json.append(convertMapToJson(map1));
                case null, default -> json.append(value);
            }

            first = false;
        }

        json.append("\n  }");
        return json.toString();
    }

    /**
     * Escape string for JSON format (converts newlines to \n, etc.)
     */
    private String escapeStringForJson(String str) {
        if (str == null) {
            return "";
        }
        return escapeJsonString(str);
    }

    /**
     * Escape JSON string properly (handles newlines, quotes, backslashes, etc.)
     */
    private String escapeJsonString(String str) {
        if (str == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (char c : str.toCharArray()) {
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                    break;
            }
        }
        return sb.toString();
    }
}
