package egain.oassdk.testgenerators.lifecycle;

import egain.oassdk.Util;
import egain.oassdk.config.TestConfig;
import egain.oassdk.core.Constants;
import egain.oassdk.core.exceptions.GenerationException;
import egain.oassdk.testgenerators.ConfigurableTestGenerator;
import egain.oassdk.testgenerators.TestGenerator;
import egain.oassdk.testgenerators.common.TestMavenSupport;
import egain.oassdk.testgenerators.common.TestOutputLayout;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates lifecycle flow harness that executes .flow files at test runtime.
 */
public class LifecycleTestGenerator implements TestGenerator, ConfigurableTestGenerator {
    private TestConfig config;

    @Override
    public void generate(Map<String, Object> spec, String outputDir, TestConfig config, String testFramework) throws GenerationException {
        this.config = config;
        try {
            String basePackage = resolvePackage(config);
            Path moduleDir = Paths.get(outputDir, "lifecycle");
            Path testDir = Paths.get(TestOutputLayout.testJavaDir(moduleDir.toString(), basePackage + ".flow"));
            Files.createDirectories(testDir);

            Map<String, OperationMeta> operations = extractOperations(spec);
            Files.writeString(testDir.resolve("OpenApiCatalog.java"), openApiCatalogSource(basePackage, operations), StandardCharsets.UTF_8);
            Files.writeString(testDir.resolve("FlowTestHarness.java"), harnessSource(basePackage), StandardCharsets.UTF_8);
            Files.writeString(moduleDir.resolve("run-lifecycle.sh"), runLifecycleScript(), StandardCharsets.UTF_8);
            Files.writeString(moduleDir.resolve("pom.xml"), pomSource(basePackage), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new GenerationException("Failed to generate lifecycle tests: " + e.getMessage(), e);
        }
    }

    private static Map<String, OperationMeta> extractOperations(Map<String, Object> spec) {
        Map<String, OperationMeta> out = new LinkedHashMap<>();
        Map<String, Object> paths = Util.asStringObjectMap(spec.get("paths"));
        if (paths == null) {
            return out;
        }
        for (Map.Entry<String, Object> pathEntry : paths.entrySet()) {
            String path = pathEntry.getKey();
            Map<String, Object> pathItem = Util.asStringObjectMap(pathEntry.getValue());
            if (pathItem == null) {
                continue;
            }
            for (String method : Constants.HTTP_METHODS) {
                Map<String, Object> op = Util.asStringObjectMap(pathItem.get(method));
                if (op == null || op.get("operationId") == null) {
                    continue;
                }
                String operationId = op.get("operationId").toString();
                boolean hasBody = op.containsKey("requestBody");
                out.put(operationId, new OperationMeta(operationId, method.toUpperCase(), path, hasBody));
            }
        }
        return out;
    }

    private static String openApiCatalogSource(String basePackage, Map<String, OperationMeta> operations) {
        StringBuilder entries = new StringBuilder();
        for (OperationMeta op : operations.values()) {
            entries.append("        OPERATIONS.put(\"").append(escape(op.operationId())).append("\", ")
                    .append("new OperationMeta(\"").append(escape(op.method())).append("\", \"")
                    .append(escape(op.path())).append("\", ").append(op.hasBody()).append("));\n");
        }
        return """
                package %s.flow;

                import java.util.LinkedHashMap;
                import java.util.Map;

                public final class OpenApiCatalog {
                    private static final Map<String, OperationMeta> OPERATIONS = new LinkedHashMap<>();

                    static {
                __ENTRIES__
                    }

                    private OpenApiCatalog() {
                    }

                    public static OperationMeta operation(String operationId) {
                        return OPERATIONS.get(operationId);
                    }

                    public record OperationMeta(String method, String path, boolean hasBody) {
                    }
                }
                """.formatted(basePackage).replace("__ENTRIES__", entries.toString());
    }

    private static String harnessSource(String basePackage) {
        return """
                package %s.flow;

                import egain.oassdk.flow.FlowAst;
                import egain.oassdk.flow.FlowDiscovery;
                import egain.oassdk.flow.FlowInterpreter;
                import egain.oassdk.flow.FlowParser;
                import io.restassured.response.Response;
                import io.restassured.specification.RequestSpecification;
                import org.junit.jupiter.api.BeforeAll;
                import org.junit.jupiter.params.ParameterizedTest;
                import org.junit.jupiter.params.provider.MethodSource;
                import %s.support.RequestBodyFactory;
                import %s.support.TestClient;
                import %s.support.TestEnv;

                import java.nio.file.Files;
                import java.nio.file.Path;
                import java.time.Duration;
                import java.util.List;
                import java.util.Map;
                import java.util.Properties;

                public class FlowTestHarness {

                    @BeforeAll
                    static void initClient() {
                        TestClient.configureRestAssured();
                    }

                    static List<Path> flowsToRun() throws Exception {
                        Properties props = new Properties();
                        props.setProperty("test.flows.dir", TestEnv.get("test.flows.dir", "src/test/flows"));
                        props.setProperty("test.flows.manifest", TestEnv.get("test.flows.manifest", ""));
                        return FlowDiscovery.discover(props, TestEnv.includeOperations());
                    }

                    @ParameterizedTest
                    @MethodSource("flowsToRun")
                    void runFlow(Path flowFile) throws Exception {
                        String text = Files.readString(flowFile);
                        FlowParser parser = new FlowParser();
                        FlowAst.FlowDefinition flow = parser.parse(text);
                        FlowInterpreter interpreter = new FlowInterpreter();
                        interpreter.execute(flow, new HarnessRuntime());
                    }

                    private static final class HarnessRuntime implements FlowInterpreter.Runtime {
                        @Override
                        public Client client() {
                            return new RestClient();
                        }

                        @Override
                        public BodyFactory bodyFactory() {
                            return new FactoryBridge();
                        }

                        @Override
                        public Duration defaultPollTimeout() {
                            return Duration.ofSeconds(Long.parseLong(TestEnv.get("test.flows.poll.timeout.seconds", "60")));
                        }
                    }

                    private static final class FactoryBridge implements FlowInterpreter.Runtime.BodyFactory {
                        @Override
                        public boolean hasBody(String operationId) {
                            return OpenApiCatalog.operation(operationId) != null && OpenApiCatalog.operation(operationId).hasBody();
                        }

                        @Override
                        public String valid(String operationId) {
                            return RequestBodyFactory.forOperation(operationId).valid();
                        }

                        @Override
                        public String withViolation(String operationId, FlowAst.PoisonClause poison) {
                            return RequestBodyFactory.forOperation(operationId)
                                    .withViolation(poison.bodyPath(), poison.kind().name(), poison.valueLiteral())
                                    .build();
                        }
                    }

                    private static final class RestClient implements FlowInterpreter.Runtime.Client {
                        @Override
                        public FlowInterpreter.Runtime.Response call(String operationId, Map<String, String> pathBinds,
                                                                     Map<String, String> headerBinds, String body) {
                            OpenApiCatalog.OperationMeta meta = OpenApiCatalog.operation(operationId);
                            if (meta == null) {
                                throw new IllegalArgumentException("Unknown operation: " + operationId);
                            }
                            RequestSpecification spec = TestClient.givenAuth().accept("application/json");
                            for (Map.Entry<String, String> e : pathBinds.entrySet()) {
                                spec = spec.pathParam(e.getKey(), e.getValue());
                            }
                            for (Map.Entry<String, String> e : headerBinds.entrySet()) {
                                spec = spec.header(e.getKey(), e.getValue());
                            }
                            if (body != null && !body.isBlank() && meta.hasBody()) {
                                spec = spec.contentType("application/json").body(body);
                            }
                            Response response = switch (meta.method()) {
                                case "POST" -> spec.post(meta.path());
                                case "PUT" -> spec.put(meta.path());
                                case "PATCH" -> spec.patch(meta.path());
                                case "DELETE" -> spec.delete(meta.path());
                                default -> spec.get(meta.path());
                            };
                            return new RestResponse(response);
                        }

                        @Override
                        public FlowInterpreter.Runtime.Response poll(String targetUrl, String expectedStatus, Duration timeout) {
                            long deadlineNanos = System.nanoTime() + timeout.toNanos();
                            int sleepMs = Integer.parseInt(TestEnv.get("test.flows.poll.interval.seconds", "2")) * 1000;
                            while (System.nanoTime() < deadlineNanos) {
                                Response response = TestClient.givenAuth().get(targetUrl);
                                if (Integer.toString(response.getStatusCode()).equals(expectedStatus)) {
                                    return new RestResponse(response);
                                }
                                try {
                                    Thread.sleep(sleepMs);
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                    break;
                                }
                            }
                            throw new IllegalStateException("poll timeout waiting for status " + expectedStatus);
                        }
                    }

                    private static final class RestResponse implements FlowInterpreter.Runtime.Response {
                        private final Response response;

                        private RestResponse(Response response) {
                            this.response = response;
                        }

                        @Override
                        public int statusCode() {
                            return response.getStatusCode();
                        }

                        @Override
                        public String header(String name) {
                            return response.getHeader(name);
                        }

                        @Override
                        public String jsonPath(String jsonPath) {
                            try {
                                return response.jsonPath().getString(jsonPath.replace("$.", ""));
                            } catch (Exception e) {
                                return null;
                            }
                        }
                    }
                }
                """.formatted(basePackage, basePackage, basePackage, basePackage);
    }

    private static String pomSource(String basePackage) {
        return TestMavenSupport.pomHeader("api-lifecycle-tests", basePackage)
                + TestMavenSupport.dependenciesBlock(
                TestMavenSupport.junitDependency()
                        + TestMavenSupport.restAssuredDependencies()
                        + """
                        <dependency>
                            <groupId>com.egain</groupId>
                            <artifactId>oas-sdk-java</artifactId>
                            <version>2.30-SNAPSHOT</version>
                            <scope>test</scope>
                        </dependency>
                """)
                + TestMavenSupport.buildSectionWithTestSupport();
    }

    private static String runLifecycleScript() {
        return """
                #!/usr/bin/env bash
                set -euo pipefail
                ROOT="$(cd "$(dirname "$0")" && pwd)"
                export TEST_ENV_FILE="${TEST_ENV_FILE:-$ROOT/../test-env.properties}"
                mvn -q test
                """;
    }

    private static String resolvePackage(TestConfig config) {
        if (config != null && config.getAdditionalProperties() != null) {
            Object pkg = config.getAdditionalProperties().get("packageName");
            if (pkg != null && !pkg.toString().isBlank()) {
                return pkg.toString().trim();
            }
        }
        return "com.example.api";
    }

    private static String escape(String v) {
        return v.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    @Override
    public String getName() {
        return "Lifecycle Test Generator";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getTestType() {
        return "lifecycle";
    }

    @Override
    public void setConfig(TestConfig config) {
        this.config = config;
    }

    @Override
    public TestConfig getConfig() {
        return config;
    }

    private record OperationMeta(String operationId, String method, String path, boolean hasBody) {
    }
}
