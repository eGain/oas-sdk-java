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
import egain.oassdk.testgenerators.common.TestCodegenSupport;
import egain.oassdk.testgenerators.common.TestMavenSupport;
import egain.oassdk.testgenerators.common.TestOutputLayout;
import egain.oassdk.testgenerators.common.TestSpecUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Emits JUnit 5 workflow chain tests with shared context, valid bodies, and cleanup.
 */
public class JavaSequenceChainTestGenerator implements TestGenerator, ConfigurableTestGenerator {

    private TestConfig config;

    @Override
    public void generate(Map<String, Object> spec, String outputDir, TestConfig config, String testFramework)
            throws GenerationException {
        this.config = config;
        try {
            String basePackage = resolvePackage(config);
            Path moduleDir = Paths.get(outputDir, "sequence-java");
            Path testDir = Paths.get(TestOutputLayout.testJavaDir(moduleDir.toString(), basePackage));
            Files.createDirectories(testDir);

            ApiCallExtractor extractor = new ApiCallExtractor();
            List<ApiCallInfo> calls = extractor.extract(spec);
            ChainEnumerator enumerator = new ChainEnumerator(readChainConfig(config));
            List<EnumeratedChain> chains = enumerator.enumerate(calls);

            Map<String, List<EnumeratedChain>> byResource = groupByResource(chains);
            int order = 1;
            for (Map.Entry<String, List<EnumeratedChain>> e : byResource.entrySet()) {
                String className = capitalize(e.getKey()) + "WorkflowTest";
                String content = renderWorkflowClass(basePackage, className, e.getValue(), spec, extractor, order);
                Files.writeString(testDir.resolve(className + ".java"), content, StandardCharsets.UTF_8);
                order += 100;
            }

            String pom = TestMavenSupport.pomHeader("api-sequence-java-tests", basePackage)
                    + TestMavenSupport.junitDependency()
                    + TestMavenSupport.buildSectionWithTestSupport();
            Files.writeString(moduleDir.resolve("pom.xml"), pom, StandardCharsets.UTF_8);

        } catch (IOException ex) {
            throw new GenerationException("Failed to generate Java sequence tests: " + ex.getMessage(), ex);
        }
    }

    private String renderWorkflowClass(String basePackage, String className, List<EnumeratedChain> chains,
                                       Map<String, Object> spec, ApiCallExtractor extractor, int startOrder) {
        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(basePackage).append(";\n\n");
        sb.append("import org.junit.jupiter.api.*;\n");
        sb.append("import static org.junit.jupiter.api.Assertions.*;\n");
        sb.append("import java.net.URI;\n");
        sb.append("import java.net.http.*;\n");
        sb.append("import java.time.Duration;\n");
        sb.append("import java.util.*;\n");
        sb.append(TestCodegenSupport.supportImport(basePackage));
        sb.append("\n");
        sb.append("@TestMethodOrder(MethodOrderer.OrderAnnotation.class)\n");
        sb.append("@TestInstance(TestInstance.Lifecycle.PER_CLASS)\n");
        sb.append("public class ").append(className).append(" {\n\n");
        sb.append("    private static final Duration TIMEOUT = Duration.ofSeconds(30);\n");
        sb.append("    private HttpClient client;\n");
        sb.append("    private final Map<String, String> vars = new HashMap<>();\n\n");
        sb.append("    @BeforeAll\n");
        sb.append("    void init() { client = TestHttp.client(); }\n\n");
        sb.append("    @AfterEach\n");
        sb.append("    void cleanup() throws Exception {\n");
        sb.append("        for (String id : TestContext.createdIds()) {\n");
        sb.append("            deleteById(id);\n");
        sb.append("        }\n");
        sb.append("        TestContext.clearCreatedIds();\n");
        sb.append("    }\n\n");

        int order = startOrder;
        for (EnumeratedChain chain : chains) {
            if (chain.unresolved()) {
                continue;
            }
            sb.append("    @Test\n    @Order(").append(order++).append(")\n");
            sb.append("    void ").append(SequenceChainTestGenerator.chainTestName(chain.seedPost(), chain.steps()))
                    .append("() throws Exception {\n");
            renderChainSteps(sb, chain, spec, extractor);
            sb.append("    }\n\n");
        }
        sb.append("    }\n\n");
        sb.append("    private String extractId(HttpResponse<String> r) {\n");
        sb.append("        if (r == null) return null;\n");
        sb.append("        var loc = r.headers().firstValue(\"Location\").orElse(null);\n");
        sb.append("        if (loc != null) { int i = loc.lastIndexOf('/'); if (i >= 0) return loc.substring(i + 1); }\n");
        sb.append("        String body = r.body();\n");
        sb.append("        if (body != null && body.contains(\"\\\"id\\\"\")) {\n");
        sb.append("            int i = body.indexOf(\"\\\"id\\\":\\\"\"); if (i >= 0) { int s = i + 6; int e = body.indexOf('\"', s); if (e > s) return body.substring(s, e); }\n");
        sb.append("        }\n");
        sb.append("        return null;\n");
        sb.append("    }\n\n");
        sb.append("    private void deleteById(String id) throws Exception {\n");
        sb.append("        String path = \"/folders/{folderID}\".replace(\"{folderID}\", id);\n");
        sb.append("        HttpRequest.Builder b = HttpRequest.newBuilder().uri(URI.create(TestEnv.baseUrl() + path))\n");
        sb.append("            .timeout(TIMEOUT).header(\"Accept-Language\", TestEnv.acceptLanguage()).DELETE();\n");
        sb.append("        String tok = TestAuth.rawToken(); if (!tok.isEmpty()) b.header(\"Authorization\", \"Bearer \" + tok);\n");
        sb.append("        HttpResponse<String> r = client.send(b.build(), HttpResponse.BodyHandlers.ofString());\n");
        sb.append("        if (r.statusCode() == 202) { Thread.sleep(2000); }\n");
        sb.append("    }\n");
        sb.append("}\n");
        return sb.toString();
    }

    private void renderChainSteps(StringBuilder sb, EnumeratedChain chain, Map<String, Object> spec,
                                  ApiCallExtractor extractor) {
        List<ApiCallInfo> steps = chain.steps();
        for (int i = 0; i < steps.size(); i++) {
            ApiCallInfo call = steps.get(i);
            String path = resolvePathExpr(call.path());
            String body = extractor.buildRequestBodyForOperation(call.operation(), spec);
            if (body == null || body.isBlank()) {
                body = IntegrationScenarioSupport.generateRequestBodyFromSchemaRaw(call.operation(), spec);
            }
            if (body == null) {
                body = "{}";
            }
            sb.append("        {\n");
            sb.append("            HttpRequest.Builder b = HttpRequest.newBuilder()\n");
            sb.append("                .uri(URI.create(TestEnv.baseUrl() + ").append(path).append("))\n");
            sb.append("                .timeout(TIMEOUT)\n");
            sb.append("                .header(\"Accept\", \"application/json\")\n");
            sb.append("                .header(\"Accept-Language\", TestEnv.acceptLanguage());\n");
            sb.append("            String tok = TestAuth.rawToken();\n");
            sb.append("            if (!tok.isEmpty()) b.header(\"Authorization\", \"Bearer \" + tok);\n");
            String method = call.method().toUpperCase(Locale.ROOT);
            if ("GET".equals(method)) {
                sb.append("            b.GET();\n");
            } else if ("DELETE".equals(method)) {
                sb.append("            b.DELETE();\n");
            } else if ("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method)) {
                sb.append("            b.header(\"Content-Type\", \"application/json\");\n");
                sb.append("            b.method(\"").append(method).append("\", HttpRequest.BodyPublishers.ofString(\"")
                        .append(IntegrationScenarioSupport.escapeJavaString(body)).append("\"));\n");
            }
            sb.append("            HttpResponse<String> r = client.send(b.build(), HttpResponse.BodyHandlers.ofString());\n");
            if ("DELETE".equals(method)) {
                sb.append("            assertTrue(r.statusCode() == 200 || r.statusCode() == 202 || r.statusCode() == 204);\n");
            } else {
                sb.append("            assertTrue(r.statusCode() >= 200 && r.statusCode() < 300);\n");
            }
            if ("POST".equals(method)) {
                sb.append("            String id = extractId(r);\n");
                sb.append("            if (id != null) { TestContext.trackCreatedId(id); vars.put(\"id\", id); }\n");
            }
            sb.append("        }\n");
        }
    }

    private String resolvePathExpr(String pathTemplate) {
        if (!pathTemplate.contains("{")) {
            return "\"" + pathTemplate + "\"";
        }
        return "\"" + pathTemplate + "\".replace(\"{folderID}\", vars.getOrDefault(\"folderID\", TestEnv.folderId()))";
    }

    private static Map<String, List<EnumeratedChain>> groupByResource(List<EnumeratedChain> chains) {
        Map<String, List<EnumeratedChain>> map = new TreeMap<>();
        for (EnumeratedChain c : chains) {
            if (!c.steps().isEmpty()) {
                map.computeIfAbsent(c.seedPost().resourceName(), k -> new java.util.ArrayList<>()).add(c);
            }
        }
        return map;
    }

    private ChainConfig readChainConfig(TestConfig tc) {
        ChainConfig.Builder b = ChainConfig.builder();
        if (tc != null && tc.getAdditionalProperties() != null) {
            Object max = tc.getAdditionalProperties().get("sequence.maxChainLength");
            if (max instanceof Number n) {
                b.maxChainLength(n.intValue());
            }
        }
        return b.build();
    }

    private static String resolvePackage(TestConfig config) {
        if (config != null && config.getAdditionalProperties() != null) {
            Object pkg = config.getAdditionalProperties().get("packageName");
            if (pkg != null) {
                return pkg.toString();
            }
        }
        return "com.example.api";
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) {
            return "Resource";
        }
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    @Override
    public String getName() {
        return "Java Sequence Chain Test Generator";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getTestType() {
        return "sequence-java";
    }

    @Override
    public void setConfig(TestConfig config) {
        this.config = config;
    }

    @Override
    public TestConfig getConfig() {
        return config;
    }
}
