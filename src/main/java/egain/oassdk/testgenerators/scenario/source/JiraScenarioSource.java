package egain.oassdk.testgenerators.scenario.source;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import egain.oassdk.config.AiScenarioConfig;
import egain.oassdk.core.exceptions.OASSDKException;
import egain.oassdk.testgenerators.scenario.ScenarioDocument;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Fetches scenarios from Jira REST API search ({@code /rest/api/2/search}).
 */
public final class JiraScenarioSource implements ScenarioSource {

    /**
     * Testable HTTP transport for Jira REST calls.
     */
    @FunctionalInterface
    public interface HttpExchange {
        HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException;
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpExchange httpExchange;
    private final Function<String, String> envLookup;

    public JiraScenarioSource() {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
        this.httpExchange = request -> client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        this.envLookup = System::getenv;
    }

    public JiraScenarioSource(HttpExchange httpExchange, Function<String, String> envLookup) {
        this.httpExchange = Objects.requireNonNull(httpExchange, "httpExchange");
        this.envLookup = envLookup != null ? envLookup : System::getenv;
    }

    @Override
    public List<ScenarioDocument> fetch(ScenarioSourceRequest request) throws OASSDKException {
        Objects.requireNonNull(request, "request");
        AiScenarioConfig.JiraConfig jira = request.getJira();
        if (jira == null) {
            throw new OASSDKException("Jira configuration is required for scenario source type 'jira'");
        }

        String baseUrl = firstNonBlank(jira.getBaseUrl(), env(jira.getBaseUrlEnv()));
        String user = firstNonBlank(jira.getUser(), env(jira.getUserEnv()));
        String token = firstNonBlank(jira.getToken(), env(jira.getTokenEnv()));
        String jql = request.resolveJql();

        if (baseUrl == null || baseUrl.isBlank()) {
            throw new OASSDKException("Jira base URL is not configured (set "
                    + jira.getBaseUrlEnv() + " or jira.baseUrl)");
        }
        if (token == null || token.isBlank()) {
            throw new OASSDKException("Jira API token is not configured (set "
                    + jira.getTokenEnv() + " or jira.token)");
        }
        if (jql == null || jql.isBlank()) {
            throw new OASSDKException("Jira JQL is required to fetch scenarios");
        }

        String normalizedBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        int max = Math.max(1, jira.getMaxIssues());
        List<String> fields = jira.getFields();
        String fieldParam = fields == null || fields.isEmpty()
                ? "summary,description,labels"
                : String.join(",", fields);

        String url = normalizedBase + "/rest/api/2/search"
                + "?jql=" + URLEncoder.encode(jql, StandardCharsets.UTF_8)
                + "&maxResults=" + max
                + "&fields=" + URLEncoder.encode(fieldParam, StandardCharsets.UTF_8);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .header("Accept", "application/json")
                .GET();

        if (user != null && !user.isBlank()) {
            String basic = Base64.getEncoder().encodeToString(
                    (user + ":" + token).getBytes(StandardCharsets.UTF_8));
            builder.header("Authorization", "Basic " + basic);
        } else {
            builder.header("Authorization", "Bearer " + token);
        }

        HttpResponse<String> response;
        try {
            response = httpExchange.send(builder.build());
        } catch (IOException e) {
            throw new OASSDKException("Failed to call Jira search API: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OASSDKException("Jira search interrupted", e);
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new OASSDKException("Jira search failed with HTTP " + response.statusCode()
                    + ": " + truncate(response.body(), 500));
        }

        try {
            return parseIssues(response.body());
        } catch (IOException e) {
            throw new OASSDKException("Failed to parse Jira search response: " + e.getMessage(), e);
        }
    }

    List<ScenarioDocument> parseIssues(String body) throws IOException {
        JsonNode root = MAPPER.readTree(body);
        JsonNode issues = root.get("issues");
        List<ScenarioDocument> docs = new ArrayList<>();
        if (issues == null || !issues.isArray()) {
            return docs;
        }
        for (JsonNode issue : issues) {
            String id = text(issue, "id");
            String key = text(issue, "key");
            JsonNode fields = issue.get("fields");
            String summary = fields != null ? text(fields, "summary") : null;
            String description = fields != null ? extractDescription(fields.get("description")) : null;
            List<String> labels = new ArrayList<>();
            if (fields != null && fields.has("labels") && fields.get("labels").isArray()) {
                for (JsonNode label : fields.get("labels")) {
                    if (label != null && !label.isNull()) {
                        labels.add(label.asText());
                    }
                }
            }
            Map<String, Object> raw = new LinkedHashMap<>();
            if (fields != null && fields.isObject()) {
                Iterator<Map.Entry<String, JsonNode>> it = fields.fields();
                while (it.hasNext()) {
                    Map.Entry<String, JsonNode> entry = it.next();
                    raw.put(entry.getKey(), MAPPER.convertValue(entry.getValue(), Object.class));
                }
            }
            docs.add(ScenarioDocument.builder()
                    .id(id)
                    .key(key)
                    .title(summary)
                    .description(description)
                    .labels(labels)
                    .rawFields(raw)
                    .build());
        }
        return docs;
    }

    private String env(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return envLookup.apply(name.trim());
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a.trim();
        }
        if (b != null && !b.isBlank()) {
            return b.trim();
        }
        return null;
    }

    private static String text(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        return node.get(field).asText();
    }

    /**
     * Jira Cloud may return ADF (Atlassian Document Format) for description; flatten to plain text.
     */
    static String extractDescription(JsonNode description) {
        if (description == null || description.isNull()) {
            return "";
        }
        if (description.isTextual()) {
            return description.asText();
        }
        if (description.isObject() && description.has("content")) {
            StringBuilder sb = new StringBuilder();
            flattenAdf(description, sb);
            return sb.toString().trim();
        }
        return description.toString();
    }

    private static void flattenAdf(JsonNode node, StringBuilder sb) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isTextual()) {
            sb.append(node.asText());
            return;
        }
        if (node.has("text") && node.get("text").isTextual()) {
            sb.append(node.get("text").asText());
        }
        if (node.has("content") && node.get("content").isArray()) {
            for (JsonNode child : node.get("content")) {
                flattenAdf(child, sb);
                String type = child.has("type") ? child.get("type").asText("").toLowerCase(Locale.ROOT) : "";
                if ("paragraph".equals(type) || "heading".equals(type) || "bulletlist".equals(type)) {
                    sb.append('\n');
                }
            }
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
