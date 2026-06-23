package egain.oassdk.testgenerators.integration;

import egain.oassdk.Util;
import egain.oassdk.config.TestConfig;
import egain.oassdk.core.Constants;
import egain.oassdk.core.exceptions.GenerationException;
import egain.oassdk.testgenerators.common.TestCodegenSupport;
import egain.oassdk.testgenerators.common.TestMavenSupport;
import egain.oassdk.testgenerators.common.TestOutputLayout;
import egain.oassdk.testgenerators.common.TestProfileSupport;
import egain.oassdk.testgenerators.common.TestSpecUtils;
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
            String apiTitle = TestSpecUtils.getApiTitle(spec);

            // Get package name from additional properties or use default
            String basePackage = "com.example.api";
            if (config != null && config.getAdditionalProperties() != null) {
                Object packageNameObj = config.getAdditionalProperties().get("packageName");
                if (packageNameObj != null) {
                    basePackage = packageNameObj.toString();
                }
            }

            // Get base URL from servers (default for test-env.properties; runtime uses TestEnv)
            String baseUrl = TestSpecUtils.resolveBaseUrl(spec, config);

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
            String packageDir = TestOutputLayout.testJavaDir(outputDir, basePackage);
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
        sb.append("import java.util.concurrent.CompletableFuture;\n");
        sb.append(TestCodegenSupport.supportImport(basePackage));

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
        sb.append("@TestInstance(TestInstance.Lifecycle.PER_CLASS)\n");
        sb.append("public class ").append(className).append(" {\n\n");

        sb.append("    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);\n");
        sb.append("    private static HttpClient httpClient;\n\n");
        sb.append(TestCodegenSupport.invalidTestConstants()).append("\n");

        sb.append("    @BeforeAll\n");
        sb.append("    static void setUpAll() throws Exception {\n");
        sb.append("        httpClient = TestHttp.client();\n");
        sb.append("        String url = TestEnv.baseUrl();\n");
        sb.append("        if (!IntegrationTestUtils.waitForServer(url, REQUEST_TIMEOUT)) {\n");
        sb.append("            System.err.println(\"Warning: Could not reach server at \" + url);\n");
        sb.append("        }\n");
        sb.append("        if (TestEnv.bootstrapEnabled()) {\n");
        sb.append("            IntegrationTestUtils.bootstrapBaseData(httpClient, REQUEST_TIMEOUT);\n");
        sb.append("        }\n");
        sb.append("    }\n\n");

        sb.append("    @BeforeEach\n");
        sb.append("    void setUp() {\n");
        sb.append("        // per-test setup\n");
        sb.append("    }\n\n");

        sb.append("    @AfterEach\n");
        sb.append("    void tearDown() throws Exception {\n");
        sb.append("        IntegrationTestUtils.cleanupCreatedResources(httpClient, REQUEST_TIMEOUT);\n");
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
        sb.append("        StringBuilder uriBuilder = new StringBuilder(TestEnv.baseUrl()).append(path);\n");
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
        sb.append(TestCodegenSupport.tokenHelpers()).append("\n");

        sb.append("}\n");

        return sb.toString();
    }

    private void appendCreateGetVerifyBlock(StringBuilder sb, String method, String path,
                                          OperationInfo opInfo, Map<String, Object> spec,
                                          boolean requiresAuth) {
        if (!"POST".equals(method)) {
            return;
        }
        Map<String, Object> responses = Util.asStringObjectMap(opInfo.operation.get("responses"));
        if (responses == null || !responses.containsKey("201")) {
            return;
        }
        String getPath = findGetByIdPath(spec, path);
        if (getPath == null) {
            return;
        }
        sb.append("        String createdId = IntegrationTestUtils.extractCreatedId(response);\n");
        sb.append("        if (createdId != null) {\n");
        sb.append("            TestContext.trackCreatedId(createdId);\n");
        sb.append("            IntegrationTestUtils.assertGetMatchesCreate(httpClient, createdId, \"")
                .append(IntegrationScenarioSupport.escapeJavaString(getPath))
                .append("\", REQUEST_TIMEOUT, requestBody");
        if (requiresAuth) {
            sb.append(", getTokenClientApplication()");
        } else {
            sb.append(", null");
        }
        sb.append(");\n");
        sb.append("        }\n");
    }

    private String findGetByIdPath(Map<String, Object> spec, String collectionPath) {
        Map<String, Object> paths = Util.asStringObjectMap(spec.get("paths"));
        if (paths == null) {
            return null;
        }
        String candidate = collectionPath.endsWith("/") ? collectionPath + "{id}" : collectionPath + "/{id}";
        for (String p : paths.keySet()) {
            if (p.matches(".*\\{[^}]+}.*") && p.startsWith(collectionPath.replaceAll("/$", ""))) {
                Map<String, Object> pathItem = Util.asStringObjectMap(paths.get(p));
                if (pathItem != null && pathItem.containsKey("get")) {
                    return p;
                }
            }
        }
        return paths.containsKey(candidate) ? candidate : null;
    }

    private void appendMultiContextAuthHelpers(StringBuilder sb) {
        // replaced by TestCodegenSupport.tokenHelpers()
    }

    private void appendJavaPathUriBlocks(StringBuilder sb, String pathTemplate,
                                         Map<String, String> pathParams, Map<String, String> queryParams) {
        if (pathParams != null && !pathParams.isEmpty()) {
            sb.append("        Map<String, String> pathParams = new HashMap<>();\n");
            for (Map.Entry<String, String> entry : pathParams.entrySet()) {
                sb.append("        pathParams.put(\"")
                        .append(entry.getKey()).append("\", ")
                        .append(TestCodegenSupport.paramValueExpression(entry.getKey(), entry.getValue())).append(");\n");
            }
            sb.append("        String path = replacePathParameters(\"").append(pathTemplate).append("\", pathParams);\n");
        } else {
            sb.append("        String path = \"").append(pathTemplate).append("\";\n");
        }
        if (queryParams != null && !queryParams.isEmpty()) {
            sb.append("        Map<String, String> queryParams = new HashMap<>();\n");
            for (Map.Entry<String, String> entry : queryParams.entrySet()) {
                sb.append("        queryParams.put(\"")
                        .append(entry.getKey()).append("\", ")
                        .append(TestCodegenSupport.paramValueExpression(entry.getKey(), entry.getValue())).append(");\n");
            }
            sb.append("        URI uri = buildUri(path, queryParams);\n");
        } else {
            sb.append("        URI uri = URI.create(TestEnv.baseUrl() + path);\n");
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
        Map<String, String> queryParams = IntegrationScenarioSupport.buildSuccessQueryParams(parameters, spec);
        for (Map<String, Object> param : parameters) {
            String paramName = (String) param.get("name");
            String paramIn = (String) param.get("in");
            if ("path".equals(paramIn)) {
                pathParams.put(paramName, IntegrationScenarioSupport.getParameterExample(param));
            }
        }
        // path params resolved via TestEnv in appendJavaPathUriBlocks

        int maxBody = IntegrationScenarioSupport.maxInvalidBodyFields(config);
        int maxParam = IntegrationScenarioSupport.maxInvalidParamCases(config);
        String requestBodyRaw = IntegrationScenarioSupport.generateRequestBodyFromSchemaRaw(operation, spec);
        String requestBodyEscaped = IntegrationScenarioSupport.escapeJavaString(requestBodyRaw);
        boolean smoke = TestProfileSupport.isSmoke(config);
        boolean destructiveOp = "DELETE".equals(method);
        Map<String, Object> requestBodySchema = IntegrationScenarioSupport.extractRequestBodySchema(operation, spec);
        boolean jsonBody = "POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method);

        String displayBase = escapeForDisplayName(summary != null ? summary : method + " " + path);

        if (requiresAuth) {
            order++;
            sb.append("    @Test\n");
            sb.append("    @Order(").append(order).append(")\n");
            sb.append("    @DisplayName(\"").append(displayBase).append(" - Successful request (client application)\")\n");
            sb.append("    void test").append(capitalize(testMethodName)).append("_Success_ClientApplication() throws Exception {\n");
            if (destructiveOp) {
                sb.append(TestCodegenSupport.destructiveGate());
            }
            sb.append("        String tok = getTokenClientApplication();\n");
            sb.append("        Assumptions.assumeTrue(tok != null && !tok.isEmpty(), \"Skip: set INTEGRATION_TOKEN_CLIENT_APPLICATION (or API_BEARER_TOKEN / API_TOKEN)\");\n");
            appendJavaPathUriBlocks(sb, path, pathParams, queryParams);
            sb.append("        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()\n");
            sb.append("            .uri(uri)\n");
            sb.append("            .timeout(REQUEST_TIMEOUT)\n");
            sb.append("            .header(\"Accept\", \"application/json\")\n");
            sb.append("            .header(\"Accept-Language\", TestEnv.acceptLanguage())\n");
            sb.append("            .header(\"Authorization\", \"Bearer \" + tok);\n");
            if (jsonBody) {
                sb.append("        String requestBody = ").append(TestCodegenSupport.requestBodyBind(requestBodyEscaped)).append(";\n");
            }
            appendHttpMethodOnRequestBuilder(sb, method, jsonBody);
            sb.append("        HttpRequest request = requestBuilder.build();\n");
            sb.append("        HttpResponse<String> response = sendRequest(request);\n");
            sb.append("        assertNotNull(response);\n");
            appendSuccessStatusAssertions(sb, responses);
            sb.append("        assertNotNull(response.body());\n");
            appendCreateGetVerifyBlock(sb, method, path, opInfo, spec, requiresAuth);
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
            sb.append("            .header(\"Accept-Language\", TestEnv.acceptLanguage())\n");
            sb.append("            .header(\"Authorization\", \"Bearer \" + tok);\n");
            if (jsonBody) {
                sb.append("        String requestBody = ").append(TestCodegenSupport.requestBodyBind(requestBodyEscaped)).append(";\n");
            }
            appendHttpMethodOnRequestBuilder(sb, method, jsonBody);
            sb.append("        HttpRequest request = requestBuilder.build();\n");
            sb.append("        HttpResponse<String> response = sendRequest(request);\n");
            sb.append("        assertNotNull(response);\n");
            appendSuccessStatusAssertions(sb, responses);
            sb.append("        assertNotNull(response.body());\n");
            appendCreateGetVerifyBlock(sb, method, path, opInfo, spec, requiresAuth);
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
                sb.append("        String requestBody = ").append(TestCodegenSupport.requestBodyBind(requestBodyEscaped)).append(";\n");
            }
            appendHttpMethodOnRequestBuilder(sb, method, jsonBody);
            sb.append("        HttpRequest request = requestBuilder.build();\n");
            sb.append("        HttpResponse<String> response = sendRequest(request);\n");
            sb.append("        assertNotNull(response);\n");
            appendSuccessStatusAssertions(sb, responses);
            sb.append("        assertNotNull(response.body());\n");
            appendCreateGetVerifyBlock(sb, method, path, opInfo, spec, requiresAuth);
            sb.append("        // Validate response against schema\n");
            generateResponseSchemaValidation(sb, responses, spec);
            sb.append("    }\n\n");
        }

        List<IntegrationScenarioSupport.OneOfVariantBody> oneOfVariants =
                jsonBody && !smoke ? IntegrationScenarioSupport.buildOneOfVariantBodies(requestBodySchema, spec) : List.of();
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

        if (!smoke && IntegrationScenarioSupport.emitDeclaredErrorCodes(config)) {
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
                    sb.append("        String requestBody = ").append(TestCodegenSupport.requestBodyBind(requestBodyEscaped)).append(";\n");
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

        if (!smoke) {
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
                sb.append("        String requestBody = ").append(TestCodegenSupport.requestBodyBind(requestBodyEscaped)).append(";\n");
            }
            appendHttpMethodOnRequestBuilder(sb, method, jsonBody);
            sb.append("        HttpRequest request = requestBuilder.build();\n");
            sb.append("        HttpResponse<String> response = sendRequest(request);\n");
            sb.append("        assertNotNull(response);\n");
            appendExpectedStatusAssertion(sb, nc.expectedStatusCodes);
            generateErrorResponseSchemaValidation(sb, responses, spec);
            sb.append("    }\n\n");
        }
        } // end !smoke param negatives

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
                sb.append("        String requestBody = ").append(TestCodegenSupport.requestBodyBind(requestBodyEscaped)).append(";\n");
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

        if (jsonBody && !smoke) {
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
        String pomContent = TestMavenSupport.pomHeader("api-integration-tests", basePackage)
                + TestMavenSupport.junitDependency()
                + TestMavenSupport.buildSectionWithTestSupport();
        Files.write(Paths.get(outputDir, "pom.xml"), pomContent.getBytes());
    }

    /**
     * Generate test utilities
     */
    private void generateTestUtilities(String outputDir, String basePackage) throws IOException {
        String packageDir = TestOutputLayout.testJavaDir(outputDir, basePackage);
        Files.createDirectories(Paths.get(packageDir));
        Files.write(Paths.get(packageDir, "IntegrationTestUtils.java"),
                generateIntegrationTestUtilsClass(basePackage).getBytes());
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

                import %s.support.*;

                public class IntegrationTestUtils {

                    private IntegrationTestUtils() {
                    }

                    public static boolean waitForServer(String baseUrl, Duration timeout) {
                        if (baseUrl == null || baseUrl.isBlank()) {
                            return false;
                        }
                        HttpClient client = TestHttp.client();
                        long deadline = System.nanoTime() + timeout.toNanos();
                        while (System.nanoTime() < deadline) {
                            try {
                                HttpRequest req = HttpRequest.newBuilder()
                                        .uri(URI.create(baseUrl))
                                        .timeout(Duration.ofSeconds(5))
                                        .header("Accept-Language", TestEnv.acceptLanguage())
                                        .GET()
                                        .build();
                                HttpResponse<Void> resp = client.send(req, HttpResponse.BodyHandlers.discarding());
                                if (resp.statusCode() < 500) {
                                    return true;
                                }
                            } catch (Exception ignored) {
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

                    public static void assertJsonHasRequiredFields(String body, String... requiredFields) {
                        if (body == null) {
                            throw new AssertionError("Response body is null");
                        }
                        String trimmed = body.trim();
                        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
                            throw new AssertionError("Response should be JSON");
                        }
                        if (requiredFields != null) {
                            for (String field : requiredFields) {
                                if (field != null && !field.isBlank() && !trimmed.contains("\\"" + field + "\\"")) {
                                    throw new AssertionError("Response should contain field '" + field + "'");
                                }
                            }
                        }
                    }

                    public static String extractCreatedId(HttpResponse<String> response) {
                        if (response == null) {
                            return null;
                        }
                        String location = response.headers().firstValue("Location").orElse(null);
                        if (location != null) {
                            int slash = location.lastIndexOf('/');
                            if (slash >= 0 && slash < location.length() - 1) {
                                return location.substring(slash + 1);
                            }
                        }
                        String body = response.body();
                        if (body != null) {
                            for (String key : List.of("id", "folderID", "folderId")) {
                                String needle = "\\"" + key + "\\":\\"";
                                int i = body.indexOf(needle);
                                if (i >= 0) {
                                    int start = i + needle.length();
                                    int end = body.indexOf('"', start);
                                    if (end > start) {
                                        return body.substring(start, end);
                                    }
                                }
                            }
                        }
                        return null;
                    }

                    public static void assertGetMatchesCreate(HttpClient client, String id, String getPathTemplate,
                                                              Duration timeout, String createBody, String token)
                            throws Exception {
                        String path = getPathTemplate.replaceAll("\\{[^}]+}", id);
                        HttpRequest.Builder b = HttpRequest.newBuilder()
                                .uri(URI.create(TestEnv.baseUrl() + path))
                                .timeout(timeout)
                                .header("Accept", "application/json")
                                .header("Accept-Language", TestEnv.acceptLanguage())
                                .GET();
                        if (token != null && !token.isEmpty()) {
                            b.header("Authorization", "Bearer " + token);
                        }
                        HttpResponse<String> getResp = client.send(b.build(), HttpResponse.BodyHandlers.ofString());
                        if (getResp.statusCode() < 200 || getResp.statusCode() >= 300) {
                            throw new AssertionError("GET after create failed: " + getResp.statusCode());
                        }
                        String getBody = getResp.body();
                        if (createBody == null || createBody.isBlank()) {
                            return;
                        }
                        assertFieldEqual(createBody, getBody, "name");
                        assertFieldEqual(createBody, getBody, "description");
                        assertNestedFieldEqual(createBody, getBody, "parent", "id");
                        assertFieldEqual(createBody, getBody, "translateContent");
                    }

                    private static void assertFieldEqual(String createJson, String getJson, String field) {
                        String createVal = extractJsonString(createJson, field);
                        if (createVal == null) {
                            return;
                        }
                        String getVal = extractJsonString(getJson, field);
                        if (getVal != null && !createVal.equals(getVal)) {
                            throw new AssertionError("Field '" + field + "' should match after create");
                        }
                    }

                    private static void assertNestedFieldEqual(String createJson, String getJson, String object, String field) {
                        String createVal = extractNestedJsonString(createJson, object, field);
                        if (createVal == null) {
                            return;
                        }
                        String getVal = extractNestedJsonString(getJson, object, field);
                        if (getVal != null && !createVal.equals(getVal)) {
                            throw new AssertionError(object + "." + field + " should match after create");
                        }
                    }

                    private static String extractJsonString(String json, String field) {
                        if (json == null) {
                            return null;
                        }
                        String needle = "\\"" + field + "\\":\\"";
                        int i = json.indexOf(needle);
                        if (i < 0) {
                            needle = "\\"" + field + "\\":";
                            i = json.indexOf(needle);
                            if (i < 0) {
                                return null;
                            }
                            int start = i + needle.length();
                            int end = json.indexOf(',', start);
                            if (end < 0) {
                                end = json.indexOf('}', start);
                            }
                            return end > start ? json.substring(start, end).trim().replaceAll("\\"", "") : null;
                        }
                        int start = i + needle.length();
                        int end = json.indexOf('"', start);
                        return end > start ? json.substring(start, end) : null;
                    }

                    private static String extractNestedJsonString(String json, String object, String field) {
                        if (json == null) {
                            return null;
                        }
                        String objNeedle = "\\"" + object + "\\":{";
                        int objIdx = json.indexOf(objNeedle);
                        if (objIdx < 0) {
                            return null;
                        }
                        int brace = json.indexOf('{', objIdx);
                        int depth = 0;
                        for (int i = brace; i < json.length(); i++) {
                            char c = json.charAt(i);
                            if (c == '{') depth++;
                            else if (c == '}') {
                                depth--;
                                if (depth == 0) {
                                    return extractJsonString(json.substring(brace, i + 1), field);
                                }
                            }
                        }
                        return null;
                    }

                    public static void cleanupCreatedResources(HttpClient client, Duration timeout) throws Exception {
                        for (String id : TestContext.createdIds()) {
                            deleteResource(client, id, timeout);
                        }
                        TestContext.clearCreatedIds();
                    }

                    private static void deleteResource(HttpClient client, String id, Duration timeout) throws Exception {
                        String hierarchyRoot = TestEnv.hierarchyRootFolderId();
                        if (hierarchyRoot != null && hierarchyRoot.equals(id)) {
                            return;
                        }
                        String deletePath = TestEnv.get("test.delete.path", "/folders/{folderID}")
                                .replace("{folderID}", id).replace("{id}", id);
                        HttpRequest.Builder b = HttpRequest.newBuilder()
                                .uri(URI.create(TestEnv.baseUrl() + deletePath))
                                .timeout(timeout)
                                .header("Accept", "application/json")
                                .header("Accept-Language", TestEnv.acceptLanguage())
                                .DELETE();
                        String token = TestAuth.rawToken();
                        if (!token.isEmpty()) {
                            b.header("Authorization", "Bearer " + token);
                        }
                        HttpResponse<String> resp = client.send(b.build(), HttpResponse.BodyHandlers.ofString());
                        if (resp.statusCode() == 202) {
                            pollAsyncDelete(client, resp, timeout);
                        }
                    }

                    private static void pollAsyncDelete(HttpClient client, HttpResponse<String> accepted,
                                                        Duration timeout) throws Exception {
                        String taskUrl = accepted.headers().firstValue("Location").orElse(null);
                        if (taskUrl == null) {
                            return;
                        }
                        long deadline = System.nanoTime() + timeout.toNanos();
                        while (System.nanoTime() < deadline) {
                            HttpRequest poll = HttpRequest.newBuilder()
                                    .uri(URI.create(taskUrl.startsWith("http") ? taskUrl : TestEnv.baseUrl() + taskUrl))
                                    .timeout(Duration.ofSeconds(10))
                                    .header("Accept", "application/json")
                                    .GET()
                                    .build();
                            HttpResponse<String> r = client.send(poll, HttpResponse.BodyHandlers.ofString());
                            if (r.statusCode() == 200 || r.statusCode() == 204) {
                                return;
                            }
                            Thread.sleep(1000);
                        }
                    }

                    public static void bootstrapBaseData(HttpClient client, Duration timeout) throws Exception {
                        String parent = TestEnv.parentFolderId();
                        String hierarchyRoot = TestEnv.hierarchyRootFolderId();
                        if (parent != null && !parent.isBlank()) {
                            verifyFolderExists(client, parent, timeout, "test.parent.folder.id");
                            TestContext.setBootstrapParentFolderId(parent);
                        }
                        if (hierarchyRoot != null && !hierarchyRoot.isBlank()) {
                            verifyFolderExists(client, hierarchyRoot, timeout, "test.hierarchy.root.folder.id");
                            TestContext.setBootstrapHierarchyRootId(hierarchyRoot);
                        }
                        if (!TestEnv.destructiveEnabled() && parent != null && !parent.isBlank()) {
                            String disposable = createDisposableSubfolder(client, parent, timeout);
                            if (disposable != null) {
                                TestContext.setDisposableFolderId(disposable);
                            }
                        }
                    }

                    private static void verifyFolderExists(HttpClient client, String folderId, Duration timeout,
                                                           String propertyName) throws Exception {
                        String path = "/folders/" + folderId;
                        HttpRequest.Builder b = HttpRequest.newBuilder()
                                .uri(URI.create(TestEnv.baseUrl() + path))
                                .timeout(timeout)
                                .header("Accept", "application/json")
                                .header("Accept-Language", TestEnv.acceptLanguage())
                                .GET();
                        String token = TestAuth.rawToken();
                        if (!token.isEmpty()) {
                            b.header("Authorization", "Bearer " + token);
                        }
                        HttpResponse<String> resp = client.send(b.build(), HttpResponse.BodyHandlers.ofString());
                        if (resp.statusCode() == 404) {
                            throw new AssertionError(propertyName + "=" + folderId + " not found — update test-env.properties");
                        }
                    }

                    private static String createDisposableSubfolder(HttpClient client, String parentId, Duration timeout)
                            throws Exception {
                        String body = RequestBodyEnv.bind("{\\"name\\":\\"sdk-bootstrap-disposable\\",\\"parent\\":{\\"id\\":\\""
                                + parentId + "\\"}}");
                        HttpRequest.Builder b = HttpRequest.newBuilder()
                                .uri(URI.create(TestEnv.baseUrl() + "/folders"))
                                .timeout(timeout)
                                .header("Content-Type", "application/json")
                                .header("Accept", "application/json")
                                .header("Accept-Language", TestEnv.acceptLanguage())
                                .POST(HttpRequest.BodyPublishers.ofString(body));
                        String token = TestAuth.rawToken();
                        if (!token.isEmpty()) {
                            b.header("Authorization", "Bearer " + token);
                        }
                        HttpResponse<String> resp = client.send(b.build(), HttpResponse.BodyHandlers.ofString());
                        if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                            String id = extractCreatedId(resp);
                            if (id != null) {
                                TestContext.trackCreatedId(id);
                            }
                            return id;
                        }
                        return null;
                    }

                    public static void cleanupTestData(String resourceId) {
                        if (resourceId != null) {
                            TestContext.trackCreatedId(resourceId);
                        }
                    }
                }
                """.formatted(basePackage, basePackage);
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
    private void generateAuthSetupFromSecuritySchemes(StringBuilder sb, Map<String, Object> spec) {
        Map<String, Object> components = Util.asStringObjectMap(spec.get("components"));
        Map<String, Object> securitySchemes = components != null ? Util.asStringObjectMap(components.get("securitySchemes")) : null;
        if (securitySchemes == null || securitySchemes.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> entry : securitySchemes.entrySet()) {
            Map<String, Object> scheme = Util.asStringObjectMap(entry.getValue());
            if (scheme == null || !"apiKey".equals(scheme.get("type"))) {
                continue;
            }
            String inField = (String) scheme.get("in");
            String paramName = (String) scheme.get("name");
            sb.append("    private String getApiKeyValue() {\n");
            sb.append("        return TestEnv.get(\"auth.apiKey\", System.getenv(\"API_KEY\"));\n");
            sb.append("    }\n\n");
            if ("header".equals(inField)) {
                sb.append("    private String getApiKeyHeaderName() {\n");
                sb.append("        return \"").append(paramName != null ? paramName : "X-API-Key").append("\";\n");
                sb.append("    }\n\n");
            }
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
