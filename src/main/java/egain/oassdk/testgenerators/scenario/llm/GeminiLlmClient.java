package egain.oassdk.testgenerators.scenario.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import egain.oassdk.config.AiScenarioConfig;
import egain.oassdk.core.exceptions.OASSDKException;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;

/**
 * Google Gemini generateContent API client.
 */
public final class GeminiLlmClient implements LlmClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AiScenarioConfig.ModelConfig modelConfig;
    private final HttpClient httpClient;
    private final Function<String, String> envLookup;

    public GeminiLlmClient(AiScenarioConfig.ModelConfig modelConfig) {
        this(modelConfig, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build(), System::getenv);
    }

    public GeminiLlmClient(AiScenarioConfig.ModelConfig modelConfig, HttpClient httpClient,
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
                : "https://generativelanguage.googleapis.com/v1beta");
        String model = modelConfig.getModel() != null ? modelConfig.getModel() : "gemini-2.0-flash";
        String url = base + "/models/" + model + ":generateContent?key="
                + URLEncoder.encode(apiKey, StandardCharsets.UTF_8);

        ObjectNode body = MAPPER.createObjectNode();
        ArrayNode contents = body.putArray("contents");
        ObjectNode user = contents.addObject();
        user.put("role", "user");
        ArrayNode parts = user.putArray("parts");
        String combined = "";
        if (request.getSystemPrompt() != null && !request.getSystemPrompt().isBlank()) {
            combined = request.getSystemPrompt() + "\n\n";
        }
        combined += nullToEmpty(request.getUserPrompt());
        parts.addObject().put("text", combined);

        ObjectNode generationConfig = body.putObject("generationConfig");
        generationConfig.put("temperature", request.getTemperature());
        generationConfig.put("maxOutputTokens", request.getMaxTokens());

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(120))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = send(httpRequest);
        try {
            JsonNode root = MAPPER.readTree(response.body());
            JsonNode candidates = root.path("candidates");
            if (!candidates.isArray() || candidates.isEmpty()) {
                throw new OASSDKException("Gemini response missing candidates: " + truncate(response.body(), 400));
            }
            JsonNode partsNode = candidates.get(0).path("content").path("parts");
            if (!partsNode.isArray() || partsNode.isEmpty()) {
                throw new OASSDKException("Gemini response missing content parts");
            }
            StringBuilder text = new StringBuilder();
            for (JsonNode part : partsNode) {
                if (part.has("text")) {
                    text.append(part.get("text").asText());
                }
            }
            if (text.isEmpty()) {
                throw new OASSDKException("Gemini response missing text");
            }
            return text.toString();
        } catch (IOException e) {
            throw new OASSDKException("Failed to parse Gemini response: " + e.getMessage(), e);
        }
    }

    private String resolveApiKey() throws OASSDKException {
        String envName = modelConfig.getApiKeyEnv() != null ? modelConfig.getApiKeyEnv() : "GOOGLE_API_KEY";
        String key = envLookup.apply(envName);
        if (key == null || key.isBlank()) {
            throw new OASSDKException("Gemini API key not found in env " + envName);
        }
        return key.trim();
    }

    private HttpResponse<String> send(HttpRequest httpRequest) throws OASSDKException {
        try {
            HttpResponse<String> response = httpClient.send(httpRequest,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new OASSDKException("Gemini API HTTP " + response.statusCode()
                        + ": " + truncate(response.body(), 500));
            }
            return response;
        } catch (IOException e) {
            throw new OASSDKException("Gemini API call failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OASSDKException("Gemini API call interrupted", e);
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
        return provider != null && ("gemini".equals(provider.trim().toLowerCase(Locale.ROOT))
                || "google".equals(provider.trim().toLowerCase(Locale.ROOT)));
    }
}
