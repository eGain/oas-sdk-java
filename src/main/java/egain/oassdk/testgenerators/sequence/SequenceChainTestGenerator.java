package egain.oassdk.testgenerators.sequence;

import egain.oassdk.Util;
import egain.oassdk.config.TestConfig;
import egain.oassdk.core.exceptions.GenerationException;
import egain.oassdk.core.sequence.ApiCallExtractor;
import egain.oassdk.core.sequence.ApiCallInfo;
import egain.oassdk.core.sequence.ChainConfig;
import egain.oassdk.core.sequence.ChainEnumerator;
import egain.oassdk.core.sequence.EnumeratedChain;
import egain.oassdk.testgenerators.ConfigurableTestGenerator;
import egain.oassdk.testgenerators.IntegrationScenarioSupport;
import egain.oassdk.testgenerators.TestGenerator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Emits a pytest bundle of <i>enumerated</i> API-call chains (one test per
 * chain, one file per resource). Complements the Schemathesis bundle —
 * Schemathesis covers the random/property-based space, this covers the
 * systematic green-path permutations.
 *
 * <p>Output layout under {@code <outputDir>/sequence/}:
 * <pre>
 *   conftest.py            shared fixtures: api_client, auth_headers, base_url, extract_id
 *   pytest.ini             minimal pytest config
 *   requirements.txt       pytest, requests
 *   README-sequence.md     run instructions
 *   test_chain_&lt;res&gt;.py    one file per resource; one def test_&lt;shape&gt; per chain
 * </pre>
 *
 * <p>Validity of every chain is guaranteed at enumeration time by
 * {@link ChainEnumerator}; each generated test asserts {@code 2xx} on
 * every step. Any non-{@code 2xx} is a real failure with a clear per-step
 * message.
 *
 * <p>Optional {@link TestConfig#getAdditionalProperties()} keys:
 * <ul>
 *   <li>{@code sequence.maxChainLength} (int, default 4)</li>
 *   <li>{@code sequence.allowRepeats} (bool, default false)</li>
 *   <li>{@code sequence.deleteLastOnly} (bool, default true)</li>
 *   <li>{@code sequence.unresolvedParamPolicy} — {@code SKIP} (default)
 *       or {@code EMIT_WITH_MARKER}; controls handling of sub-resource
 *       POSTs whose path parameters have no producer POST in the spec</li>
 *   <li>{@code sequence.baseUrl} — baked into conftest as the default
 *       {@code API_BASE_URL} when the env var is unset</li>
 * </ul>
 */
public class SequenceChainTestGenerator implements TestGenerator, ConfigurableTestGenerator {

    private TestConfig config;

    @Override
    public void generate(Map<String, Object> spec, String outputDir, TestConfig config, String testFramework) throws GenerationException {
        this.config = config;
        if (outputDir == null) {
            throw new IllegalArgumentException("Output directory cannot be null");
        }
        try {
            Path dir = Paths.get(outputDir, "sequence");
            Files.createDirectories(dir);

            ApiCallExtractor extractor = new ApiCallExtractor();
            List<ApiCallInfo> calls = extractor.extract(spec);
            ChainEnumerator enumerator = new ChainEnumerator(readChainConfig(config));
            List<EnumeratedChain> chains = enumerator.enumerate(calls);

            String baseUrl = resolveBaseUrl(spec, config);
            writeConftest(dir, baseUrl);
            writePytestIni(dir);
            writeRequirements(dir);
            writeReadme(dir);
            writeChainTestFiles(dir, chains, spec, extractor);

        } catch (IOException e) {
            throw new GenerationException("Failed to generate sequence chain tests: " + e.getMessage(), e);
        }
    }

    private ChainConfig readChainConfig(TestConfig tc) {
        ChainConfig.Builder b = ChainConfig.builder();
        Integer max = propInt(tc, "sequence.maxChainLength", null);
        if (max != null) {
            b.maxChainLength(max);
        }
        Boolean allowRepeats = propBool(tc, "sequence.allowRepeats", null);
        if (allowRepeats != null) {
            b.allowRepeats(allowRepeats);
        }
        Boolean deleteLast = propBool(tc, "sequence.deleteLastOnly", null);
        if (deleteLast != null) {
            b.deleteLastOnly(deleteLast);
        }
        String policy = propString(tc, "sequence.unresolvedParamPolicy", null);
        if (policy != null) {
            try {
                b.unresolvedParamPolicy(ChainConfig.UnresolvedParamPolicy.valueOf(
                        policy.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                // Leave the builder's default (SKIP) in place for unknown values.
            }
        }
        return b.build();
    }

    private static String resolveBaseUrl(Map<String, Object> spec, TestConfig tc) {
        String fromConfig = propString(tc, "sequence.baseUrl", null);
        if (fromConfig != null && !fromConfig.isBlank()) {
            return fromConfig.trim();
        }
        List<Map<String, Object>> servers = Util.asStringObjectMapList(spec.get("servers"));
        if (servers != null && !servers.isEmpty() && servers.get(0) != null) {
            Object url = servers.get(0).get("url");
            if (url instanceof String s && !s.isBlank()) {
                return s.trim();
            }
        }
        return "http://localhost:8080";
    }

    private void writeConftest(Path dir, String baseUrl) throws IOException {
        String content = """
                \"\"\"Shared fixtures for enumerated API-chain tests.\"\"\"
                import os
                import re
                import pytest
                import requests


                @pytest.fixture(scope="session")
                def base_url():
                    return os.getenv("API_BASE_URL", "%s").rstrip("/")


                @pytest.fixture
                def api_client():
                    session = requests.Session()
                    session.headers.update({"Accept": "application/json"})
                    return session


                @pytest.fixture
                def auth_headers():
                    token = os.getenv("API_TOKEN")
                    if not token:
                        return {}
                    if token.lower().startswith("bearer "):
                        return {"Authorization": token}
                    return {"Authorization": f"Bearer {token}"}


                def extract_id(response, hint=None):
                    \"\"\"Pull an id out of a JSON response body; None if missing or not JSON.

                    If ``hint`` is given (the OpenAPI path-parameter name the caller expects to
                    consume, e.g. ``orderId``) it takes precedence: the hint itself and a
                    snake_case variant are tried first. The generic id heuristic then runs as a
                    fallback so callers that omit ``hint`` get pre-hint behavior.
                    \"\"\"
                    try:
                        body = response.json()
                    except ValueError:
                        return None
                    if not isinstance(body, dict):
                        return None
                    if hint:
                        snake = re.sub(r"([a-z0-9])([A-Z])", r"\\1_\\2", hint).lower()
                        candidates = [hint]
                        if snake != hint:
                            candidates.append(snake)
                        for key in candidates:
                            if key in body and body[key] is not None:
                                return str(body[key])
                    for key in ("id", "ID", "_id", "uuid", "resourceId"):
                        if key in body and body[key] is not None:
                            return str(body[key])
                    for key, value in body.items():
                        if key.lower().endswith("id") and value is not None:
                            return str(value)
                    return None
                """.formatted(escapePy(baseUrl));
        Files.writeString(dir.resolve("conftest.py"), content, StandardCharsets.UTF_8);
    }

    private void writePytestIni(Path dir) throws IOException {
        String content = """
                [pytest]
                addopts = -v --tb=short --strict-markers
                testpaths = .
                python_files = test_*.py
                python_functions = test_*
                """;
        Files.writeString(dir.resolve("pytest.ini"), content, StandardCharsets.UTF_8);
    }

    private void writeRequirements(Path dir) throws IOException {
        String content = """
                # Enumerated API-chain test dependencies
                pytest>=7.0.0
                requests>=2.28.0
                """;
        Files.writeString(dir.resolve("requirements.txt"), content, StandardCharsets.UTF_8);
    }

    private void writeReadme(Path dir) throws IOException {
        String content = """
                # Enumerated API-chain tests

                One file per seed POST's resource. One test per valid
                workflow chain. Every POST in the spec seeds at least one
                chain, so no path is skipped; sub-resource POSTs are
                preceded by producer POSTs whose output ids resolve the
                path parameters. Each step asserts `2xx`.

                ## Run

                ```bash
                python -m venv .venv && source .venv/bin/activate
                pip install -r requirements.txt
                export API_BASE_URL=http://localhost:8080
                export API_TOKEN="Bearer <your-token>"   # optional
                pytest -v
                ```

                ## What these tests are not

                - They do not exercise the random/property-based ordering space.
                  That is what the Schemathesis bundle is for.
                - They do not assert response-schema conformance. Add your
                  own assertions on top if needed, or lean on Schemathesis
                  `--checks all`.

                ## Validity rules (enforced at generation time)

                1. Each chain is anchored on a seed POST. Any prefix steps
                   are producer POSTs whose responses resolve the seed's
                   path parameters (recursively).
                2. Tail steps are path-templated consumers
                   (GET/PUT/PATCH/DELETE) scoped to the seed's resource tree.
                3. DELETE, if present, is the terminal step.
                4. No operation repeats within a chain (unless
                   `sequence.allowRepeats` is enabled).

                Because these rules are enforced when the tests are generated,
                every emitted test's expected outcome is `2xx` at every step.
                Any failure is a real bug.
                """;
        Files.writeString(dir.resolve("README-sequence.md"), content, StandardCharsets.UTF_8);
    }

    private void writeChainTestFiles(Path dir, List<EnumeratedChain> chains,
                                     Map<String, Object> spec, ApiCallExtractor extractor) throws IOException {
        Map<String, List<EnumeratedChain>> byResource = groupChainsByResource(chains);
        for (Map.Entry<String, List<EnumeratedChain>> e : byResource.entrySet()) {
            String resource = e.getKey();
            String fileName = "test_chain_" + sanitizeModuleName(resource) + ".py";
            String content = renderChainTestFile(resource, e.getValue(), spec, extractor);
            Files.writeString(dir.resolve(fileName), content, StandardCharsets.UTF_8);
        }
    }

    private Map<String, List<EnumeratedChain>> groupChainsByResource(List<EnumeratedChain> chains) {
        Map<String, List<EnumeratedChain>> byResource = new TreeMap<>();
        for (EnumeratedChain chain : chains) {
            if (chain.steps().isEmpty()) {
                continue;
            }
            byResource.computeIfAbsent(chain.seedPost().resourceName(), k -> new java.util.ArrayList<>()).add(chain);
        }
        return byResource;
    }

    private String renderChainTestFile(String resource, List<EnumeratedChain> chains,
                                       Map<String, Object> spec, ApiCallExtractor extractor) {
        StringBuilder sb = new StringBuilder();
        sb.append("\"\"\"Enumerated workflow chains for resource: ").append(resource).append(".\n\n");
        sb.append("Generated by SequenceChainTestGenerator. Every chain is valid by\n");
        sb.append("construction; every step asserts 2xx.\n");
        sb.append("\"\"\"\n");
        sb.append("import pytest\n");
        sb.append("from conftest import extract_id\n\n");
        for (EnumeratedChain chain : chains) {
            sb.append(renderOneTest(chain, spec, extractor));
            sb.append('\n');
        }
        return sb.toString();
    }

    private String renderOneTest(EnumeratedChain chain, Map<String, Object> spec, ApiCallExtractor extractor) {
        StringBuilder sb = new StringBuilder();
        List<ApiCallInfo> steps = chain.steps();
        String testName = chainTestName(chain.seedPost(), steps);
        sb.append("def ").append(testName).append("(api_client, auth_headers, base_url):\n");
        if (chain.unresolved()) {
            sb.append("    pytest.skip(\"Sequence chain has an unresolved path parameter; no producer POST found in the spec\")\n");
        }
        for (int i = 0; i < steps.size(); i++) {
            ApiCallInfo call = steps.get(i);
            int stepNum = i + 1;
            String pathExpr = pythonPathExpression(call.path());
            String verb = call.method().toLowerCase(Locale.ROOT);
            String queryParam = call.defaultQueryParams().isEmpty()
                    ? ""
                    : ", params=" + toPythonDictLiteral(call.defaultQueryParams());
            String bodyArg = buildPythonBodyArgument(call, spec, extractor);
            sb.append("    # Step ").append(stepNum).append(" — ").append(call.method())
                    .append(' ').append(call.path()).append(" (expected 2xx)\n");
            sb.append("    r = api_client.").append(verb).append("(")
                    .append(pathExpr)
                    .append(bodyArg)
                    .append(queryParam)
                    .append(", headers=auth_headers)\n");
            String pathInFString = escapePyInFString(call.path());
            if ("DELETE".equalsIgnoreCase(call.method())) {
                sb.append("    assert r.status_code in (200, 202, 204), ")
                        .append("f\"Step ").append(stepNum).append(' ').append(call.method())
                        .append(' ').append(pathInFString)
                        .append(": {r.status_code} {r.text}\"\n");
            } else {
                sb.append("    assert 200 <= r.status_code < 300, ")
                        .append("f\"Step ").append(stepNum).append(' ').append(call.method())
                        .append(' ').append(pathInFString)
                        .append(": {r.status_code} {r.text}\"\n");
            }
            if ("POST".equalsIgnoreCase(call.method())) {
                for (String paramName : newlyBoundByPost(steps, i)) {
                    String var = ApiCallExtractor.idVariableName(paramName);
                    sb.append("    ").append(var).append(" = extract_id(r, hint=\"")
                            .append(escapePy(paramName)).append("\")\n");
                    sb.append("    assert ").append(var).append(" is not None, ")
                            .append("f\"Step ").append(stepNum).append(" POST response had no extractable '")
                            .append(escapePy(paramName)).append("'\"\n");
                }
            }
        }
        return sb.toString();
    }

    /**
     * Path-parameter names that a POST at {@code postIdx} binds for later
     * steps — specifically, the first path-param segment that appears
     * directly under this POST's path in any subsequent consumer path, and
     * isn't already bound by an earlier step. Usually 0 or 1 names per POST;
     * multiple only occur when the spec uses inconsistent param names at
     * the same position (e.g. {@code /orders/{id}} and {@code /orders/{orderId}}).
     */
    static List<String> newlyBoundByPost(List<ApiCallInfo> steps, int postIdx) {
        ApiCallInfo post = steps.get(postIdx);
        String scanPrefix = post.path() + "/{";
        Set<String> alreadyBound = new LinkedHashSet<>();
        for (int k = 0; k <= postIdx; k++) {
            alreadyBound.addAll(steps.get(k).pathParamNames());
        }
        LinkedHashSet<String> produced = new LinkedHashSet<>();
        for (int j = postIdx + 1; j < steps.size(); j++) {
            String laterPath = steps.get(j).path();
            if (!laterPath.startsWith(scanPrefix)) {
                continue;
            }
            int start = scanPrefix.length();
            int end = laterPath.indexOf('}', start);
            if (end <= start) {
                continue;
            }
            String param = laterPath.substring(start, end);
            if (!alreadyBound.contains(param)) {
                produced.add(param);
            }
        }
        return List.copyOf(produced);
    }

    /**
     * Convert an OpenAPI path like {@code /orders/{orderId}/items/{itemId}}
     * to a Python f-string expression where each path parameter is bound
     * to a distinct snake_cased id variable produced by an earlier POST:
     * {@code f"{base_url}/orders/{order_id}/items/{item_id}"}.
     */
    static String pythonPathExpression(String path) {
        StringBuilder sb = new StringBuilder("f\"{base_url}");
        int i = 0;
        while (i < path.length()) {
            int open = path.indexOf('{', i);
            if (open < 0) {
                sb.append(escapePy(path.substring(i)));
                break;
            }
            sb.append(escapePy(path.substring(i, open)));
            int close = path.indexOf('}', open + 1);
            if (close < 0) {
                sb.append(escapePy(path.substring(open)));
                break;
            }
            String paramName = path.substring(open + 1, close);
            sb.append('{').append(ApiCallExtractor.idVariableName(paramName)).append('}');
            i = close + 1;
        }
        sb.append("\"");
        return sb.toString();
    }

    private String buildPythonBodyArgument(ApiCallInfo call, Map<String, Object> spec, ApiCallExtractor extractor) {
        if (!call.hasRequestBody()) {
            return "";
        }
        String json = extractor.buildRequestBodyForOperation(call.operation(), spec);
        if (json == null || json.isBlank() || "{}".equals(json.trim())) {
            json = IntegrationScenarioSupport.generateRequestBodyFromSchemaRaw(call.operation(), spec);
        }
        if (json == null || json.isBlank()) {
            return ", json={}";
        }
        return ", json=" + jsonToPythonLiteral(json);
    }

    /**
     * Converts a JSON object literal emitted by
     * {@link ApiCallExtractor#buildRequestBodyForOperation} into an
     * equivalent Python dict literal. The emitter only produces single-level
     * objects with scalar values, so the substitutions are bounded.
     */
    static String jsonToPythonLiteral(String json) {
        // Single-level objects only; JSON booleans/null → Python equivalents.
        return json
                .replace(": true", ": True")
                .replace(": false", ": False")
                .replace(": null", ": None");
    }

    static String toPythonDictLiteral(Map<String, String> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> e : map.entrySet()) {
            if (!first) {
                sb.append(", ");
            }
            first = false;
            sb.append('"').append(escapePy(e.getKey())).append("\": \"").append(escapePy(e.getValue())).append("\"");
        }
        sb.append("}");
        return sb.toString();
    }

    /** Build a deterministic, filesystem-safe test function name from a chain, rooted on the seed resource. */
    static String chainTestName(ApiCallInfo seed, List<ApiCallInfo> steps) {
        StringBuilder sb = new StringBuilder("test_");
        String seedResource = seed.resourceName();
        sb.append(sanitizeModuleName(seedResource));
        for (ApiCallInfo call : steps) {
            sb.append('_').append(call.method().toLowerCase(Locale.ROOT));
            if (!call.pathParamNames().isEmpty() && !call.isCreator()) {
                String tail = tailNonParamSegment(call.path());
                if (tail != null && !tail.equals(seedResource)) {
                    sb.append('_').append(sanitizeModuleName(tail));
                }
            }
        }
        return sb.toString();
    }

    /** Last non-templated segment of {@code path}; {@code null} if none past the templated one. */
    private static String tailNonParamSegment(String path) {
        String[] segments = path.split("/");
        for (int i = segments.length - 1; i >= 0; i--) {
            String seg = segments[i];
            if (!seg.isEmpty() && !seg.startsWith("{")) {
                return seg;
            }
        }
        return null;
    }

    static String sanitizeModuleName(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "resource";
        }
        String snake = raw.replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .replaceAll("[^a-zA-Z0-9]+", "_")
                .toLowerCase(Locale.ROOT);
        if (snake.startsWith("_")) {
            snake = snake.substring(1);
        }
        if (snake.endsWith("_")) {
            snake = snake.substring(0, snake.length() - 1);
        }
        return snake.isEmpty() ? "resource" : snake;
    }

    static String escapePy(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * Like {@link #escapePy} but also doubles {@code \{} and {@code \}} so
     * the result can be embedded as literal text inside a Python f-string
     * without the f-string's brace-expansion catching the OpenAPI path
     * parameters.
     */
    static String escapePyInFString(String s) {
        if (s == null) {
            return "";
        }
        return escapePy(s).replace("{", "{{").replace("}", "}}");
    }

    private static String propString(TestConfig config, String key, String defaultValue) {
        if (config == null || config.getAdditionalProperties() == null) {
            return defaultValue;
        }
        Object v = config.getAdditionalProperties().get(key);
        if (v == null) {
            return defaultValue;
        }
        String s = v.toString();
        return s.isEmpty() ? defaultValue : s;
    }

    private static Integer propInt(TestConfig config, String key, Integer defaultValue) {
        String s = propString(config, key, null);
        if (s == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static Boolean propBool(TestConfig config, String key, Boolean defaultValue) {
        String s = propString(config, key, null);
        if (s == null) {
            return defaultValue;
        }
        String lc = s.trim().toLowerCase(Locale.ROOT);
        if ("true".equals(lc) || "1".equals(lc) || "yes".equals(lc)) {
            return Boolean.TRUE;
        }
        if ("false".equals(lc) || "0".equals(lc) || "no".equals(lc)) {
            return Boolean.FALSE;
        }
        return defaultValue;
    }

    @Override
    public String getName() {
        return "Sequence Chain Test Generator";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getTestType() {
        return "sequence";
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
