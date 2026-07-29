package egain.oassdk.testgenerators.scenario.source;

import egain.oassdk.config.AiScenarioConfig;

/**
 * Request parameters for fetching scenarios from a {@link ScenarioSource}.
 */
public final class ScenarioSourceRequest {

    private final AiScenarioConfig.JiraConfig jira;
    private final String jqlOverride;

    public ScenarioSourceRequest(AiScenarioConfig.JiraConfig jira, String jqlOverride) {
        this.jira = jira;
        this.jqlOverride = jqlOverride;
    }

    public AiScenarioConfig.JiraConfig getJira() {
        return jira;
    }

    public String getJqlOverride() {
        return jqlOverride;
    }

    public String resolveJql() {
        if (jqlOverride != null && !jqlOverride.isBlank()) {
            return jqlOverride.trim();
        }
        return jira != null ? jira.getJql() : null;
    }
}
