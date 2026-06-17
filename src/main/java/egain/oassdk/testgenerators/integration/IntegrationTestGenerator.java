package egain.oassdk.testgenerators.integration;

import egain.oassdk.Util;
import egain.oassdk.config.TestConfig;
import egain.oassdk.core.Constants;
import egain.oassdk.core.exceptions.GenerationException;
import egain.oassdk.testgenerators.ConfigurableTestGenerator;
import egain.oassdk.testgenerators.IntegrationScenarioSupport;
import egain.oassdk.testgenerators.TestGenerator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Integration test generator
 * Generates JUnit 5 integration tests for API endpoints based on OpenAPI specification
 * These tests make real HTTP calls to a running server
 */
public class IntegrationTestGenerator implements TestGenerator, ConfigurableTestGenerator {

    private TestConfig config;

    @Override
    public void generate(Map<String, Object> spec, String outputDir, TestConfig config, String testFramework) throws GenerationException {
        this.config = config;

        try {
            // Create output directory structure
            Path outputPath = Paths.get(outputDir, "integration");
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

            // Get base URL from servers
            String baseUrl = getBaseUrl(spec);

            // Generate test classes for each endpoint
            generateTestClasses(spec, outputPath.toString(), basePackage, apiTitle, baseUrl);

            // Generate test configuration
            generateTestConfiguration(outputPath.toString(), basePackage, baseUrl);

            // Generate test utilities
            generateTestUtilities(outputPath.toString(), basePackage);

            // Generate pom.xml with JaCoCo for code coverage reporting
            generatePomXml(outputPath.toString(), basePackage);

        } catch (Exception e) {
            throw new GenerationException("Failed to generate integration tests: " + e.getMessage(), e);
        }
    }

    /**
     * Generate test classes for all endpoints
     */
    private void generateTestClasses(Map<String, Object> spec, String outputDir, String basePackage, String apiTitle, String baseUrl) throws IOException {
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

            for (String method : Constants.HTTP_METHODS) {
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

            String className = toClassName(tag) + "IntegrationTest";
            String packageDir = outputDir + "/" + basePackage.replace(".", "/");
            Files.createDirectories(Paths.get(packageDir));

            String testClassContent = generateTestClass(basePackage, className, tag, operations, spec, baseUrl);
            Files.write(Paths.get(packageDir, className + ".java"), testClassContent.getBytes());
        }
    }

    /**
     * Generate a test class for a group of operations
     */
    private String generateTestClass(String basePackage, String className, String tag, List<OperationInfo> operations, Map<String, Object> spec, String baseUrl) {
        StringBuilder sb = new StringBuilder();

        // Package declaration
        sb.append("package ").append(basePackage).append(";\n\n");

        // Imports
        sb.append("import org.junit.jupiter.api.*;\n");
        sb.append("import org.junit.jupiter.api.DisplayName;\n");
        sb.append("import org.junit.jupiter.api.Order;\n");
        sb.append("import org.junit.jupiter.api.Assumptions;\n");
        sb.append("import static org.junit.jupiter.api.Assertions.*;\n\n");
        sb.append("import java.net.http.*;\n");
        sb.append("import java.net.URI;\n");
        sb.append("import java.time.Duration;\n");
        sb.append("import java.util.*;\n");
        sb.append("import java.util.concurrent.CompletableFuture;\n\n");

        // Class declaration
        sb.append("/**\n");
        sb.append(" * Integration tests for ").append(tag).append(" API endpoints\n");
        sb.append(" * Generated from OpenAPI specification\n");
        sb.append(" * \n");
        sb.append(" * These tests make real HTTP calls to a running server.\n");
        sb.append(" * Ensure the API server is running before executing these tests.\n");
        sb.append(" */\n");
        sb.append("@DisplayName(\"").append(tag).append(" Integration Tests\")\n");
        sb.append("@TestMethodOrder(MethodOrderer.OrderAnnotation.class)\n");
        sb.append("public class ").append(className).append(" {\n\n");

        // Constants
        sb.append("    private static final String BASE_URL = \"").append(baseUrl).append("\";\n");
        sb.append("    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);\n");
        sb.append("    private static HttpClient httpClient;\n\n");

        // Class-level setup
        sb.append("    @BeforeAll\n");
        sb.append("    static void setUpAll() {\n");
        sb.append("        httpClient = HttpClient.newBuilder()\n");
        sb.append("            .connectTimeout(REQUEST_TIMEOUT)\n");
        sb.append("            .build();\n");
        sb.append("        \n");
        sb.append("        if (!IntegrationTestUtils.waitForServer(BASE_URL, REQUEST_TIMEOUT)) {\n");
        sb.append("            System.err.println(\"Warning: Could not reach server at \" + BASE_URL);\n");
        sb.append("            System.err.println(\"Some tests may fail if server is not running.\");\n");
        sb.append("        }\n");
        sb.append("    }\n\n");

        // Test setup
        sb.append("    @BeforeEach\n");
        sb.append("    void setUp() {\n");
        sb.append("        // Setup test data if needed\n");
        sb.append("    }\n\n");

        sb.append("    @AfterEach\n");
        sb.append("    void tearDown() {\n");
        sb.append("        // Cleanup test data if needed\n");
        sb.append("    }\n\n");

        // Generate test methods for each operation
        int order = 1;
        for (OperationInfo opInfo : operations) {
            generateTestMethods(sb, opInfo, spec, order);
            order += 10; // Increment by 10 to allow insertion between tests
        }

        // Helper methods
        sb.append("    /**\n");
        sb.append("     * Helper method to build request URI\n");
        sb.append("     */\n");
        sb.append("    private URI buildUri(String path, Map<String, String> queryParams) {\n");
        sb.append("        StringBuilder uriBuilder = new StringBuilder(BASE_URL).append(path);\n");
        sb.append("        if (queryParams != null && !queryParams.isEmpty()) {\n");
        sb.append("            uriBuilder.append(\"?\");\n");
        sb.append("            boolean first = true;\n");
        sb.append("            for (Map.Entry<String, String> entry : queryParams.entrySet()) {\n");
        sb.append("                if (!first) uriBuilder.append(\"&\");\n");
        sb.append("                uriBuilder.append(entry.getKey()).append(\"=\").append(entry.getValue());\n");
        sb.append("                first = false;\n");
        sb.append("            }\n");
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

        sb.append("    /**\n");
        sb.append("     * Helper method to send HTTP request and get response\n");
        sb.append("     */\n");
        sb.append("    private HttpResponse<String> sendRequest(HttpRequest request) throws Exception {\n");
        sb.append("        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());\n");
        sb.append("    }\n\n");

        generateAuthSetupFromSecuritySchemes(sb, spec);
        appendMultiContextAuthHelpers(sb);

        sb.append("}\n");

        return sb.toString();
    }

    private void appendMultiContextAuthHelpers(StringBuilder sb) {
        sb.append("    /** Client application token: INTEGRATION_TOKEN_CLIENT_APPLICATION; falls back to API_BEARER_TOKEN, API_TOKEN */\n");
        sb.append("    private String getTokenClientApplication() {\n");
        sb.append("        String t = System.getenv(\"INTEGRATION_TOKEN_CLIENT_APPLICATION\");\n");
        sb.append("        if (t == null || t.isEmpty()) {\n");
        sb.append("            t = System.getenv(\"API_BEARER_TOKEN\");\n");
        sb.append("        }\n");
        sb.append("        if (t == null || t.isEmpty()) {\n");
        sb.append("            t = System.getenv(\"API_TOKEN\");\n");
        sb.append("        }\n");
        sb.append("        return t;\n");
        sb.append("    }\n\n");
        sb.append("    /** Authenticated customer: INTEGRATION_TOKEN_AUTHENTICATED_CUSTOMER */\n");
        sb.append("    private String getTokenAuthenticatedCustomer() {\n");
        sb.append("        return System.getenv(\"INTEGRATION_TOKEN_AUTHENTICATED_CUSTOMER\");\n");
        sb.append("    }\n\n");
        sb.append("    /** Prefer customer, then client app, then legacy getAuthToken() */\n");
        sb.append("    private String getPreferredAuthToken() {\n");
        sb.append("        String t = getTokenAuthenticatedCustomer();\n");
        sb.append("        if (t != null && !t.isEmpty()) {\n");
        sb.append("            return t;\n");
        sb.append("        }\n");
        sb.append("        t = getTokenClientApplication();\n");
        sb.append("        if (t != null && !t.isEmpty()) {\n");
        sb.append("            return t;\n");
        sb.append("        }\n");
        sb.append("        return getAuthToken();\n");
        sb.append("    }\n\n");
    }

    private void appendJavaPathUriBlocks(StringBuilder sb, String pathTemplate,
                                         Map<String, String> pathParams, Map<String, String> queryParams) {
        if (pathParams != null && !pathParams.isEmpty()) {
            sb.append("        Map<String, String> pathParams = new HashMap<>();\n");
            for (Map.Entry<String, String> entry : pathParams.entrySet()) {
                sb.append("        pathParams.put(\"")
                        .append(entry.getKey()).append("\", \"")
                        .append(IntegrationScenarioSupport.escapeJavaString(entry.getValue())).append("\");\n");
            }
            sb.append("        String path = replacePathParameters(\"").append(pathTemplate).append("\", pathParams);\n");
        } else {
            sb.append("        String path = \"").append(pathTemplate).append("\";\n");
        }
        if (queryParams != null && !queryParams.isEmpty()) {
            sb.append("        Map<String, String> queryParams = new HashMap<>();\n");
            for (Map.Entry<String, String> entry : queryParams.entrySet()) {
                sb.append("        queryParams.put(\"")
                        .append(entry.getKey()).append("\", \"")
                        .append(IntegrationScenarioSupport.escapeJavaString(entry.getValue())).append("\");\n");
            }
            sb.append("        URI uri = buildUri(path, queryParams);\n");
        } else {
            sb.append("        URI uri = URI.create(BASE_URL + path);\n");
        }
    }

    private void appendHttpMethodOnRequestBuilder(StringBuilder sb, String methodUpper, boolean jsonBody) {
        switch (methodUpper) {
            case "GET":
                sb.append("        requestBuilder.GET();\n");
                break;
            case "POST":
                sb.append("        requestBuilder.header(\"Content-Type\", \"application/json\");\n");
                sb.append("        requestBuilder.method(\"POST\", HttpRequest.BodyPublishers.ofString(requestBody));\n");
                break;
            case "PUT":
                sb.append("        requestBuilder.header(\"Content-Type\", \"application/json\");\n");
                sb.append("        requestBuilder.method(\"PUT\", HttpRequest.BodyPublishers.ofString(requestBody));\n");
                break;
            case "PATCH":
                sb.append("        requestBuilder.header(\"Content-Type\", \"application/json\");\n");
                sb.append("        requestBuilder.method(\"PATCH\", HttpRequest.BodyPublishers.ofString(requestBody));\n");
                break;
            case "DELETE":
                sb.append("        requestBuilder.DELETE();\n");
                break;
            default:
                sb.append("        requestBuilder.method(\"").append(methodUpper)
                        .append("\", HttpRequest.BodyPublishers.noBody());\n");
                break;
        }
    }

    private void appendExpectedStatusAssertion(StringBuilder sb, List<Integer> codes) {
        if (codes == null || codes.isEmpty()) {
            sb.append("        assertTrue(response.statusCode() == 400 || response.statusCode() == 422,\n");
            sb.append("            \"Expected 400 or 422, got: \" + response.statusCode());\n");
            return;
        }
        if (codes.size() == 1) {
            sb.append("        assertEquals(").append(codes.get(0)).append(", response.statusCode());\n");
            return;
        }
        sb.append("        assertTrue(");
        for (int i = 0; i < codes.size(); i++) {
            if (i > 0) {
                sb.append(" || ");
            }
            sb.append("response.statusCode() == ").append(codes.get(i));
        }
        sb.append(", \"Unexpected status: \" + response.statusCode());\n");
    }

    private String safeMethodSuffix(String name) {
        if (name == null || name.isEmpty()) {
            return "case";
        }
        String s = name.replaceAll("[^a-zA-Z0-9]+", "_");
        if (s.length() > 60) {
            s = s.substring(0, 60);
        }
        return capitalize(toMethodName(s));
    }

    private String escapeForDisplayName(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * Generate test methods for an operation
     */
    private void generateTestMethods(StringBuilder sb, OperationInfo opInfo, Map<String, Object> spec, int order) {
        Map<String, Object> operation = opInfo.operation;
        String operationId = (String) operation.get("operationId");
        String summary = (String) operation.get("summary");
        String method = opInfo.method.toUpperCase(Locale.ROOT);
        String path = opInfo.path;

        String testMethodName = operationId != null
                ? toMethodName(operationId)
                : toMethodName(method + "_" + sanitizePath(path));

        List<Map<String, Object>> parameters = operation.containsKey("parameters")
                ? Util.asStringObjectMapList(operation.get("parameters"))
                : new ArrayList<>();

        Map<String, Object> responses = operation.containsKey("responses")
                ? Util.asStringObjectMap(operation.get("responses"))
                : new HashMap<>();

        boolean requiresAuth = operation.containsKey("security") &&
                !((List<?>) operation.get("security")).isEmpty();

        Map<String, String> pathParams = new HashMap<>();
        Map<String, String> queryParams = new HashMap<>();
        for (Map<String, Object> param : parameters) {
            String paramName = (String) param.get("name");
            String paramIn = (String) param.get("in");
            if ("path".equals(paramIn)) {
                pathParams.put(paramName, IntegrationScenarioSupport.getParameterExample(param));
            } else if ("query".equals(paramIn)) {
                queryParams.put(paramName, IntegrationScenarioSupport.getParameterExample(param));
            }
        }

        int maxBody = IntegrationScenarioSupport.maxInvalidBodyFields(config);
        int maxParam = IntegrationScenarioSupport.maxInvalidParamCases(config);
        String requestBodyRaw = IntegrationScenarioSupport.generateRequestBodyFromSchemaRaw(operation, spec);
        String requestBodyEscaped = IntegrationScenarioSupport.escapeJavaString(requestBodyRaw);
        Map<String, Object> requestBodySchema = IntegrationScenarioSupport.extractRequestBodySchema(operation, spec);
        boolean jsonBody = "POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method);

        String displayBase = escapeForDisplayName(summary != null ? summary : method + " " + path);

        if (requiresAuth) {
            order++;
            sb.append("    @Test\n");
            sb.append("    @Order(").append(order).append(")\n");
            sb.append("    @DisplayName(\"").append(displayBase).append(" - Successful request (client application)\")\n");
            sb.append("    void test").append(capitalize(testMethodName)).append("_Success_ClientApplication() throws Exception {\n");
            sb.append("        String tok = getTokenClientApplication();\n");
            sb.append("        Assumptions.assumeTrue(tok != null && !tok.isEmpty(), \"Skip: set INTEGRATION_TOKEN_CLIENT_APPLICATION (or API_BEARER_TOKEN / API_TOKEN)\");\n");
            appendJavaPathUriBlocks(sb, path, pathParams, queryParams);
            sb.append("        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()\n");
            sb.append("            .uri(uri)\n");
            sb.append("            .timeout(REQUEST_TIMEOUT)\n");
            sb.append("            .header(\"Accept\", \"application/json\")\n");
            sb.append("            .header(\"Authorization\", \"Bearer \" + tok);\n");
            if (jsonBody) {
                sb.append("        String requestBody = \"").append(requestBodyEscaped).append("\";\n");
            }
            appendHttpMethodOnRequestBuilder(sb, method, jsonBody);
            sb.append("        HttpRequest request = requestBuilder.build();\n");
            sb.append("        HttpResponse<String> response = sendRequest(request);\n");
            sb.append("        assertNotNull(response);\n");
            appendSuccessStatusAssertions(sb, responses);
            sb.append("        assertNotNull(response.body());\n");
            sb.append("        // Validate response against schema\n");
            generateResponseSchemaValidation(sb, responses, spec);
            sb.append("    }\n\n");

            order++;
            sb.append("    @Test\n");
            sb.append("    @Order(").append(order).append(")\n");
            sb.append("    @DisplayName(\"").append(displayBase).append(" - Successful request (authenticated customer)\")\n");
            sb.append("    void test").append(capitalize(testMethodName)).append("_Success_AuthenticatedCustomer() throws Exception {\n");
            sb.append("        String tok = getTokenAuthenticatedCustomer();\n");
            sb.append("        Assumptions.assumeTrue(tok != null && !tok.isEmpty(), \"Skip: set INTEGRATION_TOKEN_AUTHENTICATED_CUSTOMER\");\n");
            appendJavaPathUriBlocks(sb, path, pathParams, queryParams);
            sb.append("        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()\n");
            sb.append("            .uri(uri)\n");
            sb.append("            .timeout(REQUEST_TIMEOUT)\n");
            sb.append("            .header(\"Accept\", \"application/json\")\n");
            sb.append("            .header(\"Authorization\", \"Bearer \" + tok);\n");
            if (jsonBody) {
                sb.append("        String requestBody = \"").append(requestBodyEscaped).append("\";\n");
            }
            appendHttpMethodOnRequestBuilder(sb, method, jsonBody);
            sb.append("        HttpRequest request = requestBuilder.build();\n");
            sb.append("        HttpResponse<String> response = sendRequest(request);\n");
            sb.append("        assertNotNull(response);\n");
            appendSuccessStatusAssertions(sb, responses);
            sb.append("        assertNotNull(response.body());\n");
            sb.append("        // Validate response against schema\n");
            generateResponseSchemaValidation(sb, responses, spec);
            sb.append("    }\n\n");
        } else {
            order++;
            sb.append("    @Test\n");
            sb.append("    @Order(").append(order).append(")\n");
            sb.append("    @DisplayName(\"").append(displayBase).append(" - Successful Request\")\n");
            sb.append("    void test").append(capitalize(testMethodName)).append("_Success() throws Exception {\n");
            appendJavaPathUriBlocks(sb, path, pathParams, queryParams);
            sb.append("        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()\n");
            sb.append("            .uri(uri)\n");
            sb.append("            .timeout(REQUEST_TIMEOUT)\n");
            sb.append("            .header(\"Accept\", \"application/json\");\n");
            if (jsonBody) {
                sb.append("        String requestBody = \"").append(requestBodyEscaped).append("\";\n");
            }
            appendHttpMethodOnRequestBuilder(sb, method, jsonBody);
            sb.append("        HttpRequest request = requestBuilder.build();\n");
            sb.append("        HttpResponse<String> response = sendRequest(request);\n");
            sb.append("        assertNotNull(response);\n");
            appendSuccessStatusAssertions(sb, responses);
            sb.append("        assertNotNull(response.body());\n");
            sb.append("        // Validate response against schema\n");
            generateResponseSchemaValidation(sb, responses, spec);
            sb.append("    }\n\n");
        }

        List<IntegrationScenarioSupport.OneOfVariantBody> oneOfVariants =
                jsonBody ? IntegrationScenarioSupport.buildOneOfVariantBodies(requestBodySchema, spec) : List.of();
        for (IntegrationScenarioSupport.OneOfVariantBody variant : oneOfVariants) {
            order++;
            sb.append("    @Test\n");
            sb.append("    @Order(").append(order).append(")\n");
            sb.append("    @DisplayName(\"").append(displayBase).append(" - Success variant: ")
                    .append(escapeForDisplayName(variant.label())).append("\")\n");
            sb.append("    void test").append(capitalize(testMethodName)).append("_Success_")
                    .append(safeMethodSuffix(variant.label())).append("() throws Exception {\n");
            if (requiresAuth) {
                sb.append("        String tok = getTokenClientApplication();\n");
                sb.append("        Assumptions.assumeTrue(tok != null && !tok.isEmpty(), \"Skip: set INTEGRATION_TOKEN_CLIENT_APPLICATION\");\n");
            }
            appendJavaPathUriBlocks(sb, path, pathParams, queryParams);
            sb.append("        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()\n");
            sb.append("            .uri(uri)\n");
            sb.append("            .timeout(REQUEST_TIMEOUT)\n");
            sb.append("            .header(\"Accept\", \"application/json\");\n");
            if (requiresAuth) {
                sb.append("        requestBuilder.header(\"Authorization\", \"Bearer \" + tok);\n");
            }
            sb.append("        String requestBody = \"").append(IntegrationScenarioSupport.escapeJavaString(variant.jsonBody())).append("\";\n");
            appendHttpMethodOnRequestBuilder(sb, method, true);
            sb.append("        HttpRequest request = requestBuilder.build();\n");
            sb.append("        HttpResponse<String> response = sendRequest(request);\n");
            sb.append("        assertNotNull(response);\n");
            appendSuccessStatusAssertions(sb, responses);
            sb.append("        assertNotNull(response.body());\n");
            generateResponseSchemaValidation(sb, responses, spec);
            sb.append("    }\n\n");
        }

        if (IntegrationScenarioSupport.emitDeclaredErrorCodes(config)) {
            List<IntegrationScenarioSupport.DeclaredErrorCase> declaredErrors =
                    IntegrationScenarioSupport.buildDeclaredErrorCases(operation, queryParams);
            for (IntegrationScenarioSupport.DeclaredErrorCase dec : declaredErrors) {
                order++;
                sb.append("    @Test\n");
                sb.append("    @Order(").append(order).append(")\n");
                sb.append("    @DisplayName(\"").append(displayBase).append(" - Declared error: ")
                        .append(escapeForDisplayName(dec.label())).append("\")\n");
                sb.append("    void test").append(capitalize(testMethodName)).append("_")
                        .append(safeMethodSuffix(dec.label())).append("() throws Exception {\n");
                appendJavaPathUriBlocks(sb, path, pathParams, dec.queryParamsOverride());
                sb.append("        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()\n");
                sb.append("            .uri(uri)\n");
                sb.append("            .timeout(REQUEST_TIMEOUT)\n");
                sb.append("            .header(\"Accept\", \"application/json\");\n");
                if (requiresAuth) {
                    sb.append("        String token = getPreferredAuthToken();\n");
                    sb.append("        if (token != null && !token.isEmpty()) {\n");
                    sb.append("            requestBuilder.header(\"Authorization\", \"Bearer \" + token);\n");
                    sb.append("        }\n");
                }
                if (jsonBody) {
                    sb.append("        String requestBody = \"").append(requestBodyEscaped).append("\";\n");
                }
                appendHttpMethodOnRequestBuilder(sb, method, jsonBody);
                sb.append("        HttpRequest request = requestBuilder.build();\n");
                sb.append("        HttpResponse<String> response = sendRequest(request);\n");
                sb.append("        assertNotNull(response);\n");
                sb.append("        assertEquals(").append(dec.expectedStatus()).append(", response.statusCode());\n");
                generateErrorResponseSchemaValidation(sb, responses, spec);
                sb.append("    }\n\n");
            }
        }

        List<IntegrationScenarioSupport.IntegrationParamNegativeCase> paramCases =
                IntegrationScenarioSupport.buildParamNegativeCases(path, operation, pathParams, queryParams, maxParam);
        for (IntegrationScenarioSupport.IntegrationParamNegativeCase nc : paramCases) {
            order++;
            sb.append("    @Test\n");
            sb.append("    @Order(").append(order).append(")\n");
            sb.append("    @DisplayName(\"").append(displayBase).append(" - Param negative: ")
                    .append(escapeForDisplayName(nc.name)).append("\")\n");
            sb.append("    void test").append(capitalize(testMethodName)).append("_ParamNegative_")
                    .append(safeMethodSuffix(nc.name)).append("() throws Exception {\n");
            appendJavaPathUriBlocks(sb, path, nc.pathParams, nc.queryParams);
            sb.append("        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()\n");
            sb.append("            .uri(uri)\n");
            sb.append("            .timeout(REQUEST_TIMEOUT)\n");
            sb.append("            .header(\"Accept\", \"application/json\");\n");
            if (requiresAuth) {
                sb.append("        String token = getPreferredAuthToken();\n");
                sb.append("        if (token != null && !token.isEmpty()) {\n");
                sb.append("            requestBuilder.header(\"Authorization\", \"Bearer \" + token);\n");
                sb.append("        }\n");
            }
            if (jsonBody) {
                sb.append("        String requestBody = \"").append(requestBodyEscaped).append("\";\n");
            }
            appendHttpMethodOnRequestBuilder(sb, method, jsonBody);
            sb.append("        HttpRequest request = requestBuilder.build();\n");
            sb.append("        HttpResponse<String> response = sendRequest(request);\n");
            sb.append("        assertNotNull(response);\n");
            appendExpectedStatusAssertion(sb, nc.expectedStatusCodes);
            generateErrorResponseSchemaValidation(sb, responses, spec);
            sb.append("    }\n\n");
        }

        if (requiresAuth) {
            order++;
            sb.append("    @Test\n");
            sb.append("    @Order(").append(order).append(")\n");
            sb.append("    @DisplayName(\"").append(displayBase).append(" - Anonymous customer (no credentials)\")\n");
            sb.append("    void test").append(capitalize(testMethodName)).append("_AnonymousNoCredentials() throws Exception {\n");
            appendJavaPathUriBlocks(sb, path, pathParams, queryParams);
            sb.append("        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()\n");
            sb.append("            .uri(uri)\n");
            sb.append("            .timeout(REQUEST_TIMEOUT)\n");
            sb.append("            .header(\"Accept\", \"application/json\");\n");
            if (jsonBody) {
                sb.append("        String requestBody = \"").append(requestBodyEscaped).append("\";\n");
            }
            appendHttpMethodOnRequestBuilder(sb, method, jsonBody);
            sb.append("        HttpRequest request = requestBuilder.build();\n");
            sb.append("        HttpResponse<String> response = sendRequest(request);\n");
            sb.append("        assertNotNull(response);\n");
            sb.append("        assertTrue(response.statusCode() == 401 || response.statusCode() == 403,\n");
            sb.append("            \"Expected 401/403 for anonymous access, got: \" + response.statusCode());\n");
            generateErrorResponseSchemaValidation(sb, responses, spec);
            sb.append("    }\n\n");
        }

        if (jsonBody) {
            if (IntegrationScenarioSupport.isRequestBodyRequired(operation, spec)) {
                order++;
                sb.append("    @Test\n");
                sb.append("    @Order(").append(order).append(")\n");
                sb.append("    @DisplayName(\"").append(displayBase).append(" - Missing request body\")\n");
                sb.append("    void test").append(capitalize(testMethodName)).append("_EmptyBody() throws Exception {\n");
                appendJavaPathUriBlocks(sb, path, pathParams, queryParams);
                sb.append("        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()\n");
                sb.append("            .uri(uri)\n");
                sb.append("            .timeout(REQUEST_TIMEOUT)\n");
                sb.append("            .header(\"Accept\", \"application/json\");\n");
                if (requiresAuth) {
                    sb.append("        String token = getPreferredAuthToken();\n");
                    sb.append("        if (token != null && !token.isEmpty()) {\n");
                    sb.append("            requestBuilder.header(\"Authorization\", \"Bearer \" + token);\n");
                    sb.append("        }\n");
                }
                sb.append("        requestBuilder.method(\"").append(method).append("\", HttpRequest.BodyPublishers.noBody());\n");
                sb.append("        HttpRequest request = requestBuilder.build();\n");
                sb.append("        HttpResponse<String> response = sendRequest(request);\n");
                sb.append("        assertNotNull(response);\n");
                sb.append("        assertTrue(response.statusCode() == 400 || response.statusCode() == 422,\n");
                sb.append("            \"Expected 400 or 422 for empty body, got: \" + response.statusCode());\n");
                generateErrorResponseSchemaValidation(sb, responses, spec);
                sb.append("    }\n\n");
            }

            String wrongTypesRaw = IntegrationScenarioSupport.generateWrongTypesBodyRaw(requestBodySchema, spec);
            String wrongTypesEsc = IntegrationScenarioSupport.escapeJavaString(wrongTypesRaw);

            order++;
            sb.append("    @Test\n");
            sb.append("    @Order(").append(order).append(")\n");
            sb.append("    @DisplayName(\"").append(displayBase).append(" - Invalid Request Body: Wrong Types\")\n");
            sb.append("    void test").append(capitalize(testMethodName)).append("_InvalidTypes() throws Exception {\n");
            appendJavaPathUriBlocks(sb, path, pathParams, queryParams);
            sb.append("        String requestBody = \"").append(wrongTypesEsc).append("\";\n");
            sb.append("        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()\n");
            sb.append("            .uri(uri)\n");
            sb.append("            .timeout(REQUEST_TIMEOUT)\n");
            sb.append("            .header(\"Accept\", \"application/json\");\n");
            if (requiresAuth) {
                sb.append("        String token = getPreferredAuthToken();\n");
                sb.append("        if (token != null && !token.isEmpty()) {\n");
                sb.append("            requestBuilder.header(\"Authorization\", \"Bearer \" + token);\n");
                sb.append("        }\n");
            }
            appendHttpMethodOnRequestBuilder(sb, method, true);
            sb.append("        HttpRequest request = requestBuilder.build();\n");
            sb.append("        HttpResponse<String> response = sendRequest(request);\n");
            sb.append("        assertNotNull(response);\n");
            sb.append("        assertTrue(response.statusCode() == 400 || response.statusCode() == 422,\n");
            sb.append("            \"Expected 400 or 422 for invalid types, got: \" + response.statusCode());\n");
            generateErrorResponseSchemaValidation(sb, responses, spec);
            sb.append("    }\n\n");

            List<String> requiredFields = IntegrationScenarioSupport.getRequiredFieldsFromSchema(requestBodySchema, spec);
            if (!requiredFields.isEmpty()) {
                String missingRaw = IntegrationScenarioSupport.generateMissingRequiredFieldsBodyRaw(requestBodySchema, spec);
                String missingEsc = IntegrationScenarioSupport.escapeJavaString(missingRaw);
                order++;
                sb.append("    @Test\n");
                sb.append("    @Order(").append(order).append(")\n");
                sb.append("    @DisplayName(\"").append(displayBase).append(" - Invalid Request Body: Missing Required Fields\")\n");
                sb.append("    void test").append(capitalize(testMethodName)).append("_MissingRequiredFields() throws Exception {\n");
                appendJavaPathUriBlocks(sb, path, pathParams, queryParams);
                sb.append("        String requestBody = \"").append(missingEsc).append("\";\n");
                sb.append("        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()\n");
                sb.append("            .uri(uri)\n");
                sb.append("            .timeout(REQUEST_TIMEOUT)\n");
                sb.append("            .header(\"Accept\", \"application/json\");\n");
                if (requiresAuth) {
                    sb.append("        String token = getPreferredAuthToken();\n");
                    sb.append("        if (token != null && !token.isEmpty()) {\n");
                    sb.append("            requestBuilder.header(\"Authorization\", \"Bearer \" + token);\n");
                    sb.append("        }\n");
                }
                appendHttpMethodOnRequestBuilder(sb, method, true);
                sb.append("        HttpRequest request = requestBuilder.build();\n");
                sb.append("        HttpResponse<String> response = sendRequest(request);\n");
                sb.append("        assertNotNull(response);\n");
                sb.append("        assertTrue(response.statusCode() == 400 || response.statusCode() == 422,\n");
                sb.append("            \"Expected 400 or 422 for missing required fields, got: \" + response.statusCode());\n");
                generateErrorResponseSchemaValidation(sb, responses, spec);
                sb.append("    }\n\n");
            }

            List<IntegrationScenarioSupport.PerFieldInvalidBody> perField =
                    IntegrationScenarioSupport.buildPerFieldInvalidBodies(requestBodySchema, spec, maxBody);
            for (IntegrationScenarioSupport.PerFieldInvalidBody pf : perField) {
                order++;
                sb.append("    @Test\n");
                sb.append("    @Order(").append(order).append(")\n");
                sb.append("    @DisplayName(\"").append(displayBase).append(" - Invalid field: ")
                        .append(escapeForDisplayName(pf.fieldName)).append("\")\n");
                sb.append("    void test").append(capitalize(testMethodName)).append("_InvalidBodyField_")
                        .append(safeMethodSuffix(pf.fieldName)).append("() throws Exception {\n");
                appendJavaPathUriBlocks(sb, path, pathParams, queryParams);
                sb.append("        String requestBody = \"").append(IntegrationScenarioSupport.escapeJavaString(pf.invalidJsonBody)).append("\";\n");
                sb.append("        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()\n");
                sb.append("            .uri(uri)\n");
                sb.append("            .timeout(REQUEST_TIMEOUT)\n");
                sb.append("            .header(\"Accept\", \"application/json\");\n");
                if (requiresAuth) {
                    sb.append("        String token = getPreferredAuthToken();\n");
                    sb.append("        if (token != null && !token.isEmpty()) {\n");
                    sb.append("            requestBuilder.header(\"Authorization\", \"Bearer \" + token);\n");
                    sb.append("        }\n");
                }
                appendHttpMethodOnRequestBuilder(sb, method, true);
                sb.append("        HttpRequest request = requestBuilder.build();\n");
                sb.append("        HttpResponse<String> response = sendRequest(request);\n");
                sb.append("        assertNotNull(response);\n");
                sb.append("        assertTrue(response.statusCode() == 400 || response.statusCode() == 422,\n");
                sb.append("            \"Expected 400 or 422 for invalid field, got: \" + response.statusCode());\n");
                generateErrorResponseSchemaValidation(sb, responses, spec);
                sb.append("    }\n\n");
            }

            List<IntegrationScenarioSupport.OneOfXorNegativeBody> xorNegatives =
                    IntegrationScenarioSupport.buildOneOfXorNegativeBodies(requestBodySchema, spec);
            for (IntegrationScenarioSupport.OneOfXorNegativeBody xn : xorNegatives) {
                order++;
                sb.append("    @Test\n");
                sb.append("    @Order(").append(order).append(")\n");
                sb.append("    @DisplayName(\"").append(displayBase).append(" - oneOf XOR negative: ")
                        .append(escapeForDisplayName(xn.label())).append("\")\n");
                sb.append("    void test").append(capitalize(testMethodName)).append("_OneOfXor_")
                        .append(safeMethodSuffix(xn.label())).append("() throws Exception {\n");
                appendJavaPathUriBlocks(sb, path, pathParams, queryParams);
                sb.append("        String requestBody = \"").append(IntegrationScenarioSupport.escapeJavaString(xn.jsonBody())).append("\";\n");
                sb.append("        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()\n");
                sb.append("            .uri(uri)\n");
                sb.append("            .timeout(REQUEST_TIMEOUT)\n");
                sb.append("            .header(\"Accept\", \"application/json\");\n");
                if (requiresAuth) {
                    sb.append("        String token = getPreferredAuthToken();\n");
                    sb.append("        if (token != null && !token.isEmpty()) {\n");
                    sb.append("            requestBuilder.header(\"Authorization\", \"Bearer \" + token);\n");
                    sb.append("        }\n");
                }
                appendHttpMethodOnRequestBuilder(sb, method, true);
                sb.append("        HttpRequest request = requestBuilder.build();\n");
                sb.append("        HttpResponse<String> response = sendRequest(request);\n");
                sb.append("        assertNotNull(response);\n");
                sb.append("        assertTrue(response.statusCode() == 400 || response.statusCode() == 422,\n");
                sb.append("            \"Expected 400 or 422 for oneOf XOR violation, got: \" + response.statusCode());\n");
                generateErrorResponseSchemaValidation(sb, responses, spec);
                sb.append("    }\n\n");
            }
        }
    }

    private void appendSuccessStatusAssertions(StringBuilder sb, Map<String, Object> responses) {
        if (responses.containsKey("200")) {
            sb.append("        assertEquals(200, response.statusCode(), \"Expected 200 OK status\");\n");
        } else if (responses.containsKey("201")) {
            sb.append("        assertEquals(201, response.statusCode(), \"Expected 201 Created status\");\n");
        } else {
            sb.append("        assertTrue(response.statusCode() >= 200 && response.statusCode() < 300,\n");
            sb.append("            \"Expected successful status code, got: \" + response.statusCode());\n");
        }
    }

    @SuppressWarnings("unchecked")
    private void generateErrorResponseSchemaValidation(StringBuilder sb, Map<String, Object> responses, Map<String, Object> spec) {
        Map<String, Object> schema = null;
        for (String code : new String[]{"422", "400", "401", "403", "404"}) {
            schema = IntegrationScenarioSupport.resolveResponseSchema(code, responses, spec);
            if (schema != null) {
                break;
            }
        }
        sb.append("        // Error response structure (when body is JSON)\n");
        if (schema != null) {
            appendSchemaAssertionsForBodyVar(sb, schema, spec, "response.body()", true);
        } else {
            sb.append("        String errBody = response.body();\n");
            sb.append("        if (errBody != null && !errBody.isBlank()) {\n");
            sb.append("            String et = errBody.trim();\n");
            sb.append("            assertTrue(et.startsWith(\"{\"),\n");
            sb.append("                \"Error body should be a JSON object when present, got: \" + et.substring(0, Math.min(80, et.length())));\n");
            sb.append("        }\n");
        }
    }

    /**
     * Append JSON shape assertions for a schema; {@code bodyExpr} is a Java expression (e.g. response.body()).
     */
    @SuppressWarnings("unchecked")
    private void appendSchemaAssertionsForBodyVar(StringBuilder sb, Map<String, Object> schema, Map<String, Object> spec,
                                                String bodyExpr, boolean errorContext) {
        if (schema == null) {
            return;
        }
        if (schema.containsKey("$ref")) {
            Map<String, Object> resolved = IntegrationScenarioSupport.resolveRef((String) schema.get("$ref"), spec);
            if (resolved != null) {
                schema = resolved;
            }
        }
        Map<String, Object> flattened = IntegrationScenarioSupport.flattenResponseSchema(schema, spec);
        if (flattened != null) {
            schema = flattened;
        }
        sb.append("        String responseBody = ").append(bodyExpr).append(";\n");
        sb.append("        assertDoesNotThrow(() -> {\n");
        sb.append("            String trimmed = responseBody != null ? responseBody.trim() : \"\";\n");
        sb.append("            assertTrue(trimmed.startsWith(\"{\") || trimmed.startsWith(\"[\"),\n");
        sb.append("                \"Response should be valid JSON, got: \" + trimmed.substring(0, Math.min(50, trimmed.length())));\n");
        sb.append("        }, \"Response body should be valid JSON\");\n");

        String type = (String) schema.get("type");
        Map<String, Object> properties = Util.asStringObjectMap(schema.get("properties"));
        List<String> required = schema.containsKey("required") ? Util.asStringList(schema.get("required")) : null;

        if ("object".equals(type) && properties != null && !properties.isEmpty()) {
            if (required != null && !required.isEmpty()) {
                sb.append("        // Required fields in ").append(errorContext ? "error " : "").append("response\n");
                for (String field : required) {
                    sb.append("        assertTrue(responseBody.contains(\"\\\"").append(field).append("\\\"\"),\n");
                    sb.append("            \"Response should contain field '").append(field).append("'\");\n");
                }
            }
            sb.append("        // Field types (heuristic)\n");
            for (Map.Entry<String, Object> propEntry : properties.entrySet()) {
                Map<String, Object> propSchema = Util.asStringObjectMap(propEntry.getValue());
                if (propSchema == null) {
                    continue;
                }
                if (propSchema.containsKey("$ref")) {
                    Map<String, Object> resolved = IntegrationScenarioSupport.resolveRef((String) propSchema.get("$ref"), spec);
                    if (resolved != null) {
                        propSchema = resolved;
                    }
                }
                String propType = (String) propSchema.get("type");
                String fieldName = propEntry.getKey();
                if ("integer".equals(propType) || "number".equals(propType)) {
                    sb.append("        if (responseBody.contains(\"\\\"").append(fieldName).append("\\\"\")) {\n");
                    sb.append("            int idx = responseBody.indexOf(\"\\\"").append(fieldName).append("\\\"\");\n");
                    sb.append("            if (idx >= 0) {\n");
                    sb.append("                int colonIdx = responseBody.indexOf(':', idx);\n");
                    sb.append("                if (colonIdx >= 0) {\n");
                    sb.append("                    String afterColon = responseBody.substring(colonIdx + 1).trim();\n");
                    sb.append("                    assertFalse(afterColon.startsWith(\"\\\"\"),\n");
                    sb.append("                        \"Field '").append(fieldName).append("' should be numeric if present\");\n");
                    sb.append("                }\n");
                    sb.append("            }\n");
                    sb.append("        }\n");
                } else if ("boolean".equals(propType)) {
                    sb.append("        if (responseBody.contains(\"\\\"").append(fieldName).append("\\\"\")) {\n");
                    sb.append("            int idx = responseBody.indexOf(\"\\\"").append(fieldName).append("\\\"\");\n");
                    sb.append("            if (idx >= 0) {\n");
                    sb.append("                int colonIdx = responseBody.indexOf(':', idx);\n");
                    sb.append("                if (colonIdx >= 0) {\n");
                    sb.append("                    String afterColon = responseBody.substring(colonIdx + 1).trim();\n");
                    sb.append("                    assertTrue(afterColon.startsWith(\"true\") || afterColon.startsWith(\"false\") || afterColon.startsWith(\"null\"),\n");
                    sb.append("                        \"Field '").append(fieldName).append("' should be boolean\");\n");
                    sb.append("                }\n");
                    sb.append("            }\n");
                    sb.append("        }\n");
                }
            }
        } else if ("array".equals(type)) {
            sb.append("        assertTrue(responseBody.trim().startsWith(\"[\"), \"Expected JSON array body\");\n");
        }
    }

    /**
     * Generate test configuration file
     */
    private void generateTestConfiguration(String outputDir, String basePackage, String baseUrl) throws IOException {
        String configContent = "# Integration Test Configuration\n" +
                "# Generated from OpenAPI specification\n\n" +
                "base.url=" + baseUrl + "\n" +
                "timeout.seconds=30\n" +
                "package.name=" + basePackage + "\n\n" +
                "# Authentication (if required)\n" +
                "# api.token=${API_TOKEN}\n" +
                "# API_BEARER_TOKEN (fallback for client-application context)\n" +
                "# oauth.client.id=${OAUTH_CLIENT_ID}\n" +
                "# oauth.client.secret=${OAUTH_CLIENT_SECRET}\n\n" +
                "# Multi-context integration tests (Bearer-style APIs)\n" +
                "# INTEGRATION_TOKEN_CLIENT_APPLICATION — client credentials / app token (falls back to API_BEARER_TOKEN, API_TOKEN)\n" +
                "# INTEGRATION_TOKEN_AUTHENTICATED_CUSTOMER — end-user / customer session token\n" +
                "# Anonymous: no env var; tests omit Authorization (expect 401 on secured operations)\n\n" +
                "# Optional caps (TestConfig additionalProperties when generating tests)\n" +
                "# integrationMaxInvalidBodyFieldsPerOperation=40\n" +
                "# integrationMaxInvalidParamCasesPerOperation=25\n";

        Files.write(Paths.get(outputDir, "test-config.properties"), configContent.getBytes());
    }

    private void generatePomXml(String outputDir, String basePackage) throws IOException {
        String pomContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
                    <modelVersion>4.0.0</modelVersion>

                    <groupId>%s</groupId>
                    <artifactId>api-integration-tests</artifactId>
                    <version>1.0.0</version>
                    <packaging>jar</packaging>

                    <properties>
                        <maven.compiler.source>21</maven.compiler.source>
                        <maven.compiler.target>21</maven.compiler.target>
                        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
                        <junit.version>5.12.2</junit.version>
                        <jacoco.version>0.8.13</jacoco.version>
                    </properties>

                    <dependencies>
                        <dependency>
                            <groupId>org.junit.jupiter</groupId>
                            <artifactId>junit-jupiter</artifactId>
                            <version>${junit.version}</version>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>

                    <build>
                        <plugins>
                            <plugin>
                                <groupId>org.apache.maven.plugins</groupId>
                                <artifactId>maven-surefire-plugin</artifactId>
                                <version>3.5.5</version>
                            </plugin>
                            <plugin>
                                <groupId>org.jacoco</groupId>
                                <artifactId>jacoco-maven-plugin</artifactId>
                                <version>${jacoco.version}</version>
                                <executions>
                                    <execution>
                                        <id>prepare-agent</id>
                                        <goals>
                                            <goal>prepare-agent</goal>
                                        </goals>
                                    </execution>
                                    <execution>
                                        <id>report</id>
                                        <phase>test</phase>
                                        <goals>
                                            <goal>report</goal>
                                        </goals>
                                    </execution>
                                </executions>
                            </plugin>
                        </plugins>
                    </build>
                </project>
                """.formatted(basePackage);

        Files.write(Paths.get(outputDir, "pom.xml"), pomContent.getBytes());
    }

    /**
     * Generate test utilities
     */
    private void generateTestUtilities(String outputDir, String basePackage) throws IOException {
        String packageDir = outputDir + "/" + basePackage.replace(".", "/");
        Files.createDirectories(Paths.get(packageDir));

        // Generate IntegrationTestUtils class
        String utilsContent = generateIntegrationTestUtilsClass(basePackage);
        Files.write(Paths.get(packageDir, "IntegrationTestUtils.java"), utilsContent.getBytes());
    }

    /**
     * Generate IntegrationTestUtils helper class
     */
    private String generateIntegrationTestUtilsClass(String basePackage) {
        return """
                package %s;

                import java.net.http.*;
                import java.net.URI;
                import java.time.Duration;
                import java.util.*;

                /**
                 * Utility class for integration tests
                 */
                public class IntegrationTestUtils {

                    private IntegrationTestUtils() {
                    }

                    /**
                     * Poll the server until a GET returns a non-5xx status or timeout elapses.
                     */
                    public static boolean waitForServer(String baseUrl, Duration timeout) {
                        if (baseUrl == null || baseUrl.isBlank()) {
                            return false;
                        }
                        HttpClient client = HttpClient.newBuilder().connectTimeout(timeout).build();
                        long deadline = System.nanoTime() + timeout.toNanos();
                        while (System.nanoTime() < deadline) {
                            try {
                                HttpRequest req = HttpRequest.newBuilder()
                                        .uri(URI.create(baseUrl))
                                        .timeout(Duration.ofSeconds(5))
                                        .GET()
                                        .build();
                                HttpResponse<Void> resp = client.send(req, HttpResponse.BodyHandlers.discarding());
                                if (resp.statusCode() < 500) {
                                    System.out.println("Server health check: " + resp.statusCode());
                                    return true;
                                }
                            } catch (Exception ignored) {
                                // retry
                            }
                            try {
                                Thread.sleep(500);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                return false;
                            }
                        }
                        return false;
                    }

                    /**
                     * Heuristic JSON validation: object/array parse check plus required top-level field names.
                     */
                    public static void assertJsonHasRequiredFields(String body, String... requiredFields) {
                        if (body == null) {
                            throw new AssertionError("Response body is null");
                        }
                        String trimmed = body.trim();
                        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
                            throw new AssertionError("Response should be JSON, got: "
                                    + trimmed.substring(0, Math.min(50, trimmed.length())));
                        }
                        if (requiredFields != null) {
                            for (String field : requiredFields) {
                                if (field != null && !field.isBlank() && !trimmed.contains("\\"" + field + "\\"")) {
                                    throw new AssertionError("Response should contain field '" + field + "'");
                                }
                            }
                        }
                    }

                    /**
                     * @deprecated Use {@link #assertJsonHasRequiredFields(String, String...)} instead.
                     */
                    @Deprecated
                    public static boolean validateResponse(Object response, Map<String, Object> schema) {
                        return true;
                    }

                    /**
                     * Optional cleanup hook when INTEGRATION_CLEANUP_ENABLED=true.
                     */
                    public static void cleanupTestData(String resourceId) {
                        if (!"true".equalsIgnoreCase(System.getenv("INTEGRATION_CLEANUP_ENABLED"))) {
                            return;
                        }
                        if (resourceId != null) {
                            System.out.println("Cleanup requested for resource: " + resourceId);
                        }
                    }
                }
                """.formatted(basePackage);
    }

    /**
     * Generate response schema validation assertions for first declared 2xx response with JSON schema.
     */
    @SuppressWarnings("unchecked")
    private void generateResponseSchemaValidation(StringBuilder sb, Map<String, Object> responses, Map<String, Object> spec) {
        Map<String, Object> responseObj = null;
        String statusCode = null;
        for (String code : new String[]{"200", "201", "202", "204"}) {
            if (responses != null && responses.containsKey(code)) {
                responseObj = Util.asStringObjectMap(responses.get(code));
                statusCode = code;
                break;
            }
        }
        if (responseObj == null) {
            return;
        }
        if (responseObj.containsKey("$ref")) {
            responseObj = IntegrationScenarioSupport.resolveRef((String) responseObj.get("$ref"), spec);
            if (responseObj == null) {
                return;
            }
        }
        if ("204".equals(statusCode)) {
            return;
        }
        Map<String, Object> content = Util.asStringObjectMap(responseObj.get("content"));
        if (content == null) {
            return;
        }
        Map<String, Object> mediaType = Util.asStringObjectMap(content.get("application/json"));
        if (mediaType == null) {
            for (Object v : content.values()) {
                mediaType = Util.asStringObjectMap(v);
                if (mediaType != null) {
                    break;
                }
            }
        }
        if (mediaType == null) {
            return;
        }
        Map<String, Object> schema = Util.asStringObjectMap(mediaType.get("schema"));
        if (schema == null) {
            return;
        }
        if (schema.containsKey("$ref")) {
            Map<String, Object> resolved = IntegrationScenarioSupport.resolveRef((String) schema.get("$ref"), spec);
            if (resolved != null) {
                schema = resolved;
            }
        }
        Map<String, Object> flattened = IntegrationScenarioSupport.flattenResponseSchema(schema, spec);
        if (flattened != null) {
            schema = flattened;
        }
        sb.append("        // Validate response against schema\n");
        appendSchemaAssertionsForBodyVar(sb, schema, spec, "response.body()", false);
    }

    /**
     * Generate auth setup methods from OAS securitySchemes
     */
    @SuppressWarnings("unchecked")
    private void generateAuthSetupFromSecuritySchemes(StringBuilder sb, Map<String, Object> spec) {
        Map<String, Object> components = Util.asStringObjectMap(spec.get("components"));
        Map<String, Object> securitySchemes = components != null ? Util.asStringObjectMap(components.get("securitySchemes")) : null;

        if (securitySchemes == null || securitySchemes.isEmpty()) {
            // Fallback: simple bearer token from environment
            sb.append("    /**\n");
            sb.append("     * Helper method to get authentication token\n");
            sb.append("     */\n");
            sb.append("    private String getAuthToken() {\n");
            sb.append("        return System.getenv(\"API_TOKEN\");\n");
            sb.append("    }\n\n");
            return;
        }

        // Emit at most one getAuthToken(); collect OAuth token URL from first oauth2 scheme with flows
        boolean generatedGetAuthToken = false;
        String oauthTokenUrl = resolveFirstOAuthTokenUrl(securitySchemes);

        for (Map.Entry<String, Object> entry : securitySchemes.entrySet()) {
            String schemeName = entry.getKey();
            Map<String, Object> scheme = Util.asStringObjectMap(entry.getValue());
            if (scheme == null) {
                continue;
            }

            String schemeType = (String) scheme.get("type");
            String bearerFormat = (String) scheme.get("bearerFormat");
            String schemeStr = (String) scheme.get("scheme");
            String inField = (String) scheme.get("in");
            String paramName = (String) scheme.get("name");

            if ("http".equals(schemeType) && "bearer".equals(schemeStr) && !generatedGetAuthToken) {
                sb.append("    /**\n");
                sb.append("     * Helper method to get authentication token (Bearer");
                if (bearerFormat != null) sb.append(" - ").append(bearerFormat);
                sb.append(")\n");
                sb.append("     * Security scheme: ").append(schemeName).append("\n");
                sb.append("     */\n");
                sb.append("    private String getAuthToken() {\n");
                sb.append("        String token = System.getenv(\"API_BEARER_TOKEN\");\n");
                sb.append("        if (token == null || token.isEmpty()) {\n");
                sb.append("            token = System.getenv(\"API_TOKEN\");\n");
                sb.append("        }\n");
                sb.append("        return token;\n");
                sb.append("    }\n\n");
                generatedGetAuthToken = true;

            } else if ("apiKey".equals(schemeType)) {
                String envVarName = "API_KEY";
                sb.append("    /**\n");
                sb.append("     * API key helpers — scheme: ").append(schemeName).append(" (apiKey in ").append(inField).append(")\n");
                sb.append("     */\n");
                if ("header".equals(inField)) {
                    sb.append("    private String getApiKeyHeaderName() {\n");
                    sb.append("        return \"").append(paramName != null ? paramName : "X-API-Key").append("\";\n");
                    sb.append("    }\n\n");
                    sb.append("    private String getApiKeyValue() {\n");
                    sb.append("        return System.getenv(\"").append(envVarName).append("\");\n");
                    sb.append("    }\n\n");
                } else if ("query".equals(inField)) {
                    sb.append("    private String getApiKeyParamName() {\n");
                    sb.append("        return \"").append(paramName != null ? paramName : "api_key").append("\";\n");
                    sb.append("    }\n\n");
                    sb.append("    private String getApiKeyValue() {\n");
                    sb.append("        return System.getenv(\"").append(envVarName).append("\");\n");
                    sb.append("    }\n\n");
                }
                if (!generatedGetAuthToken) {
                    sb.append("    private String getAuthToken() {\n");
                    sb.append("        return getApiKeyValue();\n");
                    sb.append("    }\n\n");
                    generatedGetAuthToken = true;
                }

            } else if ("oauth2".equals(schemeType) && !generatedGetAuthToken) {
                sb.append("    /**\n");
                sb.append("     * OAuth2 token helper (first configured scheme; use OAUTH2_ACCESS_TOKEN to skip fetch)\n");
                sb.append("     */\n");
                sb.append("    private String getAuthToken() {\n");
                sb.append("        String token = System.getenv(\"OAUTH2_ACCESS_TOKEN\");\n");
                sb.append("        if (token != null && !token.isEmpty()) {\n");
                sb.append("            return token;\n");
                sb.append("        }\n");
                sb.append("        String clientId = System.getenv(\"OAUTH_CLIENT_ID\");\n");
                sb.append("        String clientSecret = System.getenv(\"OAUTH_CLIENT_SECRET\");\n");
                sb.append("        String tokenUrl = \"").append(oauthTokenUrl != null ? oauthTokenUrl : "").append("\";\n");
                sb.append("        if (clientId == null || clientSecret == null || tokenUrl.isEmpty()) {\n");
                sb.append("            return null;\n");
                sb.append("        }\n");
                sb.append("        try {\n");
                sb.append("            String credentials = java.util.Base64.getEncoder().encodeToString(\n");
                sb.append("                (clientId + \":\" + clientSecret).getBytes());\n");
                sb.append("            HttpRequest tokenRequest = HttpRequest.newBuilder()\n");
                sb.append("                .uri(URI.create(tokenUrl))\n");
                sb.append("                .timeout(REQUEST_TIMEOUT)\n");
                sb.append("                .header(\"Authorization\", \"Basic \" + credentials)\n");
                sb.append("                .header(\"Content-Type\", \"application/x-www-form-urlencoded\")\n");
                sb.append("                .POST(HttpRequest.BodyPublishers.ofString(\"grant_type=client_credentials\"))\n");
                sb.append("                .build();\n");
                sb.append("            HttpResponse<String> tokenResponse = httpClient.send(tokenRequest, HttpResponse.BodyHandlers.ofString());\n");
                sb.append("            if (tokenResponse.statusCode() == 200) {\n");
                sb.append("                String body = tokenResponse.body();\n");
                sb.append("                int start = body.indexOf(\"\\\"access_token\\\":\\\"\") + 16;\n");
                sb.append("                int end = body.indexOf('\"', start);\n");
                sb.append("                if (start > 15 && end > start) {\n");
                sb.append("                    return body.substring(start, end);\n");
                sb.append("                }\n");
                sb.append("            }\n");
                sb.append("        } catch (Exception e) {\n");
                sb.append("            System.err.println(\"Error retrieving OAuth2 token: \" + e.getMessage());\n");
                sb.append("        }\n");
                sb.append("        return null;\n");
                sb.append("    }\n\n");
                generatedGetAuthToken = true;
            }
        }

        // Fallback if no recognized scheme generated getAuthToken
        if (!generatedGetAuthToken) {
            sb.append("    /**\n");
            sb.append("     * Helper method to get authentication token\n");
            sb.append("     */\n");
            sb.append("    private String getAuthToken() {\n");
            sb.append("        return System.getenv(\"API_TOKEN\");\n");
            sb.append("    }\n\n");
        }
    }

    @SuppressWarnings("unchecked")
    private String resolveFirstOAuthTokenUrl(Map<String, Object> securitySchemes) {
        if (securitySchemes == null) {
            return "";
        }
        for (Object schemeObj : securitySchemes.values()) {
            Map<String, Object> scheme = Util.asStringObjectMap(schemeObj);
            if (scheme == null || !"oauth2".equals(scheme.get("type"))) {
                continue;
            }
            Map<String, Object> flows = Util.asStringObjectMap(scheme.get("flows"));
            if (flows == null) {
                continue;
            }
            for (String flowKey : List.of("clientCredentials", "authorizationCode", "password")) {
                Map<String, Object> flow = Util.asStringObjectMap(flows.get(flowKey));
                if (flow != null) {
                    String tokenUrl = (String) flow.get("tokenUrl");
                    if (tokenUrl != null && !tokenUrl.isBlank()) {
                        return tokenUrl;
                    }
                }
            }
        }
        return "";
    }

    // Helper methods
    private String getAPITitle(Map<String, Object> spec) {
        Map<String, Object> info = Util.asStringObjectMap(spec.get("info"));
        return info != null ? (String) info.get("title") : "API";
    }

    private String getBaseUrl(Map<String, Object> spec) {
        if (spec.containsKey("servers")) {
            List<Map<String, Object>> servers = Util.asStringObjectMapList(spec.get("servers"));
            if (servers != null && !servers.isEmpty()) {
                String url = (String) servers.get(0).get("url");
                if (url != null) {
                    return url;
                }
            }
        }
        return "http://localhost:8080";
    }

    private String getOperationTag(Map<String, Object> operation) {
        List<String> tags = Util.asStringList(operation.get("tags"));
        return tags != null && !tags.isEmpty() ? tags.get(0) : "Default";
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
        return "Integration Test Generator";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getTestType() {
        return "integration";
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
