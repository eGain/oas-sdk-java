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
 * OpenAI Chat Completions API client ({@code POST /chat/completions}).
 */
public final class OpenAiLlmClient implements LlmClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AiScenarioConfig.ModelConfig modelConfig;
    private final HttpClient httpClient;
    private final Function<String, String> envLookup;

    public OpenAiLlmClient(AiScenarioConfig.ModelConfig modelConfig) {
        this(modelConfig, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build(), System::getenv);
    }

    public OpenAiLlmClient(AiScenarioConfig.ModelConfig modelConfig, HttpClient httpClient,
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
                : "https://api.openai.com/v1");

        ObjectNode body = MAPPER.createObjectNode();
        body.put("model", modelConfig.getModel());
        body.put("temperature", request.getTemperature());
        body.put("max_tokens", request.getMaxTokens());
        ArrayNode messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content", nullToEmpty(request.getSystemPrompt()));
        messages.addObject().put("role", "user").put("content", nullToEmpty(request.getUserPrompt()));

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(base + "/chat/completions"))
                .timeout(Duration.ofSeconds(120))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = send(httpRequest);
        try {
            JsonNode root = MAPPER.readTree(response.body());
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                throw new OASSDKException("OpenAI response missing choices: " + truncate(response.body(), 400));
            }
            String content = choices.get(0).path("message").path("content").asText(null);
            if (content == null || content.isBlank()) {
                throw new OASSDKException("OpenAI response missing message content");
            }
            return content;
        } catch (IOException e) {
            throw new OASSDKException("Failed to parse OpenAI response: " + e.getMessage(), e);
        }
    }

    private String resolveApiKey() throws OASSDKException {
        String envName = modelConfig.getApiKeyEnv() != null ? modelConfig.getApiKeyEnv() : "OPENAI_API_KEY";
        String key = envLookup.apply(envName);
        if (key == null || key.isBlank()) {
            throw new OASSDKException("OpenAI API key not found in env " + envName);
        }
        return key.trim();
    }

    private HttpResponse<String> send(HttpRequest httpRequest) throws OASSDKException {
        try {
            HttpResponse<String> response = httpClient.send(httpRequest,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new OASSDKException("OpenAI API HTTP " + response.statusCode()
                        + ": " + truncate(response.body(), 500));
            }
            return response;
        } catch (IOException e) {
            throw new OASSDKException("OpenAI API call failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OASSDKException("OpenAI API call interrupted", e);
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
        return provider != null && "openai".equals(provider.trim().toLowerCase(Locale.ROOT));
    }
}
