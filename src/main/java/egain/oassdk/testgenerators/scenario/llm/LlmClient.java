package egain.oassdk.testgenerators.scenario.llm;

import egain.oassdk.core.exceptions.OASSDKException;

/**
 * Language-model client used to turn scenario text + OpenAPI context into structured JSON.
 */
public interface LlmClient {

    /**
     * Complete a chat-style prompt and return the model text (expected to be JSON).
     */
    String complete(LlmRequest request) throws OASSDKException;
}
