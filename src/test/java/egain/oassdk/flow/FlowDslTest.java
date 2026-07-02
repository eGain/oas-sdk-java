package egain.oassdk.flow;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowDslTest {

    @Test
    void parserReadsArticleCrudFlow() throws Exception {
        String flowText = Files.readString(Path.of("docs/examples/article-crud.flow"));
        FlowAst.FlowDefinition flow = new FlowParser().parse(flowText);

        assertEquals("article-crud", flow.name());
        assertEquals(5, flow.steps().size());
        assertEquals("createArticle", ((FlowAst.CallStep) flow.steps().get(0)).operationId());
    }

    @Test
    void validatorAcceptsExamplesAgainstCatalog() throws Exception {
        Map<String, Object> spec = specWithOperations();
        OpenApiOperationCatalog catalog = OpenApiOperationCatalog.fromSpec(spec);
        FlowValidator validator = new FlowValidator();

        for (String name : new String[]{"article-crud.flow", "article-create-bad-folder.flow", "folder-delete-async.flow"}) {
            String flowText = Files.readString(Path.of("docs/examples/" + name));
            FlowAst.FlowDefinition flow = new FlowParser().parse(flowText);
            validator.validate(flow, catalog);
        }
    }

    @Test
    void interpreterExecutesPoisonAndLifecycleSteps() throws Exception {
        String flowText = Files.readString(Path.of("docs/examples/article-create-bad-folder.flow"));
        FlowAst.FlowDefinition flow = new FlowParser().parse(flowText);
        RecordingRuntime runtime = new RecordingRuntime();

        new FlowInterpreter().execute(flow, runtime);

        assertEquals(3, runtime.calls);
        assertTrue(runtime.lastBody.contains("POISON"));
    }

    private static Map<String, Object> specWithOperations() {
        Map<String, Object> paths = new HashMap<>();
        paths.put("/articles", Map.of("post", Map.of(
                "operationId", "createArticle",
                "requestBody", Map.of("content", Map.of("application/json", Map.of())),
                "responses", Map.of("201", Map.of("description", "Created")))));
        paths.put("/articles/{articleID}", Map.of(
                "patch", Map.of(
                        "operationId", "editArticle",
                        "parameters", java.util.List.of(Map.of("name", "articleID", "in", "path")),
                        "requestBody", Map.of("content", Map.of("application/json", Map.of())),
                        "responses", Map.of("200", Map.of("description", "OK"))),
                "delete", Map.of(
                        "operationId", "deleteArticle",
                        "parameters", java.util.List.of(Map.of("name", "articleID", "in", "path")),
                        "responses", Map.of("204", Map.of("description", "No Content")))));
        paths.put("/folders/{folderID}", Map.of(
                "delete", Map.of(
                        "operationId", "deleteFolder",
                        "parameters", java.util.List.of(Map.of("name", "folderID", "in", "path")),
                        "responses", Map.of("202", Map.of("description", "Accepted")))));
        return Map.of("paths", paths);
    }

    private static final class RecordingRuntime implements FlowInterpreter.Runtime {
        private int calls;
        private String lastBody = "";

        @Override
        public Client client() {
            return new Client() {
                @Override
                public Response call(String operationId, Map<String, String> pathBinds, Map<String, String> headerBinds, String body) {
                    calls++;
                    if (body != null) {
                        lastBody = body;
                    }
                    int status = switch (operationId) {
                        case "createArticle" -> body != null && body.contains("TOO_LONG") ? 422 : 400;
                        default -> 200;
                    };
                    Map<String, String> headers = Map.of("Location", "https://example.com/articles/123", "ETag", "abc");
                    return new SimpleResponse(status, headers);
                }

                @Override
                public Response poll(String targetUrl, String expectedStatus, Duration timeout) {
                    return new SimpleResponse(Integer.parseInt(expectedStatus), Map.of("Location", targetUrl));
                }
            };
        }

        @Override
        public BodyFactory bodyFactory() {
            return new BodyFactory() {
                @Override
                public boolean hasBody(String operationId) {
                    return true;
                }

                @Override
                public String valid(String operationId) {
                    return "{\"operation\":\"" + operationId + "\"}";
                }

                @Override
                public String withViolation(String operationId, FlowAst.PoisonClause poison) {
                    return "{\"operation\":\"" + operationId + "\",\"POISON\":\"" + poison.kind().name() + "\"}";
                }
            };
        }

        @Override
        public Duration defaultPollTimeout() {
            return Duration.ofSeconds(2);
        }
    }

    private record SimpleResponse(int statusCode, Map<String, String> headers) implements FlowInterpreter.Runtime.Response {
        @Override
        public String header(String name) {
            return headers.get(name);
        }

        @Override
        public String jsonPath(String jsonPath) {
            return null;
        }
    }
}
