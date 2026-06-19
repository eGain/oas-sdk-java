package egain.oassdk.testgenerators.python;

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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Python pytest integration test generator
 * Generates pytest integration tests for API endpoints based on OpenAPI specification
 * These tests make real HTTP calls to a running server
 */
public class PytestIntegrationTestGenerator implements TestGenerator, ConfigurableTestGenerator {

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

            // Get base URL from servers
            String baseUrl = TestSpecUtils.getBaseUrl(spec);

            // Generate test classes for each endpoint
            generateTestModules(spec, outputPath.toString(), apiTitle, baseUrl);

            // Generate test configuration
            generateTestConfiguration(outputPath.toString(), baseUrl);

            // Generate conftest.py for pytest fixtures
            generateConftest(outputPath.toString(), baseUrl);

            // Generate requirements.txt
            generateRequirements(outputPath.toString());

        } catch (Exception e) {
            throw new GenerationException("Failed to generate pytest integration tests: " + e.getMessage(), e);
        }
    }

    /**
     * Generate test modules for all endpoints
     */
    private void generateTestModules(Map<String, Object> spec, String outputDir, String apiTitle, String baseUrl) throws IOException {
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

            String moduleName = "test_" + toSnakeCase(tag) + "_integration";

            String testModuleContent = generateTestModule(moduleName, tag, operations, spec, baseUrl);
            Files.write(Paths.get(outputDir, moduleName + ".py"), testModuleContent.getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * Generate a test module for a group of operations
     */
    private String generateTestModule(String moduleName, String tag, List<OperationInfo> operations, Map<String, Object> spec, String baseUrl) {
        StringBuilder sb = new StringBuilder();

        // Module docstring
        sb.append("\"\"\"Integration tests for ").append(tag).append(" API endpoints\n\n");
        sb.append("Generated from OpenAPI specification\n");
        sb.append("These tests make real HTTP calls to a running server.\n");
        sb.append("Ensure the API server is running before executing these tests.\n");
        sb.append("\"\"\"\n\n");

        // Imports
        sb.append("import pytest\n");
        sb.append("import requests\n");
        sb.append("import json\n");
        sb.append("from typing import Dict, Any, Optional\n");
        sb.append("import os\n\n");

        // Constants
        sb.append("BASE_URL = os.getenv('API_BASE_URL', '").append(baseUrl).append("')\n");
        sb.append("REQUEST_TIMEOUT = 30\n\n");

        // Helper functions
        sb.append("def build_url(path: str, query_params: Optional[Dict[str, Any]] = None) -> str:\n");
        sb.append("    \"\"\"Build complete URL with query parameters\"\"\"\n");
        sb.append("    url = f\"{BASE_URL}{path}\"\n");
        sb.append("    if query_params:\n");
        sb.append("        params = '&'.join(f\"{k}={v}\" for k, v in query_params.items())\n");
        sb.append("        url = f\"{url}?{params}\"\n");
        sb.append("    return url\n\n\n");

        sb.append("def replace_path_params(path: str, path_params: Dict[str, str]) -> str:\n");
        sb.append("    \"\"\"Replace path parameters in URL\"\"\"\n");
        sb.append("    result = path\n");
        sb.append("    for key, value in path_params.items():\n");
        sb.append("        result = result.replace(f\"{{{key}}}\", value)\n");
        sb.append("    return result\n\n\n");

        sb.append("def get_auth_token() -> Optional[str]:\n");
        sb.append("    \"\"\"Legacy token (API_BEARER_TOKEN or API_TOKEN)\"\"\"\n");
        sb.append("    t = os.getenv('API_BEARER_TOKEN')\n");
        sb.append("    if not t:\n");
        sb.append("        t = os.getenv('API_TOKEN')\n");
        sb.append("    return t\n\n\n");
        sb.append("def get_token_client_application() -> Optional[str]:\n");
        sb.append("    t = os.getenv('INTEGRATION_TOKEN_CLIENT_APPLICATION')\n");
        sb.append("    if not t:\n");
        sb.append("        t = os.getenv('API_BEARER_TOKEN')\n");
        sb.append("    if not t:\n");
        sb.append("        t = os.getenv('API_TOKEN')\n");
        sb.append("    return t\n\n\n");
        sb.append("def get_token_authenticated_customer() -> Optional[str]:\n");
        sb.append("    return os.getenv('INTEGRATION_TOKEN_AUTHENTICATED_CUSTOMER')\n\n\n");
        sb.append("def get_preferred_auth_token() -> Optional[str]:\n");
        sb.append("    t = get_token_authenticated_customer()\n");
        sb.append("    if t:\n");
        sb.append("        return t\n");
        sb.append("    t = get_token_client_application()\n");
        sb.append("    if t:\n");
        sb.append("        return t\n");
        sb.append("    return get_auth_token()\n\n\n");

        // Test class
        sb.append("class Test").append(toPascalCase(tag)).append("Integration:\n");
        sb.append("    \"\"\"Integration tests for ").append(tag).append(" endpoints\"\"\"\n\n");

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
        String method = opInfo.method.toLowerCase(Locale.ROOT);
        String methodUpper = method.toUpperCase(Locale.ROOT);
        String path = opInfo.path;

        String testMethodName = operationId != null
                ? toSnakeCase(operationId)
                : toSnakeCase(method + "_" + sanitizePath(path));

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
        String bodyRaw = IntegrationScenarioSupport.generateRequestBodyFromSchemaRaw(operation, spec);
        String bodyPyEscaped = IntegrationScenarioSupport.escapeForPythonDoubleQuoted(bodyRaw);
        Map<String, Object> bodySchema = IntegrationScenarioSupport.extractRequestBodySchema(operation, spec);
        boolean jsonBody = "post".equals(method) || "put".equals(method) || "patch".equals(method);

        if (requiresAuth) {
            sb.append("    def test_").append(testMethodName).append("_success_client_application(self):\n");
            sb.append("        \"\"\"Successful request — client application token\"\"\"\n");
            sb.append("        tok = get_token_client_application()\n");
            sb.append("        if not tok:\n");
            sb.append("            pytest.skip('Set INTEGRATION_TOKEN_CLIENT_APPLICATION (or API_BEARER_TOKEN / API_TOKEN)')\n");
            appendPythonPathUrlSetup(sb, path, pathParams, queryParams);
            sb.append("        headers = {'Accept': 'application/json', 'Authorization': f'Bearer {tok}'}\n");
            appendPythonRequestInvocation(sb, methodUpper, jsonBody, bodyPyEscaped);
            appendPythonSuccessAssertions(sb, responses);
            appendPythonSuccessSchemaNotes(sb, responses, spec);
            sb.append("\n");

            sb.append("    def test_").append(testMethodName).append("_success_authenticated_customer(self):\n");
            sb.append("        \"\"\"Successful request — authenticated customer token\"\"\"\n");
            sb.append("        tok = get_token_authenticated_customer()\n");
            sb.append("        if not tok:\n");
            sb.append("            pytest.skip('Set INTEGRATION_TOKEN_AUTHENTICATED_CUSTOMER')\n");
            appendPythonPathUrlSetup(sb, path, pathParams, queryParams);
            sb.append("        headers = {'Accept': 'application/json', 'Authorization': f'Bearer {tok}'}\n");
            appendPythonRequestInvocation(sb, methodUpper, jsonBody, bodyPyEscaped);
            appendPythonSuccessAssertions(sb, responses);
            appendPythonSuccessSchemaNotes(sb, responses, spec);
            sb.append("\n");
        } else {
            sb.append("    def test_").append(testMethodName).append("_success(self):\n");
            sb.append("        \"\"\"Test ").append(summary != null ? summary : methodUpper + " " + path).append(" - Successful Request\"\"\"\n");
            appendPythonPathUrlSetup(sb, path, pathParams, queryParams);
            sb.append("        headers = {'Accept': 'application/json'}\n");
            appendPythonRequestInvocation(sb, methodUpper, jsonBody, bodyPyEscaped);
            appendPythonSuccessAssertions(sb, responses);
            appendPythonSuccessSchemaNotes(sb, responses, spec);
            sb.append("\n");
        }

        if (jsonBody) {
            for (IntegrationScenarioSupport.OneOfVariantBody variant :
                    IntegrationScenarioSupport.buildOneOfVariantBodies(bodySchema, spec)) {
                String vlabel = toSnakeCase(variant.label());
                sb.append("    def test_").append(testMethodName).append("_success_variant_").append(vlabel).append("(self):\n");
                sb.append("        tok = get_token_client_application()\n");
                sb.append("        if not tok:\n");
                sb.append("            pytest.skip('Set INTEGRATION_TOKEN_CLIENT_APPLICATION')\n");
                appendPythonPathUrlSetup(sb, path, pathParams, queryParams);
                sb.append("        headers = {'Accept': 'application/json', 'Authorization': f'Bearer {tok}'}\n");
                appendPythonRequestInvocation(sb, methodUpper, true,
                        IntegrationScenarioSupport.escapeForPythonDoubleQuoted(variant.jsonBody()));
                appendPythonSuccessAssertions(sb, responses);
                appendPythonSuccessSchemaNotes(sb, responses, spec);
                sb.append("\n");
            }
        }

        if (IntegrationScenarioSupport.emitDeclaredErrorCodes(config)) {
            for (IntegrationScenarioSupport.DeclaredErrorCase dec :
                    IntegrationScenarioSupport.buildDeclaredErrorCases(operation, queryParams)) {
                String suffix = toSnakeCase(dec.label());
                sb.append("    def test_").append(testMethodName).append("_").append(suffix).append("(self):\n");
                appendPythonPathUrlSetup(sb, path, pathParams, dec.queryParamsOverride());
                sb.append("        headers = {'Accept': 'application/json'}\n");
                if (requiresAuth) {
                    sb.append("        _tok = get_preferred_auth_token()\n");
                    sb.append("        if _tok:\n");
                    sb.append("            headers['Authorization'] = f'Bearer {_tok}'\n");
                }
                appendPythonRequestInvocation(sb, methodUpper, jsonBody, bodyPyEscaped);
                sb.append("        assert response.status_code == ").append(dec.expectedStatus()).append("\n");
                appendPythonErrorBodyAssert(sb, responses, spec);
                sb.append("\n");
            }
        }

        List<IntegrationScenarioSupport.IntegrationParamNegativeCase> paramCases =
                IntegrationScenarioSupport.buildParamNegativeCases(path, operation, pathParams, queryParams, maxParam);
        for (IntegrationScenarioSupport.IntegrationParamNegativeCase nc : paramCases) {
            String suffix = toSnakeCase(nc.name.replaceAll("[^a-zA-Z0-9]+", "_"));
            if (suffix.length() > 50) {
                suffix = suffix.substring(0, 50);
            }
            sb.append("    def test_").append(testMethodName).append("_param_negative_").append(suffix).append("(self):\n");
            sb.append("        \"\"\"Parameter negative: ").append(nc.name.replace("\"", "'")).append("\"\"\"\n");
            appendPythonPathUrlSetup(sb, path, nc.pathParams, nc.queryParams);
            sb.append("        headers = {'Accept': 'application/json'}\n");
            if (requiresAuth) {
                sb.append("        _tok = get_preferred_auth_token()\n");
                sb.append("        if _tok:\n");
                sb.append("            headers['Authorization'] = f'Bearer {_tok}'\n");
            }
            appendPythonRequestInvocation(sb, methodUpper, jsonBody, bodyPyEscaped);
            appendPythonExpectedStatuses(sb, nc.expectedStatusCodes);
            appendPythonErrorBodyAssert(sb, responses, spec);
            sb.append("\n");
        }

        if (requiresAuth) {
            sb.append("    def test_").append(testMethodName).append("_anonymous_no_credentials(self):\n");
            sb.append("        \"\"\"Anonymous customer — expect 401 or 403\"\"\"\n");
            appendPythonPathUrlSetup(sb, path, pathParams, queryParams);
            sb.append("        headers = {'Accept': 'application/json'}\n");
            appendPythonRequestInvocation(sb, methodUpper, jsonBody, bodyPyEscaped);
            sb.append("        assert response.status_code in (401, 403), f'Expected 401/403, got {response.status_code}'\n");
            appendPythonErrorBodyAssert(sb, responses, spec);
            sb.append("\n");
        }

        if (jsonBody) {
            if (IntegrationScenarioSupport.isRequestBodyRequired(operation, spec)) {
                sb.append("    def test_").append(testMethodName).append("_empty_body(self):\n");
                appendPythonPathUrlSetup(sb, path, pathParams, queryParams);
                sb.append("        headers = {'Accept': 'application/json'}\n");
                if (requiresAuth) {
                    sb.append("        _tok = get_preferred_auth_token()\n");
                    sb.append("        if _tok:\n");
                    sb.append("            headers['Authorization'] = f'Bearer {_tok}'\n");
                }
                sb.append("        response = requests.request('").append(methodUpper).append("', url, headers=headers, timeout=REQUEST_TIMEOUT)\n");
                sb.append("        assert response.status_code in (400, 422), f'Expected 400/422, got {response.status_code}'\n");
                appendPythonErrorBodyAssert(sb, responses, spec);
                sb.append("\n");
            }

            String wrongRaw = IntegrationScenarioSupport.generateWrongTypesBodyRaw(bodySchema, spec);
            String wrongEsc = IntegrationScenarioSupport.escapeForPythonDoubleQuoted(wrongRaw);
            sb.append("    def test_").append(testMethodName).append("_invalid_types(self):\n");
            appendPythonPathUrlSetup(sb, path, pathParams, queryParams);
            sb.append("        headers = {'Accept': 'application/json'}\n");
            if (requiresAuth) {
                sb.append("        _tok = get_preferred_auth_token()\n");
                sb.append("        if _tok:\n");
                sb.append("            headers['Authorization'] = f'Bearer {_tok}'\n");
            }
            appendPythonRequestInvocation(sb, methodUpper, true, wrongEsc);
            sb.append("        assert response.status_code in (400, 422), f'Expected 400/422, got {response.status_code}'\n");
            appendPythonErrorBodyAssert(sb, responses, spec);
            sb.append("\n");

            List<String> req = IntegrationScenarioSupport.getRequiredFieldsFromSchema(bodySchema, spec);
            if (!req.isEmpty()) {
                String missRaw = IntegrationScenarioSupport.generateMissingRequiredFieldsBodyRaw(bodySchema, spec);
                String missEsc = IntegrationScenarioSupport.escapeForPythonDoubleQuoted(missRaw);
                sb.append("    def test_").append(testMethodName).append("_missing_required_fields(self):\n");
                appendPythonPathUrlSetup(sb, path, pathParams, queryParams);
                sb.append("        headers = {'Accept': 'application/json'}\n");
                if (requiresAuth) {
                    sb.append("        _tok = get_preferred_auth_token()\n");
                    sb.append("        if _tok:\n");
                    sb.append("            headers['Authorization'] = f'Bearer {_tok}'\n");
                }
                appendPythonRequestInvocation(sb, methodUpper, true, missEsc);
                sb.append("        assert response.status_code in (400, 422), f'Expected 400/422, got {response.status_code}'\n");
                appendPythonErrorBodyAssert(sb, responses, spec);
                sb.append("\n");
            }

            List<IntegrationScenarioSupport.PerFieldInvalidBody> perField =
                    IntegrationScenarioSupport.buildPerFieldInvalidBodies(bodySchema, spec, maxBody);
            for (IntegrationScenarioSupport.PerFieldInvalidBody pf : perField) {
                String fs = toSnakeCase(pf.fieldName.replaceAll("[^a-zA-Z0-9]+", "_"));
                sb.append("    def test_").append(testMethodName).append("_invalid_field_").append(fs).append("(self):\n");
                appendPythonPathUrlSetup(sb, path, pathParams, queryParams);
                sb.append("        headers = {'Accept': 'application/json'}\n");
                if (requiresAuth) {
                    sb.append("        _tok = get_preferred_auth_token()\n");
                    sb.append("        if _tok:\n");
                    sb.append("            headers['Authorization'] = f'Bearer {_tok}'\n");
                }
                appendPythonRequestInvocation(sb, methodUpper, true,
                        IntegrationScenarioSupport.escapeForPythonDoubleQuoted(pf.invalidJsonBody));
                sb.append("        assert response.status_code in (400, 422), f'Expected 400/422, got {response.status_code}'\n");
                appendPythonErrorBodyAssert(sb, responses, spec);
                sb.append("\n");
            }

            for (IntegrationScenarioSupport.OneOfXorNegativeBody xn :
                    IntegrationScenarioSupport.buildOneOfXorNegativeBodies(bodySchema, spec)) {
                String suffix = toSnakeCase(xn.label());
                sb.append("    def test_").append(testMethodName).append("_oneof_xor_").append(suffix).append("(self):\n");
                appendPythonPathUrlSetup(sb, path, pathParams, queryParams);
                sb.append("        headers = {'Accept': 'application/json'}\n");
                if (requiresAuth) {
                    sb.append("        _tok = get_preferred_auth_token()\n");
                    sb.append("        if _tok:\n");
                    sb.append("            headers['Authorization'] = f'Bearer {_tok}'\n");
                }
                appendPythonRequestInvocation(sb, methodUpper, true,
                        IntegrationScenarioSupport.escapeForPythonDoubleQuoted(xn.jsonBody()));
                sb.append("        assert response.status_code in (400, 422), f'Expected 400/422, got {response.status_code}'\n");
                appendPythonErrorBodyAssert(sb, responses, spec);
                sb.append("\n");
            }
        }
    }

    private void appendPythonPathUrlSetup(StringBuilder sb, String path, Map<String, String> pathParams,
                                          Map<String, String> queryParams) {
        if (pathParams != null && !pathParams.isEmpty()) {
            sb.append("        path_params = {\n");
            for (Map.Entry<String, String> entry : pathParams.entrySet()) {
                sb.append("            '").append(entry.getKey()).append("': '").append(entry.getValue()).append("',\n");
            }
            sb.append("        }\n");
            sb.append("        path = replace_path_params('").append(path).append("', path_params)\n");
        } else {
            sb.append("        path = '").append(path).append("'\n");
        }
        if (queryParams != null && !queryParams.isEmpty()) {
            sb.append("        query_params = {\n");
            for (Map.Entry<String, String> entry : queryParams.entrySet()) {
                sb.append("            '").append(entry.getKey()).append("': '").append(entry.getValue()).append("',\n");
            }
            sb.append("        }\n");
            sb.append("        url = build_url(path, query_params)\n");
        } else {
            sb.append("        url = f\"{BASE_URL}{path}\"\n");
        }
    }

    private void appendPythonRequestInvocation(StringBuilder sb, String methodUpper, boolean jsonBody, String jsonEscaped) {
        if (jsonBody) {
            sb.append("        body_obj = json.loads(\"").append(jsonEscaped).append("\")\n");
            sb.append("        response = requests.request('").append(methodUpper).append("', url, headers=headers, json=body_obj, timeout=REQUEST_TIMEOUT)\n");
        } else {
            sb.append("        response = requests.request('").append(methodUpper).append("', url, headers=headers, timeout=REQUEST_TIMEOUT)\n");
        }
    }

    private void appendPythonSuccessAssertions(StringBuilder sb, Map<String, Object> responses) {
        sb.append("        assert response is not None\n");
        if (responses != null && responses.containsKey("200")) {
            sb.append("        assert response.status_code == 200, f'Expected 200, got {response.status_code}'\n");
        } else if (responses != null && responses.containsKey("201")) {
            sb.append("        assert response.status_code == 201, f'Expected 201, got {response.status_code}'\n");
        } else {
            sb.append("        assert 200 <= response.status_code < 300, f'Expected success, got {response.status_code}'\n");
        }
    }

    private void appendPythonExpectedStatuses(StringBuilder sb, List<Integer> codes) {
        sb.append("        assert response is not None\n");
        if (codes == null || codes.isEmpty()) {
            sb.append("        assert response.status_code in (400, 422), f'Unexpected status {response.status_code}'\n");
            return;
        }
        if (codes.size() == 1) {
            sb.append("        assert response.status_code == ").append(codes.get(0)).append(", f'Unexpected status {response.status_code}'\n");
        } else {
            sb.append("        assert response.status_code in (");
            for (int i = 0; i < codes.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(codes.get(i));
            }
            sb.append("), f'Unexpected status {response.status_code}'\n");
        }
    }

    private void appendPythonErrorBodyAssert(StringBuilder sb, Map<String, Object> responses, Map<String, Object> spec) {
        Map<String, Object> schema = null;
        for (String code : new String[]{"422", "400", "401", "403", "404"}) {
            schema = IntegrationScenarioSupport.resolveResponseSchema(code, responses, spec);
            if (schema != null) {
                break;
            }
        }
        if (schema != null) {
            sb.append("        try:\n");
            sb.append("            _eb = response.json()\n");
            sb.append("            assert isinstance(_eb, (dict, list))\n");
            sb.append("        except ValueError:\n");
            sb.append("            pass\n");
        } else {
            sb.append("        if response.text and response.text.strip().startswith('{'):\n");
            sb.append("            try:\n");
            sb.append("                _eb = response.json()\n");
            sb.append("                assert isinstance(_eb, dict)\n");
            sb.append("            except ValueError:\n");
            sb.append("                pass\n");
        }
    }

    private void appendPythonSuccessSchemaNotes(StringBuilder sb, Map<String, Object> responses, Map<String, Object> spec) {
        Map<String, Object> schema = null;
        for (String code : new String[]{"200", "201", "202"}) {
            schema = IntegrationScenarioSupport.resolveResponseSchema(code, responses, spec);
            if (schema != null) {
                break;
            }
        }
        if (schema != null) {
            sb.append("        try:\n");
            sb.append("            _b = response.json()\n");
            sb.append("            assert isinstance(_b, (dict, list))\n");
            sb.append("        except ValueError:\n");
            sb.append("            pytest.fail('Success response should be JSON when schema is declared')\n");
        }
    }

    /**
     * Generate test configuration file
     */
    private void generateTestConfiguration(String outputDir, String baseUrl) throws IOException {
        String configContent = "# Integration Test Configuration\n" +
                "# Generated from OpenAPI specification\n\n" +
                "[pytest]\n" +
                "addopts = -v --tb=short --strict-markers --cov=. --cov-report=term-missing --cov-report=html:coverage_html\n" +
                "testpaths = .\n" +
                "python_files = test_*.py\n" +
                "python_classes = Test*\n" +
                "python_functions = test_*\n" +
                "markers =\n" +
                "    integration: Integration tests\n" +
                "    slow: Slow running tests\n\n" +
                "[env]\n" +
                "API_BASE_URL=" + baseUrl + "\n" +
                "# INTEGRATION_TOKEN_CLIENT_APPLICATION=\n" +
                "# INTEGRATION_TOKEN_AUTHENTICATED_CUSTOMER=\n" +
                "# API_BEARER_TOKEN= / API_TOKEN= (fallbacks)\n" +
                "# Caps (TestConfig additionalProperties): integrationMaxInvalidBodyFieldsPerOperation, integrationMaxInvalidParamCasesPerOperation\n";

        Files.write(Paths.get(outputDir, "pytest.ini"), configContent.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Generate conftest.py for pytest fixtures
     */
    private void generateConftest(String outputDir, String baseUrl) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("\"\"\"Pytest configuration and fixtures for integration tests\"\"\"\n\n");
        sb.append("import pytest\n");
        sb.append("import requests\n");
        sb.append("import time\n");
        sb.append("import os\n\n");

        sb.append("@pytest.fixture(scope='session', autouse=True)\n");
        sb.append("def check_server_health():\n");
        sb.append("    \"\"\"Check if the API server is reachable before running tests\"\"\"\n");
        sb.append("    base_url = os.getenv('API_BASE_URL', '").append(baseUrl).append("')\n");
        sb.append("    try:\n");
        sb.append("        response = requests.get(base_url, timeout=5)\n");
        sb.append("        print(f\"Server health check: {response.status_code}\")\n");
        sb.append("    except Exception as e:\n");
        sb.append("        print(f\"Warning: Could not reach server at {base_url}\")\n");
        sb.append("        print(f\"Some tests may fail if server is not running.\")\n");
        sb.append("        print(f\"Error: {e}\")\n\n");

        sb.append("@pytest.fixture\n");
        sb.append("def api_client():\n");
        sb.append("    \"\"\"Provide a configured requests session\"\"\"\n");
        sb.append("    session = requests.Session()\n");
        sb.append("    session.headers.update({'Accept': 'application/json'})\n");
        sb.append("    return session\n\n");

        sb.append("@pytest.fixture\n");
        sb.append("def auth_headers():\n");
        sb.append("    \"\"\"Provide authentication headers\"\"\"\n");
        sb.append("    token = os.getenv('API_TOKEN')\n");
        sb.append("    if token:\n");
        sb.append("        return {'Authorization': f'Bearer {token}'}\n");
        sb.append("    return {}\n");

        Files.write(Paths.get(outputDir, "conftest.py"), sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Generate requirements.txt for pytest and requests
     */
    private void generateRequirements(String outputDir) throws IOException {
        String requirements = "# Test dependencies\n" +
                "pytest>=7.0.0\n" +
                "pytest-cov>=4.0.0\n" +
                "requests>=2.28.0\n" +
                "python-dotenv>=0.19.0\n";

        Files.write(Paths.get(outputDir, "requirements.txt"), requirements.getBytes(StandardCharsets.UTF_8));
    }

    // Helper methods
    private String getOperationTag(Map<String, Object> operation) {
        List<String> tags = Util.asStringList(operation.get("tags"));
        return tags != null && !tags.isEmpty() ? tags.get(0) : "Default";
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
        return "Pytest Integration Test Generator";
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

