package egain.oassdk.testgenerators.scenario.llm;

import egain.oassdk.config.AiScenarioConfig;
import egain.oassdk.core.exceptions.OASSDKException;

import java.util.Locale;

/**
 * Creates an {@link LlmClient} for the active model in {@link AiScenarioConfig}.
 */
public final class LlmClientFactory {

    private LlmClientFactory() {
    }

    public static LlmClient create(AiScenarioConfig config) throws OASSDKException {
        if (config == null) {
            throw new OASSDKException("AiScenarioConfig is required");
        }
        AiScenarioConfig.ModelConfig model = config.resolveActiveModel();
        if (model == null) {
            throw new OASSDKException("No model configured for activeModel=" + config.getActiveModel());
        }
        String provider = model.getProvider() != null
                ? model.getProvider().trim().toLowerCase(Locale.ROOT)
                : config.getActiveModel();
        if (OpenAiLlmClient.supports(provider)) {
            return new OpenAiLlmClient(model);
        }
        if (AnthropicLlmClient.supports(provider)) {
            return new AnthropicLlmClient(model);
        }
        if (GeminiLlmClient.supports(provider)) {
            return new GeminiLlmClient(model);
        }
        throw new OASSDKException("Unsupported LLM provider: " + provider
                + " (supported: openai, anthropic, gemini)");
    }
}
