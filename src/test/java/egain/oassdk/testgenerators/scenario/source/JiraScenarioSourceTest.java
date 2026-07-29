package egain.oassdk.testgenerators.scenario.source;

import egain.oassdk.config.AiScenarioConfig;
import egain.oassdk.core.exceptions.OASSDKException;
import egain.oassdk.testgenerators.scenario.ScenarioDocument;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class JiraScenarioSourceTest {

    @Test
    void parseIssues_flattensAdfAndLabels() throws Exception {
        JiraScenarioSource source = new JiraScenarioSource();
        String body = """
                {
                  "issues": [
                    {
                      "id": "10001",
                      "key": "EGS-42",
                      "fields": {
                        "summary": "Create article",
                        "description": {
                          "type": "doc",
                          "content": [
                            {
                              "type": "paragraph",
                              "content": [{"type": "text", "text": "As a user I can create an article."}]
                            }
                          ]
                        },
                        "labels": ["api-scenario", "kb"]
                      }
                    }
                  ]
                }
                """;
        List<ScenarioDocument> docs = source.parseIssues(body);
        assertEquals(1, docs.size());
        assertEquals("EGS-42", docs.get(0).getKey());
        assertEquals("Create article", docs.get(0).getTitle());
        assertTrue(docs.get(0).getDescription().contains("create an article"));
        assertEquals(List.of("api-scenario", "kb"), docs.get(0).getLabels());
    }

    @Test
    void fetch_usesHttpExchangeAndAuth() throws Exception {
        AtomicReference<HttpRequest> captured = new AtomicReference<>();
        JiraScenarioSource.HttpExchange exchange = request -> {
            captured.set(request);
            return new FixedHttpResponse(200, """
                    {"issues":[{"id":"1","key":"DEMO-1","fields":{"summary":"S","description":"D","labels":[]}}]}
                    """);
        };

        Map<String, String> env = Map.of(
                "JIRA_BASE_URL", "https://jira.example.com",
                "JIRA_USER", "user@example.com",
                "JIRA_API_TOKEN", "token-value"
        );
        JiraScenarioSource source = new JiraScenarioSource(exchange, env::get);

        AiScenarioConfig.JiraConfig jira = new AiScenarioConfig.JiraConfig();
        jira.setJql("project = DEMO");
        jira.setMaxIssues(10);

        List<ScenarioDocument> docs = source.fetch(new ScenarioSourceRequest(jira, null));
        assertEquals(1, docs.size());
        assertEquals("DEMO-1", docs.get(0).getKey());
        assertNotNull(captured.get());
        assertTrue(captured.get().uri().toString().contains("/rest/api/2/search"));
        assertTrue(captured.get().headers().firstValue("Authorization").orElse("").startsWith("Basic "));
    }

    @Test
    void fetch_missingToken_throws() {
        JiraScenarioSource source = new JiraScenarioSource(
                request -> new FixedHttpResponse(200, "{}"),
                k -> null);
        AiScenarioConfig.JiraConfig jira = new AiScenarioConfig.JiraConfig();
        jira.setBaseUrl("https://jira.example.com");
        jira.setJql("project = DEMO");
        OASSDKException ex = assertThrows(OASSDKException.class,
                () -> source.fetch(new ScenarioSourceRequest(jira, null)));
        assertTrue(ex.getMessage().toLowerCase().contains("token"));
    }

    private static final class FixedHttpResponse implements HttpResponse<String> {
        private final int status;
        private final String body;

        private FixedHttpResponse(int status, String body) {
            this.status = status;
            this.body = body;
        }

        @Override
        public int statusCode() {
            return status;
        }

        @Override
        public HttpRequest request() {
            return null;
        }

        @Override
        public Optional<HttpResponse<String>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public HttpHeaders headers() {
            return HttpHeaders.of(Map.of(), (a, b) -> true);
        }

        @Override
        public String body() {
            return body;
        }

        @Override
        public Optional<javax.net.ssl.SSLSession> sslSession() {
            return Optional.empty();
        }

        @Override
        public java.net.URI uri() {
            return java.net.URI.create("https://jira.example.com");
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }
    }
}
