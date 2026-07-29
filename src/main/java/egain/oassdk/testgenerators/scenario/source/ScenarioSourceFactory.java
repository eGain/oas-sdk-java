package egain.oassdk.testgenerators.scenario.source;

import egain.oassdk.config.AiScenarioConfig;
import egain.oassdk.core.exceptions.OASSDKException;

import java.util.Locale;

/**
 * Creates a {@link ScenarioSource} from {@link AiScenarioConfig}.
 */
public final class ScenarioSourceFactory {

    private ScenarioSourceFactory() {
    }

    public static ScenarioSource create(AiScenarioConfig config) throws OASSDKException {
        if (config == null || config.getScenarioSource() == null) {
            throw new OASSDKException("scenarioSource configuration is required");
        }
        String type = config.getScenarioSource().getType();
        String normalized = type != null ? type.trim().toLowerCase(Locale.ROOT) : "jira";
        return switch (normalized) {
            case "jira" -> new JiraScenarioSource();
            default -> throw new OASSDKException("Unsupported scenario source type: " + type
                    + " (supported: jira)");
        };
    }
}
