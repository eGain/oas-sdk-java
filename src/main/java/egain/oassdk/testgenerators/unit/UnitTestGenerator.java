package egain.oassdk.testgenerators.unit;

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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Unit test generator — emits JUnit 5 + RestAssured API tests from OpenAPI.
 */
public class UnitTestGenerator implements TestGenerator, ConfigurableTestGenerator {

    /**
     * Caps {@code "a".repeat(n)} inlined into generated sources when OpenAPI minLength/maxLength are huge
     * (avoids OOM during generation and unusable multi-gigabyte test files).
     */
    private static final int MAX_BOUNDARY_STRING_LITERAL_LENGTH = 8192;

    private TestConfig config;

    @Override
    public void generate(Map<String, Object> spec, String outputDir, TestConfig config, String testFramework) throws GenerationException {
        this.config = config;

        try {
            Path outputPath = Paths.get(outputDir, "unit");
            Files.createDirectories(outputPath);

            String basePackage = "com.example.api";
            if (config != null && config.getAdditionalProperties() != null) {
                Object packageNameObj = config.getAdditionalProperties().get("packageName");
                if (packageNameObj != null) {
                    basePackage = packageNameObj.toString();
                }
            }

            generateTestClasses(spec, outputPath.toString(), basePackage);
            generateTestUtilities(outputPath.toString(), basePackage);
            generatePomXml(outputPath.toString(), basePackage);

        } catch (Exception e) {
            throw new GenerationException("Failed to generate unit tests: " + e.getMessage(), e);
        }
    }

    private void generateTestClasses(Map<String, Object> spec, String outputDir, String basePackage) throws IOException {
        Map<String, Object> paths = Util.asStringObjectMap(spec.get("paths"));
        if (paths == null || paths.isEmpty()) {
            return;
        }

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

                    String tag = getOperationTag(operation);
                    operationsByTag.computeIfAbsent(tag, k -> new ArrayList<>()).add(opInfo);
                }
            }
        }

        for (Map.Entry<String, List<OperationInfo>> tagEntry : operationsByTag.entrySet()) {
            String tag = tagEntry.getKey();
            List<OperationInfo> operations = tagEntry.getValue();

            String className = toClassName(tag) + "ApiTest";
            String packageDir = TestOutputLayout.testJavaDir(outputDir, basePackage);
            Files.createDirectories(Paths.get(packageDir));

            String testClassContent = generateTestClass(basePackage, className, tag, operations, spec);
            Files.write(Paths.get(packageDir, className + ".java"), testClassContent.getBytes());
        }
    }

    private String generateTestClass(String basePackage, String className, String tag, List<OperationInfo> operations, Map<String, Object> spec) {
        String baseUrl = TestSpecUtils.getBaseUrl(spec);
        int concurrentThreads = resolveConcurrentThreads();
        boolean needsConcurrent = operations.stream().anyMatch(this::hasRequestBodyForConcurrency);

        StringBuilder sb = new StringBuilder();

        sb.append("package ").append(basePackage).append(";\n\n");

        sb.append("import org.junit.jupiter.api.*;\n");
        sb.append("import org.junit.jupiter.api.Assumptions;\n");
        sb.append("import org.junit.jupiter.api.DisplayName;\n");
        sb.append("import org.junit.jupiter.params.ParameterizedTest;\n");
        sb.append("import org.junit.jupiter.params.provider.ValueSource;\n");
        sb.append("import static org.junit.jupiter.api.Assertions.*;\n");
        sb.append("import static io.restassured.RestAssured.given;\n");
        sb.append("import static org.hamcrest.Matchers.*;\n\n");
        sb.append("import io.restassured.http.ContentType;\n");
        sb.append("import io.restassured.response.Response;\n");
        sb.append("import io.restassured.specification.RequestSpecification;\n");
        if (needsConcurrent) {
            sb.append("import java.util.concurrent.*;\n");
        }
        sb.append("import java.util.*;\n");
        sb.append(TestCodegenSupport.supportImport(basePackage));

        sb.append("/**\n");
        sb.append(" * API contract tests for ").append(escapeJavadoc(tag)).append(" (generated from OpenAPI).\n");
        sb.append(" * <p>Live HTTP tests — configure via test-env.properties / TestEnv.\n");
        sb.append(" */\n");
        sb.append("@DisplayName(\"").append(escapeJavaString(tag)).append(" API Contract Tests\")\n");
        sb.append("@TestInstance(TestInstance.Lifecycle.PER_CLASS)\n");
        sb.append("public class ").append(className).append(" {\n\n");

        sb.append(TestCodegenSupport.restAssuredInit()).append("\n");

        sb.append(TestCodegenSupport.invalidTestConstants()).append("\n");

        if (needsConcurrent) {
            sb.append("    private static final int CONCURRENT_TEST_THREADS = ").append(concurrentThreads).append(";\n\n");
        }

        sb.append("    private static String authToken() {\n");
        sb.append("        return TestAuth.rawToken();\n");
        sb.append("    }\n\n");

        sb.append("    @AfterEach\n");
        sb.append("    void tearDown() throws Exception {\n");
        sb.append("        UnitTestUtils.cleanupCreatedResources();\n");
        sb.append("    }\n\n");

        for (OperationInfo opInfo : operations) {
            generateTestMethods(sb, opInfo, spec);
        }

        sb.append("}\n");
        return sb.toString();
    }

    private int resolveConcurrentThreads() {
        int n = 5;
        if (config != null && config.getAdditionalProperties() != null) {
            Object v = config.getAdditionalProperties().get("concurrentTestThreads");
            if (v instanceof Number) {
                n = ((Number) v).intValue();
            } else if (v != null) {
                try {
                    n = Integer.parseInt(v.toString());
                } catch (NumberFormatException ignored) {
                    // keep default
                }
            }
        }
        return Math.max(1, Math.min(n, 64));
    }

    private boolean hasRequestBodyForConcurrency(OperationInfo op) {
        String m = op.method.toUpperCase();
        if (!("POST".equals(m) || "PUT".equals(m) || "PATCH".equals(m))) {
            return false;
        }
        return op.operation.containsKey("requestBody");
    }

    private void generateTestMethods(StringBuilder sb, OperationInfo opInfo, Map<String, Object> spec) {
        Map<String, Object> operation = opInfo.operation;
        String operationId = (String) operation.get("operationId");
        String summary = (String) operation.get("summary");
        String method = opInfo.method.toUpperCase();
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

        String successMatcher = hamcrestSuccessStatusMatcher(responses);
        boolean smoke = TestProfileSupport.isSmoke(config);
        boolean destructiveOp = "DELETE".equals(method);

        sb.append("    @Test\n");
        sb.append("    @DisplayName(\"").append(escapeJavaString(summary != null ? summary : method + " " + path))
                .append(" - Valid Request\")\n");
        sb.append("    public void test").append(capitalize(testMethodName)).append("_ValidRequest() {\n");
        if (destructiveOp) {
            sb.append(TestCodegenSupport.destructiveGate());
        }
        appendParamMaps(sb, parameters);
        String bodyLiteral = jsonBodyLiteral(operation, spec);
        if ("POST".equals(method) && responses.containsKey("201")) {
            appendRestAssuredWhenThenExtract(sb, method, path, bodyLiteral);
            sb.append("        assertNotNull(response);\n");
            sb.append("        assertTrue(response.getStatusCode() >= 200 && response.getStatusCode() < 300);\n");
            String getPath = findGetByIdPath(spec, path);
            if (getPath != null) {
                sb.append("        UnitTestUtils.assertGetMatchesCreate(response, \"")
                        .append(escapeJavaString(getPath)).append("\", ")
                        .append(TestCodegenSupport.requestBodyBind(
                                IntegrationScenarioSupport.escapeJavaString(
                                        IntegrationScenarioSupport.generateRequestBodyFromSchemaRaw(operation, spec))))
                        .append(");\n");
            }
        } else {
            appendRestAssuredWhenThen(sb, method, path, bodyLiteral, "        .then()\n            .statusCode(" + successMatcher + ");\n");
        }
        sb.append("    }\n\n");

        if (!smoke) {
        for (Map<String, Object> param : parameters) {
            String paramName = (String) param.get("name");
            Boolean required = param.containsKey("required") ? (Boolean) param.get("required") : Boolean.FALSE;
            String paramIn = (String) param.get("in");

            if (Boolean.TRUE.equals(required) && "query".equals(paramIn)) {
                sb.append("    @Test\n");
                sb.append("    @DisplayName(\"").append(escapeJavaString(summary != null ? summary : method + " " + path))
                        .append(" - Missing Required Parameter: ").append(escapeJavaString(paramName)).append("\")\n");
                sb.append("    public void test").append(capitalize(testMethodName)).append("_MissingRequiredParam_")
                        .append(capitalize(toMethodName(paramName))).append("() {\n");
                appendParamMapsForMissingQuery(sb, parameters, paramName);
                appendRestAssuredWhenThen(sb, method, path, bodyLiteral,
                        "        .then()\n            .statusCode(anyOf(equalTo(400), equalTo(422)));\n");
                sb.append("    }\n\n");
            }

            Map<String, Object> schema = param.containsKey("schema")
                    ? Util.asStringObjectMap(param.get("schema"))
                    : new HashMap<>();

            if ("query".equals(paramIn)) {
                List<String> invalidLiterals = buildInvalidQueryValueLiterals(schema);
                if (invalidLiterals != null && !invalidLiterals.isEmpty()) {
                    emitInvalidParameterTest(sb, testMethodName, summary, method, path, parameters, paramName, bodyLiteral,
                            invalidLiterals, shouldEmitPatternAssertion(schema), (String) schema.get("pattern"));
                }
            } else if ("path".equals(paramIn) && "string".equals(schema.get("type")) && schema.containsKey("pattern")) {
                String pattern = (String) schema.get("pattern");
                if (pattern != null && !pattern.isBlank()) {
                    List<String> pathPatternLiterals = List.of("invalid", "test123", "");
                    emitInvalidParameterTest(sb, testMethodName, summary, method, path, parameters, paramName, bodyLiteral,
                            pathPatternLiterals, shouldEmitPatternAssertion(schema), pattern);
                }
            } else if (Boolean.TRUE.equals(required) && "header".equals(paramIn)
                    && "Accept".equalsIgnoreCase(paramName)) {
                // CBD-8608: empty Accept must be rejected with 400
                emitEmptyAcceptHeaderTest(sb, testMethodName, summary, method, path, parameters, bodyLiteral);
            }
        }
        } // end !smoke param negatives

        if (!smoke) {
        for (String statusCode : responses.keySet()) {
            if ("default".equals(statusCode)) {
                continue;
            }
            sb.append("    @Test\n");
            sb.append("    @DisplayName(\"").append(escapeJavaString(summary != null ? summary : method + " " + path))
                    .append(" - Response Status ").append(statusCode).append("\")\n");
            sb.append("    public void test").append(capitalize(testMethodName)).append("_Status").append(statusCode).append("() {\n");
            appendParamMaps(sb, parameters);
            appendRestAssuredWhenThenExtract(sb, method, path, bodyLiteral);

            sb.append("        assertNotNull(response, \"Response should not be null\");\n");
            sb.append("        assertEquals(").append(statusCode).append(", response.getStatusCode(), \"Response status should be ")
                    .append(statusCode).append("\");\n");

            Map<String, Object> responseObj = Util.asStringObjectMap(responses.get(statusCode));
            Map<String, Object> content = responseObj != null ? Util.asStringObjectMap(responseObj.get("content")) : null;

            if ("200".equals(statusCode) || "201".equals(statusCode)) {
                sb.append("        assertNotNull(response.getBody(), \"Response body should not be null for ")
                        .append(statusCode).append(" response\");\n");
                sb.append("        assertFalse(response.getBody().asString().isEmpty(), \"Response body should not be empty for ")
                        .append(statusCode).append(" response\");\n");
            }

            if (content != null) {
                for (Map.Entry<String, Object> contentEntry : content.entrySet()) {
                    String contentType = contentEntry.getKey();
                    sb.append("        String ct = response.getContentType();\n");
                    sb.append("        assertNotNull(ct, \"Response should have Content-Type\");\n");
                    sb.append("        assertTrue(ct.contains(\"").append(escapeJavaString(contentType))
                            .append("\"), \"Content-Type should contain ").append(escapeJavaString(contentType)).append("\");\n");

                    Map<String, Object> mediaType = Util.asStringObjectMap(contentEntry.getValue());
                    Map<String, Object> responseSchema = mediaType != null ? Util.asStringObjectMap(mediaType.get("schema")) : null;
                    if (responseSchema != null) {
                        List<String> requiredFields = Util.asStringList(responseSchema.get("required"));
                        if (requiredFields != null && !requiredFields.isEmpty()) {
                            sb.append("        String responseBody = response.getBody().asString();\n");
                            for (String field : requiredFields) {
                                sb.append("        assertTrue(responseBody.contains(\"\\\"")
                                        .append(escapeJavaString(field)).append("\\\"\"),\n");
                                sb.append("            \"Response JSON should contain required field ")
                                        .append(escapeJavaString(field)).append("\");\n");
                            }
                        }
                    }
                }
            }
            sb.append("    }\n\n");
        }
        } // end !smoke status matrix

        if (!smoke && ("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method))) {
            Map<String, Object> requestBody = operation.containsKey("requestBody")
                    ? Util.asStringObjectMap(operation.get("requestBody"))
                    : null;

            sb.append("    @Test\n");
            sb.append("    @DisplayName(\"").append(escapeJavaString(summary != null ? summary : method + " " + path))
                    .append(" - Empty Request Body\")\n");
            sb.append("    public void test").append(capitalize(testMethodName)).append("_EmptyRequestBody() {\n");
            appendParamMaps(sb, parameters);
            appendRestAssuredWhenThen(sb, method, path, "\"\"",
                    "        .then()\n            .statusCode(equalTo(400));\n");
            sb.append("    }\n\n");

            sb.append("    @Test\n");
            sb.append("    @DisplayName(\"").append(escapeJavaString(summary != null ? summary : method + " " + path))
                    .append(" - Malformed JSON Request Body\")\n");
            sb.append("    public void test").append(capitalize(testMethodName)).append("_MalformedJsonBody() {\n");
            appendParamMaps(sb, parameters);
            appendRestAssuredWhenThen(sb, method, path, "\"not json\"",
                    "        .then()\n            .statusCode(equalTo(400));\n");
            sb.append("    }\n\n");

            if (requestBody != null) {
                Map<String, Object> rbContent = Util.asStringObjectMap(requestBody.get("content"));
                if (rbContent != null) {
                    Map<String, Object> jsonContent = Util.asStringObjectMap(rbContent.get("application/json"));
                    Map<String, Object> rbSchema = jsonContent != null ? Util.asStringObjectMap(jsonContent.get("schema")) : null;
                    Map<String, Object> properties = rbSchema != null ? Util.asStringObjectMap(rbSchema.get("properties")) : null;

                    if (properties != null) {
                        for (Map.Entry<String, Object> propEntry : properties.entrySet()) {
                            String propName = propEntry.getKey();
                            Map<String, Object> propSchema = Util.asStringObjectMap(propEntry.getValue());
                            if (propSchema == null) continue;

                            String propType = (String) propSchema.get("type");

                            if ("string".equals(propType)) {
                                if (propSchema.containsKey("minLength")) {
                                    int minLen = ((Number) propSchema.get("minLength")).intValue();
                                    if (minLen > 0 && minLen - 1 <= MAX_BOUNDARY_STRING_LITERAL_LENGTH) {
                                        String tooShort = "a".repeat(Math.max(0, minLen - 1));
                                        emitBoundaryBodyTest(sb, testMethodName, summary, method, path, parameters,
                                                propName, "{\"\\\"" + propName + "\\\": \\\"" + tooShort + "\\\"}\"");
                                    }
                                }
                                if (propSchema.containsKey("maxLength")) {
                                    int maxLen = ((Number) propSchema.get("maxLength")).intValue();
                                    if (maxLen >= 0 && maxLen + 1 <= MAX_BOUNDARY_STRING_LITERAL_LENGTH) {
                                        String tooLong = "a".repeat(maxLen + 1);
                                        emitBoundaryBodyTest(sb, testMethodName, summary, method, path, parameters,
                                                propName, "{\"\\\"" + propName + "\\\": \\\"" + tooLong + "\\\"}\"");
                                    }
                                }
                            }

                            if ("integer".equals(propType) || "number".equals(propType)) {
                                if (propSchema.containsKey("minimum")) {
                                    Number min = (Number) propSchema.get("minimum");
                                    long belowMin = min.longValue() - 1;
                                    emitBoundaryBodyTest(sb, testMethodName, summary, method, path, parameters,
                                            propName, "{\"\\\"" + propName + "\\\": " + belowMin + "}");
                                }
                                if (propSchema.containsKey("maximum")) {
                                    Number max = (Number) propSchema.get("maximum");
                                    long aboveMax = max.longValue() + 1;
                                    emitBoundaryBodyTest(sb, testMethodName, summary, method, path, parameters,
                                            propName, "{\"\\\"" + propName + "\\\": " + aboveMax + "}");
                                }
                            }
                        }
                    }
                }

                String concurrentBody = jsonBodyLiteral(operation, spec);
                String helperName = "call" + capitalize(testMethodName) + "Request";
                appendConcurrentHelper(sb, helperName, method, path, parameters, concurrentBody);
                appendConcurrentTest(sb, testMethodName, summary, method, path, helperName);
            }
        }
    }

    private void emitBoundaryBodyTest(StringBuilder sb, String testMethodName, String summary, String method, String path,
                                      List<Map<String, Object>> parameters, String propName, String bodyJavaExpr) {
        sb.append("    @Test\n");
        sb.append("    @DisplayName(\"").append(escapeJavaString(summary != null ? summary : method + " " + path))
                .append(" - Boundary: ").append(escapeJavaString(propName)).append("\")\n");
        sb.append("    public void test").append(capitalize(testMethodName)).append("_Boundary_")
                .append(capitalize(toMethodName(propName))).append("_Constraint() {\n");
        appendParamMaps(sb, parameters);
        appendRestAssuredWhenThen(sb, method, path, bodyJavaExpr,
                "        .then()\n            .statusCode(equalTo(400));\n");
        sb.append("    }\n\n");
    }

    private void appendConcurrentHelper(StringBuilder sb, String helperName, String method, String pathTemplate,
                                        List<Map<String, Object>> parameters, String bodyExpr) {
        sb.append("    private Response ").append(helperName).append("() {\n");
        appendParamMaps(sb, parameters);
        sb.append("        RequestSpecification spec = given()\n");
        sb.append("            .accept(ContentType.JSON)\n");
        sb.append("            .header(\"Authorization\", authToken());\n");
        sb.append("        for (Map.Entry<String, String> e : pathParams.entrySet()) {\n");
        sb.append("            spec = spec.pathParam(e.getKey(), e.getValue());\n");
        sb.append("        }\n");
        sb.append("        for (Map.Entry<String, String> e : queryParams.entrySet()) {\n");
        sb.append("            spec = spec.queryParam(e.getKey(), e.getValue());\n");
        sb.append("        }\n");
        sb.append("        spec = spec.contentType(ContentType.JSON).body(").append(bodyExpr).append(");\n");
        sb.append("        return spec.when()\n");
        appendWhenVerb(sb, method, pathTemplate);
        sb.append("        .then()\n");
        sb.append("            .statusCode(anyOf(equalTo(200), equalTo(201), equalTo(204)))\n");
        sb.append("        .extract().response();\n");
        sb.append("    }\n\n");
    }

    private void appendConcurrentTest(StringBuilder sb, String testMethodName, String summary, String method, String path,
                                      String helperName) {
        sb.append("    @Test\n");
        sb.append("    @DisplayName(\"").append(escapeJavaString(summary != null ? summary : method + " " + path))
                .append(" - Concurrent requests\")\n");
        sb.append("    public void testConcurrent_").append(capitalize(testMethodName)).append("() throws InterruptedException {\n");
        sb.append("        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_TEST_THREADS);\n");
        sb.append("        List<Future<Response>> futures = new ArrayList<>();\n");
        sb.append("        CountDownLatch ready = new CountDownLatch(CONCURRENT_TEST_THREADS);\n");
        sb.append("        CountDownLatch start = new CountDownLatch(1);\n");
        sb.append("        for (int i = 0; i < CONCURRENT_TEST_THREADS; i++) {\n");
        sb.append("            futures.add(executor.submit(() -> {\n");
        sb.append("                ready.countDown();\n");
        sb.append("                try {\n");
        sb.append("                    start.await();\n");
        sb.append("                    return ").append(helperName).append("();\n");
        sb.append("                } catch (InterruptedException e) {\n");
        sb.append("                    Thread.currentThread().interrupt();\n");
        sb.append("                    throw new RuntimeException(e);\n");
        sb.append("                }\n");
        sb.append("            }));\n");
        sb.append("        }\n");
        sb.append("        ready.await();\n");
        sb.append("        start.countDown();\n");
        sb.append("        int ok = 0;\n");
        sb.append("        for (Future<Response> f : futures) {\n");
        sb.append("            try {\n");
        sb.append("                int code = f.get().getStatusCode();\n");
        sb.append("                if (code >= 200 && code < 300) ok++;\n");
        sb.append("            } catch (Exception e) {\n");
        sb.append("                // count as non-success\n");
        sb.append("            }\n");
        sb.append("        }\n");
        sb.append("        executor.shutdown();\n");
        sb.append("        assertTrue(ok > 0, \"Expected at least one successful response under concurrency\");\n");
        sb.append("    }\n\n");
    }

    /**
     * Emits a negative test that sends an empty Accept header (CBD-8608).
     */
    private void emitEmptyAcceptHeaderTest(StringBuilder sb, String testMethodName, String summary,
                                           String method, String path, List<Map<String, Object>> parameters,
                                           String bodyLiteral) {
        sb.append("    @Test\n");
        sb.append("    @DisplayName(\"").append(escapeJavaString(summary != null ? summary : method + " " + path))
                .append(" - Empty Accept header\")\n");
        sb.append("    public void test").append(capitalize(testMethodName)).append("_EmptyAcceptHeader() {\n");
        appendParamMaps(sb, parameters);
        boolean withBody = needsRequestBody(method) && bodyLiteral != null;
        sb.append("        RequestSpecification spec = given()\n");
        sb.append("            .header(\"Accept\", \"\")\n");
        sb.append("            .header(\"Authorization\", authToken());\n");
        sb.append("        for (Map.Entry<String, String> e : pathParams.entrySet()) {\n");
        sb.append("            spec = spec.pathParam(e.getKey(), e.getValue());\n");
        sb.append("        }\n");
        sb.append("        for (Map.Entry<String, String> e : queryParams.entrySet()) {\n");
        sb.append("            spec = spec.queryParam(e.getKey(), e.getValue());\n");
        sb.append("        }\n");
        if (withBody) {
            sb.append("        spec = spec.contentType(ContentType.JSON).body(").append(bodyLiteral).append(");\n");
        }
        sb.append("        spec.when()\n");
        appendWhenVerb(sb, method, path);
        sb.append("        .then()\n            .statusCode(anyOf(equalTo(400), equalTo(422)));\n");
        sb.append("    }\n\n");
    }

    private void appendRestAssuredWhenThen(StringBuilder sb, String method, String pathTemplate, String bodyExpr, String thenClause) {
        boolean withBody = needsRequestBody(method) && bodyExpr != null;
        sb.append("        RequestSpecification spec = given()\n");
        sb.append("            .accept(ContentType.JSON)\n");
        sb.append("            .header(\"Authorization\", authToken());\n");
        sb.append("        for (Map.Entry<String, String> e : pathParams.entrySet()) {\n");
        sb.append("            spec = spec.pathParam(e.getKey(), e.getValue());\n");
        sb.append("        }\n");
        sb.append("        for (Map.Entry<String, String> e : queryParams.entrySet()) {\n");
        sb.append("            spec = spec.queryParam(e.getKey(), e.getValue());\n");
        sb.append("        }\n");
        if (withBody) {
            sb.append("        spec = spec.contentType(ContentType.JSON).body(").append(bodyExpr).append(");\n");
        }
        sb.append("        spec.when()\n");
        appendWhenVerb(sb, method, pathTemplate);
        sb.append(thenClause);
    }

    private void appendRestAssuredWhenThenExtract(StringBuilder sb, String method, String pathTemplate, String bodyExpr) {
        boolean withBody = needsRequestBody(method) && bodyExpr != null;
        sb.append("        RequestSpecification spec = given()\n");
        sb.append("            .accept(ContentType.JSON)\n");
        sb.append("            .header(\"Authorization\", authToken());\n");
        sb.append("        for (Map.Entry<String, String> e : pathParams.entrySet()) {\n");
        sb.append("            spec = spec.pathParam(e.getKey(), e.getValue());\n");
        sb.append("        }\n");
        sb.append("        for (Map.Entry<String, String> e : queryParams.entrySet()) {\n");
        sb.append("            spec = spec.queryParam(e.getKey(), e.getValue());\n");
        sb.append("        }\n");
        if (withBody) {
            sb.append("        spec = spec.contentType(ContentType.JSON).body(").append(bodyExpr).append(");\n");
        }
        sb.append("        Response response = spec.when()\n");
        appendWhenVerb(sb, method, pathTemplate);
        sb.append("        .extract().response();\n\n");
    }

    private void appendWhenVerb(StringBuilder sb, String method, String pathTemplate) {
        String p = escapeJavaString(pathTemplate);
        switch (method) {
            case "GET":
                sb.append("            .get(\"").append(p).append("\")\n");
                break;
            case "POST":
                sb.append("            .post(\"").append(p).append("\")\n");
                break;
            case "PUT":
                sb.append("            .put(\"").append(p).append("\")\n");
                break;
            case "PATCH":
                sb.append("            .patch(\"").append(p).append("\")\n");
                break;
            case "DELETE":
                sb.append("            .delete(\"").append(p).append("\")\n");
                break;
            case "HEAD":
                sb.append("            .head(\"").append(p).append("\")\n");
                break;
            case "OPTIONS":
                sb.append("            .options(\"").append(p).append("\")\n");
                break;
            case "TRACE":
                sb.append("            .request(io.restassured.http.Method.TRACE, \"").append(p).append("\")\n");
                break;
            default:
                sb.append("            .get(\"").append(p).append("\")\n");
        }
    }

    private boolean needsRequestBody(String method) {
        return "POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method);
    }

    private void appendParamMaps(StringBuilder sb, List<Map<String, Object>> parameters) {
        sb.append("        Map<String, String> pathParams = new HashMap<>();\n");
        for (Map<String, Object> param : parameters) {
            if (!"path".equals(param.get("in"))) {
                continue;
            }
            String name = (String) param.get("name");
            String example = IntegrationScenarioSupport.getParameterExample(param);
            sb.append("        pathParams.put(\"").append(escapeJavaString(name)).append("\", ")
                    .append(TestCodegenSupport.paramValueExpression(name, example)).append(");\n");
        }
        sb.append("        Map<String, String> queryParams = new HashMap<>();\n");
        Map<String, String> successQuery = IntegrationScenarioSupport.buildSuccessQueryParams(parameters, Map.of());
        for (Map.Entry<String, String> e : successQuery.entrySet()) {
            sb.append("        queryParams.put(\"").append(escapeJavaString(e.getKey())).append("\", ")
                    .append(TestCodegenSupport.paramValueExpression(e.getKey(), e.getValue())).append(");\n");
        }
    }

    private void appendParamMapsForMissingQuery(StringBuilder sb, List<Map<String, Object>> parameters, String missingName) {
        sb.append("        Map<String, String> pathParams = new HashMap<>();\n");
        for (Map<String, Object> param : parameters) {
            if (!"path".equals(param.get("in"))) {
                continue;
            }
            String name = (String) param.get("name");
            sb.append("        pathParams.put(\"").append(escapeJavaString(name)).append("\", ")
                    .append(TestCodegenSupport.paramValueExpression(name, IntegrationScenarioSupport.getParameterExample(param))).append(");\n");
        }
        sb.append("        Map<String, String> queryParams = new HashMap<>();\n");
        for (Map<String, Object> param : parameters) {
            if (!"query".equals(param.get("in"))) {
                continue;
            }
            String name = (String) param.get("name");
            if (name.equals(missingName)) {
                continue;
            }
            sb.append("        queryParams.put(\"").append(escapeJavaString(name)).append("\", ")
                    .append(TestCodegenSupport.paramValueExpression(name,
                            IntegrationScenarioSupport.getParameterExample(param))).append(");\n");
        }
    }

    private String findGetByIdPath(Map<String, Object> spec, String collectionPath) {
        Map<String, Object> paths = Util.asStringObjectMap(spec.get("paths"));
        if (paths == null) {
            return null;
        }
        for (String p : paths.keySet()) {
            if (p.matches(".*\\{[^}]+}.*") && p.startsWith(collectionPath.replaceAll("/$", ""))) {
                Map<String, Object> pathItem = Util.asStringObjectMap(paths.get(p));
                if (pathItem != null && pathItem.containsKey("get")) {
                    return p;
                }
            }
        }
        String candidate = collectionPath.endsWith("/") ? collectionPath + "{id}" : collectionPath + "/{id}";
        return paths.containsKey(candidate) ? candidate : null;
    }

    private void appendParamMapsWithInvalidParameter(StringBuilder sb, List<Map<String, Object>> parameters,
                                                     String invalidParamName, String invalidVar) {
        sb.append("        Map<String, String> pathParams = new HashMap<>();\n");
        for (Map<String, Object> param : parameters) {
            if (!"path".equals(param.get("in"))) {
                continue;
            }
            String name = (String) param.get("name");
            if (name.equals(invalidParamName)) {
                sb.append("        pathParams.put(\"").append(escapeJavaString(name)).append("\", ").append(invalidVar).append(");\n");
            } else {
                sb.append("        pathParams.put(\"").append(escapeJavaString(name)).append("\", ")
                        .append(TestCodegenSupport.paramValueExpression(name,
                                IntegrationScenarioSupport.getParameterExample(param))).append(");\n");
            }
        }
        sb.append("        Map<String, String> queryParams = new HashMap<>();\n");
        for (Map<String, Object> param : parameters) {
            if (!"query".equals(param.get("in"))) {
                continue;
            }
            String name = (String) param.get("name");
            if (name.equals(invalidParamName)) {
                sb.append("        queryParams.put(\"").append(escapeJavaString(name)).append("\", ").append(invalidVar).append(");\n");
            } else {
                sb.append("        queryParams.put(\"").append(escapeJavaString(name)).append("\", ")
                        .append(TestCodegenSupport.paramValueExpression(name,
                                IntegrationScenarioSupport.getParameterExample(param))).append(");\n");
            }
        }
    }

    /**
     * When {@code enum} is present, a bad value may still match a {@code pattern}; skip local regex assertion in that case.
     */
    private static boolean shouldEmitPatternAssertion(Map<String, Object> schema) {
        if (schema == null || !"string".equals(schema.get("type"))) {
            return false;
        }
        if (schema.containsKey("enum")) {
            return false;
        }
        String pattern = (String) schema.get("pattern");
        return pattern != null && !pattern.isBlank();
    }

    /**
     * Builds raw invalid sample strings for a query parameter schema, or {@code null} if the spec does not imply validation.
     */
    private List<String> buildInvalidQueryValueLiterals(Map<String, Object> schema) {
        if (schema == null || schema.isEmpty()) {
            return null;
        }
        String type = (String) schema.get("type");
        if (type == null) {
            return null;
        }
        LinkedHashSet<String> values = new LinkedHashSet<>();
        switch (type) {
            case "string" -> {
                List<String> enumVals = Util.asStringList(schema.get("enum"));
                if (enumVals != null && !enumVals.isEmpty()) {
                    String sentinel = "__INVALID_ENUM_VALUE_SDK__";
                    if (!enumVals.contains(sentinel)) {
                        values.add(sentinel);
                    } else {
                        values.add("__INVALID_ENUM_VALUE_SDK___");
                    }
                }
                String pattern = (String) schema.get("pattern");
                if (pattern != null && !pattern.isBlank()) {
                    values.add("invalid");
                    values.add("test123");
                    values.add("");
                }
                if (schema.containsKey("minLength")) {
                    int minLen = ((Number) schema.get("minLength")).intValue();
                    if (minLen > 0) {
                        values.add("");
                    }
                }
                if (schema.containsKey("maxLength")) {
                    int maxLen = ((Number) schema.get("maxLength")).intValue();
                    if (maxLen >= 0) {
                        int n = Math.min(maxLen + 1, MAX_BOUNDARY_STRING_LITERAL_LENGTH);
                        if (n > 0) {
                            values.add("a".repeat(n));
                        }
                    }
                }
                if (values.isEmpty()) {
                    return null;
                }
            }
            case "integer", "number" -> {
                values.add("not-a-number");
                values.add("12.34");
                addNumericBoundInvalidLiterals(schema, type, values);
            }
            case "boolean" -> {
                values.add("maybe");
                values.add("2");
                values.add("not-bool");
            }
            default -> {
                return null;
            }
        }
        return new ArrayList<>(values);
    }

    private static void addNumericBoundInvalidLiterals(Map<String, Object> schema, String type, LinkedHashSet<String> values) {
        if (schema.containsKey("minimum")) {
            Number min = (Number) schema.get("minimum");
            boolean exclusive = Boolean.TRUE.equals(schema.get("exclusiveMinimum"));
            if ("integer".equals(type)) {
                long m = min.longValue();
                if (exclusive) {
                    values.add(Long.toString(m));
                } else if (m > Long.MIN_VALUE) {
                    values.add(Long.toString(m - 1));
                }
            } else {
                double m = min.doubleValue();
                if (exclusive) {
                    values.add(Double.toString(m));
                } else {
                    values.add(Double.toString(m - 1.0));
                }
            }
        }
        if (schema.containsKey("maximum")) {
            Number max = (Number) schema.get("maximum");
            boolean exclusive = Boolean.TRUE.equals(schema.get("exclusiveMaximum"));
            if ("integer".equals(type)) {
                long m = max.longValue();
                if (exclusive) {
                    values.add(Long.toString(m));
                } else if (m < Long.MAX_VALUE) {
                    values.add(Long.toString(m + 1));
                }
            } else {
                double m = max.doubleValue();
                if (exclusive) {
                    values.add(Double.toString(m));
                } else {
                    values.add(Double.toString(m + 1.0));
                }
            }
        }
    }

    private void emitInvalidParameterTest(StringBuilder sb, String testMethodName, String summary, String method, String path,
                                          List<Map<String, Object>> parameters, String paramName, String bodyLiteral,
                                          List<String> valueLiterals, boolean emitPatternAssertion, String pattern) {
        if (valueLiterals == null || valueLiterals.isEmpty()) {
            return;
        }
        sb.append("    @ParameterizedTest\n");
        sb.append("    @ValueSource(strings = {");
        for (int i = 0; i < valueLiterals.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append("\"").append(escapeJavaString(valueLiterals.get(i))).append("\"");
        }
        sb.append("})\n");
        sb.append("    @DisplayName(\"").append(escapeJavaString(summary != null ? summary : method + " " + path))
                .append(" - Invalid ").append(escapeJavaString(paramName)).append(" Format\")\n");
        sb.append("    public void test").append(capitalize(testMethodName)).append("_Invalid")
                .append(capitalize(toMethodName(paramName))).append("Format(String invalidValue) {\n");
        appendParamMapsWithInvalidParameter(sb, parameters, paramName, "invalidValue");
        appendRestAssuredWhenThen(sb, method, path, bodyLiteral,
                "        .then()\n            .statusCode(anyOf(equalTo(400), equalTo(422)));\n");
        if (emitPatternAssertion && pattern != null && !pattern.isBlank()) {
            sb.append("        assertFalse(invalidValue.matches(\"").append(escapeJavaString(pattern))
                    .append("\"), \"Sample value should not match schema pattern\");\n");
        }
        sb.append("    }\n\n");
    }

    private String jsonBodyLiteral(Map<String, Object> operation, Map<String, Object> spec) {
        if (!operation.containsKey("requestBody")) {
            return "\"{}\"";
        }
        String raw = IntegrationScenarioSupport.generateRequestBodyFromSchemaRaw(operation, spec);
        if (raw == null || raw.isBlank()) {
            return "\"{}\"";
        }
        return TestCodegenSupport.requestBodyBind(IntegrationScenarioSupport.escapeJavaString(raw));
    }

    private String hamcrestSuccessStatusMatcher(Map<String, Object> responses) {
        List<String> codes = new ArrayList<>();
        for (String key : responses.keySet()) {
            if ("default".equals(key)) {
                continue;
            }
            try {
                int c = Integer.parseInt(key);
                if (c >= 200 && c < 300) {
                    codes.add(key);
                }
            } catch (NumberFormatException ignored) {
                // skip
            }
        }
        if (codes.isEmpty()) {
            return "equalTo(200)";
        }
        if (codes.size() == 1) {
            return "equalTo(" + codes.get(0) + ")";
        }
        StringBuilder sb = new StringBuilder("anyOf(");
        for (int i = 0; i < codes.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append("equalTo(").append(codes.get(i)).append(")");
        }
        sb.append(")");
        return sb.toString();
    }

    private static String escapeJavaString(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String escapeJavadoc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private void generateTestUtilities(String outputDir, String basePackage) throws IOException {
        String packageDir = TestOutputLayout.testJavaDir(outputDir, basePackage);
        Files.createDirectories(Paths.get(packageDir));
        Files.write(Paths.get(packageDir, "TestUtils.java"), generateTestUtilsClass(basePackage).getBytes());
        Files.write(Paths.get(packageDir, "UnitTestUtils.java"), generateUnitTestUtilsClass(basePackage).getBytes());
    }

    private String generateUnitTestUtilsClass(String basePackage) {
        return """
                package %s;

                import io.restassured.response.Response;

                import static io.restassured.RestAssured.given;

                import %s.support.*;

                public final class UnitTestUtils {

                    private UnitTestUtils() {
                    }

                    public static void cleanupCreatedResources() throws Exception {
                        for (String id : TestContext.createdIds()) {
                            deleteResource(id);
                        }
                        TestContext.clearCreatedIds();
                    }

                    private static void deleteResource(String id) {
                        String hierarchyRoot = TestEnv.hierarchyRootFolderId();
                        if (hierarchyRoot != null && hierarchyRoot.equals(id)) {
                            return;
                        }
                        String deletePath = TestEnv.get("test.delete.path", "/folders/{folderID}")
                                .replace("{folderID}", id).replace("{id}", id);
                        var spec = TestClient.givenAuth();
                        Response resp = spec.delete(deletePath);
                        if (resp.getStatusCode() == 202) {
                            pollAsyncDelete(resp);
                        }
                    }

                    private static void pollAsyncDelete(Response accepted) {
                        String taskUrl = accepted.getHeader("Location");
                        if (taskUrl == null) {
                            return;
                        }
                        String resolved = TestEnv.resolveSystemUrl(taskUrl);
                        long deadline = System.nanoTime() + 30_000_000_000L;
                        while (System.nanoTime() < deadline) {
                            Response r;
                            try {
                                String sessionId = EgainAuth.sessionId();
                                if (sessionId != null && !sessionId.isBlank()) {
                                    r = given()
                                            .header("X-egain-session", sessionId)
                                            .header("Accept", "application/json")
                                            .get(resolved);
                                } else {
                                    r = TestClient.givenAuth().get(resolved);
                                }
                            } catch (NoClassDefFoundError e) {
                                r = TestClient.givenAuth().get(resolved);
                            }
                            if (r.getStatusCode() == 200 || r.getStatusCode() == 204) {
                                return;
                            }
                            try {
                                Thread.sleep(500);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                return;
                            }
                        }
                    }

                    public static void assertGetMatchesCreate(Response createResponse, String getPath, String createBody) {
                        String createdId = extractCreatedId(createResponse);
                        if (createdId == null) {
                            return;
                        }
                        TestContext.trackCreatedId(createdId);
                        String path = getPath.replaceAll("\\{[^}]+}", createdId);
                        Response getResp = TestClient.givenAuth().get(path);
                        if (getResp.getStatusCode() < 200 || getResp.getStatusCode() >= 300) {
                            throw new AssertionError("GET after create failed: " + getResp.getStatusCode());
                        }
                        String getBody = getResp.getBody().asString();
                        assertFieldEqual(createBody, getBody, "name");
                        assertFieldEqual(createBody, getBody, "description");
                    }

                    private static void assertFieldEqual(String createJson, String getJson, String field) {
                        String createVal = extractJsonField(createJson, field);
                        if (createVal == null) {
                            return;
                        }
                        String getVal = extractJsonField(getJson, field);
                        if (getVal != null && !createVal.equals(getVal)) {
                            throw new AssertionError("Field '" + field + "' should match after create");
                        }
                    }

                    public static String extractCreatedId(Response response) {
                        if (response == null) {
                            return null;
                        }
                        String location = response.getHeader("Location");
                        if (location != null) {
                            int slash = location.lastIndexOf('/');
                            if (slash >= 0 && slash < location.length() - 1) {
                                return location.substring(slash + 1);
                            }
                        }
                        String body = response.getBody().asString();
                        for (String key : new String[]{"id", "folderID", "folderId"}) {
                            String val = extractJsonField(body, key);
                            if (val != null) {
                                return val;
                            }
                        }
                        return null;
                    }

                    private static String extractJsonField(String json, String field) {
                        if (json == null) {
                            return null;
                        }
                        String needle = "\\"" + field + "\\":\\"";
                        int i = json.indexOf(needle);
                        if (i < 0) {
                            return null;
                        }
                        int start = i + needle.length();
                        int end = json.indexOf('"', start);
                        return end > start ? json.substring(start, end) : null;
                    }
                }
                """.formatted(basePackage, basePackage);
    }

    private void generatePomXml(String outputDir, String basePackage) throws IOException {
        String pomContent = TestMavenSupport.pomHeader("api-unit-tests", basePackage)
                + TestMavenSupport.junitDependency()
                + TestMavenSupport.restAssuredDependencies()
                + TestMavenSupport.buildSectionWithTestSupport();
        Files.write(Paths.get(outputDir, "pom.xml"), pomContent.getBytes());
    }

    private String generateTestUtilsClass(String basePackage) {
        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(basePackage).append(";\n\n");
        sb.append("import java.util.*;\n\n");
        sb.append("/**\n * Utility class for unit tests\n */\n");
        sb.append("public class TestUtils {\n\n");
        sb.append("    @SuppressWarnings(\"unchecked\")\n");
        sb.append("    public static Object generateTestData(Map<String, Object> schema) {\n");
        sb.append("        if (schema == null) return null;\n");
        sb.append("        String type = (String) schema.get(\"type\");\n");
        sb.append("        if (type == null) return \"test\";\n");
        sb.append("        switch (type) {\n");
        sb.append("            case \"string\":\n");
        sb.append("                if (schema.containsKey(\"enum\")) {\n");
        sb.append("                    List<String> enumValues = (List<String>) schema.get(\"enum\");\n");
        sb.append("                    return enumValues.isEmpty() ? \"test\" : enumValues.get(0);\n");
        sb.append("                }\n");
        sb.append("                return \"test\";\n");
        sb.append("            case \"integer\": return 123;\n");
        sb.append("            case \"number\": return 123.45;\n");
        sb.append("            case \"boolean\": return true;\n");
        sb.append("            default: return \"test\";\n");
        sb.append("        }\n");
        sb.append("    }\n\n");
        sb.append("    @SuppressWarnings(\"unchecked\")\n");
        sb.append("    public static boolean validateResponse(Object response, Map<String, Object> schema) {\n");
        sb.append("        if (response == null || schema == null) return schema == null;\n");
        sb.append("        String responseStr = response.toString();\n");
        sb.append("        List<String> required = schema.containsKey(\"required\")\n");
        sb.append("            ? (List<String>) schema.get(\"required\") : Collections.emptyList();\n");
        sb.append("        for (String field : required) {\n");
        sb.append("            if (!responseStr.contains(\"\\\"\" + field + \"\\\"\")) return false;\n");
        sb.append("        }\n");
        sb.append("        return true;\n");
        sb.append("    }\n");
        sb.append("}\n");
        return sb.toString();
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
