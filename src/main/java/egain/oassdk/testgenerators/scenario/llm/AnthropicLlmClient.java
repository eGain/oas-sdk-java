package egain.oassdk.testgenerators.scenario.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import egain.oassdk.config.AiScenarioConfig;
import egain.oassdk.core.exceptions.OASSDKException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;

/**
 * Anthropic Messages API client ({@code POST /v1/messages}).
 */
public final class AnthropicLlmClient implements LlmClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private final AiScenarioConfig.ModelConfig modelConfig;
    private final HttpClient httpClient;
    private final Function<String, String> envLookup;

    public AnthropicLlmClient(AiScenarioConfig.ModelConfig modelConfig) {
        this(modelConfig, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build(), System::getenv);
    }

    public AnthropicLlmClient(AiScenarioConfig.ModelConfig modelConfig, HttpClient httpClient,
                              Function<String, String> envLookup) {
        this.modelConfig = Objects.requireNonNull(modelConfig, "modelConfig");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.envLookup = envLookup != null ? envLookup : System::getenv;
    }

    @Override
    public String complete(LlmRequest request) throws OASSDKException {
        String apiKey = resolveApiKey();
        String base = trimTrailingSlash(modelConfig.getBaseUrl() != null
                ? modelConfig.getBaseUrl()
                : "https://api.anthropic.com");

        ObjectNode body = MAPPER.createObjectNode();
        body.put("model", modelConfig.getModel());
        body.put("max_tokens", request.getMaxTokens());
        body.put("temperature", request.getTemperature());
        if (request.getSystemPrompt() != null && !request.getSystemPrompt().isBlank()) {
            body.put("system", request.getSystemPrompt());
        }
        ArrayNode messages = body.putArray("messages");
        messages.addObject().put("role", "user").put("content", nullToEmpty(request.getUserPrompt()));

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(base + "/v1/messages"))
                .timeout(Duration.ofSeconds(120))
                .header("x-api-key", apiKey)
                .header("anthropic-version", ANTHROPIC_VERSION)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = send(httpRequest);
        try {
            JsonNode root = MAPPER.readTree(response.body());
            JsonNode content = root.path("content");
            if (!content.isArray() || content.isEmpty()) {
                throw new OASSDKException("Anthropic response missing content: " + truncate(response.body(), 400));
            }
            StringBuilder text = new StringBuilder();
            for (JsonNode block : content) {
                if ("text".equals(block.path("type").asText()) && block.has("text")) {
                    text.append(block.get("text").asText());
                }
            }
            if (text.isEmpty()) {
                throw new OASSDKException("Anthropic response missing text blocks");
            }
            return text.toString();
        } catch (IOException e) {
            throw new OASSDKException("Failed to parse Anthropic response: " + e.getMessage(), e);
        }
    }

    private String resolveApiKey() throws OASSDKException {
        String envName = modelConfig.getApiKeyEnv() != null ? modelConfig.getApiKeyEnv() : "ANTHROPIC_API_KEY";
        String key = envLookup.apply(envName);
        if (key == null || key.isBlank()) {
            throw new OASSDKException("Anthropic API key not found in env " + envName);
        }
        return key.trim();
    }

    private HttpResponse<String> send(HttpRequest httpRequest) throws OASSDKException {
        try {
            HttpResponse<String> response = httpClient.send(httpRequest,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new OASSDKException("Anthropic API HTTP " + response.statusCode()
                        + ": " + truncate(response.body(), 500));
            }
            return response;
        } catch (IOException e) {
            throw new OASSDKException("Anthropic API call failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OASSDKException("Anthropic API call interrupted", e);
        }
    }

    private static String trimTrailingSlash(String s) {
        if (s == null) {
            return "";
        }
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    public static boolean supports(String provider) {
        return provider != null && "anthropic".equals(provider.trim().toLowerCase(Locale.ROOT));
    }
}
