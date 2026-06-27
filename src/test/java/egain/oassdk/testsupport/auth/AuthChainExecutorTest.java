package egain.oassdk.testsupport.auth;

import org.junit.jupiter.api.Test;

import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

class AuthChainExecutorTest {

    @Test
    void substitute_resolvesAuthAndChainVars() {
        Map<String, String> vars = Map.of("session", "abc-123");
        MapPropertySource props = new MapPropertySource(Map.of(
                "auth.username", "user1",
                "auth.login.base", "https://host/v20"
        ));

        String url = AuthChainExecutor.substitute(
                "${auth.login.base}/login?user=${auth.username}&sid=${session}", props, vars);

        assertThat(url).isEqualTo("https://host/v20/login?user=user1&sid=abc-123");
    }

    @Test
    void extractJsonString_readsTopLevelField() {
        String json = "{\"access_token\":\"eyJhbG\",\"token_type\":\"Bearer\"}";
        assertThat(AuthChainExecutor.extractJsonString(json, "access_token")).isEqualTo("eyJhbG");
        assertThat(AuthChainExecutor.extractJsonString(json, "missing")).isNull();
    }

    @Test
    void chainHeaders_discoversPrefixedHeaderKeys() {
        MapPropertySource props = new MapPropertySource(Map.of(
                "auth.chain.1.header.Content-Type", "application/json",
                "auth.chain.1.header.X-egain-session", "${session}",
                "auth.chain.2.url", "https://example/token"
        ));

        Map<String, String> headers = AuthChainExecutor.chainHeaders(props, 1);
        assertThat(headers).containsEntry("Content-Type", "application/json");
        assertThat(headers).containsEntry("X-egain-session", "${session}");
    }

    @Test
    void run_executesChainAndReturnsFinalVar() throws Exception {
        RecordingSender sender = new RecordingSender();
        sender.enqueue(200, Map.of("x-egain-session", List.of("sess-99")), "");
        sender.enqueue(200, Map.of(), "{\"access_token\":\"tok-xyz\",\"token_type\":\"Bearer\"}");

        String token = AuthChainExecutor.run(chainProps(), sender);

        assertThat(token).isEqualTo("tok-xyz");
        assertThat(sender.requests).hasSize(2);
        assertThat(sender.requests.get(0).uri().toString())
                .contains("/authentication/user/login?forceLogin=yes");
        assertThat(sender.requests.get(1).headers().firstValue("X-egain-session"))
                .contains("sess-99");
    }

    @Test
    void resolveVar_usesChainSaveName() {
        MapPropertySource props = new MapPropertySource(Map.of());
        Map<String, String> vars = Map.of("session", "saved-session");
        assertThat(AuthChainExecutor.resolveVar("session", props, vars)).isEqualTo("saved-session");
        assertThat(AuthChainExecutor.resolveVar("auth.username", props, vars)).isEqualTo("");
    }

    private static MapPropertySource chainProps() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("auth.login.base", "https://eg5843ain.ezdev.net/system/ws/v20");
        m.put("auth.username", "riyaz.3");
        m.put("auth.password", "secret");
        m.put("auth.chain.1.url", "${auth.login.base}/authentication/user/login?forceLogin=yes");
        m.put("auth.chain.1.method", "POST");
        m.put("auth.chain.1.header.Content-Type", "application/json");
        m.put("auth.chain.1.body", "{\"userName\":\"${auth.username}\",\"password\":\"${auth.password}\"}");
        m.put("auth.chain.1.extract.header", "x-egain-session");
        m.put("auth.chain.1.save", "session");
        m.put("auth.chain.2.url", "${auth.login.base}/authentication/user/advisor/oauth2/token");
        m.put("auth.chain.2.method", "GET");
        m.put("auth.chain.2.header.X-egain-session", "${session}");
        m.put("auth.chain.2.extract.json", "access_token");
        m.put("auth.chain.final", "access_token");
        return new MapPropertySource(m);
    }

    private static final class MapPropertySource implements AuthChainExecutor.PropertySource {
        private final Map<String, String> map;

        MapPropertySource(Map<String, String> map) {
            this.map = new HashMap<>(map);
        }

        @Override
        public String get(String key, String defaultValue) {
            return map.getOrDefault(key, defaultValue);
        }

        @Override
        public Iterable<String> keysWithPrefix(String prefix) {
            TreeSet<String> keys = new TreeSet<>();
            for (String k : map.keySet()) {
                if (k.startsWith(prefix)) {
                    keys.add(k);
                }
            }
            return keys;
        }
    }

    private static final class RecordingSender implements AuthChainExecutor.HttpSender {
        private final java.util.ArrayList<StubResponse> responses = new java.util.ArrayList<>();
        final java.util.ArrayList<HttpRequest> requests = new java.util.ArrayList<>();

        void enqueue(int status, Map<String, List<String>> headers, String body) {
            responses.add(new StubResponse(status, headers, body));
        }

        @Override
        public HttpResponse<String> send(HttpRequest request) {
            requests.add(request);
            if (responses.isEmpty()) {
                throw new IllegalStateException("no stub response");
            }
            StubResponse r = responses.removeFirst();
            return new SimpleResponse(request, r.status, r.headers, r.body);
        }

        private record StubResponse(int status, Map<String, List<String>> headers, String body) {
        }

        private static final class SimpleResponse implements HttpResponse<String> {
            private final HttpRequest request;
            private final int status;
            private final HttpHeaders headers;
            private final String body;

            SimpleResponse(HttpRequest request, int status, Map<String, List<String>> headerMap, String body) {
                this.request = request;
                this.status = status;
                this.headers = HttpHeaders.of(headerMap, (a, b) -> true);
                this.body = body;
            }

            @Override
            public int statusCode() {
                return status;
            }

            @Override
            public HttpRequest request() {
                return request;
            }

            @Override
            public java.util.Optional<HttpResponse<String>> previousResponse() {
                return java.util.Optional.empty();
            }

            @Override
            public HttpHeaders headers() {
                return headers;
            }

            @Override
            public String body() {
                return body;
            }

            @Override
            public java.util.Optional<javax.net.ssl.SSLSession> sslSession() {
                return java.util.Optional.empty();
            }

            @Override
            public java.net.URI uri() {
                return request.uri();
            }

            @Override
            public java.net.http.HttpClient.Version version() {
                return java.net.http.HttpClient.Version.HTTP_1_1;
            }
        }
    }
}
