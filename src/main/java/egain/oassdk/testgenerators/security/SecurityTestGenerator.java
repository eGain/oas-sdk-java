package egain.oassdk.testgenerators.security;

import egain.oassdk.Util;
import egain.oassdk.config.TestConfig;
import egain.oassdk.core.Constants;
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
 * Security test generator
 * Generates security testing and vulnerability assessment tests
 */
public class SecurityTestGenerator implements TestGenerator, ConfigurableTestGenerator {

    private TestConfig config;

    @Override
    public void generate(Map<String, Object> spec, String outputDir, TestConfig config, String testFramework) throws GenerationException {
        this.config = config;

        try {
            // Create output directory structure
            Path outputPath = Paths.get(outputDir, "security");
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

            // Generate security test classes
            generateSecurityTestClasses(spec, outputPath.toString(), basePackage, apiTitle, baseUrl);

            // Generate security test configuration
            generateSecurityConfiguration(outputPath.toString(), baseUrl);
            generatePomXml(outputPath.toString(), basePackage);

        } catch (Exception e) {
            throw new GenerationException("Failed to generate security tests: " + e.getMessage(), e);
        }
    }

    /**
     * Generate security test classes
     */
    private void generateSecurityTestClasses(Map<String, Object> spec, String outputDir, String basePackage, String apiTitle, String baseUrl) throws IOException {
        Map<String, Object> paths = Util.asStringObjectMap(spec.get("paths"));
        if (paths == null || paths.isEmpty()) {
            return;
        }

        String packageDir = TestOutputLayout.testJavaDir(outputDir, basePackage);
        Files.createDirectories(Paths.get(packageDir));

        // Generate security test class
        String className = "SecurityTest";
        String testClassContent = generateSecurityTestClass(basePackage, className, spec, baseUrl);
        Files.write(Paths.get(packageDir, className + ".java"), testClassContent.getBytes());
    }

    /**
     * Generate security test class
     */
    private String generateSecurityTestClass(String basePackage, String className, Map<String, Object> spec, String baseUrl) {
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
        sb.append("import java.util.Base64;\n");
        sb.append(TestCodegenSupport.supportImport(basePackage));

        // Class declaration
        sb.append("/**\n");
        sb.append(" * Security Tests\n");
        sb.append(" * Generated from OpenAPI specification\n");
        sb.append(" * \n");
        sb.append(" * Tests for:\n");
        sb.append(" * - Authentication and authorization\n");
        sb.append(" * - Input validation and sanitization\n");
        sb.append(" * - SQL injection prevention\n");
        sb.append(" * - XSS prevention\n");
        sb.append(" * - CSRF protection\n");
        sb.append(" */\n");
        sb.append("@DisplayName(\"Security Tests\")\n");
        sb.append("public class ").append(className).append(" {\n\n");

        // Constants
        sb.append("    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);\n");
        sb.append("    private static HttpClient httpClient;\n\n");

        sb.append("    @BeforeAll\n");
        sb.append("    static void setUpAll() {\n");
        sb.append("        httpClient = TestHttp.client();\n");
        sb.append("    }\n\n");

        sb.append("    private URI uriFor(String pathTemplate) {\n");
        sb.append("        String p = pathTemplate;\n");
        sb.append("        p = p.replace(\"{folderID}\", TestEnv.folderId());\n");
        sb.append("        p = p.replace(\"{promptID}\", TestEnv.get(\"test.prompt.id\", \"1\"));\n");
        sb.append("        p = p.replace(\"{id}\", TestEnv.folderId());\n");
        sb.append("        return URI.create(TestEnv.baseUrl() + p);\n");
        sb.append("    }\n\n");

        // Test vectors
        sb.append("    // Security test vectors\n");
        sb.append("    private static final String[] SQL_INJECTION_VECTORS = {\n");
        sb.append("        \"' OR '1'='1\",\n");
        sb.append("        \"'; DROP TABLE users; --\",\n");
        sb.append("        \"1' UNION SELECT * FROM users --\"\n");
        sb.append("    };\n\n");
        sb.append("    private static final String[] XSS_VECTORS = {\n");
        sb.append("        \"<script>alert('XSS')</script>\",\n");
        sb.append("        \"<img src=x onerror=alert('XSS')>\",\n");
        sb.append("        \"javascript:alert('XSS')\"\n");
        sb.append("    };\n\n");
        sb.append("    private static final String[] PATH_TRAVERSAL_VECTORS = {\n");
        sb.append("        \"../../../etc/passwd\",\n");
        sb.append("        \"..\\\\..\\\\..\\\\windows\\\\system32\",\n");
        sb.append("        \"%2e%2e%2f%2e%2e%2f%2e%2e%2f\"\n");
        sb.append("    };\n\n");

        // Setup
        sb.append("    @BeforeAll\n");
        sb.append("    static void setUpAll() {\n");
        sb.append("        httpClient = HttpClient.newBuilder()\n");
        sb.append("            .connectTimeout(REQUEST_TIMEOUT)\n");
        sb.append("            .build();\n");
        sb.append("    }\n\n");

        // Authentication tests
        generateAuthenticationTests(sb, spec);

        // Authorization tests
        generateAuthorizationTests(sb, spec);

        // Input validation tests
        generateInputValidationTests(sb, spec);

        // SQL injection tests
        generateSQLInjectionTests(sb, spec);

        // XSS tests
        generateXSSTests(sb, spec);

        // Path traversal tests
        generatePathTraversalTests(sb, spec);

        // CORS tests
        generateCORSTests(sb, spec);

        // Rate limiting tests
        generateRateLimitingTests(sb, spec);

        sb.append("}\n");

        return sb.toString();
    }

    /**
     * Generate authentication tests
     */
    private void generateAuthenticationTests(StringBuilder sb, Map<String, Object> spec) {
        sb.append("    // ========== Authentication Tests ==========\n\n");

        Map<String, Object> paths = Util.asStringObjectMap(spec.get("paths"));
        if (paths != null) {
            for (Map.Entry<String, Object> pathEntry : paths.entrySet()) {
                Map<String, Object> pathItem = Util.asStringObjectMap(pathEntry.getValue());
                if (pathItem == null) continue;

                String[] methods = Constants.HTTP_METHODS;
                for (String method : methods) {
                    if (pathItem.containsKey(method)) {
                        Map<String, Object> operation = Util.asStringObjectMap(pathItem.get(method));
                        if (operation != null && operation.containsKey("security")) {
                            String path = pathEntry.getKey();
                            String summary = (String) operation.get("summary");

                            sb.append("    @Test\n");
                            sb.append("    @DisplayName(\"Authentication: ").append(summary != null ? summary : method.toUpperCase() + " " + path)
                                    .append(" - Missing Token\")\n");
                            sb.append("    void testAuthentication_MissingToken_").append(sanitizePath(path)).append("_").append(method).append("() throws Exception {\n");
                            if ("delete".equals(method)) {
                                sb.append(TestCodegenSupport.destructiveGate());
                            }
                            sb.append("        // Arrange - Request without authentication\n");
                            sb.append("        URI uri = uriFor(\"").append(path).append("\");\n");
                            sb.append("        HttpRequest request = HttpRequest.newBuilder()\n");
                            sb.append("            .uri(uri)\n");
                            sb.append("            .timeout(REQUEST_TIMEOUT)\n");
                            sb.append("            .").append(method.toUpperCase()).append("()\n");
                            sb.append("            .header(\"Accept\", \"application/json\")\n");
                            sb.append("            // Intentionally omitting Authorization header\n");
                            sb.append("            .build();\n\n");
                            sb.append("        // Act\n");
                            sb.append("        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());\n\n");
                            sb.append("        // Assert\n");
                            sb.append("        assertEquals(401, response.statusCode(), \n");
                            sb.append("            \"Expected 401 Unauthorized for request without authentication token\");\n");
                            sb.append("    }\n\n");

                            sb.append("    @Test\n");
                            sb.append("    @DisplayName(\"Authentication: ").append(summary != null ? summary : method.toUpperCase() + " " + path)
                                    .append(" - Invalid Token\")\n");
                            sb.append("    void testAuthentication_InvalidToken_").append(sanitizePath(path)).append("_").append(method).append("() throws Exception {\n");
                            sb.append("        // Arrange - Request with invalid token\n");
                            sb.append("        URI uri = uriFor(\"").append(path).append("\");\n");
                            sb.append("        HttpRequest request = HttpRequest.newBuilder()\n");
                            sb.append("            .uri(uri)\n");
                            sb.append("            .timeout(REQUEST_TIMEOUT)\n");
                            sb.append("            .").append(method.toUpperCase()).append("()\n");
                            sb.append("            .header(\"Accept\", \"application/json\")\n");
                            sb.append("            .header(\"Authorization\", \"Bearer invalid-token-12345\")\n");
                            sb.append("            .build();\n\n");
                            sb.append("        // Act\n");
                            sb.append("        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());\n\n");
                            sb.append("        // Assert\n");
                            sb.append("        assertTrue(response.statusCode() == 401 || response.statusCode() == 403, \n");
                            sb.append("            \"Expected 401 or 403 for invalid authentication token, got \" + response.statusCode());\n");
                            sb.append("    }\n\n");

                            // CBD-8481: client-app tokens must not be treated as user tokens for secured APIs
                            sb.append("    @Test\n");
                            sb.append("    @DisplayName(\"Authentication: ").append(summary != null ? summary : method.toUpperCase() + " " + path)
                                    .append(" - Client App Token\")\n");
                            sb.append("    void testAuthentication_ClientAppToken_").append(sanitizePath(path)).append("_").append(method).append("() throws Exception {\n");
                            sb.append("        // Arrange - Client-application token (not a user token); expect 401/403\n");
                            sb.append("        URI uri = uriFor(\"").append(path).append("\");\n");
                            sb.append("        HttpRequest request = HttpRequest.newBuilder()\n");
                            sb.append("            .uri(uri)\n");
                            sb.append("            .timeout(REQUEST_TIMEOUT)\n");
                            sb.append("            .").append(method.toUpperCase()).append("()\n");
                            sb.append("            .header(\"Accept\", \"application/json\")\n");
                            sb.append("            .header(\"Authorization\", \"Bearer <TOKEN_CLIENT_APP>\")\n");
                            sb.append("            .build();\n\n");
                            sb.append("        // Act\n");
                            sb.append("        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());\n\n");
                            sb.append("        // Assert\n");
                            sb.append("        assertTrue(response.statusCode() == 401 || response.statusCode() == 403,\n");
                            sb.append("            \"Expected 401 or 403 for client-app token on user-secured API, got \" + response.statusCode());\n");
                            sb.append("    }\n\n");

                            break; // Only test first secured endpoint
                        }
                    }
                }
            }
        }
    }

    /**
     * Generate authorization tests
     */
    private void generateAuthorizationTests(StringBuilder sb, Map<String, Object> spec) {
        sb.append("    // ========== Authorization Tests ==========\n\n");

        // Extract security schemes from components
        Map<String, Object> components = Util.asStringObjectMap(spec.get("components"));
        Map<String, Object> securitySchemes = null;
        if (components != null) {
            securitySchemes = Util.asStringObjectMap(components.get("securitySchemes"));
        }

        Map<String, Object> paths = Util.asStringObjectMap(spec.get("paths"));
        if (paths == null || paths.isEmpty()) {
            return;
        }

        // Iterate over every operation that declares security scopes
        for (Map.Entry<String, Object> pathEntry : paths.entrySet()) {
            String path = pathEntry.getKey();
            Map<String, Object> pathItem = Util.asStringObjectMap(pathEntry.getValue());
            if (pathItem == null) continue;

            for (String method : Constants.HTTP_METHODS) {
                if (!pathItem.containsKey(method)) continue;
                Map<String, Object> operation = Util.asStringObjectMap(pathItem.get(method));
                if (operation == null || !operation.containsKey("security")) continue;

                List<Map<String, Object>> securityRequirements =
                        Util.asStringObjectMapList(operation.get("security"));
                if (securityRequirements == null) continue;

                for (Map<String, Object> requirement : securityRequirements) {
                    for (Map.Entry<String, Object> schemeEntry : requirement.entrySet()) {
                        String schemeName = schemeEntry.getKey();
                        Object scopesObj = schemeEntry.getValue();
                        if (!(scopesObj instanceof List)) continue;

                        @SuppressWarnings("unchecked")
                        List<String> requiredScopes = (List<String>) scopesObj;
                        if (requiredScopes.isEmpty()) continue;

                        String safePath = sanitizePath(path);
                        String scopeList = String.join(", ", requiredScopes);

                        // Test: correct scope should succeed (200)
                        sb.append("    @Test\n");
                        sb.append("    @DisplayName(\"Authorization: ").append(method.toUpperCase()).append(" ").append(path)
                                .append(" - Correct Scope (").append(scopeList).append(")\")\n");
                        sb.append("    void testAuthorization_CorrectScope_").append(safePath).append("_").append(method).append("() throws Exception {\n");
                        sb.append("        // Arrange - Request with a token bearing the required scopes: ").append(scopeList).append("\n");
                        sb.append("        URI uri = uriFor(\"").append(path).append("\");\n");
                        sb.append("        HttpRequest request = HttpRequest.newBuilder()\n");
                        sb.append("            .uri(uri)\n");
                        sb.append("            .timeout(REQUEST_TIMEOUT)\n");
                        sb.append("            .").append(method.toUpperCase()).append("()\n");
                        sb.append("            .header(\"Accept\", \"application/json\")\n");
                        sb.append("            .header(\"Authorization\", \"Bearer <TOKEN_WITH_SCOPES: ").append(scopeList).append(">\")\n");
                        sb.append("            .build();\n\n");
                        sb.append("        // Act\n");
                        sb.append("        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());\n\n");
                        sb.append("        // Assert - Authorized request should succeed\n");
                        sb.append("        assertEquals(200, response.statusCode(),\n");
                        sb.append("            \"Expected 200 OK for request with correct scopes [").append(scopeList).append("], got \" + response.statusCode());\n");
                        sb.append("    }\n\n");

                        // Test: insufficient scope should be forbidden (403)
                        sb.append("    @Test\n");
                        sb.append("    @DisplayName(\"Authorization: ").append(method.toUpperCase()).append(" ").append(path)
                                .append(" - Insufficient Scope\")\n");
                        sb.append("    void testAuthorization_InsufficientScope_").append(safePath).append("_").append(method).append("() throws Exception {\n");
                        sb.append("        // Arrange - Request with a token that does NOT have the required scopes\n");
                        sb.append("        URI uri = uriFor(\"").append(path).append("\");\n");
                        sb.append("        HttpRequest request = HttpRequest.newBuilder()\n");
                        sb.append("            .uri(uri)\n");
                        sb.append("            .timeout(REQUEST_TIMEOUT)\n");
                        sb.append("            .").append(method.toUpperCase()).append("()\n");
                        sb.append("            .header(\"Accept\", \"application/json\")\n");
                        sb.append("            .header(\"Authorization\", \"Bearer <TOKEN_WITHOUT_REQUIRED_SCOPES>\")\n");
                        sb.append("            .build();\n\n");
                        sb.append("        // Act\n");
                        sb.append("        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());\n\n");
                        sb.append("        // Assert - Insufficient scope should return 403 Forbidden\n");
                        sb.append("        assertEquals(403, response.statusCode(),\n");
                        sb.append("            \"Expected 403 Forbidden for token without required scopes [").append(scopeList).append("], got \" + response.statusCode());\n");
                        sb.append("    }\n\n");

                        // Test: different role accessing unauthorized endpoint (403)
                        sb.append("    @Test\n");
                        sb.append("    @DisplayName(\"Authorization: ").append(method.toUpperCase()).append(" ").append(path)
                                .append(" - Wrong Role\")\n");
                        sb.append("    void testAuthorization_WrongRole_").append(safePath).append("_").append(method).append("() throws Exception {\n");
                        sb.append("        // Arrange - Request with a valid token for a different role that lacks [").append(scopeList).append("]\n");
                        sb.append("        URI uri = uriFor(\"").append(path).append("\");\n");
                        sb.append("        HttpRequest request = HttpRequest.newBuilder()\n");
                        sb.append("            .uri(uri)\n");
                        sb.append("            .timeout(REQUEST_TIMEOUT)\n");
                        sb.append("            .").append(method.toUpperCase()).append("()\n");
                        sb.append("            .header(\"Accept\", \"application/json\")\n");
                        sb.append("            .header(\"Authorization\", \"Bearer <TOKEN_WRONG_ROLE>\")\n");
                        sb.append("            .build();\n\n");
                        sb.append("        // Act\n");
                        sb.append("        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());\n\n");
                        sb.append("        // Assert - Wrong role should be forbidden\n");
                        sb.append("        assertEquals(403, response.statusCode(),\n");
                        sb.append("            \"Expected 403 Forbidden for wrong-role token accessing [").append(method.toUpperCase()).append(" ").append(path).append("], got \" + response.statusCode());\n");
                        sb.append("    }\n\n");
                    }
                }
            }
        }
    }

    /**
     * Generate input validation tests
     */
    private void generateInputValidationTests(StringBuilder sb, Map<String, Object> spec) {
        sb.append("    // ========== Input Validation Tests ==========\n\n");

        Map<String, Object> paths = Util.asStringObjectMap(spec.get("paths"));
        if (paths != null && !paths.isEmpty()) {
            String firstPath = paths.keySet().iterator().next();

            sb.append("    @Test\n");
            sb.append("    @DisplayName(\"Input Validation: Oversized Payload\")\n");
            sb.append("    void testInputValidation_OversizedPayload() throws Exception {\n");
            sb.append("        // Arrange - Create oversized payload\n");
            sb.append("        String oversizedPayload = \"x\".repeat(100000); // 100KB payload\n");
            sb.append("        URI uri = uriFor(\"").append(firstPath).append("\");\n");
            sb.append("        HttpRequest request = HttpRequest.newBuilder()\n");
            sb.append("            .uri(uri)\n");
            sb.append("            .timeout(REQUEST_TIMEOUT)\n");
            sb.append("            .POST(HttpRequest.BodyPublishers.ofString(oversizedPayload))\n");
            sb.append("            .header(\"Content-Type\", \"application/json\")\n");
            sb.append("            .build();\n\n");
            sb.append("        // Act\n");
            sb.append("        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());\n\n");
            sb.append("        // Assert - Should reject or limit oversized payloads\n");
            sb.append("        assertTrue(response.statusCode() == 400 || response.statusCode() == 413, \n");
            sb.append("            \"Expected 400 or 413 for oversized payload, got \" + response.statusCode());\n");
            sb.append("    }\n\n");
        }
    }

    /**
     * Generate SQL injection tests
     */
    private void generateSQLInjectionTests(StringBuilder sb, Map<String, Object> spec) {
        sb.append("    // ========== SQL Injection Tests ==========\n\n");

        Map<String, Object> paths = Util.asStringObjectMap(spec.get("paths"));
        if (paths != null && !paths.isEmpty()) {
            String firstPath = paths.keySet().iterator().next();

            sb.append("    @Test\n");
            sb.append("    @DisplayName(\"SQL Injection: Parameter Injection\")\n");
            sb.append("    void testSQLInjection_ParameterInjection() throws Exception {\n");
            sb.append("        // Test each SQL injection vector\n");
            sb.append("        for (String vector : SQL_INJECTION_VECTORS) {\n");
            sb.append("            // Arrange\n");
            sb.append("            String testPath = \"").append(firstPath).append("\" + \"?param=\" + vector;\n");
            sb.append("            URI uri = uriFor(testPath);\n");
            sb.append("            HttpRequest request = HttpRequest.newBuilder()\n");
            sb.append("                .uri(uri)\n");
            sb.append("                .timeout(REQUEST_TIMEOUT)\n");
            sb.append("                .GET()\n");
            sb.append("                .build();\n\n");
            sb.append("            // Act\n");
            sb.append("            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());\n\n");
            sb.append("            // Assert - Should not execute SQL, should return error or sanitized response\n");
            sb.append("            assertTrue(response.statusCode() < 500, \n");
            sb.append("                \"SQL injection attempt should not cause server error (500), got \" + response.statusCode());\n");
            sb.append("            \n");
            sb.append("            // Response should not contain SQL error messages\n");
            sb.append("            String body = response.body();\n");
            sb.append("            assertFalse(body != null && (body.contains(\"SQL syntax\") || body.contains(\"mysql_fetch\")), \n");
            sb.append("                \"Response should not contain SQL error messages\");\n");
            sb.append("        }\n");
            sb.append("    }\n\n");
        }
    }

    /**
     * Generate XSS tests
     */
    private void generateXSSTests(StringBuilder sb, Map<String, Object> spec) {
        sb.append("    // ========== XSS (Cross-Site Scripting) Tests ==========\n\n");

        Map<String, Object> paths = Util.asStringObjectMap(spec.get("paths"));
        if (paths != null && !paths.isEmpty()) {
            String firstPath = paths.keySet().iterator().next();

            sb.append("    @Test\n");
            sb.append("    @DisplayName(\"XSS: Script Injection\")\n");
            sb.append("    void testXSS_ScriptInjection() throws Exception {\n");
            sb.append("        // Test each XSS vector\n");
            sb.append("        for (String vector : XSS_VECTORS) {\n");
            sb.append("            // Arrange\n");
            sb.append("            String testPath = \"").append(firstPath).append("\" + \"?input=\" + vector;\n");
            sb.append("            URI uri = uriFor(testPath);\n");
            sb.append("            HttpRequest request = HttpRequest.newBuilder()\n");
            sb.append("                .uri(uri)\n");
            sb.append("                .timeout(REQUEST_TIMEOUT)\n");
            sb.append("                .GET()\n");
            sb.append("                .build();\n\n");
            sb.append("            // Act\n");
            sb.append("            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());\n\n");
            sb.append("            // Assert - Response should sanitize or reject XSS attempts\n");
            sb.append("            String body = response.body();\n");
            sb.append("            if (body != null) {\n");
            sb.append("                // Check that script tags are escaped or removed\n");
            sb.append("                assertFalse(body.contains(\"<script>\"), \n");
            sb.append("                    \"Response should not contain unescaped script tags\");\n");
            sb.append("            }\n");
            sb.append("        }\n");
            sb.append("    }\n\n");
        }
    }

    /**
     * Generate path traversal tests
     */
    private void generatePathTraversalTests(StringBuilder sb, Map<String, Object> spec) {
        sb.append("    // ========== Path Traversal Tests ==========\n\n");

        Map<String, Object> paths = Util.asStringObjectMap(spec.get("paths"));
        if (paths != null && !paths.isEmpty()) {
            String firstPath = paths.keySet().iterator().next();

            sb.append("    @Test\n");
            sb.append("    @DisplayName(\"Path Traversal: Directory Traversal\")\n");
            sb.append("    void testPathTraversal_DirectoryTraversal() throws Exception {\n");
            sb.append("        // Test each path traversal vector\n");
            sb.append("        for (String vector : PATH_TRAVERSAL_VECTORS) {\n");
            sb.append("            // Arrange\n");
            sb.append("            String testPath = \"").append(firstPath).append("\".replace(\"{id}\", vector);\n");
            sb.append("            URI uri = uriFor(testPath);\n");
            sb.append("            HttpRequest request = HttpRequest.newBuilder()\n");
            sb.append("                .uri(uri)\n");
            sb.append("                .timeout(REQUEST_TIMEOUT)\n");
            sb.append("                .GET()\n");
            sb.append("                .build();\n\n");
            sb.append("            // Act\n");
            sb.append("            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());\n\n");
            sb.append("            // Assert - Should reject path traversal attempts\n");
            sb.append("            assertTrue(response.statusCode() == 400 || response.statusCode() == 404 || response.statusCode() == 403, \n");
            sb.append("                \"Path traversal attempt should be rejected, got \" + response.statusCode());\n");
            sb.append("        }\n");
            sb.append("    }\n\n");
        }
    }

    /**
     * Generate CORS (Cross-Origin Resource Sharing) tests
     */
    private void generateCORSTests(StringBuilder sb, Map<String, Object> spec) {
        sb.append("    // ========== CORS Tests ==========\n\n");

        Map<String, Object> paths = Util.asStringObjectMap(spec.get("paths"));
        if (paths == null || paths.isEmpty()) {
            return;
        }

        String firstPath = paths.keySet().iterator().next();

        // Test: OPTIONS preflight returns correct CORS headers
        sb.append("    @Test\n");
        sb.append("    @DisplayName(\"CORS: Preflight OPTIONS request returns correct headers\")\n");
        sb.append("    void testCORS_PreflightReturnsCorrectHeaders() throws Exception {\n");
        sb.append("        // Arrange - Send OPTIONS preflight request with Origin header\n");
        sb.append("        URI uri = uriFor(\"").append(firstPath).append("\");\n");
        sb.append("        HttpRequest request = HttpRequest.newBuilder()\n");
        sb.append("            .uri(uri)\n");
        sb.append("            .timeout(REQUEST_TIMEOUT)\n");
        sb.append("            .method(\"OPTIONS\", HttpRequest.BodyPublishers.noBody())\n");
        sb.append("            .header(\"Origin\", \"https://malicious-site.example.com\")\n");
        sb.append("            .header(\"Access-Control-Request-Method\", \"GET\")\n");
        sb.append("            .header(\"Access-Control-Request-Headers\", \"Authorization\")\n");
        sb.append("            .build();\n\n");
        sb.append("        // Act\n");
        sb.append("        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());\n\n");
        sb.append("        // Assert - Preflight should return 200/204 with CORS headers\n");
        sb.append("        assertTrue(response.statusCode() == 200 || response.statusCode() == 204,\n");
        sb.append("            \"Expected 200 or 204 for OPTIONS preflight, got \" + response.statusCode());\n");
        sb.append("        // Verify CORS headers are present\n");
        sb.append("        assertTrue(response.headers().firstValue(\"Access-Control-Allow-Methods\").isPresent(),\n");
        sb.append("            \"Preflight response must include Access-Control-Allow-Methods header\");\n");
        sb.append("        assertTrue(response.headers().firstValue(\"Access-Control-Allow-Headers\").isPresent(),\n");
        sb.append("            \"Preflight response must include Access-Control-Allow-Headers header\");\n");
        sb.append("    }\n\n");

        // Test: cross-origin request without proper headers is rejected
        sb.append("    @Test\n");
        sb.append("    @DisplayName(\"CORS: Cross-origin request without proper Origin is rejected\")\n");
        sb.append("    void testCORS_UnauthorizedOriginRejected() throws Exception {\n");
        sb.append("        // Arrange - Send request with an unauthorized Origin\n");
        sb.append("        URI uri = uriFor(\"").append(firstPath).append("\");\n");
        sb.append("        HttpRequest request = HttpRequest.newBuilder()\n");
        sb.append("            .uri(uri)\n");
        sb.append("            .timeout(REQUEST_TIMEOUT)\n");
        sb.append("            .GET()\n");
        sb.append("            .header(\"Accept\", \"application/json\")\n");
        sb.append("            .header(\"Origin\", \"https://malicious-site.example.com\")\n");
        sb.append("            .build();\n\n");
        sb.append("        // Act\n");
        sb.append("        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());\n\n");
        sb.append("        // Assert - Unauthorized origin should not be reflected in Access-Control-Allow-Origin\n");
        sb.append("        Optional<String> allowOrigin = response.headers().firstValue(\"Access-Control-Allow-Origin\");\n");
        sb.append("        assertTrue(allowOrigin.isEmpty() || !allowOrigin.get().equals(\"https://malicious-site.example.com\"),\n");
        sb.append("            \"Access-Control-Allow-Origin must not reflect an unauthorized origin\");\n");
        sb.append("        // A wildcard '*' with credentials is also a misconfiguration\n");
        sb.append("        if (allowOrigin.isPresent() && allowOrigin.get().equals(\"*\")) {\n");
        sb.append("            assertTrue(response.headers().firstValue(\"Access-Control-Allow-Credentials\").isEmpty()\n");
        sb.append("                || !response.headers().firstValue(\"Access-Control-Allow-Credentials\").get().equals(\"true\"),\n");
        sb.append("                \"Wildcard Access-Control-Allow-Origin must not be combined with Access-Control-Allow-Credentials: true\");\n");
        sb.append("        }\n");
        sb.append("    }\n\n");
    }

    /**
     * Generate rate limiting tests
     */
    private void generateRateLimitingTests(StringBuilder sb, Map<String, Object> spec) {
        sb.append("    // ========== Rate Limiting Tests ==========\n\n");

        Map<String, Object> paths = Util.asStringObjectMap(spec.get("paths"));
        if (paths == null || paths.isEmpty()) {
            return;
        }

        String firstPath = paths.keySet().iterator().next();

        sb.append("    @Test\n");
        sb.append("    @DisplayName(\"Rate Limiting: Rapid requests return 429 Too Many Requests\")\n");
        sb.append("    void testRateLimiting_RapidRequestsReturn429() throws Exception {\n");
        sb.append("        // Arrange - Prepare a burst of rapid requests to the same endpoint\n");
        sb.append("        URI uri = uriFor(\"").append(firstPath).append("\");\n");
        sb.append("        int burstSize = 50;\n");
        sb.append("        boolean rateLimited = false;\n\n");
        sb.append("        // Act - Send requests in rapid succession\n");
        sb.append("        for (int i = 0; i < burstSize; i++) {\n");
        sb.append("            HttpRequest request = HttpRequest.newBuilder()\n");
        sb.append("                .uri(uri)\n");
        sb.append("                .timeout(REQUEST_TIMEOUT)\n");
        sb.append("                .GET()\n");
        sb.append("                .header(\"Accept\", \"application/json\")\n");
        sb.append("                .build();\n");
        sb.append("            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());\n");
        sb.append("            if (response.statusCode() == 429) {\n");
        sb.append("                rateLimited = true;\n");
        sb.append("                break;\n");
        sb.append("            }\n");
        sb.append("        }\n\n");
        sb.append("        // Assert - At least one request should have been rate-limited\n");
        sb.append("        assertTrue(rateLimited,\n");
        sb.append("            \"Expected at least one 429 Too Many Requests response after \" + burstSize + \" rapid requests\");\n");
        sb.append("    }\n\n");
    }

    /**
     * Generate security configuration
     */
    private void generatePomXml(String outputDir, String basePackage) throws IOException {
        String pom = TestMavenSupport.pomHeader("api-security-tests", basePackage)
                + TestMavenSupport.junitDependency()
                + TestMavenSupport.buildSectionWithTestSupport();
        Files.write(Paths.get(outputDir, "pom.xml"), pom.getBytes());
    }

    private void generateSecurityConfiguration(String outputDir, String baseUrl) throws IOException {
        String configContent = "# Security Test Configuration\n" +
                "# Generated from OpenAPI specification\n\n" +
                "base.url=" + baseUrl + "\n" +
                "timeout.seconds=30\n" +
                "# Security test settings\n" +
                "test.authentication=true\n" +
                "test.authorization=true\n" +
                "test.input.validation=true\n" +
                "test.sql.injection=true\n" +
                "test.xss=true\n" +
                "test.path.traversal=true\n";

        Files.write(Paths.get(outputDir, "security-config.properties"), configContent.getBytes());
    }

    private String sanitizePath(String path) {
        return path.replaceAll("[^a-zA-Z0-9]", "_");
    }

    @Override
    public String getName() {
        return "Security Test Generator";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getTestType() {
        return "security";
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
